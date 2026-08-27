package com.example.bankjatahapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.ActivityLengkapiDataBinding
import com.example.bankjatahapp.ui.nasabah.NasabahActivity
import com.example.bankjatahapp.ui.unitbisnis.UnitBisnisActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


class LengkapiDataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLengkapiDataBinding
    private var idUser: String = ""
    private var roleUser: String = ""

    // Flag apakah ini kondisi migrasi (email masih default nomorhp@bankjatah.id)
    private var isMigrasi: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLengkapiDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        idUser   = intent.getStringExtra("id_user")   ?: ""
        roleUser = intent.getStringExtra("role_user") ?: "nasabah"

        tampilkanDialogPeringatan()
        setupUI()
        setupClickListeners()
    }

    private fun tampilkanDialogPeringatan() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠ Perhatian Penting")
            .setMessage(
                "Pastikan Anda mengisi data yang ASLI dan VALID.\n\n" +
                        "Seluruh proses transaksi, pencairan saldo, dan verifikasi " +
                        "identitas akan menggunakan data yang Anda masukkan.\n\n" +
                        "Data palsu atau tidak valid dapat menyebabkan " +
                        "akun Anda dibekukan oleh admin."
            )
            .setCancelable(false)
            .setPositiveButton("Saya Mengerti, Lanjutkan") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupUI() {
        val sudahAdaNoTelp = intent.getBooleanExtra("sudah_ada_no_telp", false)
        val sudahAdaNik    = intent.getBooleanExtra("sudah_ada_nik", false)
        val sudahAdaEmail  = intent.getBooleanExtra("sudah_ada_email", false)

        val noTelpAwal = intent.getStringExtra("nilai_no_telp") ?: ""
        val nikAwal    = intent.getStringExtra("nilai_nik")     ?: ""
        val emailAwal  = intent.getStringExtra("nilai_email")   ?: ""
        val namaAwal   = intent.getStringExtra("nilai_nama")    ?: ""

        // ✅ Deteksi kondisi migrasi:
        // Email masih format nomorhp@bankjatah.id atau @bankjatah.local
        isMigrasi = emailAwal.contains("@bankjatah.id") ||
                emailAwal.contains("@bankjatah.local")

        if (isMigrasi) {
            // ===== MODE MIGRASI — semua field bisa diedit =====
            binding.tvSubtitle.text =
                "Selamat datang! Mohon lengkapi dan perbarui data Anda untuk melanjutkan."

            // Nama lengkap — tampilkan yang sudah ada, bisa diedit
            binding.etNama.setText(namaAwal)
            binding.etNama.isEnabled  = true
            binding.tilNama.alpha     = 1.0f
            binding.tilNama.visibility = View.VISIBLE

            // Nomor telepon — bisa diedit, cek duplikat
            binding.etNoTelp.setText(noTelpAwal)
            binding.etNoTelp.isEnabled = true
            binding.tilNoTelp.alpha    = 1.0f

            // NIK — wajib diisi
            binding.etNik.setText(nikAwal)
            binding.etNik.isEnabled = true
            binding.tilNik.alpha    = 1.0f

            // Email — bisa diedit, sembunyikan email default
            val emailTampil = if (isMigrasi) "" else emailAwal
            binding.etEmail.setText(emailTampil)
            binding.etEmail.isEnabled = true
            binding.tilEmail.alpha    = 1.0f

            // Password — wajib buat baru
            binding.tilPasswordBaru.visibility       = View.VISIBLE
            binding.tilKonfirmasiPassword.visibility = View.VISIBLE
            binding.tvLabelPassword.visibility       = View.VISIBLE
            binding.tvSubtitlePassword.visibility    = View.VISIBLE

            // Info migrasi
            binding.cardInfoMigrasi.visibility = View.VISIBLE

        } else {
            // ===== MODE NORMAL — field yang sudah ada di-disable =====
            binding.tvSubtitle.text =
                "Lengkapi data Anda untuk melanjutkan."

            // Nama — sembunyikan jika tidak relevan
            binding.tilNama.visibility = View.GONE

            // Info migrasi disembunyikan
            binding.cardInfoMigrasi.visibility = View.GONE

            // Nomor telepon
            binding.etNoTelp.setText(noTelpAwal)
            if (sudahAdaNoTelp) {
                binding.etNoTelp.isEnabled    = false
                binding.tilNoTelp.alpha       = 0.6f
                binding.tilNoTelp.helperText  = "✓ Sudah terdaftar, tidak dapat diubah"
                binding.tilNoTelp.isHelperTextEnabled = true
            }

            // NIK
            binding.etNik.setText(nikAwal)
            if (sudahAdaNik) {
                binding.etNik.isEnabled    = false
                binding.tilNik.alpha       = 0.6f
                binding.tilNik.helperText  = "✓ Sudah terdaftar, tidak dapat diubah"
                binding.tilNik.isHelperTextEnabled = true
            }

            // Email
            val emailTampil = if (emailAwal.contains("@bankjatah.local")) "" else emailAwal
            binding.etEmail.setText(emailTampil)
            if (sudahAdaEmail && emailTampil.isNotEmpty()) {
                binding.etEmail.isEnabled    = false
                binding.tilEmail.alpha       = 0.6f
                binding.tilEmail.helperText  = "✓ Sudah terdaftar, tidak dapat diubah"
                binding.tilEmail.isHelperTextEnabled = true
            }

            // Password
            binding.tilPasswordBaru.visibility       = View.VISIBLE
            binding.tilKonfirmasiPassword.visibility = View.VISIBLE
            binding.tvLabelPassword.visibility       = View.VISIBLE
            binding.tvSubtitlePassword.visibility    = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.btnLanjutkan.setOnClickListener {
            simpanData()
        }

        binding.btnLogout.setOnClickListener {
            tampilkanDialogLogout()
        }
    }

    private fun tampilkanDialogLogout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Keluar")
            .setMessage("Anda akan keluar dari akun ini. Data yang belum disimpan akan hilang.\n\nAnda bisa login kembali kapan saja.")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        client.auth.signOut()
                    } catch (_: Exception) {}

                    val intent = Intent(this@LengkapiDataActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun simpanData() {
        val noTelp         = binding.etNoTelp.text.toString().trim()
        val nik            = binding.etNik.text.toString().trim()
        val email          = binding.etEmail.text.toString().trim()
        val nama           = binding.etNama.text.toString().trim()
        val passwordBaru   = binding.etPasswordBaru.text.toString().trim()
        val konfirmasiPass = binding.etKonfirmasiPassword.text.toString().trim()

        val sudahAdaNoTelp = intent.getBooleanExtra("sudah_ada_no_telp", false)
        val sudahAdaNik    = intent.getBooleanExtra("sudah_ada_nik", false)
        val sudahAdaEmail  = intent.getBooleanExtra("sudah_ada_email", false)
        val emailAwal      = intent.getStringExtra("nilai_email") ?: ""
        val emailDummy     = emailAwal.contains("@bankjatah.local") ||
                emailAwal.contains("@bankjatah.id")

        // ===== VALIDASI NAMA (mode migrasi) =====
        if (isMigrasi) {
            if (nama.isEmpty()) {
                binding.tilNama.error = "Nama lengkap tidak boleh kosong"
                return
            }
            binding.tilNama.error = null
        }

        // ===== VALIDASI NOMOR TELEPON =====
        if (!sudahAdaNoTelp || isMigrasi) {
            val noTelpBersih = noTelp.replace(Regex("[\\s\\-()]"), "")
            if (noTelp.isEmpty()) {
                binding.tilNoTelp.error = "Nomor telepon tidak boleh kosong"
                return
            }
            if (noTelpBersih.length < 8) {
                binding.tilNoTelp.error = "Nomor telepon tidak valid"
                return
            }
            binding.tilNoTelp.error = null
        }

        // ===== VALIDASI NIK =====
        if (!sudahAdaNik || isMigrasi) {
            if (nik.isEmpty()) {
                binding.tilNik.error = "NIK tidak boleh kosong"
                return
            }
            if (nik.length != 16) {
                binding.tilNik.error = "NIK harus 16 digit"
                return
            }
            binding.tilNik.error = null
        }

        // ===== VALIDASI EMAIL =====
        if (!sudahAdaEmail || emailDummy || isMigrasi) {
            if (email.isEmpty()) {
                binding.tilEmail.error = "Email tidak boleh kosong"
                return
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "Format email tidak valid"
                return
            }
            // Pastikan tidak pakai email default lagi
            if (email.contains("@bankjatah.id") || email.contains("@bankjatah.local")) {
                binding.tilEmail.error = "Gunakan email pribadi Anda, bukan email sistem"
                return
            }
            binding.tilEmail.error = null
        }

        // ===== VALIDASI PASSWORD =====
        if (passwordBaru.isEmpty()) {
            binding.tilPasswordBaru.error = "Password baru tidak boleh kosong"
            return
        }
        if (passwordBaru.length < 8) {
            binding.tilPasswordBaru.error = "Password minimal 8 karakter"
            return
        }
        binding.tilPasswordBaru.error = null

        if (konfirmasiPass.isEmpty()) {
            binding.tilKonfirmasiPassword.error = "Konfirmasi password tidak boleh kosong"
            return
        }
        if (konfirmasiPass != passwordBaru) {
            binding.tilKonfirmasiPassword.error = "Password tidak cocok"
            return
        }
        binding.tilKonfirmasiPassword.error = null

        setLoading(true)

        lifecycleScope.launch {
            try {
                val noTelpFormatted = run {
                    val bersih = noTelp.replace(Regex("[\\s\\-()]"), "")
                    when {
                        bersih.startsWith("+62") -> "0" + bersih.removePrefix("+62")
                        bersih.startsWith("62")  -> "0" + bersih.removePrefix("62")
                        bersih.startsWith("0")   -> bersih
                        else                     -> "0$bersih"
                    }
                }

                // ===== CEK NIK DUPLIKAT =====
                if (!sudahAdaNik || isMigrasi) {
                    val cekNik = client.postgrest
                        .from("nasabah_data")
                        .select { filter {
                            eq("nik", nik)
                            neq("id_nasabah", idUser)
                        }}
                        .data
                    if (cekNik != "[]") {
                        setLoading(false)
                        binding.tilNik.error = "NIK ini sudah digunakan akun lain"
                        return@launch
                    }
                }

                // ===== CEK NO TELP DUPLIKAT =====
                if (!sudahAdaNoTelp || isMigrasi) {
                    val cekTelp = client.postgrest
                        .from("users")
                        .select { filter {
                            eq("no_telp", noTelpFormatted)
                            neq("id_user", idUser)
                        }}
                        .data
                    if (cekTelp != "[]") {
                        setLoading(false)
                        binding.tilNoTelp.error = "Nomor telepon ini sudah digunakan akun lain"
                        return@launch
                    }
                }

                // ===== CEK EMAIL DUPLIKAT =====
                if (!sudahAdaEmail || emailDummy || isMigrasi) {
                    val cekEmail = client.postgrest
                        .from("users")
                        .select { filter {
                            eq("email", email)
                            neq("id_user", idUser)
                        }}
                        .data
                    if (cekEmail != "[]") {
                        setLoading(false)
                        binding.tilEmail.error = "Email ini sudah digunakan akun lain"
                        return@launch
                    }
                }

                // ===== UPDATE TABEL USERS =====
                val updateUsers = buildJsonObject {
                    if (isMigrasi && nama.isNotEmpty()) put("nama_lengkap", nama)
                    if (!sudahAdaNoTelp || isMigrasi) put("no_telp", noTelpFormatted)
                    if (!sudahAdaEmail || emailDummy || isMigrasi) put("email", email)
                }
                if (updateUsers.isNotEmpty()) {
                    client.postgrest.from("users").update(updateUsers) {
                        filter { eq("id_user", idUser) }
                    }
                }

                // ===== UPDATE EMAIL LEWAT AUTH DULU (trigger kirim konfirmasi) =====
                try {
                    client.auth.updateUser {
                        this.email = email
                    }
                } catch (_: Exception) {}

// ===== UPDATE PASSWORD LEWAT RPC =====
                try {
                    val payloadRpc = buildJsonObject {
                        put("p_id_user",       idUser)
                        put("p_email_baru",    email)
                        put("p_password_baru", passwordBaru)
                    }
                    client.postgrest.rpc("fn_update_auth_user", payloadRpc)
                } catch (e: Exception) {
                    setLoading(false)
                    Toast.makeText(
                        this@LengkapiDataActivity,
                        "Gagal update akun: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

// ===== UPDATE TABEL NASABAH_DATA =====
                if (!sudahAdaNik || isMigrasi) {
                    val updateNasabah = buildJsonObject {
                        put("nik", nik)
                    }
                    client.postgrest.from("nasabah_data").update(updateNasabah) {
                        filter { eq("id_nasabah", idUser) }
                    }
                }

// ===== LOGOUT & TAMPILKAN DIALOG KONFIRMASI EMAIL =====
                try { client.auth.signOut() } catch (_: Exception) {}

                setLoading(false)

                androidx.appcompat.app.AlertDialog.Builder(this@LengkapiDataActivity)
                    .setTitle("✓ Data Berhasil Disimpan!")
                    .setMessage(
                        "Email konfirmasi telah dikirim ke:\n$email\n\n" +
                                "Silakan buka email tersebut dan klik link konfirmasi.\n\n" +
                                "📌 Jika email tidak ditemukan di inbox, silakan cek folder " +
                                "Spam atau Promosi dan tandai sebagai 'Bukan Spam'.\n\n" +
                                "Setelah konfirmasi, login menggunakan:\n" +
                                "• Email: $email\n" +
                                "• Nomor HP: $noTelp\n" +
                                "• Password yang baru Anda buat\n\n" +
                                "Pastikan Anda mengingat email dan password Anda."
                    )
                    .setCancelable(false)
                    .setPositiveButton("Mengerti, Lanjut Login") { _, _ ->
                        val intent = Intent(this@LengkapiDataActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                    .show()

            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(
                    this@LengkapiDataActivity,
                    "Gagal menyimpan: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun navigasiKeHome() {
        val intent = if (roleUser == "unit_bisnis") {
            Intent(this, UnitBisnisActivity::class.java)
        } else {
            Intent(this, NasabahActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLanjutkan.isEnabled = !loading
        binding.btnLanjutkan.text      = if (loading) "Menyimpan..." else "Lanjutkan"
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(
            this,
            "Harap lengkapi data Anda terlebih dahulu",
            Toast.LENGTH_SHORT
        ).show()
    }
}