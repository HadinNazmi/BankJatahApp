package com.example.bankjatahapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.ActivityRegisterBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {

        // ===== Tombol Back → kembali ke LoginActivity =====
        binding.btnBackToLogin.setOnClickListener {
            finish()
        }

        // ===== Sudah punya akun? Masuk =====
        binding.tvMasuk.setOnClickListener {
            finish()
        }

        // ===== Link "Syarat & Ketentuan" di samping checkbox =====
        // Membuka dialog pop-up mode register (ada checkbox Setuju + tombol Setuju/Batal)
        binding.tvLinkSyarat.setOnClickListener {
            SyaratKetentuanDialog.tampilkan(
                context      = this,
                modeRegister = true
            ) { disetujui ->
                // Saat user klik Setuju di dalam dialog → centang checkbox otomatis
                if (disetujui) {
                    binding.cbSyarat.isChecked = true
                }
            }
        }

        // Checkbox manual juga bisa diklik langsung (tanpa buka dialog)
        binding.cbSyarat.setOnCheckedChangeListener { _, isChecked ->
            binding.btnDaftar.alpha = if (isChecked) 1.0f else 0.5f
        }

        // Default tombol daftar transparan
        binding.btnDaftar.alpha = 0.5f

        // ===== Tombol Daftar =====
        binding.btnDaftar.setOnClickListener {
            if (!validateForm()) return@setOnClickListener
            doRegister()
        }
    }

    private fun doRegister() {
        val nama     = binding.etNama.text.toString().trim()
        val email    = binding.etEmail.text.toString().trim()
        val noTelp   = "+62" + binding.etNoTelp.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val nik      = binding.etNik.text.toString().trim()
        val referal  = binding.etReferal.text.toString().trim()

        setLoading(true)

        lifecycleScope.launch {
            try {
                // STEP 1: Daftar ke Supabase Auth
                client.auth.signUpWith(Email) {
                    this.email    = email
                    this.password = password
                }

                // STEP 2: Ambil UUID
                val idUser = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Gagal mendapatkan ID user setelah registrasi")

                // STEP 3: Insert ke tabel users
                client.postgrest.from("users").insert(
                    mapOf(
                        "id_user"      to idUser,
                        "email"        to email,
                        "nama_lengkap" to nama,
                        "role"         to "nasabah",
                        "no_telp"      to noTelp,
                        "status_akun"  to "aktif"
                    )
                )

                // STEP 4: Insert ke tabel nasabah_data
                val nasabahData = mutableMapOf<String, Any?>(
                    "id_nasabah" to idUser,
                    "nik"        to nik
                )

                if (referal.isNotEmpty()) {
                    try {
                        val sponsor = client.postgrest
                            .from("nasabah_data")
                            .select { filter { eq("kode_referral", referal) } }
                            .data

                        if (sponsor != "[]" && sponsor.isNotBlank()) {
                            val idSponsor = extractIdNasabah(sponsor)
                            if (idSponsor != null) nasabahData["id_sponsor"] = idSponsor
                        }
                    } catch (e: Exception) {
                        // Referal tidak ditemukan → lanjut tanpa sponsor
                    }
                }

                client.postgrest.from("nasabah_data").insert(nasabahData)

                // STEP 5: Insert ke tabel dompet_user
                client.postgrest.from("dompet_user").insert(
                    mapOf("id_dompet" to idUser)
                )

                // STEP 6: Logout agar user login ulang
                client.auth.signOut()

                setLoading(false)
                Toast.makeText(this@RegisterActivity, "Akun berhasil dibuat! Silakan masuk.", Toast.LENGTH_LONG).show()

                // STEP 7: Kembali ke LoginActivity
                val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                setLoading(false)
                val pesan = when {
                    e.message?.contains("already registered") == true ||
                            e.message?.contains("already been registered") == true ->
                        "Email sudah terdaftar"
                    e.message?.contains("unique") == true &&
                            e.message?.contains("nik") == true ->
                        "NIK sudah terdaftar"
                    e.message?.contains("Password should be at least") == true ->
                        "Password minimal 6 karakter"
                    e.message?.contains("rate limit") == true ->
                        "Terlalu banyak percobaan, tunggu beberapa menit"
                    e.message?.contains("network") == true ||
                            e.message?.contains("Unable to resolve host") == true ->
                        "Tidak ada koneksi internet"
                    else -> "Gagal mendaftar: ${e.message}"
                }
                Toast.makeText(this@RegisterActivity, pesan, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun extractIdNasabah(jsonStr: String): String? {
        return try {
            """"id_nasabah"\s*:\s*"([^"]+)"""".toRegex().find(jsonStr)?.groupValues?.get(1)
        } catch (e: Exception) { null }
    }

    private fun setLoading(loading: Boolean) {
        if (loading) {
            binding.btnDaftar.isEnabled    = false
            binding.btnDaftar.text         = "Mendaftarkan..."
            binding.progressBar.visibility = View.VISIBLE
        } else {
            binding.btnDaftar.isEnabled    = true
            binding.btnDaftar.text         = "Daftar"
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        val nama       = binding.etNama.text.toString().trim()
        val email      = binding.etEmail.text.toString().trim()
        val noTelp     = binding.etNoTelp.text.toString().trim()
        val password   = binding.etPassword.text.toString().trim()
        val konfirmasi = binding.etKonfirmasiPassword.text.toString().trim()
        val nik        = binding.etNik.text.toString().trim()

        if (nama.isEmpty()) {
            binding.tilNama.error = "Nama lengkap tidak boleh kosong"; isValid = false
        } else binding.tilNama.error = null

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email tidak boleh kosong"; isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Format email tidak valid"; isValid = false
        } else binding.tilEmail.error = null

        if (noTelp.isEmpty()) {
            binding.tilNoTelp.error = "Nomor telepon tidak boleh kosong"; isValid = false
        } else if (noTelp.length < 9) {
            binding.tilNoTelp.error = "Nomor telepon tidak valid"; isValid = false
        } else binding.tilNoTelp.error = null

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password tidak boleh kosong"; isValid = false
        } else if (password.length < 8) {
            binding.tilPassword.error = "Password minimal 8 karakter"; isValid = false
        } else binding.tilPassword.error = null

        if (konfirmasi.isEmpty()) {
            binding.tilKonfirmasiPassword.error = "Konfirmasi password tidak boleh kosong"; isValid = false
        } else if (konfirmasi != password) {
            binding.tilKonfirmasiPassword.error = "Password tidak cocok"; isValid = false
        } else binding.tilKonfirmasiPassword.error = null

        if (nik.isEmpty()) {
            binding.tilNik.error = "NIK tidak boleh kosong"; isValid = false
        } else if (nik.length != 16) {
            binding.tilNik.error = "NIK harus 16 digit"; isValid = false
        } else binding.tilNik.error = null

        if (!binding.cbSyarat.isChecked) {
            Toast.makeText(this, "Anda harus menyetujui Syarat & Ketentuan", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }
}