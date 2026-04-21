package com.example.bankjatahapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.ActivityLoginBinding
import com.example.bankjatahapp.ui.nasabah.NasabahActivity
import com.example.bankjatahapp.ui.unitbisnis.UnitBisnisActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    // Mode login saat ini: "email" atau "nohp"
    private var modLogin = "email"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSyaratKetentuan()
        setupTabToggle()
        setupClickListeners()
    }

    // ===== SPANNABLE SYARAT & KETENTUAN =====
    private fun setupSyaratKetentuan() {
        val fullText = "Dengan masuk, Anda menyetujui Syarat & Ketentuan dan Kebijakan Privasi"
        val spannable = SpannableString(fullText)
        val orangeColor = ContextCompat.getColor(this, R.color.orange_primary)

        val syaratStart = fullText.indexOf("Syarat & Ketentuan")
        val syaratEnd   = syaratStart + "Syarat & Ketentuan".length
        spannable.setSpan(ForegroundColorSpan(orangeColor), syaratStart, syaratEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(UnderlineSpan(), syaratStart, syaratEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val kebijakanStart = fullText.indexOf("Kebijakan Privasi")
        val kebijakanEnd   = kebijakanStart + "Kebijakan Privasi".length
        spannable.setSpan(ForegroundColorSpan(orangeColor), kebijakanStart, kebijakanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(UnderlineSpan(), kebijakanStart, kebijakanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.tvSyaratKetentuan.text = spannable
    }

    // ===== TOGGLE TAB EMAIL / NO HP =====
    private fun setupTabToggle() {
        binding.tabEmail.setOnClickListener {
            if (modLogin != "email") {
                modLogin = "email"
                updateTampilan()
            }
        }
        binding.tabNoHp.setOnClickListener {
            if (modLogin != "nohp") {
                modLogin = "nohp"
                updateTampilan()
            }
        }
    }

    private fun updateTampilan() {
        val params = binding.tvLabelPassword.layoutParams as ConstraintLayout.LayoutParams

        if (modLogin == "email") {
            // Tab Email aktif
            binding.tabEmail.setBackgroundResource(R.drawable.ic_bg_tab_active)
            binding.tabEmail.setTextColor(getColor(R.color.white))
            binding.tabNoHp.setBackgroundResource(android.R.color.transparent)
            binding.tabNoHp.setTextColor(getColor(R.color.gray_text))

            binding.tvLabelIdentitas.text = "Email"
            binding.tilEmail.visibility   = View.VISIBLE
            binding.tilNoHp.visibility    = View.GONE

            // tvLabelPassword constraint ke bawah tilEmail
            params.topToBottom = binding.tilEmail.id
            binding.tilEmail.requestFocus()
        } else {
            // Tab No HP aktif
            binding.tabNoHp.setBackgroundResource(R.drawable.ic_bg_tab_active)
            binding.tabNoHp.setTextColor(getColor(R.color.white))
            binding.tabEmail.setBackgroundResource(android.R.color.transparent)
            binding.tabEmail.setTextColor(getColor(R.color.gray_text))

            binding.tvLabelIdentitas.text = "No. HP"
            binding.tilNoHp.visibility    = View.VISIBLE
            binding.tilEmail.visibility   = View.GONE

            // tvLabelPassword constraint ke bawah tilNoHp
            params.topToBottom = binding.tilNoHp.id
            binding.tilNoHp.requestFocus()
        }

        binding.tvLabelPassword.layoutParams = params
    }

    // ===== CLICK LISTENERS =====
    private fun setupClickListeners() {
        binding.btnMasuk.setOnClickListener {
            val password = binding.etPassword.text.toString().trim()

            if (password.isEmpty()) {
                binding.tilPassword.error = "Password tidak boleh kosong"
                return@setOnClickListener
            }
            binding.tilPassword.error = null

            if (modLogin == "email") {
                val email = binding.etEmail.text.toString().trim()
                if (email.isEmpty()) {
                    binding.tilEmail.error = "Email tidak boleh kosong"
                    return@setOnClickListener
                }
                binding.tilEmail.error = null
                doLoginEmail(email, password)
            } else {
                val noHp = binding.etNoHp.text.toString().trim()
                if (noHp.isEmpty()) {
                    binding.tilNoHp.error = "Nomor HP tidak boleh kosong"
                    return@setOnClickListener
                }
                binding.tilNoHp.error = null
                doLoginNoHp(noHp, password)
            }
        }

        binding.tvLupaPassword.setOnClickListener {
            Toast.makeText(this, "Fitur lupa password segera hadir", Toast.LENGTH_SHORT).show()
        }

        binding.tvDaftar.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvSyaratKetentuan.setOnClickListener {
            SyaratKetentuanDialog.tampilkan(context = this, modeRegister = false)
        }
    }

    // ===== LOGIN DENGAN EMAIL =====
    private fun doLoginEmail(email: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                client.auth.signInWith(Email) {
                    this.email    = email
                    this.password = password
                }
                navigasiSetelahLogin()
            } catch (e: Exception) {
                setLoading(false)
                showError(terjemahkanError(e.message))
            }
        }
    }

    // ===== LOGIN DENGAN NO HP =====
    // Cari email dari no_telp di tabel users, lalu login pakai email tersebut
    private fun doLoginNoHp(noHp: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                // Format nomor: tambah +62 jika belum ada
                val noTelpFormatted = when {
                    noHp.startsWith("+62") -> noHp
                    noHp.startsWith("62")  -> "+$noHp"
                    noHp.startsWith("0")   -> "+62${noHp.substring(1)}"
                    else                   -> "+62$noHp"
                }

                // Cari email berdasarkan no_telp di tabel users
                val hasil = client.postgrest
                    .from("users")
                    .select { filter { eq("no_telp", noTelpFormatted) } }
                    .data

                // Parse email dari JSON result
                val emailDitemukan = extractEmail(hasil)
                    ?: throw Exception("Nomor HP tidak terdaftar")

                // Login pakai email yang ditemukan
                client.auth.signInWith(Email) {
                    this.email    = emailDitemukan
                    this.password = password
                }

                navigasiSetelahLogin()

            } catch (e: Exception) {
                setLoading(false)
                val pesan = when {
                    e.message?.contains("Nomor HP tidak terdaftar") == true ->
                        "Nomor HP tidak terdaftar"
                    else -> terjemahkanError(e.message)
                }
                showError(pesan)
            }
        }
    }

    // ===== NAVIGASI SETELAH LOGIN BERHASIL =====
    private suspend fun navigasiSetelahLogin() {
        try {
            val userId = client.auth.currentUserOrNull()?.id
                ?: throw Exception("Session tidak ditemukan setelah login")

            val user = client.postgrest
                .from("users")
                .select { filter { eq("id_user", userId) } }
                .decodeSingle<User>()

            setLoading(false)
            when (user.role) {
                "nasabah" -> {
                    startActivity(Intent(this@LoginActivity, NasabahActivity::class.java))
                    finish()
                }
                "unit_bisnis" -> {
                    startActivity(Intent(this@LoginActivity, UnitBisnisActivity::class.java))
                    finish()
                }
                else -> showError("Role tidak dikenali: ${user.role}")
            }
        } catch (e: Exception) {
            setLoading(false)
            showError(terjemahkanError(e.message))
        }
    }

    // ===== HELPER: parse email dari JSON response =====
    private fun extractEmail(json: String): String? {
        return try {
            """"email"\s*:\s*"([^"]+)"""".toRegex().find(json)?.groupValues?.get(1)
        } catch (e: Exception) { null }
    }

    private fun terjemahkanError(msg: String?): String = when {
        msg?.contains("Invalid login credentials") == true ->
            "Email/Password salah"
        msg?.contains("Email not confirmed") == true ->
            "Email belum dikonfirmasi, cek inbox kamu"
        msg?.contains("network") == true ||
                msg?.contains("Unable to resolve host") == true ->
            "Tidak ada koneksi internet"
        else -> "Error: $msg"
    }

    private fun setLoading(loading: Boolean) {
        if (loading) {
            binding.btnMasuk.isEnabled     = false
            binding.btnMasuk.text          = "Memuat..."
            binding.progressBar.visibility = View.VISIBLE
        } else {
            binding.btnMasuk.isEnabled     = true
            binding.btnMasuk.text          = "Masuk"
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun showError(pesan: String) {
        Toast.makeText(this, pesan, Toast.LENGTH_LONG).show()
    }
}