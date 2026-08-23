package com.example.bankjatahapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.remote.SupabaseClient
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLengkapiDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        idUser   = intent.getStringExtra("id_user")   ?: ""
        roleUser = intent.getStringExtra("role_user") ?: "nasabah"

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        val sudahAdaNoTelp = intent.getBooleanExtra("sudah_ada_no_telp", false)
        val sudahAdaNik    = intent.getBooleanExtra("sudah_ada_nik", false)
        val sudahAdaEmail  = intent.getBooleanExtra("sudah_ada_email", false)

        val noTelpAwal = intent.getStringExtra("nilai_no_telp") ?: ""
        val nikAwal    = intent.getStringExtra("nilai_nik")     ?: ""
        val emailAwal  = intent.getStringExtra("nilai_email")   ?: ""

        // ===== NOMOR TELEPON =====
        binding.etNoTelp.setText(noTelpAwal)
        if (sudahAdaNoTelp) {
            binding.etNoTelp.isEnabled = false
            binding.tilNoTelp.alpha    = 0.6f
            binding.tilNoTelp.helperText = "✓ Sudah terdaftar, tidak dapat diubah"
            binding.tilNoTelp.isHelperTextEnabled = true
        }

        // ===== NIK =====
        binding.etNik.setText(nikAwal)
        if (sudahAdaNik) {
            binding.etNik.isEnabled = false
            binding.tilNik.alpha    = 0.6f
            binding.tilNik.helperText = "✓ Sudah terdaftar, tidak dapat diubah"
            binding.tilNik.isHelperTextEnabled = true
        }

        // ===== EMAIL =====
        // Jangan tampilkan email dummy @bankjatah.local
        val emailTampil = if (emailAwal.contains("@bankjatah.local")) "" else emailAwal
        binding.etEmail.setText(emailTampil)
        if (sudahAdaEmail && emailTampil.isNotEmpty()) {
            binding.etEmail.isEnabled = false
            binding.tilEmail.alpha    = 0.6f
            binding.tilEmail.helperText = "✓ Sudah terdaftar, tidak dapat diubah"
            binding.tilEmail.isHelperTextEnabled = true
        }

        // Subtitle dinamis
        val missing = mutableListOf<String>()
        if (!sudahAdaNoTelp) missing.add("nomor telepon")
        if (!sudahAdaNik)    missing.add("NIK")
        if (!sudahAdaEmail || emailTampil.isEmpty()) missing.add("email")

        binding.tvSubtitle.text = if (missing.isEmpty()) {
            "Semua data sudah lengkap. Silakan lanjutkan."
        } else {
            "Lengkapi ${missing.joinToString(" dan ")} Anda untuk melanjutkan."
        }
    }

    private fun setupClickListeners() {
        binding.btnLanjutkan.setOnClickListener {
            simpanData()
        }
    }

    private fun simpanData() {
        val sudahAdaNoTelp = intent.getBooleanExtra("sudah_ada_no_telp", false)
        val sudahAdaNik    = intent.getBooleanExtra("sudah_ada_nik", false)
        val sudahAdaEmail  = intent.getBooleanExtra("sudah_ada_email", false)
        val emailAwal      = intent.getStringExtra("nilai_email") ?: ""
        val emailDummy     = emailAwal.contains("@bankjatah.local")

        val noTelp           = binding.etNoTelp.text.toString().trim()
        val nik              = binding.etNik.text.toString().trim()
        val email            = binding.etEmail.text.toString().trim()
        val passwordBaru     = binding.etPasswordBaru.text.toString().trim()
        val konfirmasiPass   = binding.etKonfirmasiPassword.text.toString().trim()

        // ===== VALIDASI NOMOR TELEPON =====
        if (!sudahAdaNoTelp) {
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
        if (!sudahAdaNik) {
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
        if (!sudahAdaEmail || emailDummy) {
            if (email.isEmpty()) {
                binding.tilEmail.error = "Email tidak boleh kosong"
                return
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "Format email tidak valid"
                return
            }
            binding.tilEmail.error = null
        }

        // ===== VALIDASI PASSWORD BARU =====
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
                val noTelpFormatted = if (!sudahAdaNoTelp) {
                    val bersih = noTelp.replace(Regex("[\\s\\-()]"), "")
                    when {
                        bersih.startsWith("+62") -> "0" + bersih.removePrefix("+62")
                        bersih.startsWith("62")  -> "0" + bersih.removePrefix("62")
                        bersih.startsWith("0")   -> bersih
                        else                     -> "0$bersih"
                    }
                } else null

                // ===== CEK NIK TIDAK DIPAKAI AKUN LAIN =====
                if (!sudahAdaNik && nik.isNotEmpty()) {
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

                // ===== CEK NO TELP TIDAK DIPAKAI AKUN LAIN =====
                if (!sudahAdaNoTelp && noTelpFormatted != null) {
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

                // ===== UPDATE TABEL USERS =====
                val updateUsers = buildJsonObject {
                    if (!sudahAdaNoTelp && noTelpFormatted != null) {
                        put("no_telp", noTelpFormatted)
                    }
                    if ((!sudahAdaEmail || emailDummy) && email.isNotEmpty()) {
                        put("email", email)
                    }
                }
                if (updateUsers.isNotEmpty()) {
                    client.postgrest.from("users").update(updateUsers) {
                        filter { eq("id_user", idUser) }
                    }
                }

                // ===== UPDATE EMAIL & PASSWORD DI AUTH =====
                try {
                    client.auth.updateUser {
                        if ((!sudahAdaEmail || emailDummy) && email.isNotEmpty()) {
                            this.email = email
                        }
                        // ✅ Update password baru tanpa perlu password lama
                        // karena user sudah login dengan session aktif
                        this.password = passwordBaru
                    }
                } catch (_: Exception) {}

                // ===== UPDATE TABEL NASABAH_DATA =====
                if (!sudahAdaNik && nik.isNotEmpty()) {
                    val updateNasabah = buildJsonObject {
                        put("nik", nik)
                    }
                    client.postgrest.from("nasabah_data").update(updateNasabah) {
                        filter { eq("id_nasabah", idUser) }
                    }
                }

                setLoading(false)
                Toast.makeText(
                    this@LengkapiDataActivity,
                    "✓ Data dan password berhasil disimpan!",
                    Toast.LENGTH_SHORT
                ).show()

                navigasiKeHome()

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

    override fun onBackPressed() {
        // Blokir back button — user harus isi data dulu
        Toast.makeText(
            this,
            "Harap lengkapi data Anda terlebih dahulu",
            Toast.LENGTH_SHORT
        ).show()
    }
}