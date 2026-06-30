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
import com.example.bankjatahapp.ui.nasabah.NasabahActivity

class PenarikanNasabahFragment : Fragment() {

    private var _binding: FragmentPenarikanNasabahBinding? = null
    private val binding get() = _binding!!

    private var saldoTabungan: Double = 0.0
    private var saldoAfiliasi: Double = 0.0
    private var saldoTerpilih: Double = 0.0
    private var jenisTerpilih: String = "tabungan"

    private var config: SystemConfig?     = null
    private var nasabahData: NasabahData? = null
    private var isAktif: Boolean          = false
    private var biayaAdmin: Double        = 0.0

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
        cekPengajuanBerhentiAktif()
    }

    private fun cekPengajuanBerhentiAktif() {
        lifecycleScope.launch {
            val adaPengajuan = (activity as? NasabahActivity)?.cekAdaPengajuanAktif() ?: false
            if (adaPengajuan && _binding != null) {
                binding.btnAjukanPenarikan.isEnabled = false
                binding.tvWarningNominal.text =
                    "⚠️ Anda memiliki pengajuan penutupan akun aktif. Pencairan saldo akan diproses oleh sistem secara otomatis."
                binding.tvWarningNominal.visibility = View.VISIBLE
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                nasabahData = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser) } }
                    .decodeSingle<NasabahData>()

                config = client.postgrest
                    .from("system_config")
                    .select { filter { eq("id_config", 1) } }
                    .decodeSingle<SystemConfig>()

                biayaAdmin    = config?.biayaAdminPencairan ?: 0.0
                isAktif       = nasabahData?.kategoriNasabah == "aktif"
                saldoTabungan = dompet.saldoNasabah
                saldoAfiliasi = dompet.saldoAfiliasi

                binding.tvSaldoTabunganNilai.text = formatRupiah(saldoTabungan)
                binding.tvSaldoAfiliasiNilai.text = formatRupiah(saldoAfiliasi)

                // Tampilkan syarat di bawah card tabungan
                val cfg = config
                val threshold  = cfg?.thresholdSaldoNasabah ?: 120000.0
                val minSisa    = cfg?.minSisaSaldoNasabah ?: 20000.0
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val biayaInfo  = if (biayaAdmin > 0) formatRupiah(biayaAdmin) else "Gratis"

                // Syarat saldo tabungan — ditampilkan langsung di card sebagai informasi
                binding.tvSyaratTabungan.text = buildString {
                    append("• Saldo min. ${formatRupiah(threshold)} untuk mulai tarik\n")
                    append("• Sisa saldo min. ${formatRupiah(minSisa)} setelah penarikan\n")
                    append("• Minimal Bintang $minBintang\n")
                    append("• Biaya admin: $biayaInfo")
                }

                // Syarat saldo bonus afiliasi
                val minAfiliasi = cfg?.minPenarikanAfiliasi ?: 10000.0
                binding.tvSyaratAfiliasi.text = buildString {
                    append("• Minimal Bintang $minBintang\n")
                    append("• Min. penarikan: ${formatRupiah(minAfiliasi)}\n")
                    append("• Bisa ditarik sampai Rp 0\n")
                    append("• Biaya admin: $biayaInfo")
                }

                // Rekening dari profil
                val noRek    = nasabahData?.noRekening ?: ""
                val bank     = nasabahData?.bankCode ?: ""
                val atasNama = nasabahData?.atasNamaRekening ?: ""

                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listOf("$bank - $noRek ($atasNama)")
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerBank.adapter = adapter
                rekeningDipilih = Triple(bank, noRek, atasNama)

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

        // Reset state seleksi & check radio button agar tidak bertabrakan
        binding.optionTabungan.isSelected = false
        binding.optionAfiliasi.isSelected = false
        binding.radioTabungan.isChecked = false
        binding.radioAfiliasi.isChecked = false

        // Atur background kartu secara dinamis & picu pendelegasian status isSelected ke XML
        if (jenis == "tabungan") {
            binding.optionTabungan.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_active)
            binding.optionTabungan.isSelected = true
            binding.radioTabungan.isChecked = true

            binding.optionAfiliasi.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_inactive)
        } else {
            binding.optionAfiliasi.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_active)
            binding.optionAfiliasi.isSelected = true
            binding.radioAfiliasi.isChecked = true

            binding.optionTabungan.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_inactive)
        }

        // Cek syarat — tampilkan warning jika belum memenuhi, disable input
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

    // Semua nilai dari system_config — tidak ada yang hardcode
    private fun cekSyaratPenarikan(jenis: String): String? {
        val cfg = config
        return when (jenis) {
            "tabungan" -> {
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val levelSaat  = nasabahData?.levelBintang ?: 1
                if (levelSaat < minBintang) {
                    return "⚠ Penarikan tabungan membutuhkan minimal Bintang $minBintang\nLevel Anda: Bintang $levelSaat"
                }
                val threshold = cfg?.thresholdSaldoNasabah ?: 120000.0
                if (saldoTabungan < threshold) {
                    return "⚠ Saldo tabungan harus ≥ ${formatRupiah(threshold)} untuk mulai menarik\nSaldo Anda: ${formatRupiah(saldoTabungan)}"
                }
                null
            }
            "afiliasi" -> {
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val levelSaat  = nasabahData?.levelBintang ?: 1
                if (levelSaat < minBintang) {
                    return "⚠ Penarikan bonus/afiliasi membutuhkan minimal Bintang $minBintang\nLevel Anda: Bintang $levelSaat"
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
                val minSisa = cfg?.minSisaSaldoNasabah ?: 20000.0
                "Min. penarikan dinamis · Sisa wajib ${formatRupiah(minSisa)}"
            }
            "afiliasi" -> {
                val min        = cfg?.minPenarikanAfiliasi ?: 10000.0
                val minBintang = cfg?.minBintangPenarikan ?: 3
                "Min. ${formatRupiah(min)} · Bintang $minBintang ke atas"
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

        binding.btn50k.setOnClickListener   { setNominal(50_000) }
        binding.btn100k.setOnClickListener  { setNominal(100_000) }
        binding.btn200k.setOnClickListener  { setNominal(200_000) }
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

        when (jenisTerpilih) {
            "tabungan" -> {
                // Tidak ada minimum nominal khusus untuk tabungan —
                // hanya ada syarat threshold (sudah dicek di cekSyaratPenarikan)
                // dan sisa saldo minimal
                val minSisa = cfg?.minSisaSaldoNasabah ?: 20000.0
                when {
                    nominal <= 0 -> binding.cardRingkasan.visibility = View.GONE
                    nominal > saldoTerpilih -> {
                        binding.tvWarningNominal.text = "⚠ Nominal melebihi saldo tersedia (${formatRupiah(saldoTerpilih)})"
                        binding.tvWarningNominal.visibility = View.VISIBLE
                        binding.cardRingkasan.visibility = View.GONE
                    }
                    (saldoTerpilih - nominal) < minSisa -> {
                        binding.tvWarningNominal.text =
                            "⚠ Harus menyisakan minimal ${formatRupiah(minSisa)} di dompet"
                        binding.tvWarningNominal.visibility = View.VISIBLE
                        binding.cardRingkasan.visibility = View.GONE
                    }
                    else -> tampilkanRingkasan(nominal)
                }
            }
            "afiliasi" -> {
                val minAfiliasi = cfg?.minPenarikanAfiliasi ?: 10000.0
                when {
                    nominal <= 0 -> binding.cardRingkasan.visibility = View.GONE
                    nominal < minAfiliasi -> {
                        binding.tvWarningNominal.text = "⚠ Minimum penarikan bonus/afiliasi adalah ${formatRupiah(minAfiliasi)}"
                        binding.tvWarningNominal.visibility = View.VISIBLE
                        binding.cardRingkasan.visibility = View.GONE
                    }
                    nominal > saldoTerpilih -> {
                        binding.tvWarningNominal.text = "⚠ Nominal melebihi saldo tersedia (${formatRupiah(saldoTerpilih)})"
                        binding.tvWarningNominal.visibility = View.VISIBLE
                        binding.cardRingkasan.visibility = View.GONE
                    }
                    else -> tampilkanRingkasan(nominal)
                }
            }
        }
    }

    private fun tampilkanRingkasan(nominal: Double) {
        val bersih = nominal - biayaAdmin
        binding.tvRingkasanJumlah.text = formatRupiah(nominal)
        binding.tvRingkasanBiaya.text  = if (biayaAdmin > 0) formatRupiah(biayaAdmin) else "Gratis"
        binding.tvRingkasanBersih.text = formatRupiah(bersih)
        binding.cardRingkasan.visibility = View.VISIBLE
    }

    private fun ajukanPenarikan() {
        val nominalStr = binding.etNominal.text.toString().trim()
        val nominal    = nominalStr.toDoubleOrNull()
        val cfg        = config

        if (nominal == null || nominal <= 0) {
            binding.tilNominal.error = "Masukkan nominal penarikan"
            return
        }

        when (jenisTerpilih) {
            "tabungan" -> {
                val minSisa = cfg?.minSisaSaldoNasabah ?: 20000.0
                if (nominal > saldoTerpilih) {
                    binding.tilNominal.error = "Nominal melebihi saldo tersedia"
                    return
                }
                if ((saldoTerpilih - nominal) < minSisa) {
                    binding.tilNominal.error = "Harus menyisakan minimal ${formatRupiah(minSisa)}"
                    return
                }
            }
            "afiliasi" -> {
                val minAfiliasi = cfg?.minPenarikanAfiliasi ?: 10000.0
                if (nominal < minAfiliasi) {
                    binding.tilNominal.error = "Nominal minimal ${formatRupiah(minAfiliasi)}"
                    return
                }
                if (nominal > saldoTerpilih) {
                    binding.tilNominal.error = "Nominal melebihi saldo tersedia"
                    return
                }
            }
        }
        binding.tilNominal.error = null

        val kodeBank    = rekeningDipilih.first
        val noRekening  = rekeningDipilih.second
        val namaPemilik = rekeningDipilih.third

        if (kodeBank.isEmpty() || noRekening.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Data rekening belum tersedia. Lengkapi rekening di Pengaturan Akun.",
                Toast.LENGTH_LONG
            ).show()
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