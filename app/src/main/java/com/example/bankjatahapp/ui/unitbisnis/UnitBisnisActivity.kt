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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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

        // FAB QR Scan
        binding.fabQrScan.setOnClickListener {
            bukaDialogQrNavBar()
        }
    }

    private fun bukaDialogQrNavBar() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_qr_scanner)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)

        val previewView = dialog.findViewById<androidx.camera.view.PreviewView>(R.id.cameraPreviewDialog)
        val btnTutup   = dialog.findViewById<android.widget.ImageButton>(R.id.btnTutupDialog)
        val tvInstruksi = dialog.findViewById<android.widget.TextView>(R.id.tvInstruksiDialog)

        tvInstruksi?.text = "Arahkan kamera ke QR Code nasabah"

        var sudahDipindai = false
        val cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        var cameraProvider: androidx.camera.lifecycle.ProcessCameraProvider? = null

        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (!sudahDipindai) {
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(
                                    mediaImage, imageProxy.imageInfo.rotationDegrees
                                )
                                com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
                                    .process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            val uuid = barcode.rawValue ?: continue
                                            if (uuid.length == 36 && uuid.contains("-")) {
                                                sudahDipindai = true
                                                cameraProvider?.unbindAll()
                                                cameraExecutor.shutdown()
                                                dialog.dismiss()
                                                // Tampilkan dialog konfirmasi
                                                tampilkanDialogKonfirmasiQr(uuid)
                                                break
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (_: Exception) {}

        }, androidx.core.content.ContextCompat.getMainExecutor(this))

        btnTutup?.setOnClickListener {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        }

        dialog.show()
    }

    private fun tampilkanDialogKonfirmasiQr(uuid: String) {
        // Fetch nama nasabah dulu
        lifecycleScope.launch {
            try {
                val user = com.example.bankjatahapp.data.remote.SupabaseClient.client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", uuid) } }
                    .decodeSingle<com.example.bankjatahapp.data.model.User>()

                runOnUiThread {
                    androidx.appcompat.app.AlertDialog.Builder(this@UnitBisnisActivity)
                        .setTitle("Nasabah Ditemukan")
                        .setMessage("Nama: ${user.namaLengkap}\n\nApa yang ingin Anda lakukan?")
                        .setPositiveButton("Lanjut ke Setoran") { _, _ ->
                            // Navigasi ke SetoranFragment dengan data nasabah
                            val fragment = SetoranFragment().apply {
                                arguments = android.os.Bundle().apply {
                                    putString("id_nasabah_dari_qr", uuid)
                                    putString("nama_nasabah_dari_qr", user.namaLengkap)
                                }
                            }
                            currentNavId = R.id.nav_setoran
                            binding.bottomNav.selectedItemId = R.id.nav_setoran
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, fragment)
                                .commitAllowingStateLoss()
                        }
                        .setNegativeButton("Lihat Saja", null)
                        .show()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    androidx.appcompat.app.AlertDialog.Builder(this@UnitBisnisActivity)
                        .setTitle("Nasabah Tidak Ditemukan")
                        .setMessage("QR Code tidak valid atau nasabah tidak terdaftar.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
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