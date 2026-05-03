package com.example.bankjatahapp.ui.nasabah.fragment

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.DialogQrRedeemBinding
import com.example.bankjatahapp.databinding.FragmentRiwayatBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RiwayatFragment : Fragment() {

    private var _binding: FragmentRiwayatBinding? = null
    private val binding get() = _binding!!

    private var filterAktif = "semua"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRiwayatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabListeners()
        loadData()
    }

    private fun setupTabListeners() {
        binding.tabSemua.setOnClickListener {
            filterAktif = "semua"
            setActiveTab(binding.tabSemua)
            loadData()
        }
        binding.tabMingguIni.setOnClickListener {
            filterAktif = "minggu"
            setActiveTab(binding.tabMingguIni)
            loadData()
        }
        binding.tabBulanIni.setOnClickListener {
            filterAktif = "bulan"
            setActiveTab(binding.tabBulanIni)
            loadData()
        }
        binding.btnFilter.setOnClickListener { }
    }

    private fun setActiveTab(active: TextView) {
        listOf(binding.tabSemua, binding.tabMingguIni, binding.tabBulanIni).forEach { tab ->
            if (tab == active) {
                tab.setBackgroundResource(R.drawable.ic_bg_tab_active)
                tab.setTextColor(requireContext().getColor(R.color.white))
            } else {
                tab.setBackgroundResource(R.drawable.ic_bg_tab_inactive)
                tab.setTextColor(requireContext().getColor(R.color.gray_text))
            }
        }
    }

    private fun getBatasTanggal(): String? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return when (filterAktif) {
            "minggu" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                formatIso(cal.time)
            }
            "bulan" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                formatIso(cal.time)
            }
            else -> null
        }
    }

    private fun formatIso(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch
                val batas  = getBatasTanggal()

                val setoranJson = if (batas != null) {
                    client.postgrest.from("setoran").select {
                        filter { eq("id_nasabah", idUser); gte("created_at", batas) }
                    }.data
                } else {
                    client.postgrest.from("setoran").select {
                        filter { eq("id_nasabah", idUser) }
                    }.data
                }

                val pencairanJson = if (batas != null) {
                    client.postgrest.from("pencairan_dana").select {
                        filter { eq("id_user", idUser); gte("created_at", batas) }
                    }.data
                } else {
                    client.postgrest.from("pencairan_dana").select {
                        filter { eq("id_user", idUser) }
                    }.data
                }

                val redeemJson = if (batas != null) {
                    client.postgrest.from("redeem_reward").select {
                        filter { eq("id_nasabah", idUser); gte("created_at", batas) }
                    }.data
                } else {
                    client.postgrest.from("redeem_reward").select {
                        filter { eq("id_nasabah", idUser) }
                    }.data
                }

                tampilkanRiwayat(setoranJson, redeemJson, pencairanJson)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun tampilkanRiwayat(
        setoranJson: String,
        redeemJson: String,
        pencairanJson: String
    ) {
        binding.layoutDaftarRiwayat.removeAllViews()

        var totalBerat = 0.0
        var totalSaldo = 0.0

        // ===== SETORAN =====
        try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(setoranJson).jsonArray
            tambahkanLabel("Setoran Minyak")
            if (arr.isEmpty()) {
                tambahkanInfoKosong("Belum ada riwayat setoran")
            } else {
                arr.forEach { element ->
                    val obj    = element.jsonObject
                    val berat  = obj["berat_bersih_kg"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val total  = obj["total_rupiah_nasabah"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val status = obj["status_setoran"]?.jsonPrimitive?.content ?: "-"
                    val tgl    = obj["tgl_setoran"]?.jsonPrimitive?.content?.take(10) ?: "-"
                    val kode   = obj["kode_transaksi"]?.jsonPrimitive?.content ?: "-"
                    totalBerat += berat
                    totalSaldo += total
                    tambahkanItemRiwayat(
                        "💧", "Setor Minyak Jelantah",
                        "$berat Kg  •  ${formatRupiah(total)}", tgl, kode, status, "setoran"
                    )
                }
            }
        } catch (e: Exception) {
            tambahkanLabel("Setoran Minyak")
            tambahkanInfoKosong("Belum ada setoran")
        }

        // ===== PENCAIRAN =====
        try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(pencairanJson).jsonArray
            tambahkanLabel("Request Penarikan")
            if (arr.isEmpty()) {
                tambahkanInfoKosong("Belum ada riwayat penarikan")
            } else {
                arr.forEach { element ->
                    val obj    = element.jsonObject
                    val jumlah = obj["jumlah_tarik"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val bersih = obj["jumlah_bersih"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val bank   = obj["bank_tujuan"]?.jsonPrimitive?.content ?: "-"
                    val noRek  = obj["no_rekening_tujuan"]?.jsonPrimitive?.content ?: "-"
                    val status = obj["status_request"]?.jsonPrimitive?.content ?: "-"
                    val tgl    = obj["tgl_request"]?.jsonPrimitive?.content?.take(10) ?: "-"
                    val kode   = obj["kode_pencairan"]?.jsonPrimitive?.content ?: "-"
                    tambahkanItemRiwayat(
                        "🏦", "Penarikan Dana",
                        "${formatRupiah(jumlah)}  •  $bank $noRek  •  Diterima ${formatRupiah(bersih)}",
                        tgl, kode, status, "pencairan"
                    )
                }
            }
        } catch (e: Exception) {
            tambahkanLabel("Request Penarikan")
            tambahkanInfoKosong("Belum ada penarikan")
        }

        // ===== REDEEM — dengan tombol Lihat QR =====
        try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(redeemJson).jsonArray
            if (arr.isNotEmpty()) {
                tambahkanLabel("Penukaran Reward")
                arr.forEach { element ->
                    val obj      = element.jsonObject
                    val idRedeem = obj["id_redeem"]?.jsonPrimitive?.content ?: ""
                    val poin     = obj["poin_dipakai"]?.jsonPrimitive?.content ?: "0"
                    val status   = obj["status_redeem"]?.jsonPrimitive?.content ?: "-"
                    val tgl      = obj["tgl_redeem"]?.jsonPrimitive?.content?.take(10) ?: "-"
                    val kode     = idRedeem.take(8).uppercase()
                    tambahkanItemRedeem(
                        idRedeem = idRedeem,
                        poin     = poin,
                        tgl      = tgl,
                        kode     = kode,
                        status   = status
                    )
                }
            }
        } catch (e: Exception) { /* skip */ }

        binding.tvTotalSetor.text = "$totalBerat Kg"
        binding.tvSaldo.text      = formatRupiah(totalSaldo)
    }

    // ===== ITEM REDEEM KHUSUS dengan tombol Lihat QR =====
    private fun tambahkanItemRedeem(
        idRedeem: String,
        poin: String,
        tgl: String,
        kode: String,
        status: String
    ) {
        val dp8  = (8  * resources.displayMetrics.density).toInt()
        val dp20 = (20 * resources.displayMetrics.density).toInt()
        val dp4  = (4  * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp14 = (14 * resources.displayMetrics.density).toInt()

        val (bgStatus, labelStatus) = when (status) {
            "selesai"    -> Pair(R.drawable.ic_bg_status_berhasil, "Selesai")
            "disetujui"  -> Pair(R.drawable.ic_bg_status_berhasil, "Disetujui")
            "menunggu"   -> Pair(R.drawable.ic_bg_status_pending,  "Menunggu")
            "diproses"   -> Pair(R.drawable.ic_bg_status_pending,  "Diproses")
            "ditolak"    -> Pair(R.drawable.ic_bg_status_gagal,    "Ditolak")
            else         -> Pair(R.drawable.ic_bg_status_pending,  status)
        }

        val card = CardView(requireContext()).apply {
            radius        = (12 * resources.displayMetrics.density)
            cardElevation = (2  * resources.displayMetrics.density)
            setCardBackgroundColor(requireContext().getColor(R.color.white))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp20, 0, dp20, dp8) }
        }

        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp14, dp14, dp14, dp14)
        }

        // Row 1: icon + judul + badge status
        val row1 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        val tvIcon = TextView(requireContext()).apply {
            text = "🎁"; textSize = 18f
            setPadding(0, 0, dp8, 0)
        }
        val tvJudul = TextView(requireContext()).apply {
            text = "Penukaran Reward"; textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.black))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvBadge = TextView(requireContext()).apply {
            text = labelStatus; textSize = 11f
            setTextColor(requireContext().getColor(R.color.white))
            setBackgroundResource(bgStatus)
            val ph = (10 * resources.displayMetrics.density).toInt()
            val pv = (4  * resources.displayMetrics.density).toInt()
            setPadding(ph, pv, ph, pv)
        }
        row1.addView(tvIcon); row1.addView(tvJudul); row1.addView(tvBadge)

        // Tanggal
        val tvTgl = TextView(requireContext()).apply {
            text = tgl; textSize = 11f
            setTextColor(requireContext().getColor(R.color.gray_text))
            setPadding(0, dp4, 0, 0)
        }
        // Detail poin
        val tvDetail = TextView(requireContext()).apply {
            text = "$poin Poin digunakan"; textSize = 12f
            setTextColor(requireContext().getColor(R.color.orange_primary))
            setPadding(0, dp4, 0, 0)
        }
        // Kode ID
        val tvKode = TextView(requireContext()).apply {
            text = "ID: $kode..."; textSize = 10f
            setTextColor(requireContext().getColor(R.color.gray_text))
            setPadding(0, (2 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        // Divider tipis
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, dp8, 0, dp8) }
            setBackgroundColor(requireContext().getColor(R.color.gray_border))
        }

        // Tombol Lihat QR
        val btnLihatQr = TextView(requireContext()).apply {
            text = "🔍 Lihat QR Redeem"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.orange_primary))
            gravity = Gravity.CENTER
            background = requireContext().getDrawable(R.drawable.ic_bg_aktivitas_orange)
            val pv = (10 * resources.displayMetrics.density).toInt()
            setPadding(dp12, pv, dp12, pv)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            // Buka dialog QR saat diklik
            setOnClickListener {
                tampilkanDialogQrRedeem(idRedeem, poin, status)
            }
        }

        inner.addView(row1)
        inner.addView(tvTgl)
        inner.addView(tvDetail)
        inner.addView(tvKode)
        inner.addView(divider)
        inner.addView(btnLihatQr)

        card.addView(inner)
        binding.layoutDaftarRiwayat.addView(card)
    }

    // ===== DIALOG QR REDEEM =====
    private fun tampilkanDialogQrRedeem(idRedeem: String, poin: String, status: String) {
        val dialogBinding = DialogQrRedeemBinding.inflate(layoutInflater)

        val qrBitmap = generateQrBitmap(idRedeem, 600)
        dialogBinding.ivQrCode.setImageBitmap(qrBitmap)

        dialogBinding.tvNamaProduk.text   = "Penukaran Reward"
        dialogBinding.tvPoinDipakai.text  = "$poin poin"
        dialogBinding.tvKodeRedeem.text   = "ID: ${idRedeem.take(8).uppercase()}..."
        dialogBinding.tvStatusRedeem.text = "Status: ${
            when (status) {
                "selesai"   -> "Selesai"
                "disetujui" -> "Disetujui"
                "menunggu"  -> "Menunggu Verifikasi"
                "diproses"  -> "Sedang Diproses"
                "ditolak"   -> "Ditolak"
                else        -> status
            }
        }"
        dialogBinding.tvInfoQr.text = "Tunjukkan QR ini kepada petugas untuk menukarkan reward Anda."

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnSimpanQr.setOnClickListener {
            simpanQrKeGaleri(qrBitmap, idRedeem)
        }
        dialogBinding.btnTutup.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ===== GENERATE QR BITMAP =====
    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val writer    = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap    = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    // ===== SIMPAN QR KE GALERI =====
    private fun simpanQrKeGaleri(bitmap: Bitmap, idRedeem: String) {
        try {
            val namaFile = "QR_Redeem_${idRedeem.take(8).uppercase()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, namaFile)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BankJatah")
                }
                val uri = requireContext().contentResolver
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "BankJatah"
                ).apply { mkdirs() }
                val file = java.io.File(dir, namaFile)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            Toast.makeText(requireContext(), "✓ QR disimpan ke Galeri / BankJatah", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== ITEM RIWAYAT BIASA (setoran & pencairan) =====
    private fun tambahkanItemRiwayat(
        icon: String, judul: String, detail: String,
        tanggal: String, kode: String, status: String, tipeStatus: String
    ) {
        val dp8  = (8  * resources.displayMetrics.density).toInt()
        val dp20 = (20 * resources.displayMetrics.density).toInt()

        val (bgStatus, labelStatus) = when (tipeStatus) {
            "setoran" -> when (status) {
                "selesai"           -> Pair(R.drawable.ic_bg_status_berhasil, "Selesai")
                "menunggu"          -> Pair(R.drawable.ic_bg_status_pending,  "Menunggu")
                "menunggu_validasi" -> Pair(R.drawable.ic_bg_status_pending,  "Validasi")
                "ditolak"           -> Pair(R.drawable.ic_bg_status_gagal,    "Ditolak")
                else                -> Pair(R.drawable.ic_bg_status_pending,  status)
            }
            "pencairan" -> when (status) {
                "selesai"  -> Pair(R.drawable.ic_bg_status_berhasil, "Selesai")
                "diproses" -> Pair(R.drawable.ic_bg_status_pending,  "Diproses")
                "menunggu" -> Pair(R.drawable.ic_bg_status_pending,  "Menunggu")
                "gagal"    -> Pair(R.drawable.ic_bg_status_gagal,    "Gagal")
                "ditolak"  -> Pair(R.drawable.ic_bg_status_gagal,    "Ditolak")
                else       -> Pair(R.drawable.ic_bg_status_pending,  status)
            }
            else -> Pair(R.drawable.ic_bg_status_pending, status)
        }

        val card = CardView(requireContext()).apply {
            radius        = (12 * resources.displayMetrics.density)
            cardElevation = (2  * resources.displayMetrics.density)
            setCardBackgroundColor(requireContext().getColor(R.color.white))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp20, 0, dp20, dp8) }
        }

        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val p = (14 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }

        val row1 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        val tvIcon = TextView(requireContext()).apply {
            text = icon; textSize = 18f
            setPadding(0, 0, dp8, 0)
        }
        val tvJudul = TextView(requireContext()).apply {
            text = judul; textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.black))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvBadge = TextView(requireContext()).apply {
            text = labelStatus; textSize = 11f
            setTextColor(requireContext().getColor(R.color.white))
            setBackgroundResource(bgStatus)
            val ph = (10 * resources.displayMetrics.density).toInt()
            val pv = (4  * resources.displayMetrics.density).toInt()
            setPadding(ph, pv, ph, pv)
        }
        row1.addView(tvIcon); row1.addView(tvJudul); row1.addView(tvBadge)

        val dp4 = (4 * resources.displayMetrics.density).toInt()
        val tvTgl = TextView(requireContext()).apply {
            text = tanggal; textSize = 11f
            setTextColor(requireContext().getColor(R.color.gray_text))
            setPadding(0, dp4, 0, 0)
        }
        val tvDetail = TextView(requireContext()).apply {
            text = detail; textSize = 12f
            setTextColor(requireContext().getColor(R.color.orange_primary))
            setPadding(0, dp4, 0, 0)
        }
        val tvKode = TextView(requireContext()).apply {
            text = kode; textSize = 10f
            setTextColor(requireContext().getColor(R.color.gray_text))
            setPadding(0, (2 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        inner.addView(row1); inner.addView(tvTgl); inner.addView(tvDetail); inner.addView(tvKode)
        card.addView(inner)
        binding.layoutDaftarRiwayat.addView(card)
    }

    private fun tambahkanLabel(teks: String) {
        val tv = TextView(requireContext()).apply {
            text = teks; textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.black))
            val px = (14 * resources.displayMetrics.density).toInt()
            val pt = (16 * resources.displayMetrics.density).toInt()
            val pb = (6  * resources.displayMetrics.density).toInt()
            setPadding(px, pt, px, pb)
        }
        binding.layoutDaftarRiwayat.addView(tv)
    }

    private fun tambahkanInfoKosong(pesan: String) {
        val tv = TextView(requireContext()).apply {
            text = pesan; textSize = 12f
            setTextColor(requireContext().getColor(R.color.gray_text))
            gravity = Gravity.CENTER
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        binding.layoutDaftarRiwayat.addView(tv)
    }

    private fun formatRupiah(nominal: Double) =
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(nominal).replace(",00", "")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}