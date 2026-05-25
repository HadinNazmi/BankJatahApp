package com.example.bankjatahapp.ui.nasabah.fragment

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
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

    // PERBAIKAN: Pisahkan state filter agar bisa dikombinasikan (Waktu & Konten)
    private var filterWaktu = "semua"   // semua | minggu | bulan
    private var filterKonten = "semua"  // semua | setoran | penarikan | reward

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

    // Helper untuk list tab agar update UI lebih mudah
    private fun listTabWaktu() = listOf(binding.tabSemua, binding.tabMingguIni, binding.tabBulanIni)
    private fun listTabKonten() = listOf(binding.tabSetoran, binding.tabPenarikan, binding.tabReward)

    private fun setupTabListeners() {
        // Row 1: Filter Waktu
        binding.tabSemua.setOnClickListener { filterWaktu = "semua"; updateTabUI(); loadData() }
        binding.tabMingguIni.setOnClickListener { filterWaktu = "minggu"; updateTabUI(); loadData() }
        binding.tabBulanIni.setOnClickListener { filterWaktu = "bulan"; updateTabUI(); loadData() }

        // Row 2: Filter Konten (Gunakan toggle seperti Unit Bisnis)
        binding.tabSetoran.setOnClickListener { toggleFilterKonten("setoran") }
        binding.tabPenarikan.setOnClickListener { toggleFilterKonten("penarikan") }
        binding.tabReward.setOnClickListener { toggleFilterKonten("reward") }

    }

    private fun toggleFilterKonten(tipe: String) {
        filterKonten = if (filterKonten == tipe) "semua" else tipe
        updateTabUI()
        loadData()
    }

    private fun updateTabUI() {
        // Update styling baris waktu
        listTabWaktu().forEach { tab ->
            val tag = when(tab.id) {
                R.id.tabSemua -> "semua"
                R.id.tabMingguIni -> "minggu"
                else -> "bulan"
            }
            applyTabStyle(tab, filterWaktu == tag)
        }
        // Update styling baris konten
        listTabKonten().forEach { tab ->
            val tag = when(tab.id) {
                R.id.tabSetoran -> "setoran"
                R.id.tabPenarikan -> "penarikan"
                else -> "reward"
            }
            applyTabStyle(tab, filterKonten == tag)
        }
    }

    private fun applyTabStyle(tab: TextView, isActive: Boolean) {
        if (isActive) {
            tab.setBackgroundResource(R.drawable.ic_bg_tab_active)
            tab.setTextColor(requireContext().getColor(R.color.white))
        } else {
            tab.setBackgroundResource(R.drawable.ic_bg_tab_inactive)
            tab.setTextColor(requireContext().getColor(R.color.gray_text))
        }
    }

    private fun getBatasTanggal(): String? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return when (filterWaktu) {
            "minggu" -> { cal.add(Calendar.DAY_OF_YEAR, -7); formatIso(cal.time) }
            "bulan"  -> {
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

                // Filter tabel mana yang perlu di-fetch (Kombinasi Konten)
                val fetchSetoran   = filterKonten == "semua" || filterKonten == "setoran"
                val fetchPenarikan = filterKonten == "semua" || filterKonten == "penarikan"
                val fetchReward    = filterKonten == "semua" || filterKonten == "reward"

                val setoranJson = if (fetchSetoran) {
                    client.postgrest.from("setoran").select {
                        filter {
                            eq("id_nasabah", idUser)
                            if (batas != null) gte("created_at", batas)
                        }
                    }.data
                } else "[]"

                val pencairanJson = if (fetchPenarikan) {
                    client.postgrest.from("pencairan_dana").select {
                        filter {
                            eq("id_user", idUser)
                            if (batas != null) gte("created_at", batas)
                        }
                    }.data
                } else "[]"

                val redeemJson = if (fetchReward) {
                    client.postgrest.from("redeem_reward").select {
                        filter {
                            eq("id_nasabah", idUser)
                            if (batas != null) gte("created_at", batas)
                        }
                    }.data
                } else "[]"

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

        val showSetoran   = filterKonten == "semua" || filterKonten == "setoran"
        val showPenarikan = filterKonten == "semua" || filterKonten == "penarikan"
        val showReward    = filterKonten == "semua" || filterKonten == "reward"

        // ===== SETORAN =====
        if (showSetoran) {
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
                        tambahkanItemRiwayat("", "Setor Minyak Jelantah", "$berat Kg  •  ${formatRupiah(total)}", tgl, kode, status, "setoran", null)
                    }
                }
            } catch (e: Exception) {
                tambahkanLabel("Setoran Minyak")
                tambahkanInfoKosong("Gagal memuat data")
            }
        }

        // ===== PENCAIRAN =====
        if (showPenarikan) {
            try {
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(pencairanJson).jsonArray
                tambahkanLabel("Request Penarikan")
                if (arr.isEmpty()) {
                    tambahkanInfoKosong("Belum ada riwayat penarikan")
                } else {
                    arr.forEach { element ->
                        val obj           = element.jsonObject
                        val jumlah        = obj["jumlah_tarik"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                        val bersih        = obj["jumlah_bersih"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                        val bank          = obj["bank_tujuan"]?.jsonPrimitive?.content ?: "-"
                        val noRek         = obj["no_rekening_tujuan"]?.jsonPrimitive?.content ?: "-"
                        val status        = obj["status_request"]?.jsonPrimitive?.content ?: "-"
                        val tgl           = obj["tgl_request"]?.jsonPrimitive?.content?.take(10) ?: "-"
                        val kode          = obj["kode_pencairan"]?.jsonPrimitive?.content ?: "-"
                        val sumber        = obj["sumber_dana"]?.jsonPrimitive?.content ?: "setoran_minyak"
                        val labelSumber   = when (sumber) {
                            "komisi_unit"     -> "Komisi Unit"
                            "komisi_afiliasi" -> "Komisi Afiliasi"
                            else              -> "Tabungan Minyak"
                        }
                        tambahkanItemRiwayat("", "Penarikan $labelSumber", "${formatRupiah(jumlah)}  •  $bank $noRek", tgl, kode, status, "pencairan", obj["bukti_transfer"]?.jsonPrimitive?.content)
                    }
                }
            } catch (e: Exception) {
                tambahkanLabel("Request Penarikan")
                tambahkanInfoKosong("Gagal memuat data")
            }
        }

        // ===== REDEEM =====
        if (showReward) {
            try {
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(redeemJson).jsonArray
                if (arr.isNotEmpty() || filterKonten == "reward") {
                    tambahkanLabel("Penukaran Reward")
                    if (arr.isEmpty()) {
                        tambahkanInfoKosong("Belum ada penukaran reward")
                    } else {
                        arr.forEach { element ->
                            val obj      = element.jsonObject
                            val idRedeem = obj["id_redeem"]?.jsonPrimitive?.content ?: ""
                            val poin     = obj["poin_dipakai"]?.jsonPrimitive?.content ?: "0"
                            val status   = obj["status_redeem"]?.jsonPrimitive?.content ?: "-"
                            val tgl      = obj["tgl_redeem"]?.jsonPrimitive?.content?.take(10) ?: "-"
                            val kode     = idRedeem.take(8).uppercase()
                            tambahkanItemRedeem(idRedeem, poin, tgl, kode, status)
                        }
                    }
                }
            } catch (e: Exception) { /* skip */ }
        }


    }

    // --- LOGIKA UI (tambahkanItemRiwayat, Dialogs, dll) ---
    // Tetap gunakan kode asli Anda di bawah sini...

    private fun tambahkanItemRiwayat(icon: String, judul: String, detail: String, tanggal: String, kode: String, status: String, tipeStatus: String, buktiTransfer: String?) {
        val dp4  = (4  * resources.displayMetrics.density).toInt()
        val dp8  = (8  * resources.displayMetrics.density).toInt()
        val dp10 = (10 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp14 = (14 * resources.displayMetrics.density).toInt()
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
            setPadding(dp14, dp14, dp14, dp14)
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
            setPadding(dp10, dp4, dp10, dp4)
        }
        row1.addView(tvIcon); row1.addView(tvJudul); row1.addView(tvBadge)

        inner.addView(row1)
        inner.addView(TextView(requireContext()).apply { text = tanggal; textSize = 11f; setTextColor(requireContext().getColor(R.color.gray_text)); setPadding(0, dp4, 0, 0) })
        inner.addView(TextView(requireContext()).apply { text = detail; textSize = 12f; setTextColor(requireContext().getColor(R.color.orange_primary)); setPadding(0, dp4, 0, 0) })
        inner.addView(TextView(requireContext()).apply { text = kode; textSize = 10f; setTextColor(requireContext().getColor(R.color.gray_text)) })

        if (tipeStatus == "pencairan" && status == "selesai" && !buktiTransfer.isNullOrEmpty()) {
            val btnBukti = TextView(requireContext()).apply {
                text = "Lihat Bukti Transfer"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(requireContext().getColor(R.color.orange_primary))
                gravity = Gravity.CENTER
                background = requireContext().getDrawable(R.drawable.ic_bg_aktivitas_orange)
                setPadding(dp12, dp10, dp12, dp10)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp8, 0, 0) }
                setOnClickListener { tampilkanDialogBuktiTransfer(buktiTransfer, kode) }
            }
            inner.addView(btnBukti)
        }

        card.addView(inner)
        binding.layoutDaftarRiwayat.addView(card)
    }

    private fun tampilkanDialogBuktiTransfer(urlBukti: String, kodePencairan: String) {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48); setBackgroundResource(android.R.color.white) }
        val ivBukti = ImageView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (300 * resources.displayMetrics.density).toInt()); scaleType = ImageView.ScaleType.FIT_CENTER }
        Glide.with(requireContext()).load(urlBukti).into(ivBukti)
        layout.addView(TextView(requireContext()).apply { text = "Bukti Transfer"; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) })
        layout.addView(ivBukti)
        AlertDialog.Builder(requireContext()).setView(layout).setNegativeButton("Tutup", null).show()
    }

    private fun tambahkanItemRedeem(idRedeem: String, poin: String, tgl: String, kode: String, status: String) {
        val dp8 = (8 * resources.displayMetrics.density).toInt(); val dp14 = (14 * resources.displayMetrics.density).toInt()
        val card = CardView(requireContext()).apply { radius = (12 * resources.displayMetrics.density); cardElevation = 2f; setCardBackgroundColor(requireContext().getColor(R.color.white)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(40, 0, 40, dp8) } }
        val inner = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp14, dp14, dp14, dp14) }
        inner.addView(TextView(requireContext()).apply { text = "Penukaran Reward"; setTypeface(null, Typeface.BOLD); textSize = 13f })
        inner.addView(TextView(requireContext()).apply { text = "$poin Poin • $tgl"; textSize = 11f; setPadding(0, 4, 0, 0) })
        val btn = TextView(requireContext()).apply { text = "Lihat QR Redeem"; gravity = Gravity.CENTER; setTextColor(requireContext().getColor(R.color.orange_primary)); background = requireContext().getDrawable(R.drawable.ic_bg_aktivitas_orange); setPadding(0, 20, 0, 20); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 12, 0, 0) }; setOnClickListener { tampilkanDialogQrRedeem(idRedeem, poin, status) } }
        inner.addView(btn)
        card.addView(inner)
        binding.layoutDaftarRiwayat.addView(card)
    }

    private fun tampilkanDialogQrRedeem(idRedeem: String, poin: String, status: String) {
        val dialogBinding = DialogQrRedeemBinding.inflate(layoutInflater)
        val qrBitmap = generateQrBitmap(idRedeem, 600)
        dialogBinding.ivQrCode.setImageBitmap(qrBitmap)
        dialogBinding.tvPoinDipakai.text = "$poin poin"; dialogBinding.tvStatusRedeem.text = "Status: $status"
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
        dialogBinding.btnTutup.setOnClickListener { dialog.dismiss() }; dialogBinding.btnSimpanQr.setOnClickListener { simpanQrKeGaleri(qrBitmap, idRedeem) }
        dialog.show()
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val writer = QRCodeWriter(); val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        return bitmap
    }

    private fun simpanQrKeGaleri(bitmap: Bitmap, idRedeem: String) {
        try {
            val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "QR_$idRedeem.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BankJatah") }
            val uri = requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { requireContext().contentResolver.openOutputStream(it)?.use { os -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, os) } }
            Toast.makeText(requireContext(), "✓ Disimpan", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(requireContext(), "Gagal", Toast.LENGTH_SHORT).show() }
    }

    private fun tambahkanLabel(teks: String) {
        val tv = TextView(requireContext()).apply { text = teks; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(requireContext().getColor(R.color.black)); setPadding(40, 40, 40, 10) }
        binding.layoutDaftarRiwayat.addView(tv)
    }

    private fun tambahkanInfoKosong(pesan: String) {
        val tv = TextView(requireContext()).apply { text = pesan; textSize = 12f; setTextColor(requireContext().getColor(R.color.gray_text)); gravity = Gravity.CENTER; setPadding(0, 30, 0, 30) }
        binding.layoutDaftarRiwayat.addView(tv)
    }

    private fun formatRupiah(nominal: Double) = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(nominal).replace(",00", "")

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}