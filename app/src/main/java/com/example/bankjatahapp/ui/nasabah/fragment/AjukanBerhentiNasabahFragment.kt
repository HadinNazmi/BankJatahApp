package com.example.bankjatahapp.ui.nasabah.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.PengajuanBerhenti
import com.example.bankjatahapp.data.model.PengajuanBerhentiInsert
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentAjukanBerhentiNasabahBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import com.example.bankjatahapp.data.model.DompetUser

class AjukanBerhentiNasabahFragment : Fragment() {

    private var _binding: FragmentAjukanBerhentiNasabahBinding? = null
    private val binding get() = _binding!!

    private var pengajuanAktif: PengajuanBerhenti? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAjukanBerhentiNasabahBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        cekPengajuanAktif()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Cek apakah user sudah punya pengajuan aktif (menunggu/diproses).
     * Jika ada, tampilkan status pengajuan dan sembunyikan form input.
     */
    private fun cekPengajuanAktif() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val list = client.postgrest
                    .from("pengajuan_berhenti")
                    .select {
                        filter {
                            eq("id_user", idUser)
                        }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<PengajuanBerhenti>()

                val aktif = list.firstOrNull {
                    it.status == "menunggu" || it.status == "diproses"
                }

                showLoading(false)

                if (aktif != null) {
                    pengajuanAktif = aktif
                    tampilkanStatusAktif(aktif)
                } else {
                    tampilkanForm()
                }

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun tampilkanStatusAktif(pengajuan: PengajuanBerhenti) {
        binding.layoutForm.visibility = View.GONE
        binding.layoutStatusAktif.visibility = View.VISIBLE

        val statusLabel = when (pengajuan.status) {
            "menunggu" -> "Menunggu Diproses"
            "diproses" -> "Sedang Diproses Admin"
            "disetujui" -> "Disetujui"
            "ditolak" -> "Ditolak"
            else -> pengajuan.status
        }

        binding.tvStatusPengajuan.text = statusLabel
        binding.tvTglPengajuan.text = "Diajukan: ${pengajuan.createdAt?.take(10) ?: "-"}"
        binding.tvAlasanPengajuan.text = if (!pengajuan.alasan.isNullOrEmpty())
            "Alasan: ${pengajuan.alasan}" else "Alasan: -"
        binding.tvCatatanAdmin.text = if (!pengajuan.catatanAdmin.isNullOrEmpty())
            "Catatan Admin: ${pengajuan.catatanAdmin}" else ""
    }

    private fun tampilkanForm() {
        binding.layoutForm.visibility = View.VISIBLE
        binding.layoutStatusAktif.visibility = View.GONE
    }

    private fun setupClickListeners() {
        binding.btnKirimPengajuan.setOnClickListener {
            kirimPengajuan()
        }
    }

    private suspend fun validasiSaldoSebelumKirim(): String? {
        val idUser = client.auth.currentUserOrNull()?.id ?: return "Session tidak ditemukan"

        val dompet = try {
            client.postgrest
                .from("dompet_user")
                .select { filter { eq("id_dompet", idUser) } }
                .decodeSingle<DompetUser>()
        } catch (_: Exception) {
            return null // jika dompet tidak ditemukan, lewati validasi saldo
        }

        if (dompet.saldoNasabah < 0) {
            return "❌ Saldo tabungan Anda minus. Harap lunasi hutang terlebih dahulu sebelum mengajukan berhenti."
        }
        return null
    }
    private fun kirimPengajuan() {
        val alasan = binding.etAlasan.text.toString().trim()

        lifecycleScope.launch {
            showLoading(true)
            val errorMsg = validasiSaldoSebelumKirim()
            showLoading(false)
            if (errorMsg != null) {
                tampilkanDialogSyaratBelumTerpenuhi(errorMsg)
                return@launch
            }
            tampilkanDialogKonfirmasi(alasan)
        }
    }

    private fun tampilkanDialogSyaratBelumTerpenuhi(pesan: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Syarat Belum Terpenuhi")
            .setMessage(pesan)
            .setPositiveButton("OK", null)
            .show()
            .also { dialog ->
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(requireContext().getColor(com.example.bankjatahapp.R.color.orange_primary))
            }
    }

    private fun bersihkanPesanError(pesanAsli: String?): String {
        if (pesanAsli == null) return "Gagal mengirim pengajuan. Silakan coba lagi."

        // Cari pesan dari trigger PostgreSQL — formatnya: "message":"Gagal mengajukan: ..."
        val regexMessage = Regex(""""message"\s*:\s*"([^"]+)"""")
        val matchMessage = regexMessage.find(pesanAsli)
        if (matchMessage != null) {
            return matchMessage.groupValues[1].trim()
        }

        // Fallback: cari pola "Gagal mengajukan:" langsung
        val regexGagal = Regex("Gagal mengajukan:[^\"\\\\\\n]*")
        val matchGagal = regexGagal.find(pesanAsli)
        if (matchGagal != null) {
            return matchGagal.value.trim()
        }

        return "Gagal mengirim pengajuan. Pastikan semua transaksi sudah selesai dan coba lagi."
    }

    private fun tampilkanDialogKonfirmasi(alasan: String) {
        // Konfirmasi sebelum kirim
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Pengajuan")
            .setMessage(
                "Anda akan mengajukan penghentian sebagai Nasabah.\n\n" +
                        "Pastikan:\n" +
                        "• Tidak ada setoran yang sedang berjalan\n" +
                        "• Tidak ada penukaran reward yang aktif\n\n" +
                        "Admin akan memverifikasi dan memproses saldo Anda sebelum menyetujui pengajuan ini."
            )
            .setPositiveButton("Ya, Kirim") { _, _ ->
                prosesKirim(alasan)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun prosesKirim(alasan: String) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                binding.btnKirimPengajuan.isEnabled = false

                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // Double-check tidak ada pengajuan aktif
                val existing = client.postgrest
                    .from("pengajuan_berhenti")
                    .select {
                        filter {
                            eq("id_user", idUser)
                            or {
                                eq("status", "menunggu")
                                eq("status", "diproses")
                            }
                        }
                    }
                    .decodeList<PengajuanBerhenti>()

                if (existing.isNotEmpty()) {
                    showLoading(false)
                    binding.btnKirimPengajuan.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Anda sudah memiliki pengajuan aktif yang sedang diproses",
                        Toast.LENGTH_LONG
                    ).show()
                    cekPengajuanAktif()
                    return@launch
                }

                // Insert pengajuan
                val payload = PengajuanBerhentiInsert(
                    idUser = idUser,
                    tipe = "nasabah",
                    alasan = alasan.ifEmpty { null },
                    status = "menunggu"
                )

                client.postgrest
                    .from("pengajuan_berhenti")
                    .insert(payload)

                showLoading(false)

                Toast.makeText(
                    requireContext(),
                    "Pengajuan berhasil dikirim. Admin akan segera memprosesnya.",
                    Toast.LENGTH_LONG
                ).show()

                // Reload untuk tampilkan status
                cekPengajuanAktif()

            } catch (e: Exception) {
                showLoading(false)
                binding.btnKirimPengajuan.isEnabled = true
                tampilkanDialogSyaratBelumTerpenuhi(bersihkanPesanError(e.message))
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnKirimPengajuan.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}