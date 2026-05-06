package com.example.bankjatahapp.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.Notification
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentNotifikasiBinding
import com.example.bankjatahapp.ui.nasabah.NasabahActivity
import com.example.bankjatahapp.ui.unitbisnis.UnitBisnisActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class NotifikasiFragment : Fragment() {

    private var _binding: FragmentNotifikasiBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotifikasiAdapter
    private var filterWaktu = "semua"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotifikasiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupTabListeners()
        setupClickListeners()
        loadNotifikasi()
    }

    private fun setupRecyclerView() {
        adapter = NotifikasiAdapter(emptyList()) { notif ->
            onNotifKlik(notif)
        }
        binding.rvNotifikasi.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifikasi.adapter = adapter
    }

    private fun setupTabListeners() {
        binding.tabSemua.setOnClickListener {
            filterWaktu = "semua"
            setActiveTab(binding.tabSemua)
            loadNotifikasi()
        }
        binding.tabHariIni.setOnClickListener {
            filterWaktu = "hari"
            setActiveTab(binding.tabHariIni)
            loadNotifikasi()
        }
        binding.tabMingguIni.setOnClickListener {
            filterWaktu = "minggu"
            setActiveTab(binding.tabMingguIni)
            loadNotifikasi()
        }
        binding.tabBulanIni.setOnClickListener {
            filterWaktu = "bulan"
            setActiveTab(binding.tabBulanIni)
            loadNotifikasi()
        }
    }

    private fun setActiveTab(active: TextView) {
        listOf(binding.tabSemua, binding.tabHariIni, binding.tabMingguIni, binding.tabBulanIni)
            .forEach { tab ->
                if (tab == active) {
                    tab.setBackgroundResource(R.drawable.ic_bg_tab_active)
                    tab.setTextColor(requireContext().getColor(R.color.white))
                } else {
                    tab.setBackgroundResource(R.drawable.ic_bg_tab_inactive)
                    tab.setTextColor(requireContext().getColor(R.color.gray_text))
                }
            }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnTandaiSemua.setOnClickListener {
            tandaiSemuaDibaca()
        }
    }

    private fun getBatasTanggal(): String? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return when (filterWaktu) {
            "hari" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                sdf.format(cal.time)
            }
            "minggu" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                sdf.format(cal.time)
            }
            "bulan" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                sdf.format(cal.time)
            }
            else -> null
        }
    }

    private fun loadNotifikasi() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility  = View.VISIBLE
                binding.rvNotifikasi.visibility = View.GONE
                binding.tvKosong.visibility     = View.GONE

                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch
                val batas  = getBatasTanggal()

                val listNotif = if (batas != null) {
                    client.postgrest.from("notifications").select {
                        filter {
                            eq("id_user", idUser)
                            gte("created_at", batas)
                        }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<Notification>()
                } else {
                    client.postgrest.from("notifications").select {
                        filter { eq("id_user", idUser) }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<Notification>()
                }

                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE

                if (listNotif.isEmpty()) {
                    binding.tvKosong.visibility = View.VISIBLE
                    binding.tvBelumDibaca.text  = ""
                } else {
                    binding.rvNotifikasi.visibility = View.VISIBLE
                    adapter.updateData(listNotif)
                    val belumDibaca = listNotif.count { !it.isRead }
                    binding.tvBelumDibaca.text = if (belumDibaca > 0) {
                        "$belumDibaca notifikasi belum dibaca"
                    } else {
                        "Semua notifikasi sudah dibaca"
                    }
                }

            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onNotifKlik(notif: Notification) {
        lifecycleScope.launch {
            try {
                if (!notif.isRead) {
                    val payload = buildJsonObject { put("is_read", true) }
                    client.postgrest.from("notifications").update(payload) {
                        filter { eq("id_notification", notif.idNotification) }
                    }
                    loadNotifikasi()
                }
                navigasiDariLink(notif.link)
            } catch (e: Exception) {
                navigasiDariLink(notif.link)
            }
        }
    }

    private fun navigasiDariLink(link: String?) {
        if (link.isNullOrEmpty()) return
        val nasabahActivity    = activity as? NasabahActivity
        val unitBisnisActivity = activity as? UnitBisnisActivity

        when (link) {
            "riwayat" -> {
                nasabahActivity?.navigateTo(R.id.nav_riwayat)
                    ?: unitBisnisActivity?.navigateTo(R.id.nav_riwayat)
                parentFragmentManager.popBackStack()
            }
            "reward" -> {
                nasabahActivity?.navigateTo(R.id.nav_reward)
                parentFragmentManager.popBackStack()
            }
            "profil" -> {
                nasabahActivity?.navigateTo(R.id.nav_profil)
                    ?: unitBisnisActivity?.navigateTo(R.id.nav_profil)
                parentFragmentManager.popBackStack()
            }
            "setoran" -> {
                unitBisnisActivity?.navigateTo(R.id.nav_setoran)
                parentFragmentManager.popBackStack()
            }
            else -> parentFragmentManager.popBackStack()
        }
    }

    private fun tandaiSemuaDibaca() {
        lifecycleScope.launch {
            try {
                val idUser  = client.auth.currentUserOrNull()?.id ?: return@launch
                val payload = buildJsonObject { put("is_read", true) }
                client.postgrest.from("notifications").update(payload) {
                    filter {
                        eq("id_user", idUser)
                        eq("is_read", false)
                    }
                }
                Toast.makeText(requireContext(), "Semua notifikasi ditandai dibaca", Toast.LENGTH_SHORT).show()
                loadNotifikasi()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}