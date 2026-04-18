package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentSetoranBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonNull
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class SetoranFragment : Fragment() {

    private var _binding: FragmentSetoranBinding? = null
    private val binding get() = _binding!!

    private var idNasabahDipilih: String? = null
    private var namaNasabahDipilih: String? = null
    private var fotoUri: Uri? = null
    private var fotoFile: File? = null

    private val hargaSatuanSnapshot = 90.0
    private val hargaPerKg          = 90.0
    private val komisiPerKg         = 1000.0

    // ===== SCAN QR =====
    private val scanQrLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            val uuid = result.contents.trim()
            binding.etIdNasabah.setText(uuid.take(8) + "...")
            cekNasabahByUuid(uuid)
        } else {
            Toast.makeText(requireContext(), "Scan dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== KAMERA FOTO =====
    private val ambilFotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { berhasil ->
        if (berhasil && fotoUri != null) {
            binding.ivPreviewFoto.setImageURI(fotoUri)
            binding.ivPreviewFoto.visibility         = View.VISIBLE
            binding.layoutPlaceholderFoto.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetoranBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {

        binding.frameQrScanner.setOnClickListener { bukaQrScanner() }

        binding.btnCekNasabah.setOnClickListener {
            val inputId = binding.etIdNasabah.text.toString().trim()
            if (inputId.isEmpty()) {
                Toast.makeText(requireContext(), "Masukkan ID nasabah terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (inputId.length == 36) {
                cekNasabahByUuid(inputId)
            } else {
                cekNasabahManual(inputId)
            }
        }

        binding.etJumlah.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val berat = s.toString().toDoubleOrNull()
                if (berat != null && berat > 0) {
                    val formatted = formatRupiah(berat * hargaPerKg)
                    binding.tvEstimasiRupiah.text      = formatted
                    binding.tvSaldoNilai.text           = formatted
                    binding.layoutKalkulasi.visibility = View.VISIBLE
                } else {
                    binding.tvEstimasiRupiah.text      = "Rp 0"
                    binding.tvSaldoNilai.text           = "Rp 0"
                    binding.layoutKalkulasi.visibility = View.GONE
                }
            }
        })

        binding.btnBukaKamera.setOnClickListener { bukaKameraFoto() }
        binding.btnKonfirmasi.setOnClickListener { validasiDanSubmit() }
    }

    private fun bukaQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Arahkan ke QR Identitas Nasabah")
            setCameraId(0)
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        scanQrLauncher.launch(options)
    }

    // ===== CEK NASABAH DARI SCAN QR (UUID penuh - langsung eq) =====
    private fun cekNasabahByUuid(uuid: String) {
        setTombolCek(loading = true)
        lifecycleScope.launch {
            try {
                val user = client.postgrest
                    .from("users")
                    .select {
                        filter {
                            eq("id_user", uuid)
                            eq("role", "nasabah")
                        }
                    }
                    .decodeSingle<User>()

                tampilkanNasabah(user, uuid.take(8))
            } catch (e: Exception) {
                nasabahTidakDitemukan()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setTombolCek(loading = false)
            }
        }
    }

    // ===== CEK NASABAH MANUAL (8 digit - filter di Kotlin) =====
    private fun cekNasabahManual(inputId: String) {
        setTombolCek(loading = true)
        lifecycleScope.launch {
            try {
                val semuaNasabah = client.postgrest
                    .from("users")
                    .select { filter { eq("role", "nasabah") } }
                    .decodeList<User>()

                val user = semuaNasabah.firstOrNull {
                    it.idUser.startsWith(inputId, ignoreCase = true)
                } ?: throw Exception("Tidak ditemukan")

                tampilkanNasabah(user, inputId)
            } catch (e: Exception) {
                nasabahTidakDitemukan()
            } finally {
                setTombolCek(loading = false)
            }
        }
    }

    private fun tampilkanNasabah(user: User, displayId: String) {
        idNasabahDipilih   = user.idUser
        namaNasabahDipilih = user.namaLengkap
        binding.layoutInfoNasabah.visibility = View.VISIBLE
        binding.tvNamaNasabah.text            = user.namaLengkap
        binding.tvIdNasabahInfo.text          = "ID: $displayId"
        Toast.makeText(requireContext(), "Nasabah ditemukan: ${user.namaLengkap}", Toast.LENGTH_SHORT).show()
    }

    private fun nasabahTidakDitemukan() {
        idNasabahDipilih   = null
        namaNasabahDipilih = null
        binding.layoutInfoNasabah.visibility = View.GONE
        Toast.makeText(requireContext(), "Nasabah tidak ditemukan", Toast.LENGTH_SHORT).show()
    }

    private fun setTombolCek(loading: Boolean) {
        binding.btnCekNasabah.isEnabled = !loading
        binding.btnCekNasabah.text      = if (loading) "..." else "Cek"
    }

    private fun bukaKameraFoto() {
        val fotoDir = File(requireContext().cacheDir, "foto_setoran").apply { mkdirs() }
        fotoFile = File(fotoDir, "bukti_${System.currentTimeMillis()}.jpg")
        fotoUri  = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            fotoFile!!
        )
        ambilFotoLauncher.launch(fotoUri!!)
    }

    private fun validasiDanSubmit() {
        val beratStr = binding.etJumlah.text.toString().trim()
        val catatan  = binding.etCatatan.text.toString().trim()

        if (idNasabahDipilih == null) {
            Toast.makeText(requireContext(), "Scan QR atau masukkan ID nasabah terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (beratStr.isEmpty()) {
            binding.tilJumlah.error = "Berat minyak tidak boleh kosong"
            return
        }
        val berat = beratStr.toDoubleOrNull()
        if (berat == null || berat <= 0) {
            binding.tilJumlah.error = "Berat minyak tidak valid"
            return
        }
        binding.tilJumlah.error = null
        submitSetoran(berat, catatan)
    }

    // ===== SUBMIT — gunakan buildJsonObject agar null tidak error =====
    private fun submitSetoran(beratKg: Double, catatan: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val idUnit = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session login tidak ditemukan")

                val kodeTransaksi  = "TRX-SIM-QR-${System.currentTimeMillis()}"
                val totalRupiah    = beratKg * hargaPerKg
                val totalKomisi    = beratKg * komisiPerKg
                val catatanValue   = catatan.ifEmpty { null }

                // Gunakan buildJsonObject agar null ter-serialize dengan benar
                val payload = buildJsonObject {
                    put("kode_transaksi",         kodeTransaksi)
                    put("id_nasabah",             idNasabahDipilih!!)
                    put("id_unit",                idUnit)
                    put("id_batch",               JsonNull)          // null
                    put("berat_bersih_kg",        beratKg)
                    put("harga_satuan_snapshot",  hargaSatuanSnapshot)
                    put("level_bintang_snapshot", 1)
                    put("komisi_sudah_dibagi",    true)
                    put("harga_per_kg",           hargaPerKg)
                    put("total_rupiah_nasabah",   totalRupiah)
                    put("komisi_per_kg",          komisiPerKg)
                    put("total_komisi_unit",      totalKomisi)
                    put("status_setoran",         "menunggu")
                    put("bukti_foto_minyak",      JsonNull)          // null
                    if (catatanValue != null) {
                        put("catatan_unit", catatanValue)
                    } else {
                        put("catatan_unit", JsonNull)
                    }
                }

                client.postgrest.from("setoran").insert(payload)

                setLoading(false)
                Toast.makeText(
                    requireContext(),
                    "✓ Setoran berhasil!\nKode: $kodeTransaksi",
                    Toast.LENGTH_LONG
                ).show()

                resetForm()

            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetForm() {
        idNasabahDipilih   = null
        namaNasabahDipilih = null
        fotoUri  = null
        fotoFile = null
        binding.etIdNasabah.setText("")
        binding.etJumlah.setText("")
        binding.etCatatan.setText("")
        binding.layoutInfoNasabah.visibility     = View.GONE
        binding.layoutKalkulasi.visibility       = View.GONE
        binding.ivPreviewFoto.visibility         = View.GONE
        binding.layoutPlaceholderFoto.visibility = View.VISIBLE
        binding.tvSaldoNilai.text                = "Rp 0"
        binding.tvEstimasiRupiah.text            = "Rp 0"
    }

    private fun setLoading(loading: Boolean) {
        binding.btnKonfirmasi.isEnabled = !loading
        binding.btnKonfirmasi.text = if (loading) "Menyimpan..." else "Konfirmasi Setor"
    }

    private fun formatRupiah(nominal: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            .format(nominal).replace(",00", "")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}