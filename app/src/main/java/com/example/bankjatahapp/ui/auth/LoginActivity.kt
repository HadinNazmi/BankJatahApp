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
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var modLogin = "email"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSyaratKetentuan()
        setupTabToggle()
        setupClickListeners()
    }

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

    private fun setupTabToggle() {
        binding.tabEmail.setOnClickListener {
            if (modLogin != "email") { modLogin = "email"; updateTampilan() }
        }
        binding.tabNoHp.setOnClickListener {
            if (modLogin != "nohp") { modLogin = "nohp"; updateTampilan() }
        }
    }

    private fun updateTampilan() {
        val params = binding.tvLabelPassword.layoutParams as ConstraintLayout.LayoutParams

        if (modLogin == "email") {
            binding.tabEmail.setBackgroundResource(R.drawable.ic_bg_tab_active)
            binding.tabEmail.setTextColor(getColor(R.color.white))
            binding.tabNoHp.setBackgroundResource(android.R.color.transparent)
            binding.tabNoHp.setTextColor(getColor(R.color.gray_text))
            binding.tvLabelIdentitas.text = "Email"
            binding.tilEmail.visibility   = View.VISIBLE
            binding.tilNoHp.visibility    = View.GONE
            params.topToBottom = binding.tilEmail.id
            binding.tilEmail.requestFocus()
        } else {
            binding.tabNoHp.setBackgroundResource(R.drawable.ic_bg_tab_active)
            binding.tabNoHp.setTextColor(getColor(R.color.white))
            binding.tabEmail.setBackgroundResource(android.R.color.transparent)
            binding.tabEmail.setTextColor(getColor(R.color.gray_text))
            binding.tvLabelIdentitas.text = "No. HP"
            binding.tilNoHp.visibility    = View.VISIBLE
            binding.tilEmail.visibility   = View.GONE
            params.topToBottom = binding.tilNoHp.id
            binding.tilNoHp.requestFocus()
        }

        binding.tvLabelPassword.layoutParams = params
    }

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
            startActivity(Intent(this, LupaPasswordActivity::class.java))
        }

        binding.tvDaftar.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvSyaratKetentuan.setOnClickListener {
            SyaratKetentuanDialog.tampilkan(context = this, modeRegister = false)
        }
    }

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

    private fun doLoginNoHp(noHp: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val inputBersih = noHp.replace(Regex("[\\s\\-()]"), "")

                val formatLokal = when {
                    inputBersih.startsWith("+62") -> "0" + inputBersih.removePrefix("+62")
                    inputBersih.startsWith("62")  -> "0" + inputBersih.removePrefix("62")
                    inputBersih.startsWith("0")   -> inputBersih
                    else                          -> "0$inputBersih"
                }

                val formatInternasional = "+62" + formatLokal.removePrefix("0")

                var emailDitemukan: String? = null

                val hasil08 = client.postgrest
                    .from("users")
                    .select { filter { eq("no_telp", formatLokal) } }
                    .data

                emailDitemukan = extractEmail(hasil08)

                if (emailDitemukan == null) {
                    val hasil62 = client.postgrest
                        .from("users")
                        .select { filter { eq("no_telp", formatInternasional) } }
                        .data
                    emailDitemukan = extractEmail(hasil62)
                }

                if (emailDitemukan == null) {
                    throw Exception("Nomor HP tidak terdaftar")
                }

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

    // ===== NAVIGASI SETELAH LOGIN =====
    private suspend fun navigasiSetelahLogin() {
        try {
            val userId = client.auth.currentUserOrNull()?.id
                ?: throw Exception("Session tidak ditemukan setelah login")

            val user = client.postgrest
                .from("users")
                .select { filter { eq("id_user", userId) } }
                .decodeSingle<User>()

            // ===== CEK STATUS AKUN =====
            if (user.statusAkun != "aktif") {
                try { client.auth.signOut() } catch (_: Exception) {}

                setLoading(false)

                val pesan = when (user.statusAkun) {
                    "dibekukan"           ->
                        "Akun Anda dibekukan.\nSilakan hubungi admin untuk informasi lebih lanjut."
                    "menunggu_verifikasi" ->
                        "Akun Anda belum diverifikasi.\nSilakan hubungi admin untuk aktivasi akun."
                    else                  ->
                        "Akun Anda tidak aktif (${user.statusAkun}).\nSilakan hubungi admin."
                }
                showError(pesan)
                return
            }

            // ===== SIMPAN FCM TOKEN SETELAH LOGIN BERHASIL =====
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val payload = buildJsonObject { put("fcm_token", token) }
                        client.postgrest
                            .from("users")
                            .update(payload) {
                                filter { eq("id_user", userId) }
                            }
                    } catch (_: Exception) {}
                }
            }

            // Status aktif → navigasi ke halaman sesuai role
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

    private fun extractEmail(json: String): String? {
        return try {
            if (json.trim() == "[]") return null
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