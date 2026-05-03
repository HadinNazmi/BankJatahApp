package com.example.bankjatahapp.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.ImageView

object AvatarUtils {

    // Palet warna berdasarkan huruf pertama
    private val WARNA_AVATAR = listOf(
        0xFFE53935.toInt(), // merah
        0xFFD81B60.toInt(), // pink
        0xFF8E24AA.toInt(), // ungu
        0xFF3949AB.toInt(), // biru gelap
        0xFF1E88E5.toInt(), // biru
        0xFF00ACC1.toInt(), // cyan
        0xFF00897B.toInt(), // teal
        0xFF43A047.toInt(), // hijau
        0xFF7CB342.toInt(), // hijau muda
        0xFFF4511E.toInt(), // oranye
        0xFFFF7043.toInt(), // oranye muda
        0xFF6D4C41.toInt(), // coklat
        0xFF546E7A.toInt(), // abu biru
        0xFF757575.toInt(), // abu
    )

    /**
     * Ambil inisial dari nama lengkap.
     * "Nasabah Dua" → "ND"
     * "Nasabah01"   → "N"
     * "Ahmad Budi Santoso" → "AB" (hanya 2 kata pertama)
     */
    fun ambilInisial(namaLengkap: String): String {
        val kata = namaLengkap.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return when {
            kata.isEmpty() -> "?"
            kata.size == 1 -> kata[0].take(1).uppercase()
            else -> (kata[0].take(1) + kata[1].take(1)).uppercase()
        }
    }

    /**
     * Pilih warna berdasarkan huruf pertama nama — konsisten,
     * nama yang sama selalu dapat warna yang sama.
     */
    private fun ambilWarna(nama: String): Int {
        val index = (nama.firstOrNull()?.code ?: 0) % WARNA_AVATAR.size
        return WARNA_AVATAR[index]
    }

    /**
     * Generate Bitmap lingkaran dengan inisial di tengah.
     * @param nama    nama lengkap dari tabel users.nama_lengkap
     * @param ukuran  ukuran bitmap dalam pixel (default 200px)
     */
    fun buatBitmapAvatar(nama: String, ukuran: Int = 200): Bitmap {
        val bitmap = Bitmap.createBitmap(ukuran, ukuran, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val warna = ambilWarna(nama)
        val inisial = ambilInisial(nama)

        // Cat background lingkaran
        val catLingkaran = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = warna
            style = Paint.Style.FILL
        }
        val radius = ukuran / 2f
        canvas.drawCircle(radius, radius, radius, catLingkaran)

        // Cat teks inisial
        val catTeks = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = ukuran * 0.38f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Posisi teks agar tepat di tengah
        val batasKotak = android.graphics.Rect()
        catTeks.getTextBounds(inisial, 0, inisial.length, batasKotak)
        val y = radius - (catTeks.descent() + catTeks.ascent()) / 2

        canvas.drawText(inisial, radius, y, catTeks)

        return bitmap
    }

    /**
     * Langsung set ke ImageView — cara pakai paling simpel.
     * Panggil: AvatarUtils.pasangKeImageView(binding.ivFotoProfil, user.namaLengkap)
     */
    fun pasangKeImageView(imageView: ImageView, namaLengkap: String, ukuran: Int = 200) {
        val bitmap = buatBitmapAvatar(namaLengkap, ukuran)
        imageView.setImageBitmap(bitmap)
        imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
    }
}