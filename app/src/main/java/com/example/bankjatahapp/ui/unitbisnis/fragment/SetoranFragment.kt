package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.HargaMinyak
import com.example.bankjatahapp.data.model.SystemConfig
import com.example.bankjatahapp.data.model.UnitBisnisData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentSetoranBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Serializable
data class NasabahSponsorInfo(
    @SerialName("id_sponsor") val idSponsor: String? = null
)

class SetoranFragment : Fragment() {

    private var _binding: FragmentSetoranBinding? = null
    private val binding get() = _binding!!

    private var idNasabahDipilih: String? = null
    private var namaNasabahDipilih: String? = null
    private var fotoUri: Uri? = null
    private var fotoFile: File? = null

    private var hargaPerKg: Double? = null
    private var komisiPerKg: Double? = null
    private var idUnitSaatIni: String? = null

    // ===== UPAH JEMPUT =====
    private var isJemput: Boolean = false
    private var biayaJemputPerKg: Double = 500.0

    // ===== SEARCH NASABAH =====
    private var searchJob: Job? = null

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
        if (granted) bukaScanner()
        else Toast.makeText(
            requireContext(),
            "Izin kamera diperlukan untuk scan QR",
            Toast.LENGTH_LONG
        ).show()
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
        setupSearchNasabah()
    }

    private fun muatHargaDanKomisi() {
        setFormEnabled(false)
        binding.btnKonfirmasi.text = "Memuat harga..."

        lifecycleScope.launch {
            try {
                val idUnit = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session tidak ditemukan, silakan login ulang.")

                idUnitSaatIni = idUnit

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

                biayaJemputPerKg = config.biayaJemputPerKg

                if (_binding == null) return@launch

                binding.tvJemputSub.text =
                    "Nasabah -${formatRupiah(biayaJemputPerKg)}/kg • UB +${formatRupiah(biayaJemputPerKg)}/kg"
                binding.tvPoinNilai.text = "${formatRupiah(harga.hargaPerKg)}/Kg"

                setFormEnabled(true)
                binding.btnKonfirmasi.text = "Konfirmasi Setor"

            } catch (e: Exception) {
                if (_binding == null) return@launch
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
        binding.switchJemput.isEnabled   = enabled
    }

    private fun kompresGambar(bytes: ByteArray, maxSizeKb: Int = 2048): ByteArray {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        var quality = 90
        var hasil: ByteArray
        do {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            hasil = out.toByteArray()
            quality -= 10
        } while (hasil.size > maxSizeKb * 1024 && quality > 10)
        return hasil
    }

    private fun setupListeners() {
        binding.frameQrScanner.setOnClickListener { mintaIzinDanScan() }
        binding.btnTutupKamera.setOnClickListener { tutupScanner() }

        binding.btnCekNasabah.setOnClickListener {
            val inputId = binding.etIdNasabah.text.toString().trim()
            if (inputId.isEmpty()) {
                Toast.makeText(requireContext(), "Masukkan ID nasabah terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (inputId.length == 36 && inputId.contains("-")) cekUserByUuid(inputId)
            else cekUserManual(inputId)
        }

        binding.switchJemput.setOnCheckedChangeListener { _, checked ->
            isJemput = checked
            hitungUlangEstimasi()
        }

        binding.etJumlah.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { hitungUlangEstimasi() }
        })

        binding.btnBukaKamera.setOnClickListener { bukaKameraFoto() }
        binding.btnKonfirmasi.setOnClickListener { validasiDanSubmit() }
    }

    private fun setupSearchNasabah() {
        binding.layoutHasilCari.visibility = View.GONE
        binding.tvTidakAdaHasil.visibility = View.GONE

        binding.etCariNasabah.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""

                searchJob?.cancel()

                if (query.length < 2) {
                    binding.layoutHasilCari.visibility = View.GONE
                    binding.tvTidakAdaHasil.visibility = View.GONE
                    return
                }

                binding.layoutHasilCari.visibility     = View.VISIBLE
                binding.tvTidakAdaHasil.visibility     = View.GONE
                binding.progressCariNasabah.visibility = View.VISIBLE

                searchJob = lifecycleScope.launch {
                    delay(500)
                    cariNasabahByNama(query)
                }
            }
        })

        binding.etCariNasabah.setOnFocusChangeListener { _, hasFokus ->
            if (!hasFokus && binding.etCariNasabah.text.isNullOrBlank()) {
                binding.layoutHasilCari.visibility = View.GONE
                binding.tvTidakAdaHasil.visibility = View.GONE
            }
        }
    }

    private suspend fun cariNasabahByNama(query: String) {
        try {
            val semuaUser = client.postgrest
                .from("users")
                .select {
                    filter { eq("status_akun", "aktif") }
                }
                .decodeList<User>()

            val hasilUser = semuaUser.filter {
                it.namaLengkap.contains(query, ignoreCase = true)
            }

            if (_binding == null) return

            binding.progressCariNasabah.visibility = View.GONE

            if (hasilUser.isEmpty()) {
                binding.tvTidakAdaHasil.visibility = View.VISIBLE
                binding.layoutHasilCari.visibility = View.GONE
                return
            }

            binding.tvTidakAdaHasil.visibility = View.GONE
            val idUserList = hasilUser.map { it.idUser }
            tampilkanHasilPencarian(hasilUser, idUserList)

        } catch (e: Exception) {
            if (_binding == null) return
            binding.progressCariNasabah.visibility = View.GONE
            binding.tvTidakAdaHasil.text = "Error: ${e.message}"
            binding.tvTidakAdaHasil.visibility = View.VISIBLE
        }
    }

    private suspend fun tampilkanHasilPencarian(users: List<User>, validIds: List<String>) {
        if (_binding == null) return

        binding.layoutDaftarHasil.removeAllViews()

        val nasabahDataMap = try {
            client.postgrest
                .from("nasabah_data")
                .select()
                .decodeList<NasabahData>()
                .associateBy { it.idNasabah }
        } catch (e: Exception) {
            emptyMap()
        }

        var adaHasil = false

        for (user in users.take(10)) {
            val nasabahData = nasabahDataMap[user.idUser]
            if (nasabahData == null) continue

            adaHasil = true
            val itemView = buatItemHasilCari(user, nasabahData)
            binding.layoutDaftarHasil.addView(itemView)
        }

        if (!adaHasil) {
            binding.tvTidakAdaHasil.text = "Tidak ada nasabah dengan nama tersebut"
            binding.tvTidakAdaHasil.visibility = View.VISIBLE
            binding.layoutHasilCari.visibility = View.GONE
        } else {
            binding.layoutHasilCari.visibility = View.VISIBLE
        }
    }

    private fun buatItemHasilCari(user: User, nasabahData: NasabahData): View {
        val ctx   = requireContext()
        val dp8   = (8  * resources.displayMetrics.density).toInt()
        val dp12  = (12 * resources.displayMetrics.density).toInt()

        val card = androidx.cardview.widget.CardView(ctx).apply {
            radius        = (8 * resources.displayMetrics.density)
            cardElevation = (1 * resources.displayMetrics.density)
            setCardBackgroundColor(ctx.getColor(R.color.white))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp8) }
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp12, dp12, dp12, dp12)
        }

        val tvAvatar = TextView(ctx).apply {
            text      = user.namaLengkap.take(1).uppercase()
            textSize  = 16f
            gravity   = Gravity.CENTER
            setTextColor(ctx.getColor(R.color.white))
            setBackgroundResource(R.drawable.ic_bg_aktivitas_orange)
            val size = (40 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp12
            }
        }

        val infoLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val tvNama = TextView(ctx).apply {
            text      = user.namaLengkap
            textSize  = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ctx.getColor(R.color.black))
        }

        val labelRole = if (user.role == "unit_bisnis") "Unit Bisnis" else "Nasabah"
        val levelTxt = "⭐ Level ${nasabahData.levelBintang ?: 1}  •  $labelRole"
        val tvDetail = TextView(ctx).apply {
            text      = levelTxt
            textSize  = 11f
            setTextColor(ctx.getColor(R.color.gray_text))
            setPadding(0, (2 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        val tvId = TextView(ctx).apply {
            text      = "ID: ${user.idUser.take(8)}"
            textSize  = 10f
            setTextColor(ctx.getColor(R.color.gray_text))
        }

        infoLayout.addView(tvNama)
        infoLayout.addView(tvDetail)
        infoLayout.addView(tvId)

        val btnPilih = Button(ctx).apply {
            text     = "Pilih"
            textSize = 12f
            setTextColor(ctx.getColor(R.color.white))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ctx.getColor(R.color.orange_primary)
            )
            val w = (64 * resources.displayMetrics.density).toInt()
            val h = (36 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(w, h).apply {
                marginStart = dp8
            }
            setOnClickListener {
                pilihNasabahDariSearch(user, nasabahData)
            }
        }

        inner.addView(tvAvatar)
        inner.addView(infoLayout)
        inner.addView(btnPilih)
        card.addView(inner)
        return card
    }

    private fun pilihNasabahDariSearch(user: User, nasabahData: NasabahData) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etCariNasabah.windowToken, 0)

        binding.etCariNasabah.setText("")
        binding.layoutHasilCari.visibility = View.GONE
        binding.tvTidakAdaHasil.visibility = View.GONE

        binding.etIdNasabah.setText(user.idUser.take(8) + "...")

        val sponsorInfo = NasabahSponsorInfo(idSponsor = nasabahData.idSponsor)
        tampilkanUser(user, user.idUser.take(8), sponsorInfo)
    }

    private fun hitungUlangEstimasi() {
        val berat = binding.etJumlah.text.toString().trim().toDoubleOrNull()
        val harga = hargaPerKg

        if (berat != null && berat > 0 && harga != null) {
            val hargaNasabah = if (isJemput) harga - biayaJemputPerKg else harga
            val totalNasabah = berat * hargaNasabah

            binding.tvEstimasiRupiah.text      = formatRupiah(totalNasabah)
            binding.layoutKalkulasi.visibility = View.VISIBLE
            binding.tvSaldoNilai.text          = formatRupiah(totalNasabah)
            binding.tvPoinNilai.text           = if (isJemput)
                "${formatRupiah(hargaNasabah)}/Kg (-${formatRupiah(biayaJemputPerKg)})"
            else "${formatRupiah(harga)}/Kg"

            if (isJemput) {
                binding.tvInfoJemput.visibility = View.VISIBLE
                binding.tvInfoJemput.text =
                    "Harga nasabah: ${formatRupiah(hargaNasabah)}/kg (dipotong ${formatRupiah(biayaJemputPerKg)}/kg)"
            } else {
                binding.tvInfoJemput.visibility = View.GONE
            }
        } else {
            binding.tvEstimasiRupiah.text      = "Rp 0"
            binding.tvSaldoNilai.text          = "Rp 0"
            binding.tvPoinNilai.text           = "${formatRupiah(hargaPerKg ?: 0.0)}/Kg"
            binding.tvInfoJemput.visibility    = View.GONE
            binding.layoutKalkulasi.visibility = View.GONE
        }
    }

    // ===== QR SCANNER =====
    private fun mintaIzinDanScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) bukaScanner()
        else requestKameraPermission.launch(Manifest.permission.CAMERA)
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
                .build().also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (sudahDipindai) { imageProxy.close(); return@setAnalyzer }
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            BarcodeScanning.getClient().process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val hasil = barcode.rawValue ?: continue
                                        if (hasil.isNotEmpty() && !sudahDipindai) {
                                            sudahDipindai = true
                                            requireActivity().runOnUiThread { onQrTerpindai(hasil.trim()) }
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else imageProxy.close()
                    }
                }
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    .select { filter { eq("id_user", uuid); eq("status_akun", "aktif") } }
                    .decodeSingle<User>()

                val sponsorInfo = cekStatusSponsor(uuid)
                tampilkanUser(user, uuid.take(8), sponsorInfo)
            } catch (e: Exception) {
                userTidakDitemukan()
                Toast.makeText(requireContext(), "User tidak ditemukan: ${e.message}", Toast.LENGTH_LONG).show()
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

                val sponsorInfo = cekStatusSponsor(user.idUser)
                tampilkanUser(user, inputId, sponsorInfo)
            } catch (e: Exception) {
                userTidakDitemukan()
                Toast.makeText(requireContext(), "${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setTombolCek(loading = false)
            }
        }
    }

    private suspend fun cekStatusSponsor(idUser: String): NasabahSponsorInfo? {
        return try {
            client.postgrest
                .from("nasabah_data")
                .select { filter { eq("id_nasabah", idUser) } }
                .decodeSingle<NasabahSponsorInfo>()
        } catch (_: Exception) { null }
    }

    private fun tampilkanUser(user: User, displayId: String, sponsorInfo: NasabahSponsorInfo?) {
        idNasabahDipilih   = user.idUser
        namaNasabahDipilih = user.namaLengkap

        binding.layoutInfoNasabah.visibility = View.VISIBLE
        binding.tvNamaNasabah.text           = user.namaLengkap

        val labelRole = if (user.role == "unit_bisnis") "Unit Bisnis" else "Nasabah"
        binding.tvIdNasabahInfo.text = "ID: $displayId • $labelRole"

        if (sponsorInfo != null) {
            binding.tvStatusSponsor.visibility = View.VISIBLE

            when {
                user.idUser == idUnitSaatIni -> {
                    binding.tvStatusSponsor.text = "⚠️ Ini akun Anda sendiri — sponsor tidak bisa diassign ke diri sendiri"
                    binding.tvStatusSponsor.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                }
                sponsorInfo.idSponsor == null -> {
                    binding.tvStatusSponsor.text = "⚠️ Nasabah belum memiliki sponsor — UB ini akan otomatis menjadi sponsornya"
                    binding.tvStatusSponsor.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                }
                else -> {
                    binding.tvStatusSponsor.text = "✓ Nasabah sudah memiliki sponsor"
                    binding.tvStatusSponsor.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                }
            }
        } else {
            binding.tvStatusSponsor.visibility = View.GONE
        }

        Toast.makeText(requireContext(), "Ditemukan: ${user.namaLengkap} ($labelRole)", Toast.LENGTH_SHORT).show()
    }

    private fun userTidakDitemukan() {
        idNasabahDipilih   = null
        namaNasabahDipilih = null
        binding.layoutInfoNasabah.visibility = View.GONE
        binding.tvStatusSponsor.visibility   = View.GONE
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
            Toast.makeText(requireContext(), "Data harga belum termuat.", Toast.LENGTH_SHORT).show()
            return
        }
        if (idNasabahDipilih == null) {
            Toast.makeText(requireContext(), "Scan QR atau masukkan ID pengguna terlebih dahulu.", Toast.LENGTH_SHORT).show()
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
        val catatanValue = catatan.ifEmpty { null }

        lifecycleScope.launch {
            try {
                val idUnit = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session login tidak ditemukan, silakan login ulang.")

                val kodeTransaksi = "TRX-${UUID.randomUUID().toString()
                    .replace("-", "").take(16).uppercase()}"

                var urlFotoBukti: String? = null
                val foto = fotoFile
                if (foto != null && foto.exists() && foto.length() > 0) {
                    try {
                        val bytes    = kompresGambar(foto.readBytes())
                        val namaFile = "bukti-ub/${idUnit}/${kodeTransaksi}.jpg"
                        client.storage["evidence"].upload(namaFile, bytes) { upsert = true }
                        urlFotoBukti = client.storage.from("evidence").publicUrl(namaFile)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(),
                            "Foto gagal diupload (${e.message}), setoran tetap disimpan",
                            Toast.LENGTH_SHORT).show()
                    }
                }

                val payload = buildJsonObject {
                    put("kode_transaksi",  kodeTransaksi)
                    put("id_nasabah",      idNasabahDipilih!!)
                    put("id_unit",         idUnit)
                    put("berat_bersih_kg", beratKg)
                    put("status_setoran",  "menunggu")
                    put("is_jemput",       isJemput)
                    if (catatanValue != null)  put("catatan_unit",      catatanValue)
                    if (urlFotoBukti != null)  put("bukti_foto_minyak", urlFotoBukti)
                }

                client.postgrest.from("setoran").insert(payload)

                val pesanJemput = if (isJemput) "\nMode: UB Jemput (+upah jemput)" else ""
                setLoading(false)
                Toast.makeText(requireContext(),
                    "✓ Setoran berhasil!\nKode: $kodeTransaksi$pesanJemput\nMenunggu validasi admin.",
                    Toast.LENGTH_LONG).show()

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
        isJemput = false

        binding.etIdNasabah.setText("")
        binding.etJumlah.setText("")
        binding.etCatatan.setText("")
        binding.switchJemput.isChecked           = false
        binding.layoutInfoNasabah.visibility     = View.GONE
        binding.tvStatusSponsor.visibility       = View.GONE
        binding.layoutKalkulasi.visibility       = View.GONE
        binding.ivPreviewFoto.visibility         = View.GONE
        binding.layoutPlaceholderFoto.visibility = View.VISIBLE
        binding.tvSaldoNilai.text                = "Rp 0"
        binding.tvEstimasiRupiah.text            = "Rp 0"
        binding.tvInfoJemput.visibility          = View.GONE

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