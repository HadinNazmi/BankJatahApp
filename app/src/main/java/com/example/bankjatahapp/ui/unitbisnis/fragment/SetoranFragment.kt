package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.HargaMinyak
import com.example.bankjatahapp.data.model.SystemConfig
import com.example.bankjatahapp.data.model.UnitBisnisData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentSetoranBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SetoranFragment : Fragment() {

    private var _binding: FragmentSetoranBinding? = null
    private val binding get() = _binding!!

    private var idNasabahDipilih: String? = null
    private var namaNasabahDipilih: String? = null
    private var fotoUri: Uri? = null
    private var fotoFile: File? = null

    private var hargaPerKg: Double? = null
    private var komisiPerKg: Double? = null

    // CameraX
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isScannerActive = false
    private var sudahDipindai = false

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

    // ===== PERMISSION KAMERA =====
    private val requestKameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            bukaScanner()
        } else {
            Toast.makeText(
                requireContext(),
                "Izin kamera diperlukan untuk scan QR",
                Toast.LENGTH_LONG
            ).show()
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
        cameraExecutor = Executors.newSingleThreadExecutor()
        muatHargaDanKomisi()
        setupListeners()
    }

    private fun muatHargaDanKomisi() {
        setFormEnabled(false)
        binding.btnKonfirmasi.text = "Memuat harga..."

        lifecycleScope.launch {
            try {
                val idUnit = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session tidak ditemukan, silakan login ulang.")

                val unitData = client.postgrest
                    .from("unit_bisnis_data")
                    .select { filter { eq("id_unit_bisnis", idUnit) } }
                    .decodeSingle<UnitBisnisData>()

                val idWilayah = unitData.idWilayah
                    ?: throw Exception("Wilayah unit bisnis belum diatur. Hubungi admin.")

                val harga = client.postgrest
                    .from("harga_minyak")
                    .select {
                        filter {
                            eq("id_wilayah", idWilayah)
                            eq("status_harga", true)
                        }
                    }
                    .decodeSingle<HargaMinyak>()

                hargaPerKg = harga.hargaPerKg

                val config = client.postgrest
                    .from("system_config")
                    .select { filter { eq("id_config", 1) } }
                    .decodeSingle<SystemConfig>()

                komisiPerKg = when (unitData.tipeUnit) {
                    "kabupaten" -> config.bonusUbKabupaten
                    else        -> config.bonusUbKelurahan
                }

                setFormEnabled(true)
                binding.btnKonfirmasi.text = "Konfirmasi Setor"

            } catch (e: Exception) {
                binding.btnKonfirmasi.text = "Konfirmasi Setor"
                Toast.makeText(
                    requireContext(),
                    "Gagal memuat data harga: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.btnKonfirmasi.isEnabled  = enabled
        binding.etJumlah.isEnabled       = enabled
        binding.btnBukaKamera.isEnabled  = enabled
        binding.frameQrScanner.isEnabled = enabled
        binding.btnCekNasabah.isEnabled  = enabled
    }

    private fun setupListeners() {

        // Tap placeholder hitam → buka scanner
        binding.frameQrScanner.setOnClickListener {
            mintaIzinDanScan()
        }

        // Tombol X tutup scanner
        binding.btnTutupKamera.setOnClickListener {
            tutupScanner()
        }

        binding.btnCekNasabah.setOnClickListener {
            val inputId = binding.etIdNasabah.text.toString().trim()
            if (inputId.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Masukkan ID nasabah terlebih dahulu",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            if (inputId.length == 36 && inputId.contains("-")) {
                cekUserByUuid(inputId)
            } else {
                cekUserManual(inputId)
            }
        }

        binding.etJumlah.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val berat = s.toString().toDoubleOrNull()
                val harga = hargaPerKg
                when {
                    berat != null && berat > 0 && harga != null -> {
                        val estimasi = formatRupiah(berat * harga)
                        binding.tvEstimasiRupiah.text      = estimasi
                        binding.tvSaldoNilai.text           = estimasi
                        binding.layoutKalkulasi.visibility = View.VISIBLE
                    }
                    berat != null && berat > 0 -> {
                        binding.tvEstimasiRupiah.text      = "Menghitung..."
                        binding.tvSaldoNilai.text           = "Menghitung..."
                        binding.layoutKalkulasi.visibility = View.VISIBLE
                    }
                    else -> {
                        binding.tvEstimasiRupiah.text      = "Rp 0"
                        binding.tvSaldoNilai.text           = "Rp 0"
                        binding.layoutKalkulasi.visibility = View.GONE
                    }
                }
            }
        })

        binding.btnBukaKamera.setOnClickListener { bukaKameraFoto() }
        binding.btnKonfirmasi.setOnClickListener { validasiDanSubmit() }
    }

    // ===== QR SCANNER =====
    private fun mintaIzinDanScan() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bukaScanner()
        } else {
            requestKameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun bukaScanner() {
        if (isScannerActive) return
        isScannerActive = true
        sudahDipindai   = false

        binding.frameQrScanner.visibility  = View.GONE
        binding.cameraPreview.visibility   = View.VISIBLE
        binding.qrOverlay.visibility       = View.VISIBLE
        binding.tvScanInstruksi.visibility = View.VISIBLE
        binding.btnTutupKamera.visibility  = View.VISIBLE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (sudahDipindai) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            BarcodeScanning.getClient()
                                .process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val hasil = barcode.rawValue ?: continue
                                        if (hasil.isNotEmpty() && !sudahDipindai) {
                                            sudahDipindai = true
                                            requireActivity().runOnUiThread {
                                                onQrTerpindai(hasil.trim())
                                            }
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Gagal membuka kamera: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                tutupScanner()
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun tutupScanner() {
        isScannerActive = false
        sudahDipindai   = false
        cameraProvider?.unbindAll()

        binding.frameQrScanner.visibility  = View.VISIBLE
        binding.cameraPreview.visibility   = View.GONE
        binding.qrOverlay.visibility       = View.GONE
        binding.tvScanInstruksi.visibility = View.GONE
        binding.btnTutupKamera.visibility  = View.GONE
    }

    private fun onQrTerpindai(uuid: String) {
        tutupScanner()
        binding.etIdNasabah.setText(uuid.take(8) + "...")
        cekUserByUuid(uuid)
    }

    // ===== CEK USER =====
    private fun cekUserByUuid(uuid: String) {
        setTombolCek(loading = true)
        lifecycleScope.launch {
            try {
                val user = client.postgrest
                    .from("users")
                    .select {
                        filter {
                            eq("id_user", uuid)
                            eq("status_akun", "aktif")
                        }
                    }
                    .decodeSingle<User>()

                tampilkanUser(user, uuid.take(8))
            } catch (e: Exception) {
                userTidakDitemukan()
                Toast.makeText(
                    requireContext(),
                    "User tidak ditemukan: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setTombolCek(loading = false)
            }
        }
    }

    private fun cekUserManual(inputId: String) {
        setTombolCek(loading = true)
        lifecycleScope.launch {
            try {
                val semuaUser = client.postgrest
                    .from("users")
                    .select { filter { eq("status_akun", "aktif") } }
                    .decodeList<User>()

                val user = semuaUser.firstOrNull {
                    it.idUser.startsWith(inputId, ignoreCase = true)
                } ?: throw Exception("User dengan ID $inputId tidak ditemukan.")

                tampilkanUser(user, inputId)
            } catch (e: Exception) {
                userTidakDitemukan()
                Toast.makeText(requireContext(), "${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setTombolCek(loading = false)
            }
        }
    }

    private fun tampilkanUser(user: User, displayId: String) {
        idNasabahDipilih   = user.idUser
        namaNasabahDipilih = user.namaLengkap
        binding.layoutInfoNasabah.visibility = View.VISIBLE
        binding.tvNamaNasabah.text            = user.namaLengkap
        val labelRole = when (user.role) {
            "unit_bisnis" -> "Unit Bisnis"
            "nasabah"     -> "Nasabah"
            else          -> user.role
        }
        binding.tvIdNasabahInfo.text = "ID: $displayId • $labelRole"
        Toast.makeText(
            requireContext(),
            "Ditemukan: ${user.namaLengkap} ($labelRole)",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun userTidakDitemukan() {
        idNasabahDipilih   = null
        namaNasabahDipilih = null
        binding.layoutInfoNasabah.visibility = View.GONE
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
        if (hargaPerKg == null || komisiPerKg == null) {
            Toast.makeText(
                requireContext(),
                "Data harga belum termuat. Tunggu sebentar atau buka ulang halaman ini.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (idNasabahDipilih == null) {
            Toast.makeText(
                requireContext(),
                "Scan QR atau masukkan ID pengguna terlebih dahulu.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val beratStr = binding.etJumlah.text.toString().trim()
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
        submitSetoran(berat, binding.etCatatan.text.toString().trim())
    }

    private fun submitSetoran(beratKg: Double, catatan: String) {
        setLoading(true)

        val harga       = hargaPerKg!!
        val komisi      = komisiPerKg!!
        val totalKomisi = beratKg * komisi
        val catatanValue = catatan.ifEmpty { null }

        lifecycleScope.launch {
            try {
                val idUnit = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session login tidak ditemukan, silakan login ulang.")

                val kodeTransaksi = "TRX-${UUID.randomUUID().toString()
                    .replace("-", "").take(16).uppercase()}"

                // ===== UPLOAD FOTO BUKTI KE SUPABASE STORAGE =====
                var urlFotoBukti: String? = null
                val foto = fotoFile
                if (foto != null && foto.exists() && foto.length() > 0) {
                    try {
                        val bytes    = foto.readBytes()
                        val namaFile = "bukti-ub/${idUnit}/${kodeTransaksi}.jpg"
                        client.storage["evidence"].upload(namaFile, bytes) { upsert = true }
                        urlFotoBukti = client.storage.from("evidence").publicUrl(namaFile)

                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Foto gagal diupload (${e.message}), setoran tetap disimpan",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val payload = buildJsonObject {
                    put("kode_transaksi",    kodeTransaksi)
                    put("id_nasabah",        idNasabahDipilih!!)
                    put("id_unit",           idUnit)
                    put("berat_bersih_kg",   beratKg)
                    put("status_setoran",    "menunggu")
                    put("komisi_per_kg",     komisi)
                    put("total_komisi_unit", totalKomisi)
                    if (catatanValue != null)  put("catatan_unit",     catatanValue)
                    if (urlFotoBukti != null)  put("bukti_foto_minyak", urlFotoBukti)
                }

                client.postgrest.from("setoran").insert(payload)

                setLoading(false)
                Toast.makeText(
                    requireContext(),
                    "✓ Setoran berhasil!\nKode: $kodeTransaksi\nHarga: ${formatRupiah(harga)}/kg\nMenunggu validasi admin.",
                    Toast.LENGTH_LONG
                ).show()

                resetForm()

            } catch (e: Exception) {
                setLoading(false)
                val pesan = when {
                    e.message?.contains("Harga tidak ditemukan") == true ->
                        "Harga minyak wilayah ini belum diatur admin."
                    e.message?.contains("Unit Bisnis tidak valid") == true ->
                        "Data unit bisnis belum lengkap, hubungi admin."
                    e.message?.contains("duplicate key") == true ->
                        "Transaksi duplikat terdeteksi, coba lagi."
                    e.message?.contains("row-level security") == true ->
                        "Akses ditolak. Pastikan Anda sudah login."
                    else -> "Gagal menyimpan setoran: ${e.message}"
                }
                Toast.makeText(requireContext(), pesan, Toast.LENGTH_LONG).show()
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

        if (isScannerActive) tutupScanner()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnKonfirmasi.isEnabled = !loading
        binding.btnKonfirmasi.text      = if (loading) "Menyimpan..." else "Konfirmasi Setor"
    }

    private fun formatRupiah(nominal: Double): String =
        NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            .format(nominal).replace(",00", "")

    override fun onPause() {
        super.onPause()
        if (isScannerActive) tutupScanner()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        _binding = null
    }
}