package com.example.bankjatahapp.ui.nasabah.fragment

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentRiwayatBinding
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

    // "semua" | "minggu" | "bulan"
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

    // ===== HITUNG BATAS TANGGAL FILTER =====
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
            else -> null // semua data
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

                // 1. Setoran
                val setoranJson = if (batas != null) {
                    client.postgrest.from("setoran").select {
                        filter {
                            eq("id_nasabah", idUser)
                            gte("created_at", batas)
                        }
                    }.data
                } else {
                    client.postgrest.from("setoran").select {
                        filter { eq("id_nasabah", idUser) }
                    }.data
                }

                // 2. Pencairan dana
                val pencairanJson = if (batas != null) {
                    client.postgrest.from("pencairan_dana").select {
                        filter {
                            eq("id_user", idUser)
                            gte("created_at", batas)
                        }
                    }.data
                } else {
                    client.postgrest.from("pencairan_dana").select {
                        filter { eq("id_user", idUser) }
                    }.data
                }

                // 3. Redeem reward
                val redeemJson = if (batas != null) {
                    client.postgrest.from("redeem_reward").select {
                        filter {
                            eq("id_nasabah", idUser)
                            gte("created_at", batas)
                        }
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
                    tambahkanItemRiwayat("💧", "Setor Minyak Jelantah",
                        "$berat Kg  •  ${formatRupiah(total)}", tgl, kode, status, "setoran")
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
                    tambahkanItemRiwayat("🏦", "Penarikan Dana",
                        "${formatRupiah(jumlah)}  •  $bank $noRek  •  Diterima ${formatRupiah(bersih)}",
                        tgl, kode, status, "pencairan")
                }
            }
        } catch (e: Exception) {
            tambahkanLabel("Request Penarikan")
            tambahkanInfoKosong("Belum ada penarikan")
        }

        // ===== REDEEM =====
        try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(redeemJson).jsonArray
            if (arr.isNotEmpty()) {
                tambahkanLabel("Penukaran Reward")
                arr.forEach { element ->
                    val obj    = element.jsonObject
                    val poin   = obj["poin_dipakai"]?.jsonPrimitive?.content ?: "0"
                    val status = obj["status_redeem"]?.jsonPrimitive?.content ?: "-"
                    val tgl    = obj["tgl_redeem"]?.jsonPrimitive?.content?.take(10) ?: "-"
                    val kode   = obj["id_redeem"]?.jsonPrimitive?.content?.take(8) ?: "-"
                    tambahkanItemRiwayat("🎁", "Penukaran Reward",
                        "$poin Poin digunakan", tgl, "ID: $kode", status, "redeem")
                }
            }
        } catch (e: Exception) { /* skip */ }

        binding.tvTotalSetor.text = "$totalBerat Kg"
        binding.tvSaldo.text      = formatRupiah(totalSaldo)
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
            "redeem" -> when (status) {
                "completed" -> Pair(R.drawable.ic_bg_status_berhasil, "Selesai")
                "approved"  -> Pair(R.drawable.ic_bg_status_berhasil, "Disetujui")
                "pending"   -> Pair(R.drawable.ic_bg_status_pending,  "Pending")
                "rejected"  -> Pair(R.drawable.ic_bg_status_gagal,    "Ditolak")
                else        -> Pair(R.drawable.ic_bg_status_pending,  status)
            }
            else -> Pair(R.drawable.ic_bg_status_pending, status)
        }

        val card = CardView(requireContext()).apply {
            radius = (12 * resources.displayMetrics.density)
            cardElevation = (2 * resources.displayMetrics.density)
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
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvIcon = TextView(requireContext()).apply {
            text = icon; textSize = 18f
            val mr = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, mr, 0)
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

        val tvTgl = TextView(requireContext()).apply {
            text = tanggal; textSize = 11f
            setTextColor(requireContext().getColor(R.color.gray_text))
            val mt = (4 * resources.displayMetrics.density).toInt(); setPadding(0, mt, 0, 0)
        }
        val tvDetail = TextView(requireContext()).apply {
            text = detail; textSize = 12f
            setTextColor(requireContext().getColor(R.color.orange_primary))
            val mt = (4 * resources.displayMetrics.density).toInt(); setPadding(0, mt, 0, 0)
        }
        val tvKode = TextView(requireContext()).apply {
            text = kode; textSize = 10f
            setTextColor(requireContext().getColor(R.color.gray_text))
            val mt = (2 * resources.displayMetrics.density).toInt(); setPadding(0, mt, 0, 0)
        }

        inner.addView(row1); inner.addView(tvTgl); inner.addView(tvDetail); inner.addView(tvKode)
        card.addView(inner)
        binding.layoutDaftarRiwayat.addView(card)
    }

    private fun formatRupiah(nominal: Double) =
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(nominal).replace(",00", "")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}