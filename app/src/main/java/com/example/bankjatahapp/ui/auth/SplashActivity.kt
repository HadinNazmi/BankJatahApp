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
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        jalankanAnimasi()
        cekSessionDanNavigasi()
    }

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

    private fun cekSessionDanNavigasi() {
        lifecycleScope.launch {
            try {
                val mulai = System.currentTimeMillis()

                // ===== KUNCI UTAMA =====
                // Tunggu SDK selesai load session dari SharedPreferences
                // Tanpa ini, currentUserOrNull() return null walau session ada
                client.auth.awaitInitialization()

                val sesiAktif = client.auth.currentUserOrNull()

                val selesai   = System.currentTimeMillis()
                val sisaWaktu = 2000L - (selesai - mulai)
                if (sisaWaktu > 0) delay(sisaWaktu)

                if (sesiAktif != null) {
                    try {
                        val user = client.postgrest
                            .from("users")
                            .select { filter { eq("id_user", sesiAktif.id) } }
                            .decodeSingle<User>()

                        when (user.role) {
                            "nasabah" -> startActivity(
                                Intent(this@SplashActivity, NasabahActivity::class.java)
                            )
                            "unit_bisnis" -> startActivity(
                                Intent(this@SplashActivity, UnitBisnisActivity::class.java)
                            )
                            else -> startActivity(
                                Intent(this@SplashActivity, LoginActivity::class.java)
                            )
                        }
                    } catch (e: Exception) {
                        // Network error saat ambil user → tetap coba masuk
                        // jangan paksa login hanya karena network lambat
                        startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                    }
                } else {
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                }

                finish()

            } catch (e: Exception) {
                delay(2000)
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }
}