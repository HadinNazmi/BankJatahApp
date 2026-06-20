package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.MasterBank
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.UnitBisnisData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentPengaturanUnitBisnisBinding
import com.example.bankjatahapp.ui.component.AvatarUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PengaturanUnitBisnisFragment : Fragment() {

    private var _binding: FragmentPengaturanUnitBisnisBinding? = null
    private val binding get() = _binding!!

    private var idUser: String? = null

    private var listBank: List<MasterBank> = emptyList()
    private var bankDipilih: String? = null

    // ===== KOORDINAT BARU DARI PETA EDIT =====
    private var latBaru: Double? = null
    private var lonBaru: Double? = null
    private var markerEdit: org.osmdroid.views.overlay.Marker? = null

    private val requestLokasiLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) gunakanGpsSaatIni()
    }

    @Serializable
    data class ReferralResult(
        val id_owner: String,
        val nama_owner: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPengaturanUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupClickListeners()
    }

    private fun loadData() {
        setFormLoading(true)
        lifecycleScope.launch {
            try {
                idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val user = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser!!) } }
                    .decodeSingle<User>()

                val nasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser!!) } }
                    .decodeSingle<NasabahData>()

                val unit = client.postgrest
                    .from("unit_bisnis_data")
                    .select { filter { eq("id_unit_bisnis", idUser!!) } }
                    .decodeSingle<UnitBisnisData>()

                // ===== LOAD MASTER BANK =====
                try {
                    listBank = client.postgrest.from("master_bank")
                        .select { filter { eq("status_bank", "aktif") } }
                        .decodeList<MasterBank>()

                    val namaBank = listBank.map { it.namaBank }
                    val adapterBank = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        namaBank
                    )
                    adapterBank.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerBank.adapter = adapterBank

                    val indexBank = listBank.indexOfFirst { it.kodeBank == nasabah.bankCode }
                    if (indexBank >= 0) {
                        binding.spinnerBank.setSelection(indexBank)
                        bankDipilih = nasabah.bankCode
                    }

                    binding.spinnerBank.onItemSelectedListener =
                        object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(
                                p: AdapterView<*>?, v: View?, pos: Int, id: Long
                            ) { bankDipilih = listBank[pos].kodeBank }
                            override fun onNothingSelected(p: AdapterView<*>?) {}
                        }
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(), "Gagal memuat daftar bank", Toast.LENGTH_SHORT
                    ).show()
                }

                // ===== SET DATA REKENING =====
                binding.etNoRekening.setText(nasabah.noRekening ?: "")
                binding.etAtasNama.setText(nasabah.atasNamaRekening ?: "")

                // ===== AVATAR INISIAL =====
                AvatarUtils.pasangKeImageView(binding.ivFotoProfil, user.namaLengkap, 300)

                // Field yang bisa diubah
                binding.etNama.setText(user.namaLengkap)
                binding.etNoTelp.setText(user.noTelp ?: "")
                binding.etAlamat.setText(nasabah.alamatRumah ?: "")

                // ===== FIX 1: ISI SEMUA FIELD DARI DATA EXISTING UNIT BISNIS =====
                binding.etNamaUsaha.setText(unit.namaUsaha ?: "")
                binding.etJamBuka.setText(unit.jamBuka ?: "")
                binding.etJamTutup.setText(unit.jamTutup ?: "")
                binding.etHariOperasional.setText(unit.hariOperasional ?: "")
                binding.etAlamatUsaha.setText(unit.alamat ?: "")

                org.osmdroid.config.Configuration.getInstance().userAgentValue = requireContext().packageName
                val lat = unit.lokasiLat.takeIf { it != 0.0 } ?: 0.5071
                val lon = unit.lokasiLong.takeIf { it != 0.0 } ?: 101.4478

                binding.mapViewEdit.apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
                }

                if (unit.lokasiLat != 0.0) {
                    pindahkanMarkerEdit(unit.lokasiLat, unit.lokasiLong)
                    binding.tvKoordinatSaatIni.text =
                        "📍 Saat ini: ${String.format("%.6f", unit.lokasiLat)}, ${String.format("%.6f", unit.lokasiLong)}"
                }

                val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint): Boolean {
                        pindahkanMarkerEdit(p.latitude, p.longitude)
                        return true
                    }
                    override fun longPressHelper(p: org.osmdroid.util.GeoPoint): Boolean = false
                }
                binding.mapViewEdit.overlays.add(
                    org.osmdroid.views.overlay.MapEventsOverlay(mapEventsReceiver)
                )

                // Field read-only
                binding.tvEmailValue.text        = user.email
                binding.tvNikValue.text          = nasabah.nik ?: "-"
                binding.tvLevelBintangValue.text = "Bintang ${nasabah.levelBintang ?: 1}"
                binding.tvKategoriValue.text     = (nasabah.kategoriNasabah ?: "pasif")
                    .replaceFirstChar { it.uppercase() }
                binding.tvStatusAkunValue.text   = user.statusAkun.replaceFirstChar { it.uppercase() }
                binding.tvRoleValue.text         = "Unit Bisnis"

                val statusColor = when (user.statusAkun) {
                    "aktif"               -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
                    "dibekukan"           -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
                    "menunggu_verifikasi" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
                    else                  -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
                }
                binding.tvStatusAkunValue.setBackgroundColor(statusColor)

                setFormLoading(false)

            } catch (e: Exception) {
                setFormLoading(false)
                Toast.makeText(
                    requireContext(), "Gagal memuat: ${e.message}", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun pindahkanMarkerEdit(lat: Double, lon: Double) {
        latBaru = lat
        lonBaru = lon
        val geoPoint = org.osmdroid.util.GeoPoint(lat, lon)
        markerEdit?.let { binding.mapViewEdit.overlays.remove(it) }
        markerEdit = org.osmdroid.views.overlay.Marker(binding.mapViewEdit).apply {
            position = geoPoint
            title    = "Lokasi Unit Bisnis"
            setAnchor(
                org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
            )
        }
        binding.mapViewEdit.overlays.add(markerEdit)
        binding.mapViewEdit.controller.animateTo(geoPoint)
        binding.mapViewEdit.invalidate()
        binding.tvKoordinatBaru.text =
            "📍 Baru: ${String.format("%.6f", lat)}, ${String.format("%.6f", lon)}"
        binding.tvKoordinatBaru.setTextColor(
            requireContext().getColor(com.example.bankjatahapp.R.color.orange_primary)
        )
    }

    private fun gunakanGpsSaatIni() {
        try {
            com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(requireActivity())
                .lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        pindahkanMarkerEdit(location.latitude, location.longitude)
                        binding.mapViewEdit.controller.setZoom(17.0)
                    } else {
                        Toast.makeText(requireContext(), "GPS belum tersedia", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Izin lokasi belum diberikan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnSimpan.setOnClickListener {
            simpanPerubahan()
        }

        binding.btnGunakanGpsSaatIni.setOnClickListener {
            val fine   = androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (fine || coarse) gunakanGpsSaatIni()
            else requestLocationsLauncherAdapter()
        }
    }

    private fun requestLocationsLauncherAdapter() {
        requestLokasiLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }


    private fun simpanPerubahan() {
        val nama           = binding.etNama.text.toString().trim()
        val noTelp         = binding.etNoTelp.text.toString().trim()
        val alamat         = binding.etAlamat.text.toString().trim()
        val noRekening     = binding.etNoRekening.text.toString().trim()
        val atasNama       = binding.etAtasNama.text.toString().trim()

        if (nama.isEmpty()) {
            binding.tilNama.error = "Nama tidak boleh kosong"
            return
        }
        binding.tilNama.error = null

        setLoading(true)

        lifecycleScope.launch {
            try {
                val id = idUser ?: throw Exception("Session tidak ditemukan")

                // Update tabel users
                client.postgrest.from("users").update(
                    mapOf(
                        "nama_lengkap" to nama,
                        "no_telp"      to noTelp.ifEmpty { null }
                    )
                ) { filter { eq("id_user", id) } }

                // Update tabel nasabah_data
                val updatePayload = buildJsonObject {
                    put("alamat_rumah",       alamat.ifEmpty { null })
                    put("bank_code",          bankDipilih)
                    put("no_rekening",        noRekening.ifEmpty { null })
                    put("atas_nama_rekening", atasNama.ifEmpty { null })
                }

                client.postgrest.from("nasabah_data").update(updatePayload) {
                    filter { eq("id_nasabah", id) }
                }

                // ===== FIX 2: GANTI UPDATE UNIT BISNIS MENGGUNAKAN buildJsonObject =====
                val updateUnit = buildJsonObject {
                    put("nama_usaha",       binding.etNamaUsaha.text.toString().trim().ifEmpty { null })
                    put("jam_buka",         binding.etJamBuka.text.toString().trim().ifEmpty { null })
                    put("jam_tutup",        binding.etJamTutup.text.toString().trim().ifEmpty { null })
                    put("hari_operasional", binding.etHariOperasional.text.toString().trim().ifEmpty { null })
                    put("alamat",           binding.etAlamatUsaha.text.toString().trim().ifEmpty { null })

                    // Update koordinat hanya jika user memilih lokasi baru di peta
                    latBaru?.let { put("lokasi_lat",  it) }
                    lonBaru?.let { put("lokasi_long", it) }
                }

                client.postgrest.from("unit_bisnis_data").update(updateUnit) {
                    filter { eq("id_unit_bisnis", id) }
                }

                AvatarUtils.pasangKeImageView(binding.ivFotoProfil, nama, 300)

                setLoading(false)
                Toast.makeText(
                    requireContext(), "✓ Profil berhasil diperbarui!", Toast.LENGTH_SHORT
                ).show()
                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(
                    requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSimpan.isEnabled = !loading
        binding.btnSimpan.text = if (loading) "Menyimpan..." else "Simpan Perubahan"
    }

    private fun setFormLoading(loading: Boolean) {
        binding.progressBar.visibility   = if (loading) View.VISIBLE else View.GONE
        binding.scrollContent.visibility = if (loading) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) binding.mapViewEdit.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (_binding != null) binding.mapViewEdit.onPause()
    }

    override fun onDestroyView() {
        if (_binding != null) binding.mapViewEdit.onDetach()
        super.onDestroyView()
        _binding = null
    }
}