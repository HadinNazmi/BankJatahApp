package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.DompetUser
import com.example.bankjatahapp.data.model.MasterBank
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.SystemConfig
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentPenarikanUnitBisnisBinding
import com.example.bankjatahapp.ui.unitbisnis.UnitBisnisActivity
import com.google.android.material.textfield.TextInputEditText
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

    private var saldoTabungan: Double = 0.0
    private var saldoKomisi: Double   = 0.0
    private var saldoAfiliasi: Double = 0.0
    private var saldoTerpilih: Double = 0.0
    private var jenisTerpilih: String = "tabungan"

    private var config: SystemConfig?     = null
    private var nasabahData: NasabahData? = null
    private var isAktif: Boolean          = false
    private var biayaAdmin: Double        = 0.0

    // Cache daftar bank untuk dipakai di dialog rekening
    private var daftarBank: List<MasterBank> = emptyList()

    private var rekeningDipilih: Triple<String, String, String> = Triple("", "", "")

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
        cekPengajuanBerhentiAktif()
    }

    private fun cekPengajuanBerhentiAktif() {
        viewLifecycleOwner.lifecycleScope.launch {
            val adaPengajuan = (activity as? UnitBisnisActivity)?.cekAdaPengajuanAktif() ?: false
            if (adaPengajuan && _binding != null) {
                binding.btnAjukanPenarikan.isEnabled = false
                binding.tvWarningNominal.text =
                    "⚠️ Anda memiliki pengajuan penutupan akun aktif. Pencairan saldo akan diproses oleh sistem secara otomatis."
                binding.tvWarningNominal.visibility = View.VISIBLE
            }
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                try {
                    nasabahData = client.postgrest
                        .from("nasabah_data")
                        .select { filter { eq("id_nasabah", idUser) } }
                        .decodeSingle<NasabahData>()
                    isAktif = nasabahData?.kategoriNasabah == "aktif"
                } catch (_: Exception) {
                    isAktif = true
                }

                config = client.postgrest
                    .from("system_config")
                    .select { filter { eq("id_config", 1) } }
                    .decodeSingle<SystemConfig>()

                // Load daftar bank untuk dipakai di dialog rekening nanti
                try {
                    daftarBank = client.postgrest.from("master_bank")
                        .select { filter { eq("status_bank", "aktif") } }
                        .decodeList<MasterBank>()
                } catch (_: Exception) {}

                biayaAdmin    = config?.biayaAdminPencairan ?: 0.0
                saldoTabungan = dompet.saldoNasabah
                saldoKomisi   = dompet.saldoUnit
                saldoAfiliasi = dompet.saldoAfiliasi

                binding.tvSaldoTabunganNilai.text = formatRupiah(saldoTabungan)
                binding.tvSaldoKomisiNilai.text   = formatRupiah(saldoKomisi)
                binding.tvSaldoAfiliasiNilai.text = formatRupiah(saldoAfiliasi)

                // Biarkan teks syarat di card pilihan jenis saldo kosong agar tetap clean
                binding.tvSyaratTabungan.text = ""
                binding.tvSyaratKomisi.text = ""
                binding.tvSyaratAfiliasi.text = ""

                // Rekening dari profil nasabah_data
                val nasabah  = nasabahData
                val bank     = nasabah?.bankCode ?: ""
                val noRek    = nasabah?.noRekening ?: ""
                val atasNama = nasabah?.atasNamaRekening ?: ""

                if (bank.isNotEmpty() && noRek.isNotEmpty()) {
                    tampilkanInfoRekening(bank, noRek, atasNama)
                } else {
                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        listOf("Belum ada rekening — tap di sini untuk mengisi")
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerBank.adapter = adapter
                }

                pilihJenis("tabungan")

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                if (_binding != null) {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    // FUNGSI CUSTOM POP-UP SYARAT PENARIKAN (BERSIH & RAPI DENGAN HTML)
    private fun tampilkanDialogSyaratPenarikan() {
        val cfg         = config
        val threshold   = cfg?.thresholdSaldoNasabah ?: 120000.0
        val minSisa     = cfg?.minSisaSaldoNasabah ?: 20000.0
        val minBintang  = cfg?.minBintangPenarikan ?: 3
        val minKomisi   = cfg?.minPenarikanUnit ?: 50000.0
        val minAfiliasi = cfg?.minPenarikanAfiliasi ?: 10000.0
        val biayaInfo   = if (biayaAdmin > 0) formatRupiah(biayaAdmin) else "Gratis"

        val htmlSyarat = buildString {
            append("<b>• Tabungan:</b> Saldo min. ${formatRupiah(threshold)}, sisa min. ${formatRupiah(minSisa)}.<br><br>")
            append("<b>• Komisi Unit:</b> Min. penarikan ${formatRupiah(minKomisi)} (bisa ditarik sampai Rp 0).<br><br>")
            append("<b>• Bonus Afiliasi:</b> Min. penarikan ${formatRupiah(minAfiliasi)} (bisa ditarik sampai Rp 0).<br><br>")
            append("<b>• Ketentuan Umum:</b> Minimal level Bintang $minBintang untuk semua jenis penarikan.<br><br>")
            append("<b>• Biaya Admin:</b> $biayaInfo.")
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_syarat_penarikan, null)
        val tvIsi = dialogView.findViewById<android.widget.TextView>(R.id.tvIsiSyarat)
        val btnTutup = dialogView.findViewById<Button>(R.id.btnTutupSyarat)

        tvIsi.text = HtmlCompat.fromHtml(htmlSyarat, HtmlCompat.FROM_HTML_MODE_COMPACT)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnTutup.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

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

        // Reset State Seleksi Komponen Utama
        binding.optionTabungan.isSelected = false
        binding.optionKomisi.isSelected   = false
        binding.optionAfiliasi.isSelected = false

        binding.radioTabungan.isChecked = false
        binding.radioKomisi.isChecked   = false
        binding.radioAfiliasi.isChecked = false

        binding.optionTabungan.setBackgroundResource(R.drawable.ic_bg_tab_inactive)
        binding.optionKomisi.setBackgroundResource(R.drawable.ic_bg_tab_inactive)
        binding.optionAfiliasi.setBackgroundResource(R.drawable.ic_bg_tab_inactive)

        // Aktifkan State Terpilih secara spesifik
        when (jenis) {
            "tabungan" -> {
                binding.optionTabungan.setBackgroundResource(R.drawable.ic_bg_tab_active)
                binding.optionTabungan.isSelected = true
                binding.radioTabungan.isChecked = true
            }
            "komisi" -> {
                binding.optionKomisi.setBackgroundResource(R.drawable.ic_bg_tab_active)
                binding.optionKomisi.isSelected = true
                binding.radioKomisi.isChecked = true
            }
            "afiliasi" -> {
                binding.optionAfiliasi.setBackgroundResource(R.drawable.ic_bg_tab_active)
                binding.optionAfiliasi.isSelected = true
                binding.radioAfiliasi.isChecked = true
            }
        }

        // Cek syarat batas penarikan
        val syaratInfo = cekSyaratPenarikan(jenis)
        if (syaratInfo != null) {
            binding.tvWarningNominal.text = syaratInfo
            binding.tvWarningNominal.visibility = View.VISIBLE
            setInputEnabled(false)
        } else {
            binding.tvWarningNominal.visibility = View.GONE
            setInputEnabled(true)
        }

        binding.tvMinimumInfo.text = getMinimumInfo(jenis)
        binding.etNominal.setText("")
        binding.cardRingkasan.visibility = View.GONE
    }

    private fun cekSyaratPenarikan(jenis: String): String? {
        val cfg = config
        return when (jenis) {
            "tabungan" -> {
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val level      = nasabahData?.levelBintang ?: 1
                if (level < minBintang) {
                    return "⚠ Penarikan tabungan membutuhkan minimal Bintang $minBintang\nLevel Anda: Bintang $level"
                }
                val threshold = cfg?.thresholdSaldoNasabah ?: 120000.0
                if (saldoTabungan < threshold) {
                    return "⚠ Saldo tabungan harus ≥ ${formatRupiah(threshold)} untuk mulai menarik\nSaldo Anda: ${formatRupiah(saldoTabungan)}"
                }
                null
            }
            "komisi" -> {
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val level      = nasabahData?.levelBintang ?: 1
                if (level < minBintang) {
                    return "⚠ Penarikan komisi membutuhkan minimal Bintang $minBintang\nLevel Anda: Bintang $level"
                }
                if (saldoKomisi <= 0) {
                    return "⚠ Saldo komisi unit kosong"
                }
                null
            }
            "afiliasi" -> {
                val minBintang = cfg?.minBintangPenarikan ?: 3
                val level      = nasabahData?.levelBintang ?: 1
                if (level < minBintang) {
                    return "⚠ Penarikan bonus afiliasi membutuhkan minimal Bintang $minBintang\nLevel Anda: Bintang $level"
                }
                if (saldoAfiliasi <= 0) {
                    return "⚠ Saldo bonus/afiliasi kosong"
                }
                null
            }
            else -> null
        }
    }

    private fun getMinimumInfo(jenis: String): String {
        val cfg        = config
        val minBintang = cfg?.minBintangPenarikan ?: 3
        return when (jenis) {
            "tabungan" -> {
                val minSisa = cfg?.minSisaSaldoNasabah ?: 20000.0
                "Sisa wajib ${formatRupiah(minSisa)} · Bintang $minBintang ke atas"
            }
            "komisi" -> {
                val min = cfg?.minPenarikanUnit ?: 50000.0
                "Min. ${formatRupiah(min)} · Bintang $minBintang ke atas"
            }
            else -> {
                val min = cfg?.minPenarikanAfiliasi ?: 10000.0
                "Min. ${formatRupiah(min)} · Bintang $minBintang ke atas"
            }
        }
    }

    private fun setInputEnabled(enabled: Boolean) {
        binding.etNominal.isEnabled = enabled
        binding.btn50k.isEnabled    = enabled
        binding.btn100k.isEnabled   = enabled
        binding.btn500k.isEnabled   = enabled
        binding.btnSemua.isEnabled  = enabled
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // TOMBOL LIHAT SYARAT DI KARTU SALDO
        binding.tvBtnInfoSyarat.setOnClickListener {
            tampilkanDialogSyaratPenarikan()
        }

        binding.optionTabungan.setOnClickListener { pilihJenis("tabungan") }
        binding.radioTabungan.setOnClickListener  { pilihJenis("tabungan") }
        binding.optionKomisi.setOnClickListener   { pilihJenis("komisi") }
        binding.radioKomisi.setOnClickListener    { pilihJenis("komisi") }
        binding.optionAfiliasi.setOnClickListener { pilihJenis("afiliasi") }
        binding.radioAfiliasi.setOnClickListener  { pilihJenis("afiliasi") }

        binding.btn50k.setOnClickListener   { setNominal(50_000) }
        binding.btn100k.setOnClickListener  { setNominal(100_000) }
        binding.btn500k.setOnClickListener  { setNominal(500_000) }
        binding.btnSemua.setOnClickListener { setNominal(saldoTerpilih.toLong()) }

        binding.etNominal.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validasiDanTampilRingkasan((s.toString().toLongOrNull() ?: 0L).toDouble())
            }
        })

        // KLIK PADA SPINNER / REKENING UNTUK BUKA DIALOG EDIT REKENING
        binding.spinnerBank.setOnTouchListener { _, _ ->
            cekDanTampilkanDialogRekening {}
            true
        }

        binding.btnAjukanPenarikan.setOnClickListener { ajukanPenarikan() }

        binding.swipeRefresh.setColorSchemeColors(
            requireContext().getColor(R.color.orange_primary)
        )
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }
    }

    private fun cekDanTampilkanDialogRekening(onRekeningLengkap: () -> Unit) {
        val nasabah = nasabahData

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_input_rekening, null)

        val spinnerBank   = dialogView.findViewById<Spinner>(R.id.spinnerBank)
        val etNoRek       = dialogView.findViewById<TextInputEditText>(R.id.etNoRekening)
        val etAtasNama    = dialogView.findViewById<TextInputEditText>(R.id.etAtasNama)
        val btnBatal      = dialogView.findViewById<Button>(R.id.btnBatalRekening)
        val btnSimpan     = dialogView.findViewById<Button>(R.id.btnSimpanRekening)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        var bankDipilihKode: String? = nasabah?.bankCode
        val namaBank = daftarBank.map { it.namaBank }
        val adapterSpinner = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            namaBank
        )
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBank.adapter = adapterSpinner

        val indexBank = daftarBank.indexOfFirst { it.kodeBank == nasabah?.bankCode }
        if (indexBank >= 0) spinnerBank.setSelection(indexBank)

        spinnerBank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (daftarBank.isNotEmpty()) {
                    bankDipilihKode = daftarBank[pos].kodeBank
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        etNoRek.setText(nasabah?.noRekening ?: "")
        etAtasNama.setText(nasabah?.atasNamaRekening ?: "")

        btnBatal.setOnClickListener { dialog.dismiss() }

        btnSimpan.setOnClickListener {
            val noRek    = etNoRek.text.toString().trim()
            val atasNama = etAtasNama.text.toString().trim()

            if (bankDipilihKode.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Pilih bank tujuan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (noRek.isEmpty()) {
                Toast.makeText(requireContext(), "Nomor rekening tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (atasNama.isEmpty()) {
                Toast.makeText(requireContext(), "Atas nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val idUser = client.auth.currentUserOrNull()?.id
                        ?: throw Exception("Sesi tidak valid")

                    val payload = buildJsonObject {
                        put("bank_code",          bankDipilihKode)
                        put("no_rekening",         noRek)
                        put("atas_nama_rekening",  atasNama)
                    }
                    client.postgrest.from("nasabah_data").update(payload) {
                        filter { eq("id_nasabah", idUser) }
                    }

                    nasabahData = nasabahData?.copy(
                        bankCode         = bankDipilihKode,
                        noRekening       = noRek,
                        atasNamaRekening = atasNama
                    )
                    rekeningDipilih = Triple(bankDipilihKode ?: "", noRek, atasNama)

                    tampilkanInfoRekening(bankDipilihKode ?: "", noRek, atasNama)

                    Toast.makeText(requireContext(), "✓ Rekening berhasil disimpan", Toast.LENGTH_SHORT).show()
                    onRekeningLengkap()

                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Gagal menyimpan rekening: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }

    private fun tampilkanInfoRekening(bankCode: String, noRek: String, atasNama: String) {
        val namaBank = try {
            daftarBank.find { it.kodeBank == bankCode }?.namaBank ?: bankCode
        } catch (e: Exception) { bankCode }

        val labelRekening = "$namaBank - $noRek ($atasNama)"
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf(labelRekening)
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBank.adapter = adapter
        rekeningDipilih = Triple(bankCode, noRek, atasNama)
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
            "komisi" -> {
                val minKomisi = cfg?.minPenarikanUnit ?: 50000.0
                when {
                    nominal <= 0 -> binding.cardRingkasan.visibility = View.GONE
                    nominal < minKomisi -> {
                        binding.tvWarningNominal.text = "⚠ Minimum penarikan komisi adalah ${formatRupiah(minKomisi)}"
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
            "komisi" -> {
                val minKomisi = cfg?.minPenarikanUnit ?: 50000.0
                if (nominal < minKomisi) {
                    binding.tilNominal.error = "Nominal minimal ${formatRupiah(minKomisi)}"
                    return
                }
                if (nominal > saldoTerpilih) {
                    binding.tilNominal.error = "Nominal melebihi saldo tersedia"
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
            cekDanTampilkanDialogRekening {
                ajukanPenarikan()
            }
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
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session tidak ditemukan")

                val jumlahBersih  = nominal - biayaAdmin
                val kodePencairan = "PCR-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"

                val sumberDana = when (jenisTerpilih) {
                    "tabungan" -> "setoran_minyak"
                    "komisi"   -> "komisi_unit"
                    else       -> "komisi_afiliasi"
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