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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasabahBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadFragment(HomeNasabahFragment())
        binding.bottomNav.selectedItemId = R.id.nav_home

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> loadFragment(HomeNasabahFragment())
                R.id.nav_riwayat -> loadFragment(RiwayatFragment())
                R.id.nav_reward  -> loadFragment(RewardFragment())
                R.id.nav_profil  -> loadFragment(ProfilNasabahFragment())
            }
            true
        }
    }

    fun navigateTo(navItemId: Int) {
        binding.bottomNav.selectedItemId = navItemId
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}