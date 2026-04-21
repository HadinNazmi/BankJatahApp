package com.example.bankjatahapp.ui.unitbisnis.fragment

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
import com.example.bankjatahapp.databinding.FragmentRiwayatUnitBisnisBinding
import com.example.bankjatahapp.ui.unitbisnis.UnitBisnisActivity
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

class RiwayatUnitBisnisFragment : Fragment() {

    private var _binding: FragmentRiwayatUnitBisnisBinding? = null
    private val binding get() = _binding!!

    private var filterAktif = "semua"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRiwayatUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        loadData()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            (activity as? UnitBisnisActivity)?.navigateTo(R.id.nav_home)
        }
        binding.btnFilter.setOnClickListener { }

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
                val idUnit = client.auth.currentUserOrNull()?.id ?: return@launch
                val batas  = getBatasTanggal()

                val setoranJson = if (batas != null) {
                    client.postgrest.from("setoran").select {
                        filter {
                            eq("id_unit", idUnit)
                            gte("created_at", batas)
                        }
                    }.data
                } else {
                    client.postgrest.from("setoran").select {
                        filter { eq("id_unit", idUnit) }
                    }.data
                }

                val pencairanJson = if (batas != null) {
                    client.postgrest.from("pencairan_dana").select {
                        filter {
                            eq("id_user", idUnit)
                            gte("created_at", batas)
                        }
                    }.data
                } else {
                    client.postgrest.from("pencairan_dana").select {
                        filter { eq("id_user", idUnit) }
                    }.data
                }

                tampilkanRiwayat(setoranJson, pencairanJson)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun tampilkanRiwayat(setoranJson: String, pencairanJson: String) {
        binding.layoutDaftarRiwayat.removeAllViews()

        var totalBerat  = 0.0
        var totalKomisi = 0.0

        // ===== SETORAN =====
        try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(setoranJson).jsonArray
            tambahkanLabel("Setoran Minyak Diterima")
            if (arr.isEmpty()) {
                tambahkanInfoKosong("Belum ada setoran diterima")
            } else {
                arr.forEach { element ->
                    val obj    = element.jsonObject
                    val berat  = obj["berat_bersih_kg"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val komisi = obj["total_komisi_unit"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val status = obj["status_setoran"]?.jsonPrimitive?.content ?: "-"
                    val tgl    = obj["tgl_setoran"]?.jsonPrimitive?.content?.take(10) ?: "-"
                    val kode   = obj["kode_transaksi"]?.jsonPrimitive?.content ?: "-"
                    totalBerat  += berat
                    totalKomisi += komisi
                    tambahkanItemRiwayat("💧", "Penerimaan Setoran",
                        "$berat Kg  •  Komisi ${formatRupiah(komisi)}", tgl, kode, status, "setoran")
                }
            }
        } catch (e: Exception) {
            tambahkanLabel("Setoran Minyak Diterima")
            tambahkanInfoKosong("Belum ada riwayat setoran")
        }

        // ===== PENCAIRAN =====
        try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(pencairanJson).jsonArray
            tambahkanLabel("Request Penarikan")
            if (arr.isEmpty()) {
                tambahkanInfoKosong("Belum ada riwayat penarikan")
            } else {
                arr.forEach { element ->
                    val obj      = element.jsonObject
                    val jumlah   = obj["jumlah_tarik"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val bersih   = obj["jumlah_bersih"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val bank     = obj["bank_tujuan"]?.jsonPrimitive?.content ?: "-"
                    val noRek    = obj["no_rekening_tujuan"]?.jsonPrimitive?.content ?: "-"
                    val status   = obj["status_request"]?.jsonPrimitive?.content ?: "-"
                    val tgl      = obj["tgl_request"]?.jsonPrimitive?.content?.take(10) ?: "-"
                    val kode     = obj["kode_pencairan"]?.jsonPrimitive?.content ?: "-"
                    val sumber   = obj["sumber_dana"]?.jsonPrimitive?.content ?: "setoran_minyak"
                    val labelSumber = when (sumber) {
                        "komisi_unit"     -> "Komisi Unit"
                        "komisi_afiliasi" -> "Komisi Afiliasi"
                        else              -> "Tabungan Minyak"
                    }
                    tambahkanItemRiwayat("🏦", "Penarikan $labelSumber",
                        "${formatRupiah(jumlah)}  •  $bank $noRek  •  Diterima ${formatRupiah(bersih)}",
                        tgl, kode, status, "pencairan")
                }
            }
        } catch (e: Exception) {
            tambahkanLabel("Request Penarikan")
            tambahkanInfoKosong("Belum ada riwayat penarikan")
        }

        binding.tvTotalSetor.text = "$totalBerat Kg"
        binding.tvSaldo.text      = formatRupiah(totalKomisi)
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