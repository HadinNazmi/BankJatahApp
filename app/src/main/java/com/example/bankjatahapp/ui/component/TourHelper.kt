package com.example.bankjatahapp.ui.component

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence

object TourHelper {

    // ===== KEY PREFERENCES =====
    private const val PREF_NAME        = "bankjatah_tour"
    private const val KEY_TOUR_NASABAH = "tour_nasabah_selesai"
    private const val KEY_TOUR_UB      = "tour_ub_selesai"

    fun sudahLihatTourNasabah(activity: Activity): Boolean {
        return activity.getSharedPreferences(PREF_NAME, 0)
            .getBoolean(KEY_TOUR_NASABAH, false)
    }

    fun sudahLihatTourUb(activity: Activity): Boolean {
        return activity.getSharedPreferences(PREF_NAME, 0)
            .getBoolean(KEY_TOUR_UB, false)
    }

    fun tandaiTourNasabahSelesai(activity: Activity) {
        activity.getSharedPreferences(PREF_NAME, 0).edit()
            .putBoolean(KEY_TOUR_NASABAH, true).apply()
    }

    fun tandaiTourUbSelesai(activity: Activity) {
        activity.getSharedPreferences(PREF_NAME, 0).edit()
            .putBoolean(KEY_TOUR_UB, true).apply()
    }

    fun resetSemuaTour(activity: Activity) {
        activity.getSharedPreferences(PREF_NAME, 0).edit().clear().apply()
    }

    // ===== BUILDER HELPER =====
    private fun buatTarget(
        view: android.view.View,
        judul: String,
        deskripsi: String
    ): TapTarget {
        return TapTarget.forView(view, judul, deskripsi)
            .outerCircleColor(com.example.bankjatahapp.R.color.orange_primary)
            .outerCircleAlpha(0.92f)
            .targetCircleColor(android.R.color.white)
            .titleTextSize(18)
            .titleTextColor(android.R.color.white)
            .descriptionTextSize(13)
            .descriptionTextColor(android.R.color.white)
            .textColor(android.R.color.white)
            .textTypeface(Typeface.SANS_SERIF)
            .dimColor(android.R.color.black)
            .drawShadow(true)
            .cancelable(true)
            .tintTarget(true)
            .transparentTarget(false)
    }

    // ===== TOUR NASABAH — HOME =====
    fun mulaiTourNasabahHome(
        activity: Activity,
        viewSaldoTabungan: android.view.View,
        viewSaldoBonus: android.view.View,
        viewLevel: android.view.View,
        viewPoin: android.view.View,
        viewTombolAksi: android.view.View,
        viewUbTerdekat: android.view.View,
        onSelesai: () -> Unit
    ) {
        TapTargetSequence(activity)
            .targets(
                buatTarget(
                    viewSaldoTabungan,
                    "Saldo Tabungan",
                    "Hasil dari setiap setoran minyak jelantah kamu. Saldo ini bisa ditarik ke rekening bank."
                ),
                buatTarget(
                    viewSaldoBonus,
                    "Saldo Bonus Afiliasi",
                    "Komisi dari jaringan downline kamu. Setiap kali anggota referralmu menyetor, kamu dapat bonus!"
                ),
                buatTarget(
                    viewLevel,
                    "Level Bintang",
                    "Levelmu naik seiring total setoran. Butuh minimal Level 3 untuk bisa menarik saldo."
                ),
                buatTarget(
                    viewPoin,
                    "Poin Reward",
                    "Terkumpul dari setiap setoran minyak. Tukarkan poin dengan hadiah menarik di menu Reward!"
                ),
                buatTarget(
                    viewTombolAksi,
                    "Menu Aksi Cepat",
                    "Akses cepat ke Reward, Riwayat, dan Penarikan Saldo dari sini."
                ),
                buatTarget(
                    viewUbTerdekat,
                    "Unit Bisnis Terdekat",
                    "Tempat kamu menyetorkan minyak jelantah. Tap untuk lihat detail lokasi dan jam operasional."
                )
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    tandaiTourNasabahSelesai(activity)
                    onSelesai()
                }
                override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}
                override fun onSequenceCanceled(lastTarget: TapTarget) {
                    tandaiTourNasabahSelesai(activity)
                    onSelesai()
                }
            })
            .start()
    }

    // ===== TOUR NASABAH — PROFIL =====
    fun mulaiTourNasabahProfil(
        activity: Activity,
        viewQrKode: android.view.View,
        viewAfiliasi: android.view.View,
        viewPengaturan: android.view.View,
        onSelesai: () -> Unit
    ) {
        TapTargetSequence(activity)
            .targets(
                buatTarget(
                    viewQrKode,
                    "QR Identitas Kamu",
                    "Tunjukkan QR ini ke Unit Bisnis saat menyetor minyak. UB akan scan QR ini untuk mencatat transaksimu."
                ),
                buatTarget(
                    viewAfiliasi,
                    "Jaringan Afiliasi",
                    "Lihat daftar anggota yang bergabung menggunakan kode referralmu beserta total setorannya."
                ),
                buatTarget(
                    viewPengaturan,
                    "Pengaturan Akun",
                    "Update data diri, rekening bank untuk penarikan, dan kelola akun kamu di sini."
                )
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() { onSelesai() }
                override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}
                override fun onSequenceCanceled(lastTarget: TapTarget) { onSelesai() }
            })
            .start()
    }

    // ===== TOUR NASABAH — REWARD =====
    fun mulaiTourNasabahReward(
        activity: Activity,
        viewKatalog: android.view.View,
        viewSyarat: android.view.View,
        onSelesai: () -> Unit
    ) {
        TapTargetSequence(activity)
            .targets(
                buatTarget(
                    viewKatalog,
                    "Katalog Hadiah",
                    "Pilih hadiah yang ingin kamu tukar menggunakan poin yang sudah terkumpul."
                ),
                buatTarget(
                    viewSyarat,
                    "Syarat Penukaran",
                    "Untuk menukar reward kamu perlu: minimal Level Bintang 3, dan total setoran pribadi sesuai ketentuan. Cek detailnya di tiap produk."
                )
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() { onSelesai() }
                override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}
                override fun onSequenceCanceled(lastTarget: TapTarget) { onSelesai() }
            })
            .start()
    }

    // ===== TOUR UNIT BISNIS — HOME =====
    fun mulaiTourUbHome(
        activity: Activity,
        viewSaldoTabungan: android.view.View,
        viewSaldoKomisi: android.view.View,
        viewSaldoBonus: android.view.View,
        viewTombolSetoran: android.view.View,
        viewTombolPenarikan: android.view.View,
        onSelesai: () -> Unit
    ) {
        TapTargetSequence(activity)
            .targets(
                buatTarget(
                    viewSaldoTabungan,
                    "Saldo Tabungan",
                    "Hasil setoranmu sebagai nasabah biasa. Bisa ditarik ke rekening bank."
                ),
                buatTarget(
                    viewSaldoKomisi,
                    "Saldo Komisi Unit",
                    "Komisi yang kamu terima setiap kali ada nasabah menyetor minyak di tempatmu. Jumlah per kg ditentukan oleh tipe wilayah."
                ),
                buatTarget(
                    viewSaldoBonus,
                    "Saldo Bonus Afiliasi",
                    "Bonus dari jaringan downline referralmu. Semakin besar jaringanmu, semakin besar bonusnya!"
                ),
                buatTarget(
                    viewTombolSetoran,
                    "Input Setoran Minyak",
                    "Catat setoran minyak dari nasabah di sini. Scan QR nasabah atau cari nama, lalu isi berat minyaknya."
                ),
                buatTarget(
                    viewTombolPenarikan,
                    "Tarik Saldo",
                    "Ajukan pencairan dari tiga jenis saldo: Tabungan, Komisi Unit, dan Bonus Afiliasi. Masing-masing memiliki syarat minimum sendiri."
                )
            )
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    tandaiTourUbSelesai(activity)
                    onSelesai()
                }
                override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}
                override fun onSequenceCanceled(lastTarget: TapTarget) {
                    tandaiTourUbSelesai(activity)
                    onSelesai()
                }
            })
            .start()
    }

    // ===== TOUR UNIT BISNIS — SETORAN =====
    fun mulaiTourUbSetoran(
        activity: Activity,
        viewQrScanner: android.view.View,
        viewCariNama: android.view.View,
        viewToggleJemput: android.view.View,
        viewBerat: android.view.View,
        onSelesai: () -> Unit
    ) {
        // Delay kecil supaya fragment setoran sudah fully rendered
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                TapTargetSequence(activity)
                    .targets(
                        buatTarget(
                            viewQrScanner,
                            "Scan QR Nasabah",
                            "Minta nasabah membuka halaman Profil lalu tunjukkan QR-nya. Tap area kamera ini untuk memulai scan — identitas nasabah langsung terdeteksi otomatis."
                        ),
                        buatTarget(
                            viewCariNama,
                            "Cari Nama (Alternatif)",
                            "Jika kamera bermasalah, ketik minimal 2 huruf nama nasabah di sini. Pilih dari daftar yang muncul untuk mengisi identitas."
                        ),
                        buatTarget(
                            viewToggleJemput,
                            "Mode Jemput",
                            "Aktifkan jika kamu yang mendatangi nasabah untuk mengambil minyaknya. Harga nasabah akan dikurangi biaya jemput, dan komisimu bertambah otomatis."
                        ),
                        buatTarget(
                            viewBerat,
                            "Berat Minyak Bersih",
                            "Isi berat minyak dalam kilogram sesuai timbangan. Nilai rupiah untuk nasabah dihitung otomatis berdasarkan harga aktif di wilayahmu."
                        )
                    )
                    .listener(object : TapTargetSequence.Listener {
                        override fun onSequenceFinish() { onSelesai() }
                        override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}
                        override fun onSequenceCanceled(lastTarget: TapTarget) { onSelesai() }
                    })
                    .start()
            }
        }, 600)
    }
}