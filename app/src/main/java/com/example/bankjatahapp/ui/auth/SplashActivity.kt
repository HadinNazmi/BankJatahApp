package com.example.bankjatahapp.ui.auth

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.ActivitySplashBinding
import com.example.bankjatahapp.ui.nasabah.NasabahActivity
import com.example.bankjatahapp.ui.unitbisnis.UnitBisnisActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sembunyikan action bar
        supportActionBar?.hide()

        jalankanAnimasi()
        cekSessionDanNavigasi()
    }

    // ===== ANIMASI FADE IN logo + teks =====
    private fun jalankanAnimasi() {
        val fadeInLogo = ObjectAnimator.ofFloat(binding.ivSplashLogo, "alpha", 0f, 1f).apply {
            duration = 600
        }
        val fadeInNama = ObjectAnimator.ofFloat(binding.tvNamaApp, "alpha", 0f, 1f).apply {
            duration = 600
            startDelay = 200
        }
        val fadeInTagline = ObjectAnimator.ofFloat(binding.tvTagline, "alpha", 0f, 1f).apply {
            duration = 600
            startDelay = 400
        }
        val scaleX = ObjectAnimator.ofFloat(binding.ivSplashLogo, "scaleX", 0.8f, 1f).apply {
            duration = 600
        }
        val scaleY = ObjectAnimator.ofFloat(binding.ivSplashLogo, "scaleY", 0.8f, 1f).apply {
            duration = 600
        }

        AnimatorSet().apply {
            playTogether(fadeInLogo, fadeInNama, fadeInTagline, scaleX, scaleY)
            start()
        }
    }

    // ===== CEK SESSION SUPABASE =====
    // Jika session masih valid → langsung ke Home (tidak perlu login ulang)
    // Jika belum / sudah expired → ke LoginActivity setelah 2 detik
    private fun cekSessionDanNavigasi() {
        lifecycleScope.launch {
            try {
                // Tunggu minimal 2 detik supaya splash terlihat
                val mulai = System.currentTimeMillis()

                // Cek apakah ada session aktif
                val sesiAktif = client.auth.currentUserOrNull()

                val selesai = System.currentTimeMillis()
                val sisaWaktu = 2000L - (selesai - mulai)
                if (sisaWaktu > 0) delay(sisaWaktu)

                if (sesiAktif != null) {
                    // Session ada — ambil role dari tabel users
                    try {
                        val user = client.postgrest
                            .from("users")
                            .select { filter { eq("id_user", sesiAktif.id) } }
                            .decodeSingle<User>()

                        when (user.role) {
                            "nasabah" -> {
                                startActivity(Intent(this@SplashActivity, NasabahActivity::class.java))
                            }
                            "unit_bisnis" -> {
                                startActivity(Intent(this@SplashActivity, UnitBisnisActivity::class.java))
                            }
                            else -> {
                                // Role tidak dikenal → paksa login ulang
                                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                            }
                        }
                    } catch (e: Exception) {
                        // Gagal ambil user (misal network error) → ke login
                        startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                    }
                } else {
                    // Tidak ada session → ke LoginActivity
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                }

                finish() // tutup SplashActivity agar tidak bisa back ke sini

            } catch (e: Exception) {
                delay(2000)
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                finish()
            }
        }
    }
}