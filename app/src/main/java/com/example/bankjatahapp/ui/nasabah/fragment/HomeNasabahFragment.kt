package com.example.bankjatahapp.ui.nasabah.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.DompetUser
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.Notification
import com.example.bankjatahapp.data.model.ProdukReward
import com.example.bankjatahapp.data.model.SystemConfig
import com.example.bankjatahapp.data.model.UnitBisnisData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentHomeNasabahBinding
import com.example.bankjatahapp.ui.common.NotifikasiFragment
import com.example.bankjatahapp.ui.nasabah.NasabahActivity
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import com.example.bankjatahapp.ui.component.TourHelper
import com.example.bankjatahapp.ui.unitbisnis.fragment.BantuanFaqFragment
import kotlin.math.*

class HomeNasabahFragment : Fragment() {

    private var _binding: FragmentHomeNasabahBinding? = null
    private val binding get() = _binding!!

    private var nasabahData: NasabahData? = null
    private var systemConfig: SystemConfig? = null
    private var realtimeChannel: RealtimeChannel? = null

    private var dataUser: User? = null
    private var dataDompet: DompetUser? = null
    private var dataNasabah: NasabahData? = null
    private var dataRewardTersedia: Int = 0
    private var sudahLoad = false

    // State sembunyikan saldo default = TRUE (Otomatis tersembunyi saat app dibuka)
    private var isSaldoHidden = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeNasabahBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        mulaiDengarkanNotifikasi()

        if (sudahLoad && dataUser != null) {
            // Pakai cache — tidak fetch ulang
            tampilkanData(dataUser!!, dataDompet!!, dataNasabah!!, dataRewardTersedia)
            loadUnitBisnisPreview()
            cekDanMulaiTour()
        } else {
            loadData()
        }
    }

    override fun onResume() {
        super.onResume()
        if (sudahLoad && dataUser != null) {
            cekDanMulaiTour()
        }
    }

    // ===== PERBAIKAN TARGET TOUR AGAR TEPAT DAN TIDAK MELESET =====
    private fun cekDanMulaiTour() {
        val activity = activity ?: return
        if (!TourHelper.sudahLihatTourNasabah(activity)) {
            // Menggunakan binding.root.post agar dihitung pasca-layout selesai ter-draw sempurna
            binding.root.post {
                if (_binding == null) return@post
                TourHelper.mulaiTourNasabahHome(
                    activity           = activity,
                    viewSaldoTabungan  = binding.tvSaldoTabungan,
                    viewSaldoBonus     = binding.tvSaldoBonus,
                    viewLevel          = binding.tvLevelLabel,
                    viewPoin           = binding.tvTotalPoin,
                    viewTomboCariUB    = binding.btnCariUB,
                    viewTombolReward   = binding.btnReward,
                    viewTombolRiwayat  = binding.btnRiwayat,
                    viewTombolRequest  = binding.btnRequestPenarikan
                ) {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                realtimeChannel?.let { client.realtime.removeChannel(it) }
            } catch (_: Exception) {}
        }
        _binding = null
    }

    private fun mulaiDengarkanNotifikasi() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                client.realtime.connect()

                try {
                    client.realtime.removeChannel(
                        client.realtime.channel("notifikasi-nasabah-$idUser")
                    )
                } catch (_: Exception) {}

                val channel = client.realtime.channel("notifikasi-nasabah-$idUser")
                realtimeChannel = channel

                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table  = "notifications"
                    filter("id_user", FilterOperator.EQ, idUser)
                }.onEach { action ->
                    val record  = action.record
                    val title   = record["title"]?.toString()?.trim('"') ?: "Notifikasi Baru"
                    val message = record["message"]?.toString()?.trim('"') ?: ""
                    updateBadgeNotifikasi()
                    tampilkanPopupNotifikasi(title, message)
                }.launchIn(viewLifecycleOwner.lifecycleScope)

                channel.subscribe()
                updateBadgeNotifikasi()

            } catch (_: Exception) {}
        }
    }

    private fun updateBadgeNotifikasi() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch
                val listBelumDibaca = client.postgrest
                    .from("notifications")
                    .select { filter { eq("id_user", idUser); eq("is_read", false) } }
                    .decodeList<Notification>()

                if (_binding == null) return@launch
                val jumlah = listBelumDibaca.size
                if (jumlah > 0) {
                    binding.tvBadgeNotif.visibility = View.VISIBLE
                    binding.tvBadgeNotif.text = if (jumlah > 99) "99+" else jumlah.toString()
                } else {
                    binding.tvBadgeNotif.visibility = View.GONE
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshData() {
        sudahLoad = false
        loadData()
    }

    private fun tampilkanPopupNotifikasi(title: String, message: String) {
        if (_binding == null) return
        val snackbar = Snackbar.make(binding.root, "🔔 $title\n$message", Snackbar.LENGTH_LONG)
        snackbar.setAction("Lihat") { bukaHalamanNotifikasi() }
        snackbar.setActionTextColor(requireContext().getColor(R.color.orange_primary))
        snackbar.show()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val user = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser) } }
                    .decodeSingle<User>()

                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                nasabahData = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser) } }
                    .decodeSingle<NasabahData>()

                loadUnitBisnisPreview()

                systemConfig = client.postgrest
                    .from("system_config")
                    .select { filter { eq("id_config", 1) } }
                    .decodeSingle<SystemConfig>()

                val produkList = client.postgrest
                    .from("produk_reward")
                    .select { filter { eq("status_produk", "aktif") } }
                    .decodeList<ProdukReward>()
                val rewardTersedia = produkList.count { it.stok > 0 }

                dataUser           = user
                dataDompet         = dompet
                dataNasabah        = nasabahData
                dataRewardTersedia = rewardTersedia
                sudahLoad          = true

                tampilkanData(user, dompet, nasabahData!!, rewardTersedia)
                cekDanMulaiTour()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadUnitBisnisPreview() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val listUnit = client.postgrest
                    .from("unit_bisnis_data")
                    .select()
                    .decodeList<UnitBisnisData>()
                    .take(5)

                val listWithNama = listUnit.map { unit ->
                    val nama = if (!unit.namaUsaha.isNullOrEmpty()) {
                        unit.namaUsaha!!
                    } else {
                        try {
                            val user = client.postgrest
                                .from("users")
                                .select { filter { eq("id_user", unit.idUnitBisnis) } }
                                .decodeSingle<User>()
                            user.namaLengkap
                        } catch (_: Exception) { "Unit Bisnis" }
                    }
                    Triple(unit, nama, null)
                }

                if (_binding == null) return@launch
                binding.rvUnitBisnisPreview.layoutManager =
                    LinearLayoutManager(requireContext())

                binding.rvUnitBisnisPreview.adapter =
                    LokasiUnitBisnisAdapter(listWithNama)

            } catch (_: Exception) {}
        }
    }

    private fun tampilkanData(
        user: User,
        dompet: DompetUser,
        nasabah: NasabahData,
        rewardTersedia: Int
    ) {
        binding.tvNamaUser.text      = user.namaLengkap
        binding.tvRoleUser.text      = "Nasabah"
        binding.tvTotalPoin.text      = dompet.poinReward.toString()
        binding.tvRewardTersedia.text = rewardTersedia.toString()
        binding.tvInfoReward.text     = "Ada $rewardTersedia reward yang bisa kamu tukar sekarang!"

        renderSaldo()

        val totalKg = nasabah.totalSetoranLifetime ?: 0.0
        binding.tvSaldoMinyak.text = "$totalKg Kg"

        val level = nasabah.levelBintang ?: 1
        binding.tvLevelLabel.text = labelLevel(level)

        val progressPersen = hitungProgressLevel(level, totalKg)
        binding.progressMinyak.progress = progressPersen
        binding.tvProgressLabel.text = if (progressPersen >= 100) {
            "Level maksimum tercapai! 🎉"
        } else {
            "$progressPersen% menuju level ${level + 1}"
        }
    }

    private fun renderSaldo() {
        val dompet = dataDompet ?: return
        if (isSaldoHidden) {
            binding.tvSaldoTabungan.text = "••••••••"
            binding.tvSaldoBonus.text    = "••••••••"
        } else {
            binding.tvSaldoTabungan.text = formatRupiah(dompet.saldoNasabah)
            binding.tvSaldoBonus.text    = formatRupiah(dompet.saldoAfiliasi)
        }
    }

    private fun labelLevel(level: Int): String = when (level) {
        1 -> "⭐ Level 1"
        2 -> "⭐ Level 2"
        3 -> "⭐ Level 3"
        4 -> "⭐ Level 4"
        5 -> "⭐ Level 5"
        6 -> "⭐ Level 6"
        7 -> "⭐ Level 7"
        8 -> "⭐ Level 8"
        else -> "Level $level"
    }

    private fun hitungProgressLevel(level: Int, totalKg: Double): Int {
        if (level >= 8) return 100
        val targetKg = level * 50.0
        val prevKg   = (level - 1) * 50.0
        return ((totalKg - prevKg) / (targetKg - prevKg) * 100).toInt().coerceIn(0, 100)
    }

    private fun bukaHalamanNotifikasi() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, NotifikasiFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    private fun bukaHalamanBantuan() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, BantuanFaqFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    private fun setupClickListeners() {
        binding.ivNotifikasi.setOnClickListener { bukaHalamanNotifikasi() }

        // Tombol Pusat Bantuan di header kanan
        binding.btnPusatBantuan.setOnClickListener { bukaHalamanBantuan() }

        // Icon Mata untuk Toggle Sembunyikan Saldo
        binding.btnToggleHideSaldo.setOnClickListener {
            isSaldoHidden = !isSaldoHidden
            renderSaldo()
        }

        binding.btnCariUB.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LokasiUnitBisnisFragment())
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }
        binding.btnReward.setOnClickListener {
            (activity as? NasabahActivity)?.navigateTo(R.id.nav_reward)
        }
        binding.btnRiwayat.setOnClickListener {
            (activity as? NasabahActivity)?.navigateTo(R.id.nav_riwayat)
        }
        binding.tvLihatSemuaUB.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LokasiUnitBisnisFragment())
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }
        binding.btnRequestPenarikan.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PenarikanNasabahFragment())
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }
        binding.btnDaftarUnitBisnis.setOnClickListener { cekSyaratDanBukaRegistrasiUB() }

        binding.swipeRefresh.setColorSchemeColors(
            requireContext().getColor(R.color.orange_primary)
        )
        binding.swipeRefresh.setOnRefreshListener {
            sudahLoad = false
            loadData()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun cekSyaratDanBukaRegistrasiUB() {
        if (nasabahData == null || systemConfig == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val idUser = client.auth.currentUserOrNull()?.id ?: return@launch
                    if (nasabahData == null) {
                        nasabahData = client.postgrest
                            .from("nasabah_data")
                            .select { filter { eq("id_nasabah", idUser) } }
                            .decodeSingle<NasabahData>()
                    }
                    if (systemConfig == null) {
                        systemConfig = client.postgrest
                            .from("system_config")
                            .select { filter { eq("id_config", 1) } }
                            .decodeSingle<SystemConfig>()
                    }
                    prosesKlikDaftarUB()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            prosesKlikDaftarUB()
        }
    }

    private fun prosesKlikDaftarUB() {
        val nasabah      = nasabahData ?: return
        val levelSaatIni = nasabah.levelBintang ?: 1
        val minBintang   = systemConfig?.minBintangKemitraan ?: 3
        if (levelSaatIni < minBintang) {
            val sisa = minBintang - levelSaatIni
            Toast.makeText(
                requireContext(),
                "⚠ Belum memenuhi syarat!\nMinimal Bintang $minBintang.\nLevel kamu: Bintang $levelSaatIni\nKurang $sisa tingkat lagi.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, RegistrasiUnitBisnisFragment())
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }
    }

    private fun formatRupiah(nominal: Double): String =
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(nominal).replace(",00", "")
}