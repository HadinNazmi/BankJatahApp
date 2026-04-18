package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.DompetUser
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.ProdukReward
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentHomeUnitBisnisBinding
import com.example.bankjatahapp.ui.unitbisnis.UnitBisnisActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class HomeUnitBisnisFragment : Fragment() {

    private var _binding: FragmentHomeUnitBisnisBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupClickListeners()
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // 1. Data users
                val user = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser) } }
                    .decodeSingle<User>()

                // 2. Data dompet — pakai DompetUser model (bukan regex manual)
                // Unit bisnis punya saldo_nasabah (tabungan) + saldo_unit (komisi)
                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                // 3. Data nasabah_data → level + total setoran
                // Unit bisnis juga nasabah, datanya tetap ada di nasabah_data
                val nasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser) } }
                    .decodeSingle<NasabahData>()

                // 4. Produk reward aktif
                val produkList = client.postgrest
                    .from("produk_reward")
                    .select { filter { eq("status_produk", "aktif") } }
                    .decodeList<ProdukReward>()
                val rewardTersedia = produkList.count { it.stok > 0 }

                tampilkanData(user, dompet, nasabah, rewardTersedia)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun tampilkanData(
        user: User,
        dompet: DompetUser,
        nasabah: NasabahData,
        rewardTersedia: Int
    ) {
        // Header
        binding.tvNamaUser.text = user.namaLengkap

        // Saldo tabungan (saldo_nasabah) & saldo komisi (saldo_unit) dari DompetUser model
        binding.tvSaldoTabungan.text = formatRupiah(dompet.saldoNasabah)
        binding.tvSaldoKomisi.text   = formatRupiah(dompet.saldoUnit)

        // Poin & reward
        binding.tvTotalPoin.text      = dompet.poinReward.toString()
        binding.tvRewardTersedia.text = rewardTersedia.toString()
        binding.tvInfoReward.text     = "Ada $rewardTersedia reward yang bisa ditukar sekarang!"

        // Total setoran lifetime
        val totalKg = nasabah.totalSetoranLifetime ?: 0.0
        binding.tvSaldoMinyak.text = "$totalKg Kg"

        // Level bintang dari nasabah_data
        val level = nasabah.levelBintang ?: 1
        binding.tvLevelLabel.text = labelLevel(level)

        // Progress bar
        val progressPersen = hitungProgressLevel(level, totalKg)
        binding.progressMinyak.progress = progressPersen
        binding.tvProgressLabel.text = if (progressPersen >= 100) {
            "Level maksimum tercapai! 🎉"
        } else {
            "$progressPersen% menuju level ${level + 1}"
        }
    }

    private fun labelLevel(level: Int): String = when (level) {
        1 -> "⭐ Level 1 - Pemula"
        2 -> "⭐⭐ Level 2 - Penabung"
        3 -> "⭐⭐⭐ Level 3 - Aktif"
        4 -> "⭐⭐⭐⭐ Level 4 - Mahir"
        5 -> "⭐⭐⭐⭐⭐ Level 5 - Veteran"
        6 -> "⭐⭐⭐⭐⭐⭐ Level 6 - Pakar"
        7 -> "⭐⭐⭐⭐⭐⭐⭐ Level 7 - Master"
        8 -> "⭐⭐⭐⭐⭐⭐⭐⭐ Level 8 - Legenda"
        else -> "Level $level"
    }

    private fun hitungProgressLevel(level: Int, totalKg: Double): Int {
        if (level >= 8) return 100
        val targetKg = level * 50.0
        val prevKg   = (level - 1) * 50.0
        val progress = ((totalKg - prevKg) / (targetKg - prevKg) * 100).toInt()
        return progress.coerceIn(0, 100)
    }

    private fun setupClickListeners() {
        binding.btnSetoran.setOnClickListener {
            (activity as? UnitBisnisActivity)?.navigateTo(R.id.nav_setoran)
        }
        binding.btnReward.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, RewardUnitBisnisFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.btnRiwayat.setOnClickListener {
            (activity as? UnitBisnisActivity)?.navigateTo(R.id.nav_riwayat)
        }

        // Tombol Request Penarikan → navigasi ke PenarikanUnitBisnisFragment
        binding.btnRequestPenarikan.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PenarikanUnitBisnisFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.tvLihatSemua.setOnClickListener { }
        binding.ivNotifikasi.setOnClickListener { }
    }

    private fun formatRupiah(nominal: Double): String =
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(nominal).replace(",00", "")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}