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
        Configuration.getInstance().userAgentValue = requireContext().packageName
        _binding = FragmentRegistrasiUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        loadProvinsi()
        setupClickListeners()
    }

    // ===== SETUP MAP =====
    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
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
        lifecycleScope.launch {
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
                // Retry otomatis 1x jika gagal
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
        lifecycleScope.launch {
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

                // Reset level di bawahnya
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
        lifecycleScope.launch {
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

                // Reset wilayah
                binding.layoutWilayah.visibility = View.GONE
                listWilayah.clear()
                wilayahDipilih = null

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat kecamatan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadWilayahByKecamatan(idKecamatan: String) {
        lifecycleScope.launch {
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

    // ===== KOMPRESI GAMBAR =====
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

        // FAB WhatsApp Support
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

        // ===== SPINNER PROVINSI =====
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

        // ===== SPINNER KABUPATEN =====
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

        // ===== SPINNER KECAMATAN =====
        binding.spinnerKecamatan.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (listKecamatan.isEmpty()) return
                kecamatanDipilih = listKecamatan[pos]
                wilayahDipilih   = null
                loadWilayahByKecamatan(listKecamatan[pos].idKecamatan)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ===== SPINNER WILAYAH =====
        binding.spinnerWilayah.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (listWilayah.isEmpty()) return
                wilayahDipilih = listWilayah[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ===== VALIDASI FORM =====
    private fun validasiDanAjukan() {
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

        // Validasi wilayah bertingkat (Wajib sampai kelurahan/wilayah)
        if (provinsiDipilih == null) {
            Toast.makeText(requireContext(), "Pilih provinsi", Toast.LENGTH_SHORT).show()
            return
        }
        if (kabupatenDipilih == null) {
            Toast.makeText(requireContext(), "Pilih kabupaten/kota", Toast.LENGTH_SHORT).show()
            return
        }
        if (kecamatanDipilih == null) {
            Toast.makeText(requireContext(), "Pilih kecamatan", Toast.LENGTH_SHORT).show()
            return
        }
        if (wilayahDipilih == null) {
            Toast.makeText(requireContext(), "Pilih wilayah/kelurahan", Toast.LENGTH_SHORT).show()
            return
        }

        // Validasi koordinat peta
        if (latDipilih == null || lonDipilih == null) {
            binding.tvKoordinatDipilih.text = "⚠ Belum memilih lokasi di peta!"
            binding.tvKoordinatDipilih.setTextColor(Color.RED)
            Toast.makeText(
                requireContext(),
                "Pilih lokasi unit bisnis di peta.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Validasi foto bukti pembayaran
        if (uriBuktiBayar == null) {
            Toast.makeText(
                requireContext(),
                "⚠ Foto bukti pembayaran biaya pendaftaran wajib dilampirkan.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        ajukanKemitraan(
            namaUsaha,
            alamat,
            latDipilih!!,
            lonDipilih!!,
            wilayahDipilih!!
        )
    }

    // ===== SUBMIT PENGAJUAN =====
    private fun ajukanKemitraan(
        namaUsaha: String,
        alamat: String,
        lat: Double,
        lon: Double,
        wilayah: MasterWilayah
    ) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session tidak ditemukan, silakan login ulang")

                // 1. Cek sudah pernah mengajukan
                val existing = client.postgrest
                    .from("unit_bisnis_data")
                    .select { filter { eq("id_unit_bisnis", idUser) } }
                    .data

                if (existing != "[]" && existing.isNotBlank()) {
                    setLoading(false)
                    Toast.makeText(
                        requireContext(),
                        "Anda sudah pernah mengajukan kemitraan.\nTunggu proses verifikasi dari admin.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // 2. Upload foto bukti pembayaran ke bucket 'evidence'
                val uriBukti    = uriBuktiBayar!!
                val inputStream = requireContext().contentResolver.openInputStream(uriBukti)
                    ?: throw Exception("Gagal membaca file foto")
                val fotoBytes = kompresGambar(inputStream.readBytes())
                inputStream.close()

                val namaFile = "bukti-pendaftaran-ub/$idUser.jpg"
                val bucket   = client.storage.from("evidence")
                bucket.upload(namaFile, fotoBytes) { upsert = false }
                val fotoUrl = bucket.publicUrl(namaFile)

                // 3. Catat timestamp pembayaran
                val sdf      = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                val tglBayar = sdf.format(Date())

                // 4. Insert ke unit_bisnis_data
                val payload = buildJsonObject {
                    put("id_unit_bisnis",          idUser)
                    put("nama_usaha",              namaUsaha)
                    put("alamat",                  alamat)
                    put("lokasi_lat",              lat)
                    put("lokasi_long",             lon)
                    put("status_verifikasi_unit",  "menunggu")
                    put("tipe_unit",               "kelurahan") // Selalu kelurahan saat awal daftar
                    put("transaksi_harian",        0)
                    put("bukti_bayar_pendaftaran", fotoUrl)
                    put("tgl_bayar_pendaftaran",   tglBayar)
                    put("id_wilayah",              wilayah.idWilayah)
                }

                client.postgrest.from("unit_bisnis_data").insert(payload)

                setLoading(false)
                Toast.makeText(
                    requireContext(),
                    "✓ Pengajuan kemitraan berhasil dikirim!\nTim kami akan menghubungi Anda segera.",
                    Toast.LENGTH_LONG
                ).show()

                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                setLoading(false)
                val pesan = when {
                    e.message?.contains("duplicate") == true ||
                            e.message?.contains("unique") == true ->
                        "Anda sudah pernah mengajukan kemitraan."
                    e.message?.contains("row-level security") == true ->
                        "Akses ditolak. Pastikan Anda sudah login."
                    e.message?.contains("Gagal membaca") == true ->
                        "Gagal membaca file foto. Coba pilih foto lain."
                    else -> "Gagal mengajukan: ${e.message}"
                }
                Toast.makeText(requireContext(), pesan, Toast.LENGTH_LONG).show()
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