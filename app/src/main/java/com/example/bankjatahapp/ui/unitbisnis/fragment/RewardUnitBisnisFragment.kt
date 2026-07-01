package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.ProdukReward
import com.example.bankjatahapp.data.model.RedeemReward
import com.example.bankjatahapp.data.model.SystemConfig
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.DialogQrRedeemBinding
import com.example.bankjatahapp.databinding.FragmentRewardUnitBisnisBinding
import com.example.bankjatahapp.ui.nasabah.adapter.ProdukRewardAdapter
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.bankjatahapp.ui.unitbisnis.UnitBisnisActivity

class RewardUnitBisnisFragment : Fragment() {

    private var _binding: FragmentRewardUnitBisnisBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProdukRewardAdapter
    private var poinSaatIni: Int = 0
    private var nasabahData: NasabahData? = null

    // Variabel Config System
    private var minBintangRedeem: Int = 0
    private var minKgPribadiRedeem: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRewardUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadData()
        cekPengajuanBerhentiAktif()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun cekPengajuanBerhentiAktif() {
        viewLifecycleOwner.lifecycleScope.launch {
            val adaPengajuan = (activity as? UnitBisnisActivity)?.cekAdaPengajuanAktif() ?: false
            if (adaPengajuan && _binding != null) {
                Toast.makeText(
                    requireContext(),
                    "⚠️ Penukaran reward dinonaktifkan karena Anda memiliki pengajuan penutupan akun aktif.",
                    Toast.LENGTH_LONG
                ).show()
                adapter.setSemuaDisabled(true)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ProdukRewardAdapter(emptyList()) { produk ->
            onTukarKlik(produk)
        }
        binding.rvProdukReward.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter       = this@RewardUnitBisnisFragment.adapter
        }
    }

    private fun loadData() {
        binding.progressBar.visibility    = View.VISIBLE
        binding.layoutEmpty.visibility    = View.GONE
        binding.rvProdukReward.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // 1. Ambil Config System
                val config = client.postgrest
                    .from("system_config")
                    .select { filter { eq("id_config", 1) } }
                    .decodeSingle<SystemConfig>()

                minBintangRedeem = config.minBintangRedeem
                minKgPribadiRedeem = config.minKgPribadiRedeem

                // Update UI info syarat
                binding.tvSyaratRedeem.text =
                    "Syarat: Min. Bintang $minBintangRedeem • Min. ${minKgPribadiRedeem.toInt()} Kg setor"

                // 2. Ambil poin dari dompet_user
                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .data
                poinSaatIni = extractPoin(dompet)
                binding.tvTotalPoin.text = formatAngka(poinSaatIni)

                // 3. Ambil data profil (level & lifetime)
                try {
                    nasabahData = client.postgrest
                        .from("nasabah_data")
                        .select { filter { eq("id_nasabah", idUser) } }
                        .decodeSingle<NasabahData>()
                } catch (_: Exception) { }

                // 4. Ambil produk reward aktif
                val produkList = client.postgrest
                    .from("produk_reward")
                    .select { filter { eq("status_produk", "aktif") } }
                    .decodeList<ProdukReward>()

                binding.progressBar.visibility = View.GONE

                if (produkList.isEmpty()) {
                    binding.layoutEmpty.visibility    = View.VISIBLE
                    binding.rvProdukReward.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility    = View.GONE
                    binding.rvProdukReward.visibility = View.VISIBLE
                    adapter.updateData(produkList)
                    val tersedia = produkList.count { it.stok > 0 }
                    binding.tvRewardTersedia.text = tersedia.toString()
                    binding.tvJumlahProduk.text   = "${produkList.size} produk"
                }

            } catch (e: Exception) {
                binding.progressBar.visibility    = View.GONE
                binding.layoutEmpty.visibility    = View.VISIBLE
                binding.rvProdukReward.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onTukarKlik(produk: ProdukReward) {
        if (produk.stok <= 0) {
            Toast.makeText(requireContext(), "Stok produk habis", Toast.LENGTH_SHORT).show()
            return
        }

        val nasabah = nasabahData
        val levelSaatIni = nasabah?.levelBintang ?: 1
        val kgPribadi    = nasabah?.totalSetoranLifetime ?: 0.0

        // 1. Cek syarat level bintang
        if (levelSaatIni < minBintangRedeem) {
            AlertDialog.Builder(requireContext())
                .setTitle("Syarat Tidak Terpenuhi")
                .setMessage(
                    "Penukaran reward membutuhkan minimal Bintang $minBintangRedeem.\n\n" +
                            "Level kamu saat ini: Bintang $levelSaatIni\n" +
                            "Terus setor minyak untuk naik level!"
                )
                .setPositiveButton("OK", null)
                .show()
                .also {
                    it.getButton(AlertDialog.BUTTON_POSITIVE)
                        ?.setTextColor(requireContext().getColor(R.color.orange_primary))
                }
            return
        }

        // 2. Cek syarat kg pribadi
        if (kgPribadi < minKgPribadiRedeem) {
            AlertDialog.Builder(requireContext())
                .setTitle("Syarat Tidak Terpenuhi")
                .setMessage(
                    "Penukaran reward membutuhkan minimal ${minKgPribadiRedeem.toInt()} Kg setor pribadi.\n\n" +
                            "Total setor kamu: ${kgPribadi} Kg\n" +
                            "Kekurangan: ${(minKgPribadiRedeem - kgPribadi)} Kg lagi"
                )
                .setPositiveButton("OK", null)
                .show()
                .also {
                    it.getButton(AlertDialog.BUTTON_POSITIVE)
                        ?.setTextColor(requireContext().getColor(R.color.orange_primary))
                }
            return
        }

        // 3. Cek poin cukup
        if (poinSaatIni < produk.poinDibutuhkan) {
            AlertDialog.Builder(requireContext())
                .setTitle("Poin Tidak Cukup")
                .setMessage(
                    "Poin kamu: ${formatAngka(poinSaatIni)}\n" +
                            "Dibutuhkan: ${formatAngka(produk.poinDibutuhkan)}\n\n" +
                            "Kekurangan: ${formatAngka(produk.poinDibutuhkan - poinSaatIni)} poin lagi"
                )
                .setPositiveButton("OK", null)
                .show()
                .also {
                    it.getButton(AlertDialog.BUTTON_POSITIVE)
                        ?.setTextColor(requireContext().getColor(R.color.orange_primary))
                }
            return
        }

        // 4. Konfirmasi Tukar
        AlertDialog.Builder(requireContext())
            .setTitle("Tukar Poin")
            .setMessage(
                "Tukar ${formatAngka(produk.poinDibutuhkan)} poin untuk mendapatkan\n" +
                        "\"${produk.namaProduk}\"?\n\n" +
                        "Poin kamu saat ini: ${formatAngka(poinSaatIni)}"
            )
            .setPositiveButton("Ya, Tukar") { _, _ ->
                ajukanRedeem(produk)
            }
            .setNegativeButton("Batal", null)
            .show()
            .also { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(requireContext().getColor(R.color.orange_primary))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    ?.setTextColor(requireContext().getColor(R.color.gray_text))
            }
    }

    private fun ajukanRedeem(produk: ProdukReward) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session tidak ditemukan")

                val payload = buildJsonObject {
                    put("id_nasabah",    idUser)
                    put("id_produk",     produk.idProduk)
                    put("poin_dipakai",  produk.poinDibutuhkan)
                    put("status_redeem", "menunggu")
                }

                val result = client.postgrest
                    .from("redeem_reward")
                    .insert(payload) { select() }
                    .decodeSingle<RedeemReward>()

                poinSaatIni -= produk.poinDibutuhkan
                binding.tvTotalPoin.text = formatAngka(poinSaatIni)

                tampilkanQrRedeem(result, produk)

            } catch (e: Exception) {
                val pesan = when {
                    e.message?.contains("Bintang") == true            -> e.message!!
                    e.message?.contains("setoran pribadi") == true    -> e.message!!
                    e.message?.contains("Poin tidak mencukupi") == true -> e.message!!
                    else -> "Gagal menukar poin: ${e.message}"
                }
                Toast.makeText(requireContext(), pesan, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun tampilkanQrRedeem(redeem: RedeemReward, produk: ProdukReward) {
        val dialogBinding = DialogQrRedeemBinding.inflate(layoutInflater)
        val qrBitmap = generateQrBitmap(redeem.idRedeem, 600)
        dialogBinding.ivQrCode.setImageBitmap(qrBitmap)

        dialogBinding.tvNamaProduk.text   = produk.namaProduk
        dialogBinding.tvPoinDipakai.text  = "${formatAngka(produk.poinDibutuhkan)} poin"
        dialogBinding.tvKodeRedeem.text   = "ID: ${redeem.idRedeem.take(8).uppercase()}..."
        dialogBinding.tvStatusRedeem.text = "Status: Menunggu Verifikasi"
        dialogBinding.tvInfoQr.text       = "Tunjukkan QR ini kepada petugas untuk menukarkan reward Anda."

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnSimpanQr.setOnClickListener {
            simpanQrKeGaleri(qrBitmap, redeem.idRedeem)
        }

        dialogBinding.btnTutup.setOnClickListener {
            dialog.dismiss()
            loadData()
        }

        dialog.show()
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun simpanQrKeGaleri(bitmap: Bitmap, idRedeem: String) {
        try {
            val namaFile = "QR_Redeem_${idRedeem.take(8).uppercase()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, namaFile)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BankJatah")
                }
                val uri = requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BankJatah").apply { mkdirs() }
                val file = java.io.File(dir, namaFile)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            Toast.makeText(requireContext(), "✓ QR disimpan ke Galeri / BankJatah", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractPoin(jsonStr: String): Int {
        return try {
            """"poin_reward"\s*:\s*(\d+)""".toRegex().find(jsonStr)?.groupValues?.get(1)?.toInt() ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun formatAngka(angka: Int): String =
        String.format("%,d", angka).replace(',', '.')

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}