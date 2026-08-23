package com.example.bankjatahapp.ui.nasabah

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.PengajuanBerhenti
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.ActivityNasabahBinding
import com.example.bankjatahapp.ui.auth.DataChecker
import com.example.bankjatahapp.ui.auth.PermissionDialogFragment
import com.example.bankjatahapp.ui.nasabah.fragment.HomeNasabahFragment
import com.example.bankjatahapp.ui.nasabah.fragment.ProfilNasabahFragment
import com.example.bankjatahapp.ui.nasabah.fragment.RewardFragment
import com.example.bankjatahapp.ui.nasabah.fragment.RiwayatFragment
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class NasabahActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNasabahBinding

    private var lastNavTime = 0L
    private var currentNavId = R.id.nav_home

    private var lastResumeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasabahBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Cek kelengkapan data saat activity dibuka
        lifecycleScope.launch {
            DataChecker.cekDanArahkanJikaDataKurang(this@NasabahActivity) {
                if (savedInstanceState == null) {
                    loadFragment(HomeNasabahFragment(), R.id.nav_home)
                }
                binding.bottomNav.selectedItemId = R.id.nav_home
                setupBottomNav()
            }
        }

        // Panggil pop-up izin otomatis saat masuk halaman utama
        PermissionDialogFragment.periksaDanTampilkan(supportFragmentManager, this)
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val now = System.currentTimeMillis()
            if (now - lastNavTime < 400L) return@setOnItemSelectedListener true
            lastNavTime = now
            if (item.itemId == currentNavId) return@setOnItemSelectedListener true

            when (item.itemId) {
                R.id.nav_home    -> loadFragment(HomeNasabahFragment(),   R.id.nav_home)
                R.id.nav_riwayat -> loadFragment(RiwayatFragment(),       R.id.nav_riwayat)
                R.id.nav_reward  -> loadFragment(RewardFragment(),         R.id.nav_reward)
                R.id.nav_profil  -> loadFragment(ProfilNasabahFragment(), R.id.nav_profil)
            }
            true
        }

        binding.fabQrScan.setOnClickListener {
            bukaDialogQrNasabah()
        }
    }

    private fun bukaDialogQrNasabah() {
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

        tvInstruksi?.text = "Arahkan kamera ke QR Code pengguna lain"

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
                                                // Panggil fungsi penampil info profil (tanpa navigasi setoran)[cite: 1]
                                                tampilkanInfoQrNasabah(uuid)
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

    private fun tampilkanInfoQrNasabah(uuid: String) {
        lifecycleScope.launch {
            try {
                val user = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", uuid) } }
                    .decodeSingle<User>()

                runOnUiThread {
                    val roleFormatted = when (user.role) {
                        "unit_bisnis" -> "Unit Bisnis"
                        "nasabah"     -> "Nasabah"
                        else          -> user.role ?: "Pengguna"
                    }

                    AlertDialog.Builder(this@NasabahActivity)
                        .setTitle("Informasi Pengguna")
                        .setMessage(
                            "Nama  : ${user.namaLengkap}\n" +
                                    "Role  : $roleFormatted\n" +
                                    "Status: ${if (user.statusAkun == "aktif") "Aktif" else "Tidak Aktif"}"
                        )
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@NasabahActivity)
                        .setTitle("Tidak Ditemukan")
                        .setMessage("QR Code tidak valid atau pengguna tidak terdaftar.")
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
            if (fragment is HomeNasabahFragment) fragment.refreshData()
        }
        lastResumeTime = now
    }

    fun navigateTo(navItemId: Int) {
        if (navItemId == currentNavId) return
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