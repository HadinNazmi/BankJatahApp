package com.example.bankjatahapp.ui.nasabah.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bankjatahapp.data.model.DownlineItem
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentListAfiliasiBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import java.util.Locale
import android.content.Intent

class ListAfiliasiFragment : Fragment() {

    private var _binding: FragmentListAfiliasiBinding? = null
    private val binding get() = _binding!!

    private var sudahAdaSponsor = false
    private var idUser: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListAfiliasiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvAfiliasi.layoutManager = LinearLayoutManager(requireContext())
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        loadAfiliasi()
        setupReferral()
    }

    private fun loadAfiliasi() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvAfiliasi.visibility = View.GONE

                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val hasil = client.postgrest.from("nasabah_data").select(columns = Columns.raw("""
                    id_nasabah, level_bintang, kategori_nasabah, total_setoran_lifetime, created_at,
                    users!id_nasabah (nama_lengkap, url_foto_profil),
                    downlines:nasabah_data!id_sponsor (
                        id_nasabah, level_bintang, kategori_nasabah, total_setoran_lifetime, created_at,
                        users!id_nasabah (nama_lengkap, url_foto_profil),
                        downlines:nasabah_data!id_sponsor (
                            id_nasabah, level_bintang, kategori_nasabah, total_setoran_lifetime, created_at,
                            users!id_nasabah (nama_lengkap, url_foto_profil)
                        )
                    )
                """.trimIndent())) {
                    filter { eq("id_sponsor", idUser) }
                }.decodeList<DownlineItem>()

                val totalG1 = hasil.size
                val totalG2 = hasil.sumOf { it.downlines?.size ?: 0 }
                val totalG3 = hasil.sumOf { g1 -> g1.downlines?.sumOf { it.downlines?.size ?: 0 } ?: 0 }
                val totalSemua = totalG1 + totalG2 + totalG3

                val totalOmset = hasil.sumOf { g1 ->
                    (g1.totalSetoranLifetime ?: 0.0) +
                            (g1.downlines?.sumOf { g2 ->
                                (g2.totalSetoranLifetime ?: 0.0) +
                                        (g2.downlines?.sumOf { it.totalSetoranLifetime ?: 0.0 } ?: 0.0)
                            } ?: 0.0)
                }

                // Update UI
                binding.tvJumlah.text = "$totalSemua afiliasi"
                binding.tvTotalJaringan.text = totalSemua.toString()
                binding.tvTotalOmset.text = "${String.format(Locale.US, "%,.1f", totalOmset)} Kg"
                binding.tvJumlahG1.text = "G1: $totalG1"
                binding.tvJumlahG2.text = "G2: $totalG2"
                binding.tvJumlahG3.text = "G3: $totalG3"

                binding.progressBar.visibility = View.GONE
                if (hasil.isEmpty()) {
                    binding.tvKosong.visibility = View.VISIBLE
                } else {
                    binding.rvAfiliasi.visibility = View.VISIBLE
                    binding.rvAfiliasi.adapter = DownlineTreeAdapter(hasil)
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupReferral() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uid = client.auth.currentUserOrNull()?.id ?: return@launch
                idUser = uid

                val nasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", uid) } }
                    .decodeSingle<NasabahData>()

                // Tampilkan kode referral saya
                binding.tvKodeReferralSaya.text = nasabah.kodeReferral ?: "-"

                // Cek apakah sudah ada sponsor
                sudahAdaSponsor = !nasabah.idSponsor.isNullOrEmpty()
                if (sudahAdaSponsor) {
                    binding.etKodeReferralSponsor.isEnabled = false
                    binding.tilKodeReferralSponsor.isEnabled = false
                    binding.tilKodeReferralSponsor.alpha = 0.6f
                    binding.btnSimpanSponsor.isEnabled = false
                    binding.btnSimpanSponsor.alpha = 0.5f
                    // Tampilkan kode sponsor yang sudah ada
                    try {
                        val sponsor = client.postgrest
                            .from("nasabah_data")
                            .select { filter { eq("id_nasabah", nasabah.idSponsor!!) } }
                            .decodeSingle<NasabahData>()
                        binding.etKodeReferralSponsor.setText(sponsor.kodeReferral ?: "")
                    } catch (_: Exception) {}
                    binding.tilKodeReferralSponsor.helperText = "✓ Sponsor sudah terdaftar, tidak dapat diubah"
                }

            } catch (_: Exception) {}
        }

        binding.tvBagikanKeTeman.setOnClickListener {
            val kode = binding.tvKodeReferralSaya.text.toString()
            if (kode == "-" || kode.isEmpty()) {
                Toast.makeText(requireContext(), "Kode referral belum tersedia", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pesan = """
        🎉 Hei! Saya mengundang kamu bergabung di *Bank Jatah Indonesia*!
        
        Bank Jatah adalah platform pengelolaan minyak jelantah yang menguntungkan. Setor minyak jelantahmu dan dapatkan saldo, poin reward, serta komisi afiliasi!
        
        Gunakan kode referral saya saat mendaftar:
        ✨ *$kode* ✨
        
        Belum punya aplikasinya? Download sekarang di:
        🌐 https://www.bjindonesia.online/aplikasi
        
        Daftar sekarang dan mulai hasilkan dari minyak jelantahmu! 💰
    """.trimIndent()

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, pesan)
            }
            startActivity(Intent.createChooser(intent, "Bagikan via"))
        }

        binding.btnSimpanSponsor.setOnClickListener {
            if (sudahAdaSponsor) return@setOnClickListener
            val kode = binding.etKodeReferralSponsor.text.toString().trim()
            if (kode.isEmpty()) {
                binding.tilKodeReferralSponsor.error = "Masukkan kode sponsor"
                return@setOnClickListener
            }
            simpanSponsor(kode)
        }
    }

    private fun simpanSponsor(kodeRefSponsor: String) {
        binding.btnSimpanSponsor.isEnabled = false
        binding.btnSimpanSponsor.text = "Menyimpan..."
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uid = idUser ?: return@launch
                val sponsorData = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("kode_referral", kodeRefSponsor) } }
                    .decodeSingle<NasabahData>()

                client.postgrest.from("nasabah_data").update(
                    mapOf("id_sponsor" to sponsorData.idNasabah)
                ) { filter { eq("id_nasabah", uid) } }

                sudahAdaSponsor = true
                binding.etKodeReferralSponsor.isEnabled = false
                binding.tilKodeReferralSponsor.isEnabled = false
                binding.tilKodeReferralSponsor.alpha = 0.6f
                binding.tilKodeReferralSponsor.helperText = "✓ Sponsor berhasil disimpan"
                binding.btnSimpanSponsor.text = "Tersimpan"

                Toast.makeText(requireContext(), "✓ Sponsor berhasil ditambahkan!", Toast.LENGTH_SHORT).show()

            } catch (_: Exception) {
                binding.btnSimpanSponsor.isEnabled = true
                binding.btnSimpanSponsor.text = "Simpan"
                binding.tilKodeReferralSponsor.error = "Kode sponsor tidak ditemukan"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}