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
    private var saldoTabungan: Double  = 0.0   // saldo_nasabah
    private var saldoAfiliasi: Double  = 0.0   // saldo_afiliasi / bonus
    private var saldoTerpilih: Double  = 0.0
    private var jenisTerpilih: String  = "tabungan" // "tabungan" atau "afiliasi"

    // Config dari DB
    private var config: SystemConfig?   = null
    private var nasabahData: NasabahData? = null
    private var isAktif: Boolean        = false

    private val biayaAdmin: Double = 0.0
    private val listBank = mutableListOf<MasterBank>()

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

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // Ambil semua data sekaligus
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

                // Cek aktif/pasif berdasarkan kategori_nasabah
                isAktif = nasabahData?.kategoriNasabah == "aktif"

                saldoTabungan = dompet.saldoNasabah
                saldoAfiliasi = dompet.saldoAfiliasi

                // Tampilkan nilai di opsi pilihan
                binding.tvSaldoTabunganNilai.text = formatRupiah(saldoTabungan)
                binding.tvSaldoAfiliasiNilai.text = formatRupiah(saldoAfiliasi)

                // Load bank
                val banks = client.postgrest
                    .from("master_bank")
                    .select { filter { eq("status_bank", "aktif") } }
                    .decodeList<MasterBank>()

                listBank.clear()
                listBank.addAll(banks)
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listBank.map { "${it.kodeBank} - ${it.namaBank}" }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerBank.adapter = adapter

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

        // Update label saldo aktif
        binding.tvJenisSaldoLabel.text = when (jenis) {
            "tabungan"  -> "Saldo Tabungan Tersedia"
            else        -> "Saldo Bonus Afiliasi Tersedia"
        }
        binding.tvSaldoTersedia.text = formatRupiah(saldoTerpilih)

        // Update visual opsi
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

        // Cek syarat berdasarkan jenis saldo
        val cfg = config
        val syaratInfo = cekSyaratPenarikan(jenis, cfg)

        if (syaratInfo != null) {
            // Ada syarat yang tidak terpenuhi
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

        // Update info minimum di card saldo
        val minInfo = getMinimumInfo(jenis, cfg)
        binding.tvMinimumInfo.text = minInfo

        binding.etNominal.setText("")
        binding.cardRingkasan.visibility = View.GONE
    }

    // ===== CEK SYARAT PENARIKAN =====
    // Return null = boleh tarik, return String = pesan error syarat tidak terpenuhi
    private fun cekSyaratPenarikan(jenis: String, cfg: SystemConfig?): String? {
        return when (jenis) {
            "tabungan" -> {
                // Nasabah aktif: cek threshold saldo minimum
                if (isAktif && cfg != null) {
                    if (saldoTabungan < cfg.thresholdSaldoNasabahAktif) {
                        return "⚠ Nasabah aktif baru bisa menarik jika saldo ≥ ${formatRupiah(cfg.thresholdSaldoNasabahAktif)}\nSaldo Anda: ${formatRupiah(saldoTabungan)}"
                    }
                }
                // Pasif: cek saldo minimal saja
                val minPasif = cfg?.minPenarikanNasabahPasif ?: 25000.0
                if (saldoTabungan < minPasif) {
                    return "⚠ Saldo tidak mencukupi minimum penarikan ${formatRupiah(minPasif)}"
                }
                null
            }
            "afiliasi" -> {
                // Saldo afiliasi/bonus hanya bisa ditarik jika level bintang cukup
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

    private fun getMinimumInfo(jenis: String, cfg: SystemConfig?): String {
        return when (jenis) {
            "tabungan" -> {
                val min = if (isAktif) cfg?.minPenarikanNasabahAktif ?: 15000.0
                else cfg?.minPenarikanNasabahPasif ?: 25000.0
                "Minimum penarikan: ${formatRupiah(min)}"
            }
            "afiliasi" -> {
                val minBintang = cfg?.minBintangPenarikan ?: 3
                "Tersedia untuk Bintang $minBintang ke atas"
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
                val nominal = s.toString().toLongOrNull() ?: 0L
                validasiDanTampilRingkasan(nominal.toDouble())
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
            jenisTerpilih == "tabungan" && isAktif ->
                cfg?.minPenarikanNasabahAktif ?: 15000.0
            jenisTerpilih == "tabungan" ->
                cfg?.minPenarikanNasabahPasif ?: 25000.0
            else ->
                cfg?.minPenarikanKomisi ?: 10000.0
        }

        when {
            nominal <= 0 -> binding.cardRingkasan.visibility = View.GONE

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

            // Cek sisa saldo minimal untuk nasabah aktif saldo tabungan
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
        val noRekening  = binding.etNoRekening.text.toString().trim()
        val namaPemilik = binding.etNamaPemilik.text.toString().trim()

        val nominal = nominalStr.toDoubleOrNull()
        val cfg     = config

        val minPenarikan = when {
            jenisTerpilih == "tabungan" && isAktif -> cfg?.minPenarikanNasabahAktif ?: 15000.0
            jenisTerpilih == "tabungan"            -> cfg?.minPenarikanNasabahPasif ?: 25000.0
            else                                   -> cfg?.minPenarikanKomisi ?: 10000.0
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

        val selectedIndex = binding.spinnerBank.selectedItemPosition
        if (listBank.isEmpty() || selectedIndex < 0) {
            Toast.makeText(requireContext(), "Pilih bank tujuan", Toast.LENGTH_SHORT).show()
            return
        }

        if (noRekening.isEmpty()) {
            binding.tilNoRekening.error = "Nomor rekening tidak boleh kosong"
            return
        }
        binding.tilNoRekening.error = null

        if (namaPemilik.isEmpty()) {
            binding.tilNamaPemilik.error = "Nama pemilik tidak boleh kosong"
            return
        }
        binding.tilNamaPemilik.error = null

        submitPenarikan(nominal, listBank[selectedIndex].kodeBank, noRekening, namaPemilik)
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
                val kodePencairan = "PCR-${UUID.randomUUID().toString().replace("-","").take(12).uppercase()}"

                // Tentukan sumber_dana
                val sumberDana = when (jenisTerpilih) {
                    "tabungan" -> "setoran_minyak"
                    "afiliasi" -> "komisi_afiliasi"
                    else       -> "setoran_minyak"
                }

                // Insert ke pencairan_dana — trigger DB yang potong saldo otomatis
                val payload = buildJsonObject {
                    put("kode_pencairan",         kodePencairan)
                    put("id_user",                idUser)
                    put("jumlah_tarik",           nominal)
                    put("biaya_admin",            biayaAdmin)
                    put("jumlah_bersih",          jumlahBersih)
                    put("metode_pencairan",       "manual")
                    put("bank_tujuan",            kodeBank)
                    put("no_rekening_tujuan",     noRekening)
                    put("nama_pemilik_rekening",  namaPemilik)
                    put("status_request",         "menunggu")
                    put("sumber_dana",            sumberDana)
                    put("is_tutup_akun",          false)
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
                // Tangkap pesan error dari trigger DB
                val pesan = when {
                    e.message?.contains("Saldo tidak mencukupi") == true ->
                        e.message!!
                    e.message?.contains("Minimal saldo") == true ->
                        e.message!!
                    e.message?.contains("menyisakan") == true ->
                        e.message!!
                    e.message?.contains("Bintang") == true ->
                        e.message!!
                    e.message?.contains("row-level security") == true ->
                        "Akses ditolak. Pastikan Anda sudah login."
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