package com.example.bankjatahapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.ActivityLupaPasswordBinding
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class LupaPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLupaPasswordBinding

    // Disimpan setelah verifikasi sukses, dipakai ulang saat reset password
    // (tidak ditampilkan ke user, hanya internal flow)
    private var nikTerverifikasi: String = ""
    private var noHpTerverifikasi: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLupaPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            // Jika sudah di step 2, tombol back kembali ke step 1 dulu
            if (binding.layoutStep2PasswordBaru.visibility == View.VISIBLE) {
                kembaliKeStep1()
            } else {
                finish()
            }
        }

        binding.btnVerifikasi.setOnClickListener {
            val nik  = binding.etNik.text.toString().trim()
            val noHp = binding.etNoHp.text.toString().trim()

            if (!validasiStep1(nik, noHp)) return@setOnClickListener
            verifikasiIdentitas(nik, noHp)
        }

        binding.btnSimpanPassword.setOnClickListener {
            val password   = binding.etPasswordBaru.text.toString().trim()
            val konfirmasi = binding.etKonfirmasiPassword.text.toString().trim()

            if (!validasiStep2(password, konfirmasi)) return@setOnClickListener
            resetPassword(password)
        }
    }

    // ===== VALIDASI INPUT STEP 1 =====
    private fun validasiStep1(nik: String, noHp: String): Boolean {
        var valid = true

        if (nik.isEmpty()) {
            binding.tilNik.error = "NIK tidak boleh kosong"
            valid = false
        } else if (nik.length != 16) {
            binding.tilNik.error = "NIK harus 16 digit"
            valid = false
        } else {
            binding.tilNik.error = null
        }

        if (noHp.isEmpty()) {
            binding.tilNoHp.error = "Nomor HP tidak boleh kosong"
            valid = false
        } else if (noHp.replace(Regex("[\\s\\-()]"), "").length < 8) {
            binding.tilNoHp.error = "Nomor HP tidak valid"
            valid = false
        } else {
            binding.tilNoHp.error = null
        }

        return valid
    }

    // ===== VALIDASI INPUT STEP 2 =====
    private fun validasiStep2(password: String, konfirmasi: String): Boolean {
        var valid = true

        if (password.isEmpty()) {
            binding.tilPasswordBaru.error = "Password tidak boleh kosong"
            valid = false
        } else if (password.length < 8) {
            binding.tilPasswordBaru.error = "Password minimal 8 karakter"
            valid = false
        } else {
            binding.tilPasswordBaru.error = null
        }

        if (konfirmasi.isEmpty()) {
            binding.tilKonfirmasiPassword.error = "Konfirmasi password tidak boleh kosong"
            valid = false
        } else if (konfirmasi != password) {
            binding.tilKonfirmasiPassword.error = "Password tidak cocok"
            valid = false
        } else {
            binding.tilKonfirmasiPassword.error = null
        }

        return valid
    }

    // ===== STEP 1: PANGGIL fn_verifikasi_identitas_reset =====
    private fun verifikasiIdentitas(nik: String, noHpInput: String) {
        setLoadingStep1(true)

        // Format nomor HP sama seperti di LoginActivity: pakai format 08xxx (lokal)
        val noHpBersih = noHpInput.replace(Regex("[\\s\\-()]"), "")
        val noHpFormatted = when {
            noHpBersih.startsWith("+62") -> "0" + noHpBersih.removePrefix("+62")
            noHpBersih.startsWith("62")  -> "0" + noHpBersih.removePrefix("62")
            noHpBersih.startsWith("0")   -> noHpBersih
            else                         -> "0$noHpBersih"
        }

        lifecycleScope.launch {
            try {
                val payload = buildJsonObject {
                    put("p_nik", nik)
                    put("p_no_telp", noHpFormatted)
                }

                val rpcResult = client.postgrest
                    .rpc("fn_verifikasi_identitas_reset", payload)
                    .data

                val resultJson: JsonObject = kotlinx.serialization.json.Json
                    .parseToJsonElement(rpcResult)
                    .jsonObject

                val success = resultJson["success"]?.jsonPrimitive?.content == "true"

                setLoadingStep1(false)

                if (success) {
                    val nama = resultJson["nama_lengkap"]?.jsonPrimitive?.content ?: ""

                    // Simpan untuk dipakai saat reset password
                    nikTerverifikasi  = nik
                    noHpTerverifikasi = noHpFormatted

                    lanjutKeStep2(nama)
                } else {
                    val pesan = resultJson["message"]?.jsonPrimitive?.content
                        ?: "NIK dan Nomor HP tidak cocok"
                    Toast.makeText(this@LupaPasswordActivity, pesan, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                setLoadingStep1(false)
                val pesan = when {
                    e.message?.contains("network") == true ||
                            e.message?.contains("Unable to resolve host") == true ->
                        "Tidak ada koneksi internet"
                    else -> "Gagal memverifikasi: ${e.message}"
                }
                Toast.makeText(this@LupaPasswordActivity, pesan, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ===== PINDAH KE STEP 2 =====
    private fun lanjutKeStep2(nama: String) {
        binding.tvSapaanNama.text = if (nama.isNotEmpty()) {
            "Halo, $nama! Silakan buat password baru untuk akun Anda"
        } else {
            "Identitas terverifikasi. Silakan buat password baru"
        }

        binding.layoutStep1Verifikasi.visibility   = View.GONE
        binding.layoutStep2PasswordBaru.visibility = View.VISIBLE
        binding.tvJudulHalaman.text = "Password Baru"

        // Bersihkan field password jika sebelumnya pernah diisi
        binding.etPasswordBaru.setText("")
        binding.etKonfirmasiPassword.setText("")
    }

    // ===== KEMBALI KE STEP 1 (tombol back saat di step 2) =====
    private fun kembaliKeStep1() {
        binding.layoutStep2PasswordBaru.visibility = View.GONE
        binding.layoutStep1Verifikasi.visibility   = View.VISIBLE
        binding.tvJudulHalaman.text = "Lupa Password"
        nikTerverifikasi  = ""
        noHpTerverifikasi = ""
    }

    // ===== STEP 2: PANGGIL fn_reset_password_by_identity =====
    private fun resetPassword(passwordBaru: String) {
        if (nikTerverifikasi.isEmpty() || noHpTerverifikasi.isEmpty()) {
            Toast.makeText(this, "Sesi verifikasi tidak valid, silakan ulangi", Toast.LENGTH_LONG).show()
            kembaliKeStep1()
            return
        }

        setLoadingStep2(true)

        lifecycleScope.launch {
            try {
                val payload = buildJsonObject {
                    put("p_nik", nikTerverifikasi)
                    put("p_no_telp", noHpTerverifikasi)
                    put("p_password_baru", passwordBaru)
                }

                val rpcResult = client.postgrest
                    .rpc("fn_reset_password_by_identity", payload)
                    .data

                val resultJson: JsonObject = kotlinx.serialization.json.Json
                    .parseToJsonElement(rpcResult)
                    .jsonObject

                val success = resultJson["success"]?.jsonPrimitive?.content == "true"

                setLoadingStep2(false)

                if (success) {
                    Toast.makeText(
                        this@LupaPasswordActivity,
                        "✓ Password berhasil diubah! Silakan login dengan password baru.",
                        Toast.LENGTH_LONG
                    ).show()

                    val intent = Intent(this@LupaPasswordActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    val pesan = resultJson["message"]?.jsonPrimitive?.content
                        ?: "Gagal mengubah password"
                    Toast.makeText(this@LupaPasswordActivity, pesan, Toast.LENGTH_LONG).show()

                    // Jika identitas tiba-tiba tidak cocok lagi, paksa ulang dari step 1
                    if (pesan.contains("tidak cocok", ignoreCase = true)) {
                        kembaliKeStep1()
                    }
                }

            } catch (e: Exception) {
                setLoadingStep2(false)
                val pesan = when {
                    e.message?.contains("network") == true ||
                            e.message?.contains("Unable to resolve host") == true ->
                        "Tidak ada koneksi internet"
                    else -> "Gagal menyimpan password: ${e.message}"
                }
                Toast.makeText(this@LupaPasswordActivity, pesan, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoadingStep1(loading: Boolean) {
        binding.btnVerifikasi.isEnabled     = !loading
        binding.btnVerifikasi.text          = if (loading) "Memverifikasi..." else "Verifikasi"
        binding.progressBarStep1.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun setLoadingStep2(loading: Boolean) {
        binding.btnSimpanPassword.isEnabled = !loading
        binding.btnSimpanPassword.text      = if (loading) "Menyimpan..." else "Simpan Password Baru"
        binding.progressBarStep2.visibility = if (loading) View.VISIBLE else View.GONE
    }
}