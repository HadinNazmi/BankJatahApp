package com.example.bankjatahapp.ui.nasabah

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.PengajuanBerhenti
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.ActivityNasabahBinding
import com.example.bankjatahapp.ui.nasabah.fragment.HomeNasabahFragment
import com.example.bankjatahapp.ui.nasabah.fragment.ProfilNasabahFragment
import com.example.bankjatahapp.ui.nasabah.fragment.RewardFragment
import com.example.bankjatahapp.ui.nasabah.fragment.RiwayatFragment
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

class NasabahActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNasabahBinding

    private var lastNavTime = 0L
    private var currentNavId = R.id.nav_home

    private var lastResumeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasabahBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(HomeNasabahFragment(), R.id.nav_home)
        }

        binding.bottomNav.selectedItemId = R.id.nav_home

        binding.bottomNav.setOnItemSelectedListener { item ->
            // ===== TAMBAH: debounce 400ms + skip jika tab sama =====
            val now = System.currentTimeMillis()
            if (now - lastNavTime < 400L) return@setOnItemSelectedListener true
            lastNavTime = now
            if (item.itemId == currentNavId) return@setOnItemSelectedListener true
            // =========================================================

            when (item.itemId) {
                R.id.nav_home    -> loadFragment(HomeNasabahFragment(),   R.id.nav_home)
                R.id.nav_riwayat -> loadFragment(RiwayatFragment(),       R.id.nav_riwayat)
                R.id.nav_reward  -> loadFragment(RewardFragment(),         R.id.nav_reward)
                R.id.nav_profil  -> loadFragment(ProfilNasabahFragment(), R.id.nav_profil)
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        // Auto-refresh jika app sudah di background lebih dari 2 menit
        if (now - lastResumeTime > 2 * 60 * 1000L && lastResumeTime > 0) {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (fragment is HomeNasabahFragment) fragment.refreshData()
        }
        lastResumeTime = now
    }

    fun navigateTo(navItemId: Int) {
        if (navItemId == currentNavId) return  // ← TAMBAH ini
        binding.bottomNav.selectedItemId = navItemId
    }

    private fun loadFragment(fragment: Fragment, navId: Int) {
        currentNavId = navId
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()  // ← ganti dari commit() ke ini
    }

    suspend fun cekAdaPengajuanAktif(): Boolean {
        return try {
            val idUser = client.auth.currentUserOrNull()?.id ?: return false
            val hasil = client.postgrest
                .from("pengajuan_berhenti")
                .select {
                    filter {
                        eq("id_user", idUser)
                        or {
                            eq("status", "menunggu")
                            eq("status", "diproses")
                        }
                    }
                }
                .decodeList<PengajuanBerhenti>()
            hasil.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}