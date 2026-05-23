package com.example.bankjatahapp.ui.nasabah

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.bankjatahapp.R
import com.example.bankjatahapp.databinding.ActivityNasabahBinding
import com.example.bankjatahapp.ui.nasabah.fragment.HomeNasabahFragment
import com.example.bankjatahapp.ui.nasabah.fragment.ProfilNasabahFragment
import com.example.bankjatahapp.ui.nasabah.fragment.RewardFragment
import com.example.bankjatahapp.ui.nasabah.fragment.RiwayatFragment

class NasabahActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNasabahBinding

    private var lastNavTime = 0L
    private var currentNavId = R.id.nav_home

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
}