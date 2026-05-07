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

    // Pisahkan state filter agar bisa dikombinasikan
    private var filterWaktu = "semua" // "semua" | "minggu" | "bulan"
    private var filterKonten = "semua" // "semua" | "setoran" | "penarikan" | "reward"

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

    private fun listTabWaktu() = listOf(binding.tabSemua, binding.tabMingguIni, binding.tabBulanIni)
    private fun listTabKonten() = listOf(binding.tabSetoran, binding.tabPenarikan, binding.tabReward)

    private fun setupTabListeners() {
        // Row 1: Filter Waktu
        binding.tabSemua.setOnClickListener {
            filterWaktu = "semua"; updateTabUI(); loadData()
        }
        binding.tabMingguIni.setOnClickListener {
            filterWaktu = "minggu"; updateTabUI(); loadData()
        }
        binding.tabBulanIni.setOnClickListener {
            filterWaktu = "bulan"; updateTabUI(); loadData()
        }

        // Row 2: Filter Konten
        binding.tabSetoran.setOnClickListener {
            toggleKontenFilter("setoran")
        }
        binding.tabPenarikan.setOnClickListener {
            toggleKontenFilter("penarikan")
        }
        binding.tabReward.setOnClickListener {
            toggleKontenFilter("reward")
        }
    }

    private fun toggleKontenFilter(selected: String) {
        // Jika mengklik yang sudah aktif, balikkan ke "semua"
        filterKonten = if (filterKonten == selected) "semua" else selected
        updateTabUI()
        loadData()
    }

    private fun updateTabUI() {
        // Update UI Row Waktu
        listTabWaktu().forEach { tab ->
            val tag = when(tab.id) {
                R.id.tabSemua -> "semua"
                R.id.tabMingguIni -> "minggu"
                else -> "bulan"
            }
            setTabStyle(tab, filterWaktu == tag)
        }

        // Update UI Row Konten
        listTabKonten().forEach { tab ->
            val tag = when(tab.id) {
                R.id.tabSetoran -> "setoran"
                R.id.tabPenarikan -> "penarikan"
                else -> "reward"
            }
            setTabStyle(tab, filterKonten == tag)
        }
    }

    private fun setTabStyle(tab: TextView, isActive: Boolean) {
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
                val batas = getBatasTanggal()

                // Logika Kombinasi: Cek apakah konten harus ditampilkan berdasarkan filterKonten
                val showSetoran = filterKonten == "semua" || filterKonten == "setoran"
                val showPenarikan = filterKonten == "semua" || filterKonten == "penarikan"
                val showReward = filterKonten == "semua" || filterKonten == "reward"

                val setoranJson = if (showSetoran) {
                    client.postgrest.from("setoran").select {
                        filter {
                            eq("id_nasabah", idUser)
                            if (batas != null) gte("created_at", batas)
                        }
                    }.data
                } else "[]"

                val pencairanJson = if (showPenarikan) {
                    client.postgrest.from("pencairan_dana").select {
                        filter {
                            eq("id_user", idUser)
                            if (batas != null) gte("created_at", batas)
                        }
                    }.data
                } else "[]"

                val redeemJson = if (showReward) {
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

    private fun tampilkanRiwayat(setoranJson: String, redeemJson: String, pencairanJson: String) {
        binding.layoutDaftarRiwayat.removeAllViews()
        var totalBerat = 0.0
        var totalSaldo = 0.0

        val showSetoran = filterKonten == "semua" || filterKonten == "setoran"
        val showPenarikan = filterKonten == "semua" || filterKonten == "penarikan"
        val showReward = filterKonten == "semua" || filterKonten == "reward"

        // Bagian penampilan data tetap menggunakan flag showXXX
        // agar label "Belum ada riwayat" tidak muncul jika memang sedang tidak difilter

        if (showSetoran) {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(setoranJson).jsonArray
            tambahkanLabel("Setoran Minyak")
            if (arr.isEmpty()) tambahkanInfoKosong("Tidak ada setoran pada periode ini")
            else arr.forEach { element ->
                val obj = element.jsonObject
                val berat = obj["berat_bersih_kg"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val total = obj["total_rupiah_nasabah"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                totalBerat += berat; totalSaldo += total
                tambahkanItemRiwayat("", "Setor Minyak", "$berat Kg • ${formatRupiah(total)}",
                    obj["tgl_setoran"]?.jsonPrimitive?.content?.take(10) ?: "-",
                    obj["kode_transaksi"]?.jsonPrimitive?.content ?: "-",
                    obj["status_setoran"]?.jsonPrimitive?.content ?: "-", "setoran", null)
            }
        }

        if (showPenarikan) {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(pencairanJson).jsonArray
            tambahkanLabel("Request Penarikan")
            if (arr.isEmpty()) tambahkanInfoKosong("Tidak ada penarikan pada periode ini")
            else arr.forEach { element ->
                val obj = element.jsonObject
                val jumlah = obj["jumlah_tarik"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                tambahkanItemRiwayat("", "Penarikan", formatRupiah(jumlah),
                    obj["tgl_request"]?.jsonPrimitive?.content?.take(10) ?: "-",
                    obj["kode_pencairan"]?.jsonPrimitive?.content ?: "-",
                    obj["status_request"]?.jsonPrimitive?.content ?: "-", "pencairan",
                    obj["bukti_transfer"]?.jsonPrimitive?.content)
            }
        }

        if (showReward) {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(redeemJson).jsonArray
            tambahkanLabel("Penukaran Reward")
            if (arr.isEmpty()) tambahkanInfoKosong("Tidak ada penukaran reward pada periode ini")
            else arr.forEach { element ->
                val obj = element.jsonObject
                tambahkanItemRedeem(obj["id_redeem"]?.jsonPrimitive?.content ?: "",
                    obj["poin_dipakai"]?.jsonPrimitive?.content ?: "0",
                    obj["tgl_redeem"]?.jsonPrimitive?.content?.take(10) ?: "-",
                    "ID: ${obj["id_redeem"]?.jsonPrimitive?.content?.take(8)}",
                    obj["status_redeem"]?.jsonPrimitive?.content ?: "-")
            }
        }

        binding.tvTotalSetor.text = "$totalBerat Kg"
        binding.tvSaldo.text = formatRupiah(totalSaldo)
    }

    // ... (Fungsi tambahkanItemRiwayat, tambahkanItemRedeem, dll tetap sama dengan kode Anda)

    private fun tambahkanItemRiwayat(icon: String, judul: String, detail: String, tanggal: String, kode: String, status: String, tipeStatus: String, buktiTransfer: String?) {
        // Implementasi desain item riwayat Anda...
    }

    private fun tambahkanItemRedeem(idRedeem: String, poin: String, tgl: String, kode: String, status: String) {
        // Implementasi desain item redeem Anda...
    }

    private fun formatRupiah(nominal: Double) =
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(nominal).replace(",00", "")

    private fun tambahkanLabel(teks: String) {
        val tv = TextView(requireContext()).apply {
            text = teks; textSize = 13f; setTypeface(null, Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.black))
            setPadding(40, 45, 40, 15)
        }
        binding.layoutDaftarRiwayat.addView(tv)
    }

    private fun tambahkanInfoKosong(pesan: String) {
        val tv = TextView(requireContext()).apply {
            text = pesan; textSize = 12f; setTextColor(requireContext().getColor(R.color.gray_text))
            gravity = Gravity.CENTER; setPadding(0, 20, 0, 20)
        }
        binding.layoutDaftarRiwayat.addView(tv)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}