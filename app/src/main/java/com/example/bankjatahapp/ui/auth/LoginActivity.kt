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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSyaratKetentuan()
        setupClickListeners()
    }

    private fun setupSyaratKetentuan() {
        val fullText = "Dengan masuk, Anda menyetujui Syarat & Ketentuan dan Kebijakan Privasi"
        val spannable = SpannableString(fullText)
        val orangeColor = ContextCompat.getColor(this, R.color.orange_primary)

        // Syarat & Ketentuan — orange + underline
        val syaratStart = fullText.indexOf("Syarat & Ketentuan")
        val syaratEnd   = syaratStart + "Syarat & Ketentuan".length
        spannable.setSpan(ForegroundColorSpan(orangeColor), syaratStart, syaratEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(UnderlineSpan(), syaratStart, syaratEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Kebijakan Privasi — orange + underline
        val kebijakanStart = fullText.indexOf("Kebijakan Privasi")
        val kebijakanEnd   = kebijakanStart + "Kebijakan Privasi".length
        spannable.setSpan(ForegroundColorSpan(orangeColor), kebijakanStart, kebijakanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(UnderlineSpan(), kebijakanStart, kebijakanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.tvSyaratKetentuan.text = spannable
    }

    private fun setupClickListeners() {
        binding.btnMasuk.setOnClickListener {
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty()) {
                binding.tilEmail.error = "Email tidak boleh kosong"
                return@setOnClickListener
            } else {
                binding.tilEmail.error = null
            }

            if (password.isEmpty()) {
                binding.tilPassword.error = "Password tidak boleh kosong"
                return@setOnClickListener
            } else {
                binding.tilPassword.error = null
            }

            doLogin(email, password)
        }

        binding.tvLupaPassword.setOnClickListener {
            Toast.makeText(this, "Fitur lupa password segera hadir", Toast.LENGTH_SHORT).show()
        }

        binding.tvDaftar.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Klik teks "Syarat & Ketentuan" → buka dialog pop-up (mode login = hanya baca)
        binding.tvSyaratKetentuan.setOnClickListener {
            SyaratKetentuanDialog.tampilkan(
                context       = this,
                modeRegister  = false
            )
        }
    }

    private fun doLogin(email: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                client.auth.signInWith(Email) {
                    this.email    = email
                    this.password = password
                }

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
                val pesan = when {
                    e.message?.contains("Invalid login credentials") == true ->
                        "Email atau password salah"
                    e.message?.contains("Email not confirmed") == true ->
                        "Email belum dikonfirmasi, cek inbox kamu"
                    e.message?.contains("network") == true ||
                            e.message?.contains("Unable to resolve host") == true ->
                        "Tidak ada koneksi internet"
                    else -> "Error: ${e.message}"
                }
                showError(pesan)
            }
        }
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