package com.example.bankjatahapp.ui.unitbisnis

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.bankjatahapp.R
import com.example.bankjatahapp.databinding.ActivityUnitBisnisBinding
import com.example.bankjatahapp.ui.unitbisnis.fragment.HomeUnitBisnisFragment
import com.example.bankjatahapp.ui.unitbisnis.fragment.ProfilUnitBisnisFragment
import com.example.bankjatahapp.ui.unitbisnis.fragment.RiwayatUnitBisnisFragment
import com.example.bankjatahapp.ui.unitbisnis.fragment.SetoranFragment

class UnitBisnisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnitBisnisBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnitBisnisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadFragment(HomeUnitBisnisFragment())
        binding.bottomNav.selectedItemId = R.id.nav_home

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> loadFragment(HomeUnitBisnisFragment())
                R.id.nav_setoran -> loadFragment(SetoranFragment())
                R.id.nav_riwayat -> loadFragment(RiwayatUnitBisnisFragment())
                R.id.nav_profil  -> loadFragment(ProfilUnitBisnisFragment())
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