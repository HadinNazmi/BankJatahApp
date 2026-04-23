package com.example.bankjatahapp.ui.nasabah.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.MasterWilayah
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentRegistrasiUnitBisnisBinding
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

class RegistrasiUnitBisnisFragment : Fragment() {

    private var _binding: FragmentRegistrasiUnitBisnisBinding? = null
    private val binding get() = _binding!!

    private val listWilayah = mutableListOf<MasterWilayah>()

    // Koordinat yang dipilih dari peta
    private var latDipilih: Double? = null
    private var lonDipilih: Double? = null

    // Marker di peta
    private var markerLokasi: Marker? = null

    // Default center peta: Pekanbaru, Riau
    private val defaultLat = 0.5071
    private val defaultLon = 101.4478

    // Permission launcher untuk lokasi
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            gunakanLokasiSaya()
        } else {
            Toast.makeText(
                requireContext(),
                "Izin lokasi ditolak. Tap manual di peta untuk memilih lokasi.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Wajib set userAgentValue sebelum pakai OSMDroid
        Configuration.getInstance().userAgentValue = requireContext().packageName
        _binding = FragmentRegistrasiUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        loadWilayah()
        setupClickListeners()
    }

    // ===== SETUP MAP =====
    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK) // OpenStreetMap
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(defaultLat, defaultLon))
        }

        // Overlay untuk tangkap tap di peta
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                pindahkanMarker(p.latitude, p.longitude)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        binding.mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))
    }

    // ===== PINDAHKAN / SET MARKER =====
    private fun pindahkanMarker(lat: Double, lon: Double) {
        latDipilih = lat
        lonDipilih = lon

        val geoPoint = GeoPoint(lat, lon)

        // Hapus marker lama jika ada
        markerLokasi?.let { binding.mapView.overlays.remove(it) }

        // Buat marker baru
        markerLokasi = Marker(binding.mapView).apply {
            position  = geoPoint
            title     = "Lokasi Unit Bisnis"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.mapView.overlays.add(markerLokasi)

        // Geser peta ke titik yang dipilih
        binding.mapView.controller.animateTo(geoPoint)
        binding.mapView.invalidate()

        // Update field teks
        val latStr = String.format("%.6f", lat)
        val lonStr = String.format("%.6f", lon)
        binding.etLatitude.setText(latStr)
        binding.etLongitude.setText(lonStr)
        binding.tvKoordinatDipilih.text = "📍 $latStr, $lonStr"
        binding.tvKoordinatDipilih.setTextColor(
            ContextCompat.getColor(requireContext(), com.example.bankjatahapp.R.color.orange_primary)
        )
    }

    // ===== GUNAKAN LOKASI GPS =====
    private fun mintaIzinLokasi() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            gunakanLokasiSaya()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun gunakanLokasiSaya() {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            fusedClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    pindahkanMarker(location.latitude, location.longitude)
                    binding.mapView.controller.setZoom(17.0)
                    Toast.makeText(requireContext(), "Lokasi GPS berhasil diambil!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Lokasi GPS belum tersedia. Pastikan GPS aktif lalu coba lagi, atau tap manual di peta.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Gagal mengambil lokasi: ${it.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Izin lokasi belum diberikan.", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== LOAD WILAYAH =====
    private fun loadWilayah() {
        lifecycleScope.launch {
            try {
                val hasil = client.postgrest
                    .from("master_wilayah")
                    .select { filter { eq("status_wilayah", "aktif") } }
                    .decodeList<MasterWilayah>()

                listWilayah.clear()
                listWilayah.addAll(hasil)

                val namaList = listWilayah.map { "${it.kodeWilayah} - ${it.namaWilayah}" }
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    namaList
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerWilayah.adapter = adapter

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Gagal memuat wilayah: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnGunakanLokasiSaya.setOnClickListener {
            mintaIzinLokasi()
        }

        binding.btnAjukanKemitraan.setOnClickListener {
            validasiDanAjukan()
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

        if (listWilayah.isEmpty()) {
            Toast.makeText(requireContext(), "Data wilayah belum dimuat", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedIndex = binding.spinnerWilayah.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= listWilayah.size) {
            Toast.makeText(requireContext(), "Pilih wilayah operasional", Toast.LENGTH_SHORT).show()
            return
        }

        if (alamat.isEmpty()) {
            binding.tilAlamat.error = "Alamat tidak boleh kosong"
            return
        }
        binding.tilAlamat.error = null

        // Validasi koordinat — wajib pilih dari peta
        if (latDipilih == null || lonDipilih == null) {
            Toast.makeText(
                requireContext(),
                "Pilih lokasi unit bisnis di peta terlebih dahulu.\nTap di peta atau gunakan tombol 'Lokasi Saya'.",
                Toast.LENGTH_LONG
            ).show()
            // Scroll ke peta (tidak bisa otomatis dari fragment, tapi toast sudah cukup)
            binding.tvKoordinatDipilih.text = "⚠ Belum memilih lokasi di peta!"
            binding.tvKoordinatDipilih.setTextColor(Color.RED)
            return
        }

        val wilayahDipilih = listWilayah[selectedIndex]
        ajukanKemitraan(namaUsaha, alamat, latDipilih!!, lonDipilih!!, wilayahDipilih)
    }

    // ===== SUBMIT =====
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

                // Cek apakah sudah pernah mengajukan
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

                val payload = buildJsonObject {
                    put("id_unit_bisnis",         idUser)
                    put("id_wilayah",             wilayah.idWilayah)
                    put("nama_usaha",             namaUsaha)
                    put("alamat",                 alamat)
                    put("lokasi_lat",             lat)
                    put("lokasi_long",            lon)
                    put("status_verifikasi_unit", "menunggu")
                    put("tipe_unit",              "kelurahan")
                    put("transaksi_harian",       0)
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
                    else -> "Gagal mengajukan: ${e.message}"
                }
                Toast.makeText(requireContext(), pesan, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnAjukanKemitraan.isEnabled = !loading
        binding.btnAjukanKemitraan.text =
            if (loading) "Mengajukan..." else "Ajukan Kemitraan Final"
    }

    // OSMDroid perlu resume/pause untuk kelola tile cache
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