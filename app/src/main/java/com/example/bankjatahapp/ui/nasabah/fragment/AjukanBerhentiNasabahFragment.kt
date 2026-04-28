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

    private fun kirimPengajuan() {
        val alasan = binding.etAlasan.text.toString().trim()

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
                Toast.makeText(
                    requireContext(),
                    "Gagal mengirim pengajuan: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
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