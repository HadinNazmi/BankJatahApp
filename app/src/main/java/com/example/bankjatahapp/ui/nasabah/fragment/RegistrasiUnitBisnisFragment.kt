package com.example.bankjatahapp.ui.nasabah.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.MasterKabupaten
import com.example.bankjatahapp.data.model.MasterKecamatan
import com.example.bankjatahapp.data.model.MasterProvinsi
import com.example.bankjatahapp.data.model.MasterWilayah
import com.example.bankjatahapp.data.model.HargaMinyak
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentRegistrasiUnitBisnisBinding
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegistrasiUnitBisnisFragment : Fragment() {

    private var _binding: FragmentRegistrasiUnitBisnisBinding? = null
    private val binding get() = _binding!!

    // ===== DATA WILAYAH BERTINGKAT =====
    private val listProvinsi   = mutableListOf<MasterProvinsi>()
    private val listKabupaten  = mutableListOf<MasterKabupaten>()
    private val listKecamatan  = mutableListOf<MasterKecamatan>()
    private val listWilayah    = mutableListOf<MasterWilayah>()

    private var provinsiDipilih  : MasterProvinsi?  = null
    private var kabupatenDipilih : MasterKabupaten? = null
    private var kecamatanDipilih : MasterKecamatan? = null
    private var wilayahDipilih   : MasterWilayah?   = null
    private var hargaWilayah     : Double?           = null

    // Koordinat dari peta
    private var latDipilih: Double? = null
    private var lonDipilih: Double? = null
    private var markerLokasi: Marker? = null

    // Default center: Pekanbaru, Riau
    private val defaultLat = 0.5071
    private val defaultLon = 101.4478

    // URI foto bukti bayar yang dipilih user
    private var uriBuktiBayar: Uri? = null

    // ===== LAUNCHER: Pilih Foto dari Galeri =====
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uriBuktiBayar = uri
            binding.ivPreviewBuktiBayar.setImageURI(uri)
            binding.ivPreviewBuktiBayar.visibility = View.VISIBLE
            binding.tvBuktiBayarStatus.text = "✓ Foto bukti pembayaran dipilih"
            binding.tvBuktiBayarStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.orange_primary)
            )
        }
    }

    // ===== LAUNCHER: Izin Lokasi =====
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) gunakanLokasiSaya()
        else Toast.makeText(
            requireContext(),
            "Izin lokasi ditolak. Tap manual di peta untuk memilih lokasi.",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Fix tile blocked — set semua config OSMDroid di sini
        Configuration.getInstance().apply {
            userAgentValue    = requireContext().packageName
            osmdroidBasePath  = requireContext().cacheDir
            osmdroidTileCache = java.io.File(requireContext().cacheDir, "osmdroid")
        }
        _binding = FragmentRegistrasiUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        loadProvinsi()
        setupClickListeners()
        cekPengajuanBerhentiAktif()
    }

    private fun cekPengajuanBerhentiAktif() {
        viewLifecycleOwner.lifecycleScope.launch {
            val adaPengajuan = (activity as? com.example.bankjatahapp.ui.nasabah.NasabahActivity)
                ?.cekAdaPengajuanAktif() ?: false
            if (adaPengajuan && _binding != null) {
                binding.btnAjukanKemitraan.isEnabled = false
                binding.btnAjukanKemitraan.text = "Tidak Dapat Mendaftar"
                binding.btnAjukanKemitraan.alpha = 0.5f
                Toast.makeText(
                    requireContext(),
                    "⚠️ Anda memiliki pengajuan penutupan akun aktif. Pendaftaran Unit Bisnis tidak dapat dilakukan.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ===== SETUP MAP =====
    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.XYTileSource(
                "Carto",
                0, 19, 256, ".png",
                arrayOf(
                    "https://a.basemaps.cartocdn.com/light_all/",
                    "https://b.basemaps.cartocdn.com/light_all/",
                    "https://c.basemaps.cartocdn.com/light_all/"
                )
            ))
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(defaultLat, defaultLon))
        }
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                pindahkanMarker(p.latitude, p.longitude)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        binding.mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))
    }

    // ===== MARKER PETA =====
    private fun pindahkanMarker(lat: Double, lon: Double) {
        latDipilih = lat
        lonDipilih = lon
        val geoPoint = GeoPoint(lat, lon)
        markerLokasi?.let { binding.mapView.overlays.remove(it) }
        markerLokasi = Marker(binding.mapView).apply {
            position = geoPoint
            title    = "Lokasi Unit Bisnis"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.mapView.overlays.add(markerLokasi)
        binding.mapView.controller.animateTo(geoPoint)
        binding.mapView.invalidate()

        val latStr = String.format("%.6f", lat)
        val lonStr = String.format("%.6f", lon)
        binding.etLatitude.setText(latStr)
        binding.etLongitude.setText(lonStr)
        binding.tvKoordinatDipilih.text = "📍 $latStr, $lonStr"
        binding.tvKoordinatDipilih.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.orange_primary)
        )
    }

    // ===== LOKASI GPS =====
    private fun mintaIzinLokasi() {
        val fine   = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) gunakanLokasiSaya()
        else requestPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun gunakanLokasiSaya() {
        try {
            LocationServices.getFusedLocationProviderClient(requireActivity())
                .lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        pindahkanMarker(location.latitude, location.longitude)
                        binding.mapView.controller.setZoom(17.0)
                        Toast.makeText(requireContext(), "Lokasi GPS berhasil diambil!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Lokasi GPS belum tersedia. Tap manual di peta.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Gagal mengambil lokasi: ${it.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Izin lokasi belum diberikan.", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== LOAD WILAYAH BERTINGKAT =====

    private fun loadProvinsi() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.spinnerProvinsi.isEnabled = false
                val hasil = client.postgrest
                    .from("master_provinsi")
                    .select()
                    .decodeList<MasterProvinsi>()

                if (hasil.isEmpty()) {
                    Toast.makeText(requireContext(), "Belum ada data provinsi. Hubungi admin.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                listProvinsi.clear()
                listProvinsi.addAll(hasil)

                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listProvinsi.map { it.namaProvinsi }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerProvinsi.adapter = adapter
                binding.spinnerProvinsi.isEnabled = true

            } catch (e: Exception) {
                try {
                    delay(1500)
                    val hasil = client.postgrest
                        .from("master_provinsi")
                        .select()
                        .decodeList<MasterProvinsi>()

                    listProvinsi.clear()
                    listProvinsi.addAll(hasil)

                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        listProvinsi.map { it.namaProvinsi }
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerProvinsi.adapter = adapter
                    binding.spinnerProvinsi.isEnabled = true
                } catch (e2: Exception) {
                    if (_binding == null) return@launch
                    Toast.makeText(requireContext(), "Gagal memuat provinsi. Cek koneksi internet.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadKabupaten(idProvinsi: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val hasil = client.postgrest
                    .from("master_kabupaten")
                    .select { filter { eq("provinsi_id", idProvinsi) } }
                    .decodeList<MasterKabupaten>()

                listKabupaten.clear()
                listKabupaten.addAll(hasil)

                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listKabupaten.map { it.namaKabupaten }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerKabupaten.adapter = adapter
                binding.layoutKabupaten.visibility = View.VISIBLE

                binding.layoutKecamatan.visibility = View.GONE
                binding.layoutWilayah.visibility   = View.GONE
                listKecamatan.clear()
                listWilayah.clear()
                kecamatanDipilih = null
                wilayahDipilih   = null

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat kabupaten: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadKecamatan(idKabupaten: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val hasil = client.postgrest
                    .from("master_kecamatan")
                    .select { filter { eq("kabupaten_id", idKabupaten) } }
                    .decodeList<MasterKecamatan>()

                listKecamatan.clear()
                listKecamatan.addAll(hasil)

                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listKecamatan.map { it.namaKecamatan }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerKecamatan.adapter = adapter
                binding.layoutKecamatan.visibility = View.VISIBLE

                binding.layoutWilayah.visibility = View.GONE
                listWilayah.clear()
                wilayahDipilih = null

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat kecamatan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadWilayahByKecamatan(idKecamatan: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val hasil = client.postgrest
                    .from("master_wilayah")
                    .select { filter {
                        eq("kecamatan_id", idKecamatan)
                        eq("status_wilayah", "aktif")
                    }}
                    .decodeList<MasterWilayah>()

                listWilayah.clear()
                listWilayah.addAll(hasil)

                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listWilayah.map { "${it.kodeWilayah} - ${it.namaWilayah}" }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerWilayah.adapter = adapter
                binding.layoutWilayah.visibility = View.VISIBLE

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat wilayah: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadHargaWilayah(idWilayah: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.cardHargaWilayah.visibility = View.GONE
                binding.tvHargaLoading.visibility   = View.VISIBLE
                val listHarga = client.postgrest
                    .from("harga_minyak")
                    .select {
                        filter {
                            eq("id_wilayah", idWilayah)
                            eq("status_harga", true)
                        }
                    }
                    .decodeList<HargaMinyak>()
                if (_binding == null) return@launch
                binding.tvHargaLoading.visibility = View.GONE
                if (listHarga.isNotEmpty()) {
                    val harga = listHarga.first()
                    hargaWilayah = harga.hargaPerKg
                    binding.cardHargaWilayah.visibility = View.VISIBLE
                    binding.tvHargaPerKg.text = formatRupiah(harga.hargaPerKg)
                    binding.tvWilayahHarga.text = wilayahDipilih?.namaWilayah ?: "-"
                    binding.tvKecamatanHarga.text = kecamatanDipilih?.namaKecamatan ?: "-"
                } else {
                    binding.cardHargaWilayah.visibility = View.VISIBLE
                    binding.tvHargaPerKg.text = "Belum diatur"
                    binding.tvWilayahHarga.text = wilayahDipilih?.namaWilayah ?: "-"
                    binding.tvKecamatanHarga.text = kecamatanDipilih?.namaKecamatan ?: "-"
                    hargaWilayah = null
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.tvHargaLoading.visibility   = View.GONE
                binding.cardHargaWilayah.visibility = View.GONE
            }
        }
    }

    private fun formatRupiah(nominal: Double): String =
        java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id", "ID"))
            .format(nominal).replace(",00", "")

    // ===== KOMPRESI GAMBAR =====
    private fun kompresGambar(bytes: ByteArray, maxSizeKb: Int = 800): ByteArray {
        // ===== STEP 1: Baca dimensi tanpa decode penuh =====
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        // ===== STEP 2: Hitung inSampleSize (target max 1080px) =====
        val maxPx = 1080
        var sampleSize = 1
        val tinggi = options.outHeight
        val lebar  = options.outWidth
        if (tinggi > maxPx || lebar > maxPx) {
            val halfTinggi = tinggi / 2
            val halfLebar  = lebar / 2
            while ((halfTinggi / sampleSize) >= maxPx || (halfLebar / sampleSize) >= maxPx) {
                sampleSize *= 2
            }
        }

        // ===== STEP 3: Decode dengan sample size =====
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)

        // ===== STEP 4: Scale ulang jika masih lebih besar dari maxPx =====
        val w = bitmap.width
        val h = bitmap.height
        if (w > maxPx || h > maxPx) {
            val ratio = maxPx.toFloat() / maxOf(w, h)
            val newW  = (w * ratio).toInt()
            val newH  = (h * ratio).toInt()
            bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        }

        // ===== STEP 5: TAMBAHKAN WATERMARK =====
        bitmap = tambahkanWatermark(bitmap, "Bukti Pendaftaran Unit Bisnis")

        // ===== STEP 6: Compress quality bertahap sampai di bawah maxSizeKb =====
        var quality = 85
        var hasil: ByteArray
        do {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            hasil = out.toByteArray()
            quality -= 10
        } while (hasil.size > maxSizeKb * 1024 && quality > 10)

        return hasil
    }

    private fun tambahkanWatermark(src: android.graphics.Bitmap, tipeDokumen: String = "Dokumen Resmi"): android.graphics.Bitmap {
        val lebar  = src.width
        val tinggi = src.height

        val hasil = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(hasil)

        val ukuranLogo = maxOf((lebar * 0.08f).toInt(), 40)
        val padding    = (lebar * 0.025f).toInt()

        // ===== 1. LOAD DAN PUTIHKAN LOGO =====
        var logoPutih: android.graphics.Bitmap? = null
        try {
            val logoAsli = android.graphics.BitmapFactory.decodeResource(
                requireContext().resources,
                R.drawable.bankjatahlogo
            )
            if (logoAsli != null) {
                // Buat salinan ARGB_8888 agar pixel bisa dimanipulasi
                val logoEditabel = logoAsli.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                val totalPixel   = logoEditabel.width * logoEditabel.height
                val arrayPixel   = IntArray(totalPixel)

                // Baca semua pixel sekaligus (lebih efisien dari getPixel loop)
                logoEditabel.getPixels(arrayPixel, 0, logoEditabel.width, 0, 0, logoEditabel.width, logoEditabel.height)

                // Ganti semua pixel yang tidak transparan menjadi PUTIH
                // Pertahankan nilai alpha aslinya agar tepi logo tetap halus (anti-alias)
                for (i in arrayPixel.indices) {
                    val alpha = android.graphics.Color.alpha(arrayPixel[i])
                    if (alpha > 10) { // threshold kecil untuk abaikan pixel semi-transparan tepi
                        arrayPixel[i] = android.graphics.Color.argb(alpha, 255, 255, 255)
                    }
                }

                logoEditabel.setPixels(arrayPixel, 0, logoEditabel.width, 0, 0, logoEditabel.width, logoEditabel.height)

                logoPutih = android.graphics.Bitmap.createScaledBitmap(
                    logoEditabel, ukuranLogo, ukuranLogo, true
                )
            }
        } catch (e: Exception) {
            // Lanjut tanpa logo jika gagal
        }

        // ===== 2. UKURAN DAN POSISI ELEMEN TEKS =====
        val ukuranTeks    = lebar * 0.035f
        val ukuranTeksSub = ukuranTeks * 0.75f

        val paintTeks = android.graphics.Paint().apply {
            color       = android.graphics.Color.WHITE
            textSize    = ukuranTeks
            isAntiAlias = true
            typeface    = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
        }
        val paintTeksSub = android.graphics.Paint().apply {
            color       = android.graphics.Color.argb(210, 255, 255, 255)
            textSize    = ukuranTeksSub
            isAntiAlias = true
            typeface    = android.graphics.Typeface.DEFAULT
        }

        val teksUtama = "Bank Jatah Indonesia"
        val teksSub = "$tipeDokumen • ${
            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        }"

        val lebarTeksUtama = paintTeks.measureText(teksUtama)
        val lebarTeksSub   = paintTeksSub.measureText(teksSub)

        // Lebar kotak = logo + teks terlebar + padding
        val gapLogoTeks  = (padding * 0.8f).toInt()
        val lebarIsiTeks = maxOf(lebarTeksUtama, lebarTeksSub)
        val lebarKotak   = (if (logoPutih != null) ukuranLogo + gapLogoTeks else 0) + lebarIsiTeks + (padding * 2f)
        val tinggiKotak  = maxOf(ukuranLogo.toFloat(), ukuranTeks * 3.2f) + (padding * 1f)

        val kotakX = lebar  - lebarKotak - padding
        val kotakY = tinggi - tinggiKotak - padding

        // ===== 3. BACKGROUND KOTAK SEMI-TRANSPARAN =====
        val paintBg = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(150, 0, 0, 0) // hitam 59% opacity
            style = android.graphics.Paint.Style.FILL
        }
        val rectF = android.graphics.RectF(
            kotakX.toFloat(), kotakY.toFloat(),
            lebar.toFloat() - padding,
            tinggi.toFloat() - padding
        )
        canvas.drawRoundRect(rectF, 14f, 14f, paintBg)

        // ===== 4. GAMBAR LOGO PUTIH (vertikal center di dalam kotak) =====
        if (logoPutih != null) {
            val logoX = kotakX + padding
            val logoY = kotakY + (tinggiKotak - ukuranLogo) / 2f
            canvas.drawBitmap(logoPutih, logoX.toFloat(), logoY, null)
        }

        // ===== 5. GAMBAR TEKS (di sebelah kanan logo) =====
        val teksStartX = if (logoPutih != null) {
            kotakX + padding + ukuranLogo + gapLogoTeks
        } else {
            kotakX + padding
        }.toFloat()

        // Teks utama: posisi vertikal sedikit di atas tengah kotak
        val tengahKotakY   = kotakY + tinggiKotak / 2f
        val teksUtamaY     = tengahKotakY - (ukuranTeks * 0.15f)
        val teksSubY       = teksUtamaY + ukuranTeks * 1.2f

        canvas.drawText(teksUtama, teksStartX, teksUtamaY, paintTeks)
        canvas.drawText(teksSub,   teksStartX, teksSubY,   paintTeksSub)

        return hasil
    }

    // ===== SETUP LISTENERS =====
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnGunakanLokasiSaya.setOnClickListener {
            mintaIzinLokasi()
        }
        binding.btnPilihBuktiBayar.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        binding.btnAjukanKemitraan.setOnClickListener {
            validasiDanAjukan()
        }

        binding.fabWhatsapp.setOnClickListener {
            val nomorAdmin = "6282283884373"
            val pesan = "Halo Admin, saya ingin bertanya mengenai pendaftaran Unit Bisnis Bank Jatah."
            val url = "https://wa.me/$nomorAdmin?text=${Uri.encode(pesan)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        binding.tvHubungiWa.setOnClickListener {
            val nomorAdmin = "6282283884373"
            val pesan = "Halo Admin, wilayah saya belum tersedia dalam daftar pilihan pendaftaran Unit Bisnis Bank Jatah. Mohon bantuannya."
            val url = "https://wa.me/$nomorAdmin?text=${Uri.encode(pesan)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        binding.spinnerProvinsi.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (listProvinsi.isEmpty()) return
                provinsiDipilih  = listProvinsi[pos]
                kabupatenDipilih = null
                kecamatanDipilih = null
                wilayahDipilih   = null
                loadKabupaten(listProvinsi[pos].idProvinsi)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerKabupaten.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (listKabupaten.isEmpty()) return
                kabupatenDipilih = listKabupaten[pos]
                kecamatanDipilih = null
                wilayahDipilih   = null
                loadKecamatan(listKabupaten[pos].idKabupaten)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerKecamatan.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (listKecamatan.isEmpty()) return
                kecamatanDipilih = listKecamatan[pos]
                wilayahDipilih   = null
                loadWilayahByKecamatan(listKecamatan[pos].idKecamatan)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerWilayah.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (listWilayah.isEmpty()) return
                wilayahDipilih = listWilayah[pos]
                loadHargaWilayah(listWilayah[pos].idWilayah)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun validasiDanAjukan() {
        if (!binding.btnAjukanKemitraan.isEnabled) return
        val namaUsaha = binding.etNamaUsaha.text.toString().trim()
        val alamat    = binding.etAlamat.text.toString().trim()

        if (namaUsaha.isEmpty()) {
            binding.tilNamaUsaha.error = "Nama usaha tidak boleh kosong"
            return
        }
        binding.tilNamaUsaha.error = null

        if (alamat.isEmpty()) {
            binding.tilAlamat.error = "Alamat tidak boleh kosong"
            return
        }
        binding.tilAlamat.error = null

        if (provinsiDipilih == null || kabupatenDipilih == null || kecamatanDipilih == null || wilayahDipilih == null) {
            Toast.makeText(requireContext(), "Lengkapi data wilayah", Toast.LENGTH_SHORT).show()
            return
        }

        if (latDipilih == null || lonDipilih == null) {
            Toast.makeText(requireContext(), "Pilih lokasi di peta", Toast.LENGTH_SHORT).show()
            return
        }

        if (uriBuktiBayar == null) {
            Toast.makeText(requireContext(), "Lampirkan bukti pembayaran", Toast.LENGTH_LONG).show()
            return
        }

        ajukanKemitraan(namaUsaha, alamat, latDipilih!!, lonDipilih!!, wilayahDipilih!!)
    }

    private fun ajukanKemitraan(
        namaUsaha: String,
        alamat: String,
        lat: Double,
        lon: Double,
        wilayah: MasterWilayah
    ) {
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: throw Exception("Session habis")

                val existing = client.postgrest
                    .from("unit_bisnis_data")
                    .select { filter { eq("id_unit_bisnis", idUser) } }
                    .data

                if (existing != "[]" && existing.isNotBlank()) {
                    setLoading(false)
                    Toast.makeText(requireContext(), "Anda sudah pernah mengajukan", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val uriBukti    = uriBuktiBayar!!
                val inputStream = requireContext().contentResolver.openInputStream(uriBukti) ?: throw Exception("Gagal baca foto")
                val fotoBytes = kompresGambar(inputStream.readBytes())
                inputStream.close()

                val namaFile = "bukti-pendaftaran-ub/$idUser.jpg"
                val bucket   = client.storage.from("evidence")
                bucket.upload(namaFile, fotoBytes) { upsert = false }
                val fotoUrl = bucket.publicUrl(namaFile)

                val sdf      = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                val tglBayar = sdf.format(Date())

                val payload = buildJsonObject {
                    put("id_unit_bisnis",          idUser)
                    put("nama_usaha",              namaUsaha)
                    put("alamat",                  alamat)
                    put("lokasi_lat",              lat)
                    put("lokasi_long",             lon)
                    put("status_verifikasi_unit",  "menunggu")
                    put("tipe_unit",               "kelurahan")
                    put("transaksi_harian",        0)
                    put("bukti_bayar_pendaftaran", fotoUrl)
                    put("tgl_bayar_pendaftaran",   tglBayar)
                    put("id_wilayah",              wilayah.idWilayah)
                }

                client.postgrest.from("unit_bisnis_data").insert(payload)

                setLoading(false)
                Toast.makeText(requireContext(), "Pengajuan berhasil dikirim!", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnAjukanKemitraan.isEnabled = !loading
        binding.btnAjukanKemitraan.text      = if (loading) "Mengajukan..." else "Ajukan Kemitraan Final"
        binding.btnPilihBuktiBayar.isEnabled = !loading
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDetach()
        _binding = null
    }
}