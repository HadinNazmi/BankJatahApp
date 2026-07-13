package com.example.bankjatahapp.ui.nasabah.fragment

import android.content.ClipboardManager
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
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.DialogEditFieldBinding
import com.example.bankjatahapp.databinding.FragmentPengaturanAkunBinding
import com.example.bankjatahapp.ui.component.AvatarUtils
import com.example.bankjatahapp.ui.component.TourHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PengaturanAkunFragment : Fragment() {

    private var _binding: FragmentPengaturanAkunBinding? = null
    private val binding get() = _binding!!

    private var idUser: String? = null
    private var cachedUser: User? = null
    private var cachedNasabah: NasabahData? = null
    private var listBank: List<MasterBank> = emptyList()
    private var bankDipilih: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPengaturanAkunBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupClickListeners()
    }

    private fun loadData() {
        setFormLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // 1. Ambil data Tabel Users
                cachedUser = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser!!) } }
                    .decodeSingle<User>()

                // 2. Ambil data Tabel Nasabah
                cachedNasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser!!) } }
                    .decodeSingle<NasabahData>()

                // 3. Ambil Master Daftar Bank Komersial Aktif
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

                // Terapkan nilai ke UI komponen teks
                updateUiTexts()

                setFormLoading(false)
            } catch (e: Exception) {
                setFormLoading(false)
                Toast.makeText(requireContext(), "Gagal sinkronisasi data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUiTexts() {
        val user = cachedUser ?: return
        val nasabah = cachedNasabah ?: return

        // Pasang avatar awal grafis nama
        AvatarUtils.pasangKeImageView(binding.ivFotoProfil, user.namaLengkap, 300)

        // Teks Bagian Daftar yang dapat Diedit
        binding.tvNamaValue.text = user.namaLengkap
        binding.tvNoTelpValue.text = user.noTelp ?: "Belum diisi"
        binding.tvAlamatValue.text = nasabah.alamatRumah ?: "Belum diisi"
        binding.tvNoRekeningValue.text = nasabah.noRekening ?: "Belum diisi"
        binding.tvAtasNamaValue.text = nasabah.atasNamaRekening ?: "Belum diisi"

        // Teks Bagian Informasi Read Only Sistem
        binding.tvEmailValue.text = user.email
        binding.tvNikValue.text = nasabah.nik ?: "-"
        binding.tvKodeReferralValue.text = nasabah.kodeReferral ?: "-"
        binding.tvLevelBintangValue.text = "Bintang ${nasabah.levelBintang ?: 1}"
        binding.tvKategoriValue.text = (nasabah.kategoriNasabah ?: "pasif").replaceFirstChar { it.uppercase() }
        binding.tvStatusAkunValue.text = user.statusAkun.replaceFirstChar { it.uppercase() }

        val statusColor = when (user.statusAkun.lowercase()) {
            "aktif" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
            "dibekukan" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
            "menunggu_verifikasi" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
            else -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
        }
        binding.tvStatusAkunValue.setBackgroundColor(statusColor)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // Fitur menyalin kode referral langsung ke clipboard android
        binding.tvBagikanKeTeman.setOnClickListener {
            val textToCopy = binding.tvKodeReferralValue.text.toString()
            if (textToCopy != "-") {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("Kode Referral", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Kode referral disalin ke clipboard", Toast.LENGTH_SHORT).show()
            }
        }

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
                val payload = buildJsonObject {
                    put("no_telp", nilaiBaru.ifEmpty { null })
                }
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

        binding.itemEditBank.setOnClickListener {
            tampilkanDialogEditBank()
        }

        binding.itemGantiPassword.setOnClickListener {
            tampilkanDialogGantiPassword()
        }

        binding.itemTourPanduan.setOnClickListener {
            val activity = activity ?: return@setOnClickListener
            TourHelper.resetSemuaTour(activity)
            // Navigasi langsung ke Home, bukan sekedar popBackStack
            (activity as? com.example.bankjatahapp.ui.nasabah.NasabahActivity)
                ?.navigateTo(R.id.nav_home)
        }

// Di PengaturanUnitBisnisFragment (UB) — sama persis
    }

    // ================= FUNGSI GENERIK DIALOG INPUT POP UP TEXT =================
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

            // Eksekusi fungsi validasi lokal sebelum menyimpan
            val pesanError = onSimpanDitekan(textInput)
            if (pesanError != null) {
                dialogBinding.tilDialogInput.error = pesanError
            } else {
                builder.dismiss()
            }
        }
        builder.show()
    }

    // ================= FUNGSI KHUSUS DIALOG POP UP SPINNER SELEKSI BANK =================
    private fun tampilkanDialogEditBank() {
        val dialogBinding = DialogEditFieldBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.tvDialogTitle.text = "Pilih Bank Pencairan"

        // Sembunyikan Input Teks, Munculkan Opsi Spinner
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

    // ================= LOGIK PROSES AKSES DATA KE SUPABASE BACKEND =================
    private fun updateKeSupabase(namaTabel: String, dataPayload: kotlinx.serialization.json.JsonObject) {
        setFormLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = idUser ?: throw Exception("Sesi kedaluwarsa")
                val kolomKunci = if (namaTabel == "users") "id_user" else "id_nasabah"

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}