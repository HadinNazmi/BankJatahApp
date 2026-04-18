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
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentPenarikanNasabahBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PenarikanNasabahFragment : Fragment() {

    private var _binding: FragmentPenarikanNasabahBinding? = null
    private val binding get() = _binding!!

    private var saldoTersedia: Double = 0.0
    private val minPenarikan: Double = 50_000.0
    private val biayaAdmin: Double = 0.0          // saat ini gratis, ubah jika perlu

    private val listBank = mutableListOf<MasterBank>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPenarikanNasabahBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSaldoDanBank()
        setupListeners()
    }

    // ===== LOAD SALDO & DAFTAR BANK =====
    private fun loadSaldoDanBank() {
        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // Ambil saldo dari dompet_user pakai model
                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                saldoTersedia = dompet.saldoNasabah

                // Tampilkan saldo
                binding.tvSaldoTersedia.text = formatRupiah(saldoTersedia)

                // Jika saldo < minimum, disable input nominal
                val cukup = saldoTersedia >= minPenarikan
                binding.etNominal.isEnabled = cukup
                binding.btn50k.isEnabled    = cukup
                binding.btn100k.isEnabled   = cukup
                binding.btn200k.isEnabled   = cukup
                binding.btnSemua.isEnabled  = cukup

                if (!cukup) {
                    binding.tilNominal.error = "Saldo tidak mencukupi minimum penarikan Rp 50.000"
                    binding.tvWarningNominal.text = "⚠ Saldo tidak mencukupi untuk melakukan penarikan"
                    binding.tvWarningNominal.visibility = View.VISIBLE
                }

                // Ambil daftar bank aktif
                val banks = client.postgrest
                    .from("master_bank")
                    .select { filter { eq("status_bank", "aktif") } }
                    .decodeList<MasterBank>()

                listBank.clear()
                listBank.addAll(banks)

                val namaList = listBank.map { "${it.kodeBank} - ${it.namaBank}" }
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    namaList
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerBank.adapter = adapter

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ===== SETUP LISTENERS =====
    private fun setupListeners() {

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Shortcut nominal
        binding.btn50k.setOnClickListener    { setNominal(50_000) }
        binding.btn100k.setOnClickListener   { setNominal(100_000) }
        binding.btn200k.setOnClickListener   { setNominal(200_000) }
        binding.btnSemua.setOnClickListener  { setNominal(saldoTersedia.toLong()) }

        // Watcher input nominal → validasi real-time + tampilkan ringkasan
        binding.etNominal.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val nominal = s.toString().toLongOrNull() ?: 0L
                validasiNominalDanTampilRingkasan(nominal.toDouble())
            }
        })

        binding.btnAjukanPenarikan.setOnClickListener {
            ajukanPenarikan()
        }
    }

    private fun setNominal(nominal: Long) {
        binding.etNominal.setText(nominal.toString())
        binding.etNominal.setSelection(binding.etNominal.text?.length ?: 0)
    }

    // ===== VALIDASI NOMINAL & TAMPILKAN RINGKASAN =====
    private fun validasiNominalDanTampilRingkasan(nominal: Double) {
        binding.tvWarningNominal.visibility = View.GONE
        binding.tilNominal.error = null

        when {
            nominal <= 0 -> {
                binding.cardRingkasan.visibility = View.GONE
            }
            nominal < minPenarikan -> {
                binding.tvWarningNominal.text = "⚠ Minimum penarikan adalah ${formatRupiah(minPenarikan)}"
                binding.tvWarningNominal.visibility = View.VISIBLE
                binding.cardRingkasan.visibility = View.GONE
            }
            nominal > saldoTersedia -> {
                binding.tvWarningNominal.text = "⚠ Nominal melebihi saldo tersedia (${formatRupiah(saldoTersedia)})"
                binding.tvWarningNominal.visibility = View.VISIBLE
                binding.cardRingkasan.visibility = View.GONE
            }
            else -> {
                // Tampilkan ringkasan
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
        val nominalStr    = binding.etNominal.text.toString().trim()
        val noRekening    = binding.etNoRekening.text.toString().trim()
        val namaPemilik   = binding.etNamaPemilik.text.toString().trim()

        // Validasi nominal
        val nominal = nominalStr.toDoubleOrNull()
        if (nominal == null || nominal < minPenarikan) {
            binding.tilNominal.error = "Nominal minimal ${formatRupiah(minPenarikan)}"
            return
        }
        if (nominal > saldoTersedia) {
            binding.tilNominal.error = "Nominal melebihi saldo tersedia"
            return
        }
        binding.tilNominal.error = null

        // Validasi bank
        val selectedIndex = binding.spinnerBank.selectedItemPosition
        if (listBank.isEmpty() || selectedIndex < 0 || selectedIndex >= listBank.size) {
            Toast.makeText(requireContext(), "Pilih bank tujuan", Toast.LENGTH_SHORT).show()
            return
        }
        val bankDipilih = listBank[selectedIndex]

        // Validasi rekening
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
                    ?: throw Exception("Session tidak ditemukan, silakan login ulang")

                val jumlahBersih  = nominal - biayaAdmin
                val kodePencairan = "PCR-${System.currentTimeMillis()}"
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
                val now = sdf.format(Date())

                // ===== 1. Insert ke tabel pencairan_dana =====
                val payloadPencairan = buildJsonObject {
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
                    put("tgl_request",            now)
                    put("sumber_dana",            "setoran_minyak")
                }

                client.postgrest.from("pencairan_dana").insert(payloadPencairan)

                // ===== 2. Kurangi saldo di dompet_user =====
                // Ambil saldo terbaru dulu untuk snapshot mutasi
                val dompetTerbaru = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                val saldoSebelum = dompetTerbaru.saldoNasabah
                val saldoSesudah = saldoSebelum - nominal

                // Update saldo (kurangi)
                val payloadDompet = buildJsonObject {
                    put("saldo_nasabah", saldoSesudah)
                }
                client.postgrest.from("dompet_user")
                    .update(payloadDompet) {
                        filter { eq("id_dompet", idUser) }
                    }

                // ===== 3. Catat ke mutasi_saldo =====
                val payloadMutasi = buildJsonObject {
                    put("id_user",          idUser)
                    put("tipe_transaksi",   "pencairan_dana")
                    put("arus_dana",        "keluar")
                    put("nominal",          nominal)
                    put("saldo_sebelum",    saldoSebelum)
                    put("saldo_sesudah",    saldoSesudah)
                    put("deskripsi",        "Request penarikan $kodePencairan ke $kodeBank $noRekening a.n $namaPemilik")
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