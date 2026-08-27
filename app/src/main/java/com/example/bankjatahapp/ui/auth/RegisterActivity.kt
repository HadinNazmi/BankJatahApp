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
import com.example.bankjatahapp.databinding.ActivityRegisterBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBackToLogin.setOnClickListener { finish() }
        binding.tvMasuk.setOnClickListener { finish() }

        binding.tvLinkSyarat.setOnClickListener {
            SyaratKetentuanDialog.tampilkan(
                context      = this,
                modeRegister = true
            ) { disetujui ->
                if (disetujui) binding.cbSyarat.isChecked = true
            }
        }

        binding.cbSyarat.setOnCheckedChangeListener { _, isChecked ->
            binding.btnDaftar.alpha = if (isChecked) 1.0f else 0.5f
        }

        binding.btnDaftar.alpha = 0.5f

        binding.btnDaftar.setOnClickListener {
            if (!validateForm()) return@setOnClickListener
            cekDanDaftar()
        }
    }

    private fun cekDanDaftar() {
        val nik    = binding.etNik.text.toString().trim()
        val email  = binding.etEmail.text.toString().trim()
        val noTelp = binding.etNoTelp.text.toString().trim()

        val noTelpBersih = noTelp.replace(Regex("[\\s\\-()]"), "")
        val noTelpFormatted = when {
            noTelpBersih.startsWith("+62") -> "0" + noTelpBersih.removePrefix("+62")
            noTelpBersih.startsWith("62")  -> "0" + noTelpBersih.removePrefix("62")
            noTelpBersih.startsWith("0")   -> noTelpBersih
            else                           -> "0$noTelpBersih"
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                // ===== CEK NIK proaktif =====
                val listNik = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("nik", nik) } }
                    .decodeList<NasabahData>()

                if (listNik.isNotEmpty()) {
                    setLoading(false)
                    runOnUiThread {
                        binding.tilNik.error = "NIK ini sudah terdaftar, gunakan NIK lain"
                        binding.tilNik.requestFocus()
                    }
                    return@launch
                }
                runOnUiThread { binding.tilNik.error = null }

                // ===== CEK EMAIL proaktif =====
                val listEmail = client.postgrest
                    .from("users")
                    .select { filter { eq("email", email) } }
                    .decodeList<User>()

                if (listEmail.isNotEmpty()) {
                    setLoading(false)
                    runOnUiThread {
                        binding.tilEmail.error = "Email ini sudah terdaftar"
                        binding.tilEmail.requestFocus()
                    }
                    return@launch
                }
                runOnUiThread { binding.tilEmail.error = null }

                // ===== CEK NO TELEPON proaktif =====
                val listNoTelp = client.postgrest
                    .from("users")
                    .select { filter { eq("no_telp", noTelpFormatted) } }
                    .decodeList<User>()

                if (listNoTelp.isNotEmpty()) {
                    setLoading(false)
                    runOnUiThread {
                        binding.tilNoTelp.error = "Nomor telepon ini sudah terdaftar"
                        binding.tilNoTelp.requestFocus()
                    }
                    return@launch
                }
                runOnUiThread { binding.tilNoTelp.error = null }

                // Semua valid → daftar
                doRegister(noTelpFormatted)

            } catch (e: Exception) {
                setLoading(false)
                runOnUiThread {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Gagal memvalidasi: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun doRegister(noTelpFormatted: String) {
        val nama    = binding.etNama.text.toString().trim()
        val email   = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val nik     = binding.etNik.text.toString().trim()
        val referal = binding.etReferal.text.toString().trim()

        try {
            // STEP 1: Buat akun Supabase Auth
            client.auth.signUpWith(Email) {
                this.email    = email
                this.password = password
            }

            // STEP 2: Panggil RPC fn_register_nasabah
            // Pencarian sponsor dari kode referral dilakukan di dalam function DB
            // agar tidak kena RLS — Android cukup kirim kode referral string-nya
            val rpcPayload = buildJsonObject {
                put("p_nama_lengkap",  nama)
                put("p_email",         email)
                put("p_no_telp",       noTelpFormatted)
                put("p_nik",           nik)
                // Kirim kode referral apa adanya, null jika kosong
                if (referal.isNotEmpty()) {
                    put("p_kode_referral", referal)
                } else {
                    put("p_kode_referral", JsonNull)
                }
            }

            val rpcResult = client.postgrest
                .rpc("fn_register_nasabah", rpcPayload)
                .data

            val resultJson = kotlinx.serialization.json.Json
                .parseToJsonElement(rpcResult)
                .jsonObject

            val success      = resultJson["success"]?.jsonPrimitive?.content == "true"
            val errorCode    = resultJson["error"]?.jsonPrimitive?.content ?: ""
            val refWarning   = resultJson["referral_warning"]?.jsonPrimitive?.content == "true"

            if (!success) {
                // RPC gagal → rollback: hapus akun Auth
                try { client.auth.signOut() } catch (_: Exception) {}

                setLoading(false)
                runOnUiThread {
                    when (errorCode) {
                        "nik_exists" -> {
                            binding.tilNik.error = "NIK ini sudah terdaftar, gunakan NIK lain"
                            binding.tilNik.requestFocus()
                        }
                        "telp_exists" -> {
                            binding.tilNoTelp.error = "Nomor telepon ini sudah terdaftar"
                            binding.tilNoTelp.requestFocus()
                        }
                        "user_not_found" -> {
                            Toast.makeText(
                                this@RegisterActivity,
                                "Gagal memproses akun, silakan coba lagi.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            val msg = resultJson["message"]?.jsonPrimitive?.content ?: errorCode
                            Toast.makeText(
                                this@RegisterActivity,
                                "Gagal mendaftar: $msg",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                return
            }

            // STEP 3: Logout setelah berhasil (tetap logout, karena email belum confirmed)
            client.auth.signOut()

            setLoading(false)
            runOnUiThread {
                tampilkanDialogKonfirmasiEmail(email, refWarning && referal.isNotEmpty(), referal)
            }

        } catch (e: Exception) {
            try { client.auth.signOut() } catch (_: Exception) {}
            setLoading(false)

            val pesanError = e.message ?: ""
            runOnUiThread {
                when {
                    pesanError.contains("already registered") ||
                            pesanError.contains("already been registered") -> {
                        binding.tilEmail.error = "Email ini sudah terdaftar"
                        binding.tilEmail.requestFocus()
                    }
                    pesanError.contains("Password should be at least") -> {
                        binding.tilPassword.error = "Password minimal 6 karakter"
                        binding.tilPassword.requestFocus()
                    }
                    pesanError.contains("rate limit") ->
                        Toast.makeText(
                            this@RegisterActivity,
                            "Terlalu banyak percobaan, tunggu beberapa menit",
                            Toast.LENGTH_LONG
                        ).show()
                    pesanError.contains("network") ||
                            pesanError.contains("Unable to resolve host") ->
                        Toast.makeText(
                            this@RegisterActivity,
                            "Tidak ada koneksi internet",
                            Toast.LENGTH_LONG
                        ).show()
                    else ->
                        Toast.makeText(
                            this@RegisterActivity,
                            "Gagal mendaftar: $pesanError",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }
    }

    //  FUNGSI BARU: Dialog konfirmasi email setelah register sukses
    private fun tampilkanDialogKonfirmasiEmail(
        email: String,
        adaRefWarning: Boolean,
        kodeReferal: String
    ) {
        val pesan = buildString {
            append("Akun berhasil dibuat!\n\n")
            append("Kami telah mengirim email konfirmasi ke:\n$email\n\n")
            append("📌 Jika email tidak ditemukan di inbox, silakan cek folder ")
            append("Spam atau Promosi dan tandai sebagai 'Bukan Spam'.")
            append("Silakan buka email tersebut dan klik link konfirmasi sebelum login.")
            if (adaRefWarning) {
                append("\n\n⚠ Kode referral \"$kodeReferal\" tidak ditemukan. Akun dibuat tanpa sponsor.")
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cek Email Kamu!")
            .setMessage(pesan)
            .setCancelable(false)
            .setPositiveButton("OK, Mengerti") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun setLoading(loading: Boolean) {
        runOnUiThread {
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
            binding.tilNama.error = "Nama lengkap tidak boleh kosong"
            isValid = false
        } else binding.tilNama.error = null

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email tidak boleh kosong"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Format email tidak valid"
            isValid = false
        } else binding.tilEmail.error = null

        if (noTelp.isEmpty()) {
            binding.tilNoTelp.error = "Nomor telepon tidak boleh kosong"
            isValid = false
        } else if (noTelp.replace(Regex("[\\s\\-()]"), "").length < 8) {
            binding.tilNoTelp.error = "Nomor telepon tidak valid"
            isValid = false
        } else binding.tilNoTelp.error = null

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password tidak boleh kosong"
            isValid = false
        } else if (password.length < 8) {
            binding.tilPassword.error = "Password minimal 8 karakter"
            isValid = false
        } else binding.tilPassword.error = null

        if (konfirmasi.isEmpty()) {
            binding.tilKonfirmasiPassword.error = "Konfirmasi password tidak boleh kosong"
            isValid = false
        } else if (konfirmasi != password) {
            binding.tilKonfirmasiPassword.error = "Password tidak cocok"
            isValid = false
        } else binding.tilKonfirmasiPassword.error = null

        if (nik.isEmpty()) {
            binding.tilNik.error = "NIK tidak boleh kosong"
            isValid = false
        } else if (nik.length != 16) {
            binding.tilNik.error = "NIK harus 16 digit"
            isValid = false
        } else binding.tilNik.error = null

        if (!binding.cbSyarat.isChecked) {
            Toast.makeText(
                this,
                "Anda harus menyetujui Syarat & Ketentuan",
                Toast.LENGTH_SHORT
            ).show()
            isValid = false
        }

        return isValid
    }
}