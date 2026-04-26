package com.example.bankjatahapp.ui.nasabah.fragment

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
import com.example.bankjatahapp.data.model.SystemConfig
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentHomeNasabahBinding
import com.example.bankjatahapp.ui.nasabah.NasabahActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class HomeNasabahFragment : Fragment() {

    private var _binding: FragmentHomeNasabahBinding? = null
    private val binding get() = _binding!!

    // Simpan data yang diperlukan untuk cek syarat UB
    private var nasabahData: NasabahData? = null
    private var systemConfig: SystemConfig? = null

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

                // 2. Data dompet
                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<DompetUser>()

                // 3. Data nasabah_data → level + total setoran
                nasabahData = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser) } }
                    .decodeSingle<NasabahData>()

                // 4. System config — untuk ambil min_bintang_kemitraan secara dinamis
                systemConfig = client.postgrest
                    .from("system_config")
                    .select { filter { eq("id_config", 1) } }
                    .decodeSingle<SystemConfig>()

                // 5. Produk reward aktif
                val produkList = client.postgrest
                    .from("produk_reward")
                    .select { filter { eq("status_produk", "aktif") } }
                    .decodeList<ProdukReward>()
                val rewardTersedia = produkList.count { it.stok > 0 }

                tampilkanData(user, dompet, nasabahData!!, rewardTersedia)

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
        binding.tvRoleUser.text = when (user.role) {
            "nasabah"     -> "Nasabah"
            "unit_bisnis" -> "Unit Bisnis"
            else          -> user.role
        }

        // Card orange: Saldo Tabungan + Saldo Bonus
        binding.tvSaldoTabungan.text = formatRupiah(dompet.saldoNasabah)
        binding.tvSaldoBonus.text    = formatRupiah(dompet.saldoAfiliasi)

        // Card poin reward
        binding.tvTotalPoin.text      = dompet.poinReward.toString()
        binding.tvRewardTersedia.text = rewardTersedia.toString()
        binding.tvInfoReward.text     = "Ada $rewardTersedia reward yang bisa kamu tukar sekarang!"

        // Total setoran lifetime
        val totalKg = nasabah.totalSetoranLifetime ?: 0.0
        binding.tvSaldoMinyak.text = "$totalKg Kg"

        // Level bintang
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
        binding.btnSetorMinyak.setOnClickListener {
            (activity as? NasabahActivity)?.navigateTo(R.id.nav_setor)
        }
        binding.btnReward.setOnClickListener {
            (activity as? NasabahActivity)?.navigateTo(R.id.nav_reward)
        }
        binding.btnRiwayat.setOnClickListener {
            (activity as? NasabahActivity)?.navigateTo(R.id.nav_riwayat)
        }
        binding.btnRequestPenarikan.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PenarikanNasabahFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.tvLihatSemua.setOnClickListener { }
        binding.ivNotifikasi.setOnClickListener { }

        // ===== TOMBOL DAFTAR UNIT BISNIS =====
        // Cek syarat bintang dari system_config sebelum buka form registrasi
        binding.btnDaftarUnitBisnis.setOnClickListener {
            cekSyaratDanBukaRegistrasiUB()
        }
    }

    // ===== CEK SYARAT LEVEL BINTANG UNTUK DAFTAR UB =====
    // Syarat diambil dari system_config.min_bintang_kemitraan (dinamis, diatur admin)
    // Default fallback = 3 jika config belum dimuat
    private fun cekSyaratDanBukaRegistrasiUB() {
        val nasabah = nasabahData
        val config  = systemConfig

        // Jika data belum dimuat, load dulu
        if (nasabah == null || config == null) {
            lifecycleScope.launch {
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

                    // Setelah data dimuat, cek lagi
                    prosesKlikDaftarUB()

                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Gagal memuat data: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            prosesKlikDaftarUB()
        }
    }

    private fun prosesKlikDaftarUB() {
        val nasabah       = nasabahData ?: return
        val config        = systemConfig
        val levelSaatIni  = nasabah.levelBintang ?: 1

        // Ambil syarat minimum bintang dari config Supabase
        // Fallback ke 3 jika config belum ada (sesuai default DB)
        val minBintang = config?.minBintangKemitraan ?: 3

        if (levelSaatIni < minBintang) {
            // Belum memenuhi syarat — tampilkan pesan jelas
            val sisa = minBintang - levelSaatIni
            Toast.makeText(
                requireContext(),
                "⚠ Belum memenuhi syarat!\n\n" +
                        "Untuk mendaftar sebagai Unit Bisnis, Anda perlu minimal Bintang $minBintang.\n\n" +
                        "Level Anda saat ini: Bintang $levelSaatIni\n" +
                        "Kurang $sisa tingkat lagi.\n\n" +
                        "Terus setor minyak untuk naik level!",
                Toast.LENGTH_LONG
            ).show()
        } else {
            // Sudah memenuhi syarat — buka form registrasi UB
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, RegistrasiUnitBisnisFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun formatRupiah(nominal: Double): String =
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(nominal).replace(",00", "")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}