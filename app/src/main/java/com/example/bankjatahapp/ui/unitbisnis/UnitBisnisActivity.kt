package com.example.bankjatahapp.ui.unitbisnis

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.PengajuanBerhenti
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.ActivityUnitBisnisBinding
import com.example.bankjatahapp.ui.unitbisnis.fragment.HomeUnitBisnisFragment
import com.example.bankjatahapp.ui.unitbisnis.fragment.ProfilUnitBisnisFragment
import com.example.bankjatahapp.ui.unitbisnis.fragment.RiwayatUnitBisnisFragment
import com.example.bankjatahapp.ui.unitbisnis.fragment.SetoranFragment
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

class UnitBisnisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnitBisnisBinding

    private var lastNavTime = 0L
    private var currentNavId = R.id.nav_home

    private var lastResumeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnitBisnisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(HomeUnitBisnisFragment(), R.id.nav_home)
        }
        binding.bottomNav.selectedItemId = R.id.nav_home

        binding.bottomNav.setOnItemSelectedListener { item ->
            val now = System.currentTimeMillis()
            if (now - lastNavTime < 400L) return@setOnItemSelectedListener true
            lastNavTime = now
            if (item.itemId == currentNavId) return@setOnItemSelectedListener true

            when (item.itemId) {
                R.id.nav_home    -> loadFragment(HomeUnitBisnisFragment(),     R.id.nav_home)
                R.id.nav_setoran -> loadFragment(SetoranFragment(),            R.id.nav_setoran)
                R.id.nav_riwayat -> loadFragment(RiwayatUnitBisnisFragment(), R.id.nav_riwayat)
                R.id.nav_profil  -> loadFragment(ProfilUnitBisnisFragment(),  R.id.nav_profil)
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (now - lastResumeTime > 2 * 60 * 1000L && lastResumeTime > 0) {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (fragment is HomeUnitBisnisFragment) fragment.refreshData()
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
            .commitAllowingStateLoss()
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