package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.MasterBank
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.UnitBisnisData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.DialogEditFieldBinding
import com.example.bankjatahapp.databinding.FragmentPengaturanUnitBisnisBinding
import com.example.bankjatahapp.ui.component.AvatarUtils
import com.example.bankjatahapp.ui.component.TourHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PengaturanUnitBisnisFragment : Fragment() {

    private var _binding: FragmentPengaturanUnitBisnisBinding? = null
    private val binding get() = _binding!!

    private var idUser: String? = null
    private var cachedUser: User? = null
    private var cachedNasabah: NasabahData? = null
    private var cachedUnit: UnitBisnisData? = null

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

    // ===================== LOAD DATA =====================
    private fun loadData() {
        setFormLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                cachedUser = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser!!) } }
                    .decodeSingle<User>()

                cachedNasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser!!) } }
                    .decodeSingle<NasabahData>()

                cachedUnit = client.postgrest
                    .from("unit_bisnis_data")
                    .select { filter { eq("id_unit_bisnis", idUser!!) } }
                    .decodeSingle<UnitBisnisData>()

                // ===== LOAD MASTER BANK =====
                try {
                    listBank = client.postgrest.from("master_bank")
                        .select { filter { eq("status_bank", "aktif") } }
                        .decodeList<MasterBank>()

                    val currentBank = listBank.find { it.kodeBank == cachedNasabah?.bankCode }
                    binding.tvBankValue.text = currentBank?.namaBank ?: "Belum Memilih Bank"
                    bankDipilih = cachedNasabah?.bankCode
                } catch (e: Exception) {
                    binding.tvBankValue.text = "Gagal memuat opsi bank"
                }

                updateUiTexts()
                setupPeta()

                setFormLoading(false)

            } catch (e: Exception) {
                setFormLoading(false)
                Toast.makeText(requireContext(), "Gagal memuat: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUiTexts() {
        val user    = cachedUser ?: return
        val nasabah = cachedNasabah ?: return
        val unit    = cachedUnit ?: return

        AvatarUtils.pasangKeImageView(binding.ivFotoProfil, user.namaLengkap, 300)

        // Profil personal
        binding.tvNamaValue.text   = user.namaLengkap
        binding.tvNoTelpValue.text = user.noTelp ?: "Belum diisi"
        binding.tvAlamatValue.text = nasabah.alamatRumah ?: "Belum diisi"

        // Rekening pencairan
        binding.tvNoRekeningValue.text = nasabah.noRekening ?: "Belum diisi"
        binding.tvAtasNamaValue.text   = nasabah.atasNamaRekening ?: "Belum diisi"

        // Data unit bisnis
        binding.tvNamaUsahaValue.text       = unit.namaUsaha ?: "Belum diisi"
        binding.tvJamBukaValue.text         = unit.jamBuka ?: "Belum diisi"
        binding.tvJamTutupValue.text        = unit.jamTutup ?: "Belum diisi"
        binding.tvHariOperasionalValue.text = unit.hariOperasional ?: "Belum diisi"
        binding.tvAlamatUsahaValue.text     = unit.alamat ?: "Belum diisi"

        // Informasi sistem (read only)
        binding.tvEmailValue.text        = user.email
        binding.tvNikValue.text          = nasabah.nik ?: "-"
        binding.tvRoleValue.text         = "Unit Bisnis"
        binding.tvLevelBintangValue.text = "Bintang ${nasabah.levelBintang ?: 1}"
        binding.tvKategoriValue.text     = (nasabah.kategoriNasabah ?: "pasif").replaceFirstChar { it.uppercase() }
        binding.tvStatusAkunValue.text   = user.statusAkun.replaceFirstChar { it.uppercase() }

        val statusColor = when (user.statusAkun.lowercase()) {
            "aktif"                -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
            "dibekukan"             -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
            "menunggu_verifikasi"   -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
            else                    -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
        }
        binding.tvStatusAkunValue.setBackgroundColor(statusColor)

        // Koordinat saat ini
        if (unit.lokasiLat != 0.0) {
            binding.tvKoordinatSaatIni.text =
                "📍 Saat ini: ${String.format("%.6f", unit.lokasiLat)}, ${String.format("%.6f", unit.lokasiLong)}"
        } else {
            binding.tvKoordinatSaatIni.text = "Belum ada koordinat"
        }
    }

    // ===================== PETA =====================
    private fun setupPeta() {
        val unit = cachedUnit ?: return

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
            pindahkanMarkerEdit(unit.lokasiLat, unit.lokasiLong, simpanSebagaiPerubahan = false)
        }

        val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint): Boolean {
                pindahkanMarkerEdit(p.latitude, p.longitude, simpanSebagaiPerubahan = true)
                return true
            }
            override fun longPressHelper(p: org.osmdroid.util.GeoPoint): Boolean = false
        }
        binding.mapViewEdit.overlays.add(
            org.osmdroid.views.overlay.MapEventsOverlay(mapEventsReceiver)
        )
    }

    private fun pindahkanMarkerEdit(lat: Double, lon: Double, simpanSebagaiPerubahan: Boolean) {
        if (simpanSebagaiPerubahan) {
            latBaru = lat
            lonBaru = lon
        }

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

        if (simpanSebagaiPerubahan) {
            binding.tvKoordinatBaru.text =
                "📍 Baru: ${String.format("%.6f", lat)}, ${String.format("%.6f", lon)}"
            binding.tvKoordinatBaru.setTextColor(
                requireContext().getColor(com.example.bankjatahapp.R.color.orange_primary)
            )
        }
    }

    private fun gunakanGpsSaatIni() {
        try {
            com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(requireActivity())
                .lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        pindahkanMarkerEdit(location.latitude, location.longitude, simpanSebagaiPerubahan = true)
                        binding.mapViewEdit.controller.setZoom(17.0)
                    } else {
                        Toast.makeText(requireContext(), "GPS belum tersedia", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Izin lokasi belum diberikan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun simpanLokasiBaru() {
        val lat = latBaru
        val lon = lonBaru
        if (lat == null || lon == null) {
            Toast.makeText(requireContext(), "Tap peta atau gunakan GPS untuk memilih lokasi baru dulu", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = buildJsonObject {
            put("lokasi_lat", lat)
            put("lokasi_long", lon)
        }
        updateKeSupabase("unit_bisnis_data", payload)

        cachedUnit = cachedUnit?.copy(lokasiLat = lat, lokasiLong = lon)
        latBaru = null
        lonBaru = null
        binding.tvKoordinatBaru.text = "Tap peta untuk pilih lokasi baru"
        binding.tvKoordinatBaru.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.gray_text))
    }

    // ===================== CLICK LISTENERS =====================
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // --- Profil Personal ---
        binding.itemEditNama.setOnClickListener {
            tampilkanDialogEdit("Nama Lengkap", cachedUser?.namaLengkap ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) { nilaiBaru ->
                if (nilaiBaru.isEmpty()) return@tampilkanDialogEdit "Nama tidak boleh kosong"
                val payload = buildJsonObject { put("nama_lengkap", nilaiBaru) }
                updateKeSupabase("users", payload)
                cachedUser = cachedUser?.copy(namaLengkap = nilaiBaru)
                null
            }
        }

        binding.itemEditNoTelp.setOnClickListener {
            tampilkanDialogEdit("Nomor Telepon", cachedUser?.noTelp ?: "", InputType.TYPE_CLASS_PHONE) { nilaiBaru ->
                val payload = buildJsonObject { put("no_telp", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("users", payload)
                cachedUser = cachedUser?.copy(noTelp = nilaiBaru.ifEmpty { null })
                null
            }
        }

        binding.itemEditAlamat.setOnClickListener {
            tampilkanDialogEdit("Alamat Rumah", cachedNasabah?.alamatRumah ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) { nilaiBaru ->
                val payload = buildJsonObject { put("alamat_rumah", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("nasabah_data", payload)
                cachedNasabah = cachedNasabah?.copy(alamatRumah = nilaiBaru.ifEmpty { null })
                null
            }
        }

        // --- Rekening Pencairan ---
        binding.itemEditBank.setOnClickListener { tampilkanDialogEditBank() }

        binding.itemEditNoRekening.setOnClickListener {
            tampilkanDialogEdit("Nomor Rekening", cachedNasabah?.noRekening ?: "", InputType.TYPE_CLASS_NUMBER) { nilaiBaru ->
                val payload = buildJsonObject { put("no_rekening", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("nasabah_data", payload)
                cachedNasabah = cachedNasabah?.copy(noRekening = nilaiBaru.ifEmpty { null })
                null
            }
        }

        binding.itemEditAtasNama.setOnClickListener {
            tampilkanDialogEdit("Atas Nama Rekening", cachedNasabah?.atasNamaRekening ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) { nilaiBaru ->
                val payload = buildJsonObject { put("atas_nama_rekening", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("nasabah_data", payload)
                cachedNasabah = cachedNasabah?.copy(atasNamaRekening = nilaiBaru.ifEmpty { null })
                null
            }
        }

        binding.itemGantiPassword.setOnClickListener {
            tampilkanDialogGantiPassword()
        }

        // --- Data Unit Bisnis ---
        binding.itemEditNamaUsaha.setOnClickListener {
            tampilkanDialogEdit("Nama Usaha / Outlet", cachedUnit?.namaUsaha ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) { nilaiBaru ->
                val payload = buildJsonObject { put("nama_usaha", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("unit_bisnis_data", payload)
                cachedUnit = cachedUnit?.copy(namaUsaha = nilaiBaru.ifEmpty { null })
                null
            }
        }

        binding.itemEditJamBuka.setOnClickListener {
            tampilkanTimePickerDialog(
                judul       = "Jam Buka Operasional",
                nilaiSaatIni = cachedUnit?.jamBuka
            ) { jamTerpilih ->
                val payload = buildJsonObject { put("jam_buka", jamTerpilih) }
                updateKeSupabase("unit_bisnis_data", payload)
                cachedUnit = cachedUnit?.copy(jamBuka = jamTerpilih)
            }
        }

        binding.itemEditJamTutup.setOnClickListener {
            tampilkanTimePickerDialog(
                judul        = "Jam Tutup Operasional",
                nilaiSaatIni = cachedUnit?.jamTutup
            ) { jamTerpilih ->
                val payload = buildJsonObject { put("jam_tutup", jamTerpilih) }
                updateKeSupabase("unit_bisnis_data", payload)
                cachedUnit = cachedUnit?.copy(jamTutup = jamTerpilih)
            }
        }

        binding.itemEditHariOperasional.setOnClickListener {
            tampilkanDialogEdit("Hari Operasional", cachedUnit?.hariOperasional ?: "", InputType.TYPE_CLASS_TEXT) { nilaiBaru ->
                val payload = buildJsonObject { put("hari_operasional", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("unit_bisnis_data", payload)
                cachedUnit = cachedUnit?.copy(hariOperasional = nilaiBaru.ifEmpty { null })
                null
            }
        }

        binding.itemEditAlamatUsaha.setOnClickListener {
            tampilkanDialogEdit("Alamat Lengkap Unit Bisnis", cachedUnit?.alamat ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) { nilaiBaru ->
                val payload = buildJsonObject { put("alamat", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("unit_bisnis_data", payload)
                cachedUnit = cachedUnit?.copy(alamat = nilaiBaru.ifEmpty { null })
                null
            }
        }

        // --- Peta ---
        binding.btnGunakanGpsSaatIni.setOnClickListener {
            val fine = androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (fine || coarse) gunakanGpsSaatIni()
            else requestLokasiLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        // Di PengaturanAkunFragment (Nasabah):
        binding.itemTourPanduan.setOnClickListener {
            val activity = activity ?: return@setOnClickListener
            TourHelper.resetSemuaTour(activity)
            (activity as? com.example.bankjatahapp.ui.unitbisnis.UnitBisnisActivity)
                ?.navigateTo(R.id.nav_home)
        }

// Di PengaturanUnitBisnisFragment (UB) — sama persis

        binding.btnSimpanLokasi.setOnClickListener { simpanLokasiBaru() }
    }

    // ================= DIALOG GENERIK INPUT TEKS =================
    private fun tampilkanDialogEdit(
        judul: String,
        nilaiSekarang: String,
        jenisInput: Int,
        onSimpanDitekan: (String) -> String?
    ) {
        val dialogBinding = DialogEditFieldBinding.inflate(LayoutInflater.from(requireContext()))

        dialogBinding.tvDialogTitle.text = "Ubah $judul"
        dialogBinding.etDialogInput.setText(nilaiSekarang)
        dialogBinding.etDialogInput.inputType = jenisInput
        dialogBinding.etDialogInput.setSelection(dialogBinding.etDialogInput.text?.length ?: 0)

        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnDialogBatal.setOnClickListener { builder.dismiss() }

        dialogBinding.btnDialogSimpan.setOnClickListener {
            val textInput = dialogBinding.etDialogInput.text.toString().trim()
            val pesanError = onSimpanDitekan(textInput)
            if (pesanError != null) {
                dialogBinding.tilDialogInput.error = pesanError
            } else {
                builder.dismiss()
            }
        }
        builder.show()
    }

    // ================= DIALOG KHUSUS PILIH BANK =================
    private fun tampilkanDialogEditBank() {
        val dialogBinding = DialogEditFieldBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.tvDialogTitle.text = "Pilih Bank Pencairan"

        dialogBinding.tilDialogInput.visibility = View.GONE
        dialogBinding.spinnerDialogBank.visibility = View.VISIBLE

        val namaBankList = listBank.map { it.namaBank }
        val adapterBank = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, namaBankList)
        adapterBank.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerDialogBank.adapter = adapterBank

        val indexBank = listBank.indexOfFirst { it.kodeBank == bankDipilih }
        if (indexBank >= 0) dialogBinding.spinnerDialogBank.setSelection(indexBank)

        var bankTemp: String? = bankDipilih
        dialogBinding.spinnerDialogBank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                bankTemp = listBank[pos].kodeBank
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val builder = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
        dialogBinding.btnDialogBatal.setOnClickListener { builder.dismiss() }
        dialogBinding.btnDialogSimpan.setOnClickListener {
            bankDipilih = bankTemp
            val selectedBankName = listBank.find { it.kodeBank == bankDipilih }?.namaBank ?: "-"
            binding.tvBankValue.text = selectedBankName

            val payload = buildJsonObject { put("bank_code", bankDipilih) }
            updateKeSupabase("nasabah_data", payload)
            cachedNasabah = cachedNasabah?.copy(bankCode = bankDipilih)

            builder.dismiss()
        }
        builder.show()
    }

    // ================= TIME PICKER DIALOG (UNTUK JAM BUKA & TUTUP) =================
    private fun tampilkanTimePickerDialog(
        judul: String,
        nilaiSaatIni: String?,   // format "HH:mm" atau null
        onJamDipilih: (String) -> Unit
    ) {
        // Parse jam & menit dari nilai yang sudah tersimpan, fallback ke jam sekarang
        val (jamAwal, menitAwal) = try {
            val parts = nilaiSaatIni?.split(":")
            val h = parts?.getOrNull(0)?.toIntOrNull()
            val m = parts?.getOrNull(1)?.take(2)?.toIntOrNull()
            if (h != null && m != null) Pair(h, m)
            else {
                val cal = java.util.Calendar.getInstance()
                Pair(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            }
        } catch (e: Exception) {
            val cal = java.util.Calendar.getInstance()
            Pair(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        }

        val timePicker = android.app.TimePickerDialog(
            requireContext(),
            { _, jam, menit ->
                // Format hasil menjadi "HH:mm" — konsisten, tidak perlu ketik
                val hasilFormatted = String.format("%02d:%02d", jam, menit)
                onJamDipilih(hasilFormatted)
            },
            jamAwal,
            menitAwal,
            true // true = format 24 jam (lebih cocok untuk jam operasional bisnis)
        )

        timePicker.setTitle(judul)
        timePicker.show()
    }

    // ================= DIALOG GANTI PASSWORD =================
    private fun tampilkanDialogGantiPassword() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(com.example.bankjatahapp.R.layout.dialog_ganti_password, null)

        val tilPasswordLama     = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(com.example.bankjatahapp.R.id.tilPasswordLama)
        val etPasswordLama      = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.bankjatahapp.R.id.etPasswordLama)
        val tilPasswordBaru     = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(com.example.bankjatahapp.R.id.tilPasswordBaru)
        val etPasswordBaru      = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.bankjatahapp.R.id.etPasswordBaru)
        val tilKonfirmasiPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(com.example.bankjatahapp.R.id.tilKonfirmasiPassword)
        val etKonfirmasiPassword  = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.bankjatahapp.R.id.etKonfirmasiPassword)
        val btnBatal            = dialogView.findViewById<android.widget.Button>(com.example.bankjatahapp.R.id.btnDialogBatalPassword)
        val btnSimpan           = dialogView.findViewById<android.widget.Button>(com.example.bankjatahapp.R.id.btnDialogSimpanPassword)
        val progressBar         = dialogView.findViewById<android.widget.ProgressBar>(com.example.bankjatahapp.R.id.progressBarPassword)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnBatal.setOnClickListener { dialog.dismiss() }

        btnSimpan.setOnClickListener {
            val passwordLama      = etPasswordLama.text.toString()
            val passwordBaru      = etPasswordBaru.text.toString()
            val konfirmasiPassword = etKonfirmasiPassword.text.toString()

            // Reset error sebelumnya
            tilPasswordLama.error     = null
            tilPasswordBaru.error     = null
            tilKonfirmasiPassword.error = null

            // Validasi input lokal dulu
            var valid = true
            if (passwordLama.isEmpty()) {
                tilPasswordLama.error = "Password lama tidak boleh kosong"
                valid = false
            }
            if (passwordBaru.isEmpty()) {
                tilPasswordBaru.error = "Password baru tidak boleh kosong"
                valid = false
            } else if (passwordBaru.length < 8) {
                tilPasswordBaru.error = "Password baru minimal 8 karakter"
                valid = false
            }
            if (konfirmasiPassword != passwordBaru) {
                tilKonfirmasiPassword.error = "Konfirmasi password tidak cocok"
                valid = false
            }
            if (!valid) return@setOnClickListener

            // Nonaktifkan tombol, tampilkan loading
            btnSimpan.isEnabled = false
            btnBatal.isEnabled  = false
            progressBar.visibility = android.view.View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val email = cachedUser?.email
                        ?: throw Exception("Data akun tidak ditemukan, silakan muat ulang halaman")

                    // ===== STEP 1: Verifikasi password lama =====
                    // Pakai signInWith untuk mengecek apakah password lama benar
                    try {
                        com.example.bankjatahapp.data.remote.SupabaseClient.client.auth
                            .signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                                this.email    = email
                                this.password = passwordLama
                            }
                    } catch (e: Exception) {
                        // signInWith gagal = password lama salah
                        progressBar.visibility = android.view.View.GONE
                        btnSimpan.isEnabled    = true
                        btnBatal.isEnabled     = true
                        tilPasswordLama.error  = "Password lama tidak sesuai"
                        etPasswordLama.requestFocus()
                        return@launch
                    }

                    // ===== STEP 2: Update password baru =====
                    // Session sudah aktif dari signInWith di atas, langsung update
                    com.example.bankjatahapp.data.remote.SupabaseClient.client.auth.updateUser {
                        password = passwordBaru
                    }

                    progressBar.visibility = android.view.View.GONE
                    btnSimpan.isEnabled    = true
                    btnBatal.isEnabled     = true

                    dialog.dismiss()
                    android.widget.Toast.makeText(
                        requireContext(),
                        "✓ Password berhasil diubah!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                } catch (e: Exception) {
                    progressBar.visibility = android.view.View.GONE
                    btnSimpan.isEnabled    = true
                    btnBatal.isEnabled     = true
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Gagal mengubah password: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }

    // ================= UPDATE KE SUPABASE =================
    private fun updateKeSupabase(namaTabel: String, dataPayload: kotlinx.serialization.json.JsonObject) {
        setFormLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = idUser ?: throw Exception("Sesi kedaluwarsa")
                val kolomKunci = when (namaTabel) {
                    "users"            -> "id_user"
                    "unit_bisnis_data" -> "id_unit_bisnis"
                    else               -> "id_nasabah"
                }

                client.postgrest.from(namaTabel).update(dataPayload) {
                    filter { eq(kolomKunci, id) }
                }

                updateUiTexts()
                setFormLoading(false)
                Toast.makeText(requireContext(), "✓ Perubahan berhasil diterapkan secara instan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                setFormLoading(false)
                Toast.makeText(requireContext(), "Gagal sinkronisasi data: ${e.message}", Toast.LENGTH_LONG).show()
                loadData()
            }
        }
    }

    private fun setFormLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
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