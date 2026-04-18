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
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.DompetUser
import com.example.bankjatahapp.data.model.MasterBank
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentPenarikanUnitBisnisBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PenarikanUnitBisnisFragment : Fragment() {

    private var _binding: FragmentPenarikanUnitBisnisBinding? = null
    private val binding get() = _binding!!

    private var saldoTabungan: Double = 0.0   // dompet_user.saldo_nasabah
    private var saldoKomisi: Double   = 0.0   // dompet_user.saldo_unit
    private var saldoTerpilih: Double = 0.0   // saldo yang sedang aktif
    private var jenisTerpilih: String = "tabungan"  // "tabungan" atau "komisi"

    private val minPenarikan: Double = 50_000.0
    private val biayaAdmin: Double   = 0.0

    private val listBank = mutableListOf<MasterBank>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPenarikanUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSaldoDanBank()
        setupListeners()
    }

    // ===== LOAD DATA =====
    private fun loadSaldoDanBank() {
        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                saldoTabungan = dompet.saldoNasabah
                saldoKomisi   = dompet.saldoUnit

                // Tampilkan nilai pada masing-masing opsi
                binding.tvSaldoTabunganNilai.text = formatRupiah(saldoTabungan)
                binding.tvSaldoKomisiNilai.text   = formatRupiah(saldoKomisi)

                // Default aktif: tabungan
                pilihJenis("tabungan")

                // Load daftar bank
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

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ===== PILIH JENIS SALDO =====
    private fun pilihJenis(jenis: String) {
        jenisTerpilih = jenis
        saldoTerpilih = if (jenis == "tabungan") saldoTabungan else saldoKomisi

        // Update label & tampilan saldo aktif
        binding.tvJenisSaldoLabel.text = if (jenis == "tabungan")
            "Saldo Tabungan Tersedia" else "Saldo Komisi Tersedia"
        binding.tvSaldoTersedia.text = formatRupiah(saldoTerpilih)

        // Update visual opsi — aktif = orange, nonaktif = abu
        if (jenis == "tabungan") {
            binding.optionTabungan.setBackgroundResource(R.drawable.ic_bg_tab_active)
            binding.tvSaldoTabunganNilai.setTextColor(requireContext().getColor(R.color.white))
            binding.radioTabungan.isChecked = true
            binding.optionKomisi.setBackgroundResource(R.drawable.ic_bg_tab_inactive)
            binding.tvSaldoKomisiNilai.setTextColor(requireContext().getColor(R.color.black))
            binding.radioKomisi.isChecked = false
        } else {
            binding.optionKomisi.setBackgroundResource(R.drawable.ic_bg_tab_active)
            binding.tvSaldoKomisiNilai.setTextColor(requireContext().getColor(R.color.white))
            binding.radioKomisi.isChecked = true
            binding.optionTabungan.setBackgroundResource(R.drawable.ic_bg_tab_inactive)
            binding.tvSaldoTabunganNilai.setTextColor(requireContext().getColor(R.color.black))
            binding.radioTabungan.isChecked = false
        }

        // Disable input jika saldo tidak cukup
        val cukup = saldoTerpilih >= minPenarikan
        binding.etNominal.isEnabled = cukup
        binding.btn50k.isEnabled    = cukup
        binding.btn100k.isEnabled   = cukup
        binding.btn500k.isEnabled   = cukup
        binding.btnSemua.isEnabled  = cukup

        if (!cukup) {
            binding.tvWarningNominal.text = "⚠ Saldo tidak mencukupi minimum penarikan Rp 50.000"
            binding.tvWarningNominal.visibility = View.VISIBLE
            binding.cardRingkasan.visibility    = View.GONE
        } else {
            binding.tvWarningNominal.visibility = View.GONE
        }

        // Reset nominal saat ganti jenis
        binding.etNominal.setText("")
        binding.cardRingkasan.visibility = View.GONE
    }

    // ===== SETUP LISTENERS =====
    private fun setupListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.optionTabungan.setOnClickListener { pilihJenis("tabungan") }
        binding.radioTabungan.setOnClickListener  { pilihJenis("tabungan") }
        binding.optionKomisi.setOnClickListener   { pilihJenis("komisi") }
        binding.radioKomisi.setOnClickListener    { pilihJenis("komisi") }

        binding.btn50k.setOnClickListener  { setNominal(50_000) }
        binding.btn100k.setOnClickListener { setNominal(100_000) }
        binding.btn500k.setOnClickListener { setNominal(500_000) }
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

    // ===== VALIDASI NOMINAL =====
    private fun validasiDanTampilRingkasan(nominal: Double) {
        binding.tvWarningNominal.visibility = View.GONE
        binding.tilNominal.error = null

        when {
            nominal <= 0 -> binding.cardRingkasan.visibility = View.GONE
            nominal < minPenarikan -> {
                binding.tvWarningNominal.text = "⚠ Minimum penarikan adalah ${formatRupiah(minPenarikan)}"
                binding.tvWarningNominal.visibility = View.VISIBLE
                binding.cardRingkasan.visibility    = View.GONE
            }
            nominal > saldoTerpilih -> {
                binding.tvWarningNominal.text = "⚠ Nominal melebihi saldo tersedia (${formatRupiah(saldoTerpilih)})"
                binding.tvWarningNominal.visibility = View.VISIBLE
                binding.cardRingkasan.visibility    = View.GONE
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

    // ===== SUBMIT =====
    private fun ajukanPenarikan() {
        val nominalStr  = binding.etNominal.text.toString().trim()
        val noRekening  = binding.etNoRekening.text.toString().trim()
        val namaPemilik = binding.etNamaPemilik.text.toString().trim()

        val nominal = nominalStr.toDoubleOrNull()
        if (nominal == null || nominal < minPenarikan) {
            binding.tilNominal.error = "Nominal minimal ${formatRupiah(minPenarikan)}"
            return
        }
        if (nominal > saldoTerpilih) {
            binding.tilNominal.error = "Nominal melebihi saldo tersedia"
            return
        }
        binding.tilNominal.error = null

        val selectedIndex = binding.spinnerBank.selectedItemPosition
        if (listBank.isEmpty() || selectedIndex < 0 || selectedIndex >= listBank.size) {
            Toast.makeText(requireContext(), "Pilih bank tujuan", Toast.LENGTH_SHORT).show()
            return
        }
        val bankDipilih = listBank[selectedIndex]

        if (noRekening.isEmpty()) {
            binding.tilNoRekening.error = "Nomor rekening tidak boleh kosong"
            return
        }
        binding.tilNoRekening.error = null

        if (namaPemilik.isEmpty()) {
            binding.tilNamaPemilik.error = "Nama pemilik rekening tidak boleh kosong"
            return
        }
        binding.tilNamaPemilik.error = null

        submitPenarikan(nominal, bankDipilih.kodeBank, noRekening, namaPemilik)
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
                val kodePencairan = "PCR-${System.currentTimeMillis()}"
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
                val now = sdf.format(Date())

                // Tentukan sumber_dana berdasarkan jenis yang dipilih
                val sumberDana = if (jenisTerpilih == "tabungan") "setoran_minyak" else "komisi_unit"

                // ===== 1. Insert pencairan_dana =====
                val payloadPencairan = buildJsonObject {
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
                    put("tgl_request",           now)
                    put("sumber_dana",           sumberDana)
                }
                client.postgrest.from("pencairan_dana").insert(payloadPencairan)

                // ===== 2. Ambil saldo terbaru dan kurangi =====
                val dompetTerbaru = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                val saldoSebelum: Double
                val saldoSesudah: Double
                val fieldSaldo: String

                if (jenisTerpilih == "tabungan") {
                    saldoSebelum = dompetTerbaru.saldoNasabah
                    saldoSesudah = saldoSebelum - nominal
                    fieldSaldo   = "saldo_nasabah"
                } else {
                    saldoSebelum = dompetTerbaru.saldoUnit
                    saldoSesudah = saldoSebelum - nominal
                    fieldSaldo   = "saldo_unit"
                }

                // Update saldo (kurangi)
                val payloadDompet = buildJsonObject { put(fieldSaldo, saldoSesudah) }
                client.postgrest.from("dompet_user")
                    .update(payloadDompet) { filter { eq("id_dompet", idUser) } }

                // ===== 3. Insert mutasi_saldo =====
                val labelJenis = if (jenisTerpilih == "tabungan") "Tabungan Minyak" else "Komisi Unit"
                val payloadMutasi = buildJsonObject {
                    put("id_user",        idUser)
                    put("tipe_transaksi", "pencairan_dana")
                    put("arus_dana",      "keluar")
                    put("nominal",        nominal)
                    put("saldo_sebelum",  saldoSebelum)
                    put("saldo_sesudah",  saldoSesudah)
                    put("deskripsi",      "Request penarikan $labelJenis $kodePencairan ke $kodeBank $noRekening a.n $namaPemilik")
                }
                client.postgrest.from("mutasi_saldo").insert(payloadMutasi)

                setLoading(false)
                Toast.makeText(
                    requireContext(),
                    "✓ Pengajuan penarikan berhasil!\nKode: $kodePencairan\nStatus: Menunggu persetujuan admin.",
                    Toast.LENGTH_LONG
                ).show()

                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                setLoading(false)
                val pesan = when {
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