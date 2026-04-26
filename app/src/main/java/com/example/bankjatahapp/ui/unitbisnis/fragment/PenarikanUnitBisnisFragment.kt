package com.example.bankjatahapp.ui.unitbisnis.fragment

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
import com.example.bankjatahapp.databinding.FragmentPenarikanUnitBisnisBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

class PenarikanUnitBisnisFragment : Fragment() {

    private var _binding: FragmentPenarikanUnitBisnisBinding? = null
    private val binding get() = _binding!!

    // Tiga kantong saldo unit bisnis
    private var saldoTabungan: Double = 0.0   // saldo_nasabah  (UB juga punya tabungan sebagai nasabah)
    private var saldoKomisi: Double   = 0.0   // saldo_unit     (komisi dari setoran nasabah ke UB)
    private var saldoAfiliasi: Double = 0.0   // saldo_afiliasi (bonus jaringan afiliasi)

    private var saldoTerpilih: Double = 0.0
    private var jenisTerpilih: String = "tabungan" // "tabungan", "komisi", atau "afiliasi"

    // Config & nasabah dari DB — semua nilai dinamis dari Supabase
    private var config: SystemConfig?   = null
    private var nasabahData: NasabahData? = null
    private var isAktif: Boolean        = false

    // biayaAdmin diambil dari config.biayaAdminPencairan, bukan hardcode
    private var biayaAdmin: Double = 0.0

    private val listBank = mutableListOf<MasterBank>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPenarikanUnitBisnisBinding.inflate(inflater, container, false)
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

                // 2. Data nasabah — UB juga punya row nasabah_data karena UB adalah nasabah
                try {
                    nasabahData = client.postgrest
                        .from("nasabah_data")
                        .select { filter { eq("id_nasabah", idUser) } }
                        .decodeSingle<NasabahData>()
                    isAktif = nasabahData?.kategoriNasabah == "aktif"
                } catch (_: Exception) {
                    // Jika tidak ada row nasabah_data, anggap aktif
                    isAktif = true
                }

                // 3. System config dari Supabase — SEMUA nilai dari sini, tidak ada yang hardcode
                config = client.postgrest
                    .from("system_config")
                    .select { filter { eq("id_config", 1) } }
                    .decodeSingle<SystemConfig>()

                // 4. Biaya admin dari config (dinamis, bisa diubah admin)
                biayaAdmin = config?.biayaAdminPencairan ?: 0.0

                saldoTabungan = dompet.saldoNasabah
                saldoKomisi   = dompet.saldoUnit
                saldoAfiliasi = dompet.saldoAfiliasi

                // Tampilkan nilai di tiap opsi pilihan
                binding.tvSaldoTabunganNilai.text = formatRupiah(saldoTabungan)
                binding.tvSaldoKomisiNilai.text   = formatRupiah(saldoKomisi)
                binding.tvSaldoAfiliasiNilai.text = formatRupiah(saldoAfiliasi)

                // 5. Daftar bank aktif
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

    // ===== PILIH JENIS SALDO =====
    private fun pilihJenis(jenis: String) {
        jenisTerpilih = jenis
        saldoTerpilih = when (jenis) {
            "tabungan" -> saldoTabungan
            "komisi"   -> saldoKomisi
            else       -> saldoAfiliasi
        }

        binding.tvJenisSaldoLabel.text = when (jenis) {
            "tabungan" -> "Saldo Tabungan Tersedia"
            "komisi"   -> "Saldo Komisi Unit Tersedia"
            else       -> "Saldo Bonus Afiliasi Tersedia"
        }
        binding.tvSaldoTersedia.text = formatRupiah(saldoTerpilih)

        // Reset visual semua opsi ke inactive
        listOf(
            binding.optionTabungan to binding.tvSaldoTabunganNilai,
            binding.optionKomisi   to binding.tvSaldoKomisiNilai,
            binding.optionAfiliasi to binding.tvSaldoAfiliasiNilai
        ).forEach { (opt, tv) ->
            opt.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_inactive)
            tv.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.black))
        }
        binding.radioTabungan.isChecked = false
        binding.radioKomisi.isChecked   = false
        binding.radioAfiliasi.isChecked = false

        // Aktifkan opsi yang dipilih
        when (jenis) {
            "tabungan" -> {
                binding.optionTabungan.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_active)
                binding.tvSaldoTabunganNilai.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.white))
                binding.radioTabungan.isChecked = true
            }
            "komisi" -> {
                binding.optionKomisi.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_active)
                binding.tvSaldoKomisiNilai.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.white))
                binding.radioKomisi.isChecked = true
            }
            "afiliasi" -> {
                binding.optionAfiliasi.setBackgroundResource(com.example.bankjatahapp.R.drawable.ic_bg_tab_active)
                binding.tvSaldoAfiliasiNilai.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.white))
                binding.radioAfiliasi.isChecked = true
            }
        }

        // Cek syarat — semua nilai dari config Supabase
        val syaratInfo = cekSyaratPenarikan(jenis)
        if (syaratInfo != null) {
            binding.tvWarningNominal.text = syaratInfo
            binding.tvWarningNominal.visibility = View.VISIBLE
            setInputEnabled(false)
            binding.cardRingkasan.visibility = View.GONE
        } else {
            binding.tvWarningNominal.visibility = View.GONE
            setInputEnabled(true)
        }

        binding.tvMinimumInfo.text = getMinimumInfo(jenis)
        binding.etNominal.setText("")
        binding.cardRingkasan.visibility = View.GONE
    }

    // ===== CEK SYARAT PENARIKAN =====
    // Return null = boleh tarik, return String = pesan error
    // Semua nilai ambil dari config Supabase, fallback ke default jika config belum dimuat
    private fun cekSyaratPenarikan(jenis: String): String? {
        val cfg = config
        return when (jenis) {
            "tabungan" -> {
                // UB dianggap aktif — gunakan threshold_saldo_nasabah_aktif (dari config)
                val threshold = cfg?.thresholdSaldoNasabahAktif ?: 120000.0
                when {
                    saldoTabungan <= 0 ->
                        "⚠ Saldo tabungan kosong"
                    saldoTabungan < threshold ->
                        "⚠ Saldo tabungan baru bisa ditarik jika ≥ ${formatRupiah(threshold)}\nSaldo: ${formatRupiah(saldoTabungan)}"
                    else -> null
                }
            }
            "komisi" -> {
                // Komisi unit: butuh min_bintang_penarikan (dari config)
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val level      = nasabahData?.levelBintang ?: 1
                when {
                    level < minBintang ->
                        "⚠ Penarikan komisi membutuhkan minimal Bintang $minBintang\nLevel Anda: Bintang $level"
                    saldoKomisi <= 0 ->
                        "⚠ Saldo komisi unit kosong"
                    else -> null
                }
            }
            "afiliasi" -> {
                // Bonus afiliasi: butuh min_bintang_penarikan (dari config)
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val level      = nasabahData?.levelBintang ?: 1
                when {
                    level < minBintang ->
                        "⚠ Penarikan bonus afiliasi membutuhkan minimal Bintang $minBintang\nLevel Anda: Bintang $level"
                    saldoAfiliasi <= 0 ->
                        "⚠ Saldo bonus/afiliasi kosong"
                    else -> null
                }
            }
            else -> null
        }
    }

    // Teks info minimum penarikan — nilai dari config Supabase
    private fun getMinimumInfo(jenis: String): String {
        val cfg        = config
        val minBintang = cfg?.minBintangPenarikan ?: 3
        return when (jenis) {
            "tabungan" -> "Minimum penarikan: ${formatRupiah(cfg?.minPenarikanNasabahAktif ?: 15000.0)}"
            "komisi"   -> "Tersedia untuk Bintang $minBintang ke atas · Min: ${formatRupiah(cfg?.minPenarikanUnit    ?: 50000.0)}"
            else       -> "Tersedia untuk Bintang $minBintang ke atas · Min: ${formatRupiah(cfg?.minPenarikanKomisi  ?: 10000.0)}"
        }
    }

    private fun setInputEnabled(enabled: Boolean) {
        binding.etNominal.isEnabled = enabled
        binding.btn50k.isEnabled    = enabled
        binding.btn100k.isEnabled   = enabled
        binding.btn500k.isEnabled   = enabled
        binding.btnSemua.isEnabled  = enabled
    }

    // ===== SETUP LISTENERS =====
    private fun setupListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.optionTabungan.setOnClickListener { pilihJenis("tabungan") }
        binding.radioTabungan.setOnClickListener  { pilihJenis("tabungan") }
        binding.optionKomisi.setOnClickListener   { pilihJenis("komisi") }
        binding.radioKomisi.setOnClickListener    { pilihJenis("komisi") }
        binding.optionAfiliasi.setOnClickListener { pilihJenis("afiliasi") }
        binding.radioAfiliasi.setOnClickListener  { pilihJenis("afiliasi") }

        binding.btn50k.setOnClickListener  { setNominal(50_000) }
        binding.btn100k.setOnClickListener { setNominal(100_000) }
        binding.btn500k.setOnClickListener { setNominal(500_000) }
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

    // ===== VALIDASI REAL-TIME & TAMPIL RINGKASAN =====
    // Semua batas dari config Supabase
    private fun validasiDanTampilRingkasan(nominal: Double) {
        binding.tvWarningNominal.visibility = View.GONE
        binding.tilNominal.error = null

        val cfg = config
        val minPenarikan = when (jenisTerpilih) {
            "tabungan" -> cfg?.minPenarikanNasabahAktif ?: 15000.0
            "komisi"   -> cfg?.minPenarikanUnit         ?: 50000.0
            else       -> cfg?.minPenarikanKomisi       ?: 10000.0
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
                binding.tvWarningNominal.text = "⚠ Nominal melebihi saldo (${formatRupiah(saldoTerpilih)})"
                binding.tvWarningNominal.visibility = View.VISIBLE
                binding.cardRingkasan.visibility = View.GONE
            }
            // Untuk tabungan: wajib sisakan min_sisa_saldo_nasabah_aktif (dari config)
            jenisTerpilih == "tabungan" && cfg != null &&
                    (saldoTerpilih - nominal) < cfg.minSisaSaldoNasabahAktif -> {
                binding.tvWarningNominal.text =
                    "⚠ Harus menyisakan minimal ${formatRupiah(cfg.minSisaSaldoNasabahAktif)}"
                binding.tvWarningNominal.visibility = View.VISIBLE
                binding.cardRingkasan.visibility = View.GONE
            }
            else -> {
                // biayaAdmin dari config.biayaAdminPencairan (dinamis)
                val bersih = nominal - biayaAdmin
                binding.tvRingkasanJumlah.text = formatRupiah(nominal)
                binding.tvRingkasanBiaya.text  = if (biayaAdmin > 0) formatRupiah(biayaAdmin) else "Gratis"
                binding.tvRingkasanBersih.text = formatRupiah(bersih)
                binding.cardRingkasan.visibility = View.VISIBLE
            }
        }
    }

    // ===== SUBMIT PENARIKAN =====
    private fun ajukanPenarikan() {
        val nominalStr  = binding.etNominal.text.toString().trim()
        val noRekening  = binding.etNoRekening.text.toString().trim()
        val namaPemilik = binding.etNamaPemilik.text.toString().trim()

        val cfg = config
        // Batas minimal dari config Supabase
        val minPenarikan = when (jenisTerpilih) {
            "tabungan" -> cfg?.minPenarikanNasabahAktif ?: 15000.0
            "komisi"   -> cfg?.minPenarikanUnit         ?: 50000.0
            else       -> cfg?.minPenarikanKomisi       ?: 10000.0
        }

        val nominal = nominalStr.toDoubleOrNull()
        if (nominal == null || nominal < minPenarikan) {
            binding.tilNominal.error = "Nominal minimal ${formatRupiah(minPenarikan)}"
            return
        }
        if (nominal > saldoTerpilih) {
            binding.tilNominal.error = "Nominal melebihi saldo tersedia"
            return
        }
        // Sisa saldo minimal tabungan — dari config Supabase
        if (jenisTerpilih == "tabungan" && cfg != null &&
            (saldoTerpilih - nominal) < cfg.minSisaSaldoNasabahAktif) {
            binding.tilNominal.error = "Harus menyisakan ${formatRupiah(cfg.minSisaSaldoNasabahAktif)}"
            return
        }
        binding.tilNominal.error = null

        val selectedIndex = binding.spinnerBank.selectedItemPosition
        if (listBank.isEmpty() || selectedIndex < 0 || selectedIndex >= listBank.size) {
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

                // biayaAdmin dari config Supabase (bukan hardcode)
                val jumlahBersih  = nominal - biayaAdmin
                val kodePencairan = "PCR-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"

                // sumber_dana berdasarkan jenis yang dipilih
                val sumberDana = when (jenisTerpilih) {
                    "tabungan" -> "setoran_minyak"
                    "komisi"   -> "komisi_unit"
                    else       -> "komisi_afiliasi"
                }

                // Insert ke pencairan_dana — trigger DB yang handle potong saldo,
                // catat mutasi, dan validasi akhir secara atomik
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
                // Tangkap pesan error dari trigger DB Supabase
                val pesan = when {
                    e.message?.contains("Saldo tidak mencukupi") == true -> e.message!!
                    e.message?.contains("Minimal saldo")         == true -> e.message!!
                    e.message?.contains("menyisakan")            == true -> e.message!!
                    e.message?.contains("Bintang")               == true -> e.message!!
                    e.message?.contains("Maaf, penarikan")       == true -> e.message!!
                    e.message?.contains("row-level security")    == true -> "Akses ditolak. Pastikan Anda sudah login."
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