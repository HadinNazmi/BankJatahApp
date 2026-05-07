package com.example.bankjatahapp.ui.nasabah.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.DompetUser
import com.example.bankjatahapp.data.model.MasterBank
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.SystemConfig
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentPenarikanNasabahBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

class PenarikanNasabahFragment : Fragment() {

    private var _binding: FragmentPenarikanNasabahBinding? = null
    private val binding get() = _binding!!

    // Data dompet
    private var saldoTabungan: Double = 0.0   // saldo_nasabah
    private var saldoAfiliasi: Double = 0.0   // saldo_afiliasi / bonus
    private var saldoTerpilih: Double = 0.0
    private var jenisTerpilih: String = "tabungan" // "tabungan" atau "afiliasi"

    // Config & nasabah dari DB
    private var config: SystemConfig?   = null
    private var nasabahData: NasabahData? = null
    private var isAktif: Boolean        = false

    // Biaya admin dinamis
    private var biayaAdmin: Double = 0.0

    // Variabel baru untuk menampung data rekening nasabah (Triple: Kode Bank, No Rek, Atas Nama)
    private var rekeningDipilih: Triple<String, String, String> = Triple("", "", "")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPenarikanNasabahBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupListeners()
    }

    // ===== LOAD SEMUA DATA DARI SUPABASE =====
    private fun loadData() {
        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // 1. Dompet user
                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                // 2. Data nasabah (untuk cek aktif/pasif & level bintang & data rekening)
                nasabahData = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser) } }
                    .decodeSingle<NasabahData>()

                // 3. System config
                config = client.postgrest
                    .from("system_config")
                    .select { filter { eq("id_config", 1) } }
                    .decodeSingle<SystemConfig>()

                // 4. Update variabel lokal
                biayaAdmin = config?.biayaAdminPencairan ?: 0.0
                isAktif = nasabahData?.kategoriNasabah == "aktif"
                saldoTabungan = dompet.saldoNasabah
                saldoAfiliasi = dompet.saldoAfiliasi

                // Tampilkan nilai saldo di opsi pilihan
                binding.tvSaldoTabunganNilai.text = formatRupiah(saldoTabungan)
                binding.tvSaldoAfiliasiNilai.text = formatRupiah(saldoAfiliasi)

                // 5. PERUBAHAN: Setup data rekening nasabah ke Spinner (Tanpa fetch master bank)
                val noRek = nasabahData?.noRekening ?: ""
                val bank = nasabahData?.bankCode ?: ""
                val atasNama = nasabahData?.atasNamaRekening ?: ""

                // Isi spinner dengan 1 item rekening milik nasabah
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listOf("$bank - $noRek ($atasNama)")
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerBank.adapter = adapter

                // Simpan ke variabel Triple untuk proses submit
                rekeningDipilih = Triple(bank, noRek, atasNama)

                // Default pilih tabungan
                pilihJenis("tabungan")

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pilihJenis(jenis: String) {
        jenisTerpilih = jenis
        saldoTerpilih = if (jenis == "tabungan") saldoTabungan else saldoAfiliasi

        binding.tvJenisSaldoLabel.text = when (jenis) {
            "tabungan" -> "Saldo Tabungan Tersedia"
            else       -> "Saldo Bonus Afiliasi Tersedia"
        }
        binding.tvSaldoTersedia.text = formatRupiah(saldoTerpilih)

        if (jenis == "tabungan") {
            binding.optionTabungan.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_active)
            binding.tvSaldoTabunganNilai.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.white))
            binding.radioTabungan.isChecked = true
            binding.optionAfiliasi.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_inactive)
            binding.tvSaldoAfiliasiNilai.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.black))
            binding.radioAfiliasi.isChecked = false
        } else {
            binding.optionAfiliasi.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_active)
            binding.tvSaldoAfiliasiNilai.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.white))
            binding.radioAfiliasi.isChecked = true
            binding.optionTabungan.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_inactive)
            binding.tvSaldoTabunganNilai.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.black))
            binding.radioTabungan.isChecked = false
        }

        val syaratInfo = cekSyaratPenarikan(jenis)
        if (syaratInfo != null) {
            binding.tvWarningNominal.text = syaratInfo
            binding.tvWarningNominal.visibility = View.VISIBLE
            binding.etNominal.isEnabled = false
            binding.btn50k.isEnabled    = false
            binding.btn100k.isEnabled   = false
            binding.btn200k.isEnabled   = false
            binding.btnSemua.isEnabled  = false
            binding.cardRingkasan.visibility = View.GONE
        } else {
            binding.tvWarningNominal.visibility = View.GONE
            binding.etNominal.isEnabled = true
            binding.btn50k.isEnabled    = true
            binding.btn100k.isEnabled   = true
            binding.btn200k.isEnabled   = true
            binding.btnSemua.isEnabled  = true
        }

        binding.tvMinimumInfo.text = getMinimumInfo(jenis)
        binding.etNominal.setText("")
        binding.cardRingkasan.visibility = View.GONE
    }

    private fun cekSyaratPenarikan(jenis: String): String? {
        val cfg = config
        return when (jenis) {
            "tabungan" -> {
                if (isAktif) {
                    val threshold = cfg?.thresholdSaldoNasabahAktif ?: 120000.0
                    if (saldoTabungan < threshold) {
                        return "⚠ Nasabah aktif baru bisa menarik jika saldo ≥ ${formatRupiah(threshold)}\nSaldo Anda: ${formatRupiah(saldoTabungan)}"
                    }
                } else {
                    val minPasif = cfg?.minPenarikanNasabahPasif ?: 25000.0
                    if (saldoTabungan < minPasif) {
                        return "⚠ Saldo tidak mencukupi minimum penarikan ${formatRupiah(minPasif)}"
                    }
                }
                null
            }
            "afiliasi" -> {
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val levelSaat  = nasabahData?.levelBintang ?: 1
                if (levelSaat < minBintang) {
                    return "⚠ Penarikan bonus/afiliasi membutuhkan minimal Bintang $minBintang\nLevel Anda saat ini: Bintang $levelSaat"
                }
                if (saldoAfiliasi <= 0) {
                    return "⚠ Saldo bonus/afiliasi Anda kosong"
                }
                null
            }
            else -> null
        }
    }

    private fun getMinimumInfo(jenis: String): String {
        val cfg = config
        return when (jenis) {
            "tabungan" -> {
                val min = if (isAktif) cfg?.minPenarikanNasabahAktif ?: 15000.0
                else        cfg?.minPenarikanNasabahPasif  ?: 25000.0
                "Minimum penarikan: ${formatRupiah(min)}"
            }
            "afiliasi" -> {
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val minNominal = cfg?.minPenarikanKomisi  ?: 10000.0
                "Tersedia untuk Bintang $minBintang ke atas · Min: ${formatRupiah(minNominal)}"
            }
            else -> ""
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.optionTabungan.setOnClickListener { pilihJenis("tabungan") }
        binding.radioTabungan.setOnClickListener  { pilihJenis("tabungan") }
        binding.optionAfiliasi.setOnClickListener { pilihJenis("afiliasi") }
        binding.radioAfiliasi.setOnClickListener  { pilihJenis("afiliasi") }

        binding.btn50k.setOnClickListener  { setNominal(50_000) }
        binding.btn100k.setOnClickListener { setNominal(100_000) }
        binding.btn200k.setOnClickListener { setNominal(200_000) }
        binding.btnSemua.setOnClickListener { setNominal(saldoTerpilih.toLong()) }

        binding.etNominal.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validasiDanTampilRingkasan((s.toString().toLongOrNull() ?: 0L).toDouble())
            }
        })

        binding.btnAjukanPenarikan.setOnClickListener { ajukanPenarikan() }
    }

    private fun setNominal(nominal: Long) {
        binding.etNominal.setText(nominal.toString())
        binding.etNominal.setSelection(binding.etNominal.text?.length ?: 0)
    }

    private fun validasiDanTampilRingkasan(nominal: Double) {
        binding.tvWarningNominal.visibility = View.GONE
        binding.tilNominal.error = null

        val cfg = config
        val minPenarikan = when {
            jenisTerpilih == "tabungan" && isAktif -> cfg?.minPenarikanNasabahAktif ?: 15000.0
            jenisTerpilih == "tabungan"            -> cfg?.minPenarikanNasabahPasif ?: 25000.0
            else                                   -> cfg?.minPenarikanKomisi       ?: 10000.0
        }

        when {
            nominal <= 0 -> {
                binding.cardRingkasan.visibility = View.GONE
            }
            nominal < minPenarikan -> {
                binding.tvWarningNominal.text = "⚠ Minimum penarikan adalah ${formatRupiah(minPenarikan)}"
                binding.tvWarningNominal.visibility = View.VISIBLE
                binding.cardRingkasan.visibility = View.GONE
            }
            nominal > saldoTerpilih -> {
                binding.tvWarningNominal.text = "⚠ Nominal melebihi saldo tersedia (${formatRupiah(saldoTerpilih)})"
                binding.tvWarningNominal.visibility = View.VISIBLE
                binding.cardRingkasan.visibility = View.GONE
            }
            jenisTerpilih == "tabungan" && isAktif && cfg != null &&
                    (saldoTerpilih - nominal) < cfg.minSisaSaldoNasabahAktif -> {
                binding.tvWarningNominal.text =
                    "⚠ Harus menyisakan minimal ${formatRupiah(cfg.minSisaSaldoNasabahAktif)} di dompet"
                binding.tvWarningNominal.visibility = View.VISIBLE
                binding.cardRingkasan.visibility = View.GONE
            }
            else -> {
                val bersih = nominal - biayaAdmin
                binding.tvRingkasanJumlah.text = formatRupiah(nominal)
                binding.tvRingkasanBiaya.text  = if (biayaAdmin > 0) formatRupiah(biayaAdmin) else "Gratis"
                binding.tvRingkasanBersih.text = formatRupiah(bersih)
                binding.cardRingkasan.visibility = View.VISIBLE
            }
        }
    }

    private fun ajukanPenarikan() {
        val nominalStr  = binding.etNominal.text.toString().trim()
        val nominal = nominalStr.toDoubleOrNull()
        val cfg     = config

        val minPenarikan = when {
            jenisTerpilih == "tabungan" && isAktif -> cfg?.minPenarikanNasabahAktif ?: 15000.0
            jenisTerpilih == "tabungan"            -> cfg?.minPenarikanNasabahPasif ?: 25000.0
            else                                   -> cfg?.minPenarikanKomisi       ?: 10000.0
        }

        if (nominal == null || nominal < minPenarikan) {
            binding.tilNominal.error = "Nominal minimal ${formatRupiah(minPenarikan)}"
            return
        }
        if (nominal > saldoTerpilih) {
            binding.tilNominal.error = "Nominal melebihi saldo tersedia"
            return
        }
        if (jenisTerpilih == "tabungan" && isAktif && cfg != null) {
            if ((saldoTerpilih - nominal) < cfg.minSisaSaldoNasabahAktif) {
                binding.tilNominal.error = "Harus menyisakan minimal ${formatRupiah(cfg.minSisaSaldoNasabahAktif)}"
                return
            }
        }
        binding.tilNominal.error = null

        // PERUBAHAN: Gunakan data dari rekeningDipilih yang sudah diisi di loadData()
        val kodeBank    = rekeningDipilih.first
        val noRekening  = rekeningDipilih.second
        val namaPemilik = rekeningDipilih.third

        if (kodeBank.isEmpty() || noRekening.isEmpty()) {
            Toast.makeText(requireContext(), "Data rekening belum tersedia. Lengkapi profil Anda.", Toast.LENGTH_SHORT).show()
            return
        }

        submitPenarikan(nominal, kodeBank, noRekening, namaPemilik)
    }

    private fun submitPenarikan(
        nominal: Double,
        kodeBank: String,
        noRekening: String,
        namaPemilik: String
    ) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session tidak ditemukan")

                val jumlahBersih  = nominal - biayaAdmin
                val kodePencairan = "PCR-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"

                val sumberDana = when (jenisTerpilih) {
                    "tabungan" -> "setoran_minyak"
                    "afiliasi" -> "komisi_afiliasi"
                    else       -> "setoran_minyak"
                }

                val payload = buildJsonObject {
                    put("kode_pencairan",        kodePencairan)
                    put("id_user",               idUser)
                    put("jumlah_tarik",          nominal)
                    put("biaya_admin",           biayaAdmin)
                    put("jumlah_bersih",         jumlahBersih)
                    put("metode_pencairan",      "manual")
                    put("bank_tujuan",           kodeBank)
                    put("no_rekening_tujuan",    noRekening)
                    put("nama_pemilik_rekening", namaPemilik)
                    put("status_request",        "menunggu")
                    put("sumber_dana",           sumberDana)
                    put("is_tutup_akun",         false)
                }

                client.postgrest.from("pencairan_dana").insert(payload)

                setLoading(false)
                Toast.makeText(
                    requireContext(),
                    "✓ Pengajuan penarikan berhasil!\nKode: $kodePencairan\nMenunggu persetujuan admin.",
                    Toast.LENGTH_LONG
                ).show()

                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                setLoading(false)
                val pesan = when {
                    e.message?.contains("Saldo tidak mencukupi") == true -> e.message!!
                    e.message?.contains("Minimal saldo")         == true -> e.message!!
                    e.message?.contains("menyisakan")            == true -> e.message!!
                    e.message?.contains("Bintang")               == true -> e.message!!
                    e.message?.contains("Maaf, penarikan")       == true -> e.message!!
                    else -> "Gagal mengajukan: ${e.message}"
                }
                Toast.makeText(requireContext(), pesan, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnAjukanPenarikan.isEnabled = !loading
        binding.btnAjukanPenarikan.text = if (loading) "Memproses..." else "Ajukan Penarikan"
    }

    private fun formatRupiah(nominal: Double): String =
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(nominal).replace(",00", "")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}