package com.example.bankjatahapp.ui.nasabah.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.MasterBank
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentPengaturanAkunBinding
import com.example.bankjatahapp.ui.component.AvatarUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class PengaturanAkunFragment : Fragment() {

    private var _binding: FragmentPengaturanAkunBinding? = null
    private val binding get() = _binding!!
    private var idUser: String? = null

    // Variabel baru untuk Bank
    private var listBank: List<MasterBank> = emptyList()
    private var bankDipilih: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPengaturanAkunBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupClickListeners()
    }

    private fun loadData() {
        setFormLoading(true)
        lifecycleScope.launch {
            try {
                idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val user = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser!!) } }
                    .decodeSingle<User>()

                val nasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser!!) } }
                    .decodeSingle<NasabahData>()

                // ===== LOAD MASTER BANK =====
                try {
                    listBank = client.postgrest.from("master_bank")
                        .select { filter { eq("status_bank", "aktif") } }
                        .decodeList<MasterBank>()

                    val namaBank = listBank.map { it.namaBank }
                    val adapterBank = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, namaBank)
                    adapterBank.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerBank.adapter = adapterBank

                    // Set pilihan sesuai data existing
                    val indexBank = listBank.indexOfFirst { it.kodeBank == nasabah.bankCode }
                    if (indexBank >= 0) {
                        binding.spinnerBank.setSelection(indexBank)
                        bankDipilih = nasabah.bankCode
                    }

                    binding.spinnerBank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                            bankDipilih = listBank[pos].kodeBank
                        }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal memuat daftar bank", Toast.LENGTH_SHORT).show()
                }

                // ===== SET DATA REKENING =====
                binding.etNoRekening.setText(nasabah.noRekening ?: "")
                binding.etAtasNama.setText(nasabah.atasNamaRekening ?: "")

                // ===== AVATAR INISIAL =====
                AvatarUtils.pasangKeImageView(binding.ivFotoProfil, user.namaLengkap, 300)

                // Field yang bisa diubah
                binding.etNama.setText(user.namaLengkap)
                binding.etNoTelp.setText(user.noTelp ?: "")
                binding.etAlamat.setText(nasabah.alamatRumah ?: "")

                // Kode referral sponsor
                if (!nasabah.idSponsor.isNullOrEmpty()) {
                    try {
                        val sponsor = client.postgrest
                            .from("nasabah_data")
                            .select { filter { eq("id_nasabah", nasabah.idSponsor!!) } }
                            .decodeSingle<NasabahData>()
                        binding.etKodeReferralSponsor.setText(sponsor.kodeReferral ?: "")
                    } catch (_: Exception) {
                        binding.etKodeReferralSponsor.setText("")
                    }
                }

                // Field read-only
                binding.tvEmailValue.text        = user.email
                binding.tvNikValue.text          = nasabah.nik ?: "-"
                binding.tvKodeReferralValue.text = nasabah.kodeReferral ?: "-"
                binding.tvLevelBintangValue.text = "Bintang ${nasabah.levelBintang ?: 1}"
                binding.tvKategoriValue.text     = (nasabah.kategoriNasabah ?: "pasif")
                    .replaceFirstChar { it.uppercase() }
                binding.tvStatusAkunValue.text   = user.statusAkun.replaceFirstChar { it.uppercase() }
                binding.tvRoleValue.text         = "Nasabah"

                val statusColor = when (user.statusAkun) {
                    "aktif"               -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
                    "dibekukan"           -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
                    "menunggu_verifikasi" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
                    else                  -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
                }
                binding.tvStatusAkunValue.setBackgroundColor(statusColor)

                setFormLoading(false)

            } catch (e: Exception) {
                setFormLoading(false)
                Toast.makeText(requireContext(), "Gagal memuat: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnSimpan.setOnClickListener {
            simpanPerubahan()
        }
        binding.tvBagikanKeTeman.setOnClickListener {
            val kodeReferral = binding.tvKodeReferralValue.text.toString()
            if (kodeReferral == "-" || kodeReferral.isEmpty()) {
                Toast.makeText(requireContext(), "Kode referral belum tersedia", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            bagikanKodeReferral(kodeReferral)
        }
    }

    private fun bagikanKodeReferral(kode: String) {
        val link = "bankjatah://register/ref?kode=$kode"
        val pesan = """
            🎉 Hei! Aku mengundang kamu bergabung di aplikasi Bank Jatah!
            
            Gunakan kode referral aku: *$kode*
            
            Daftar sekarang lewat link ini:
            $link
            
            Atau buka aplikasi Bank Jatah dan masukkan kode referral saat daftar.
        """.trimIndent()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, pesan)
        }
        startActivity(Intent.createChooser(intent, "Bagikan kode referral via"))
    }

    private fun simpanPerubahan() {
        val nama           = binding.etNama.text.toString().trim()
        val noTelp         = binding.etNoTelp.text.toString().trim()
        val alamat         = binding.etAlamat.text.toString().trim()
        val kodeRefSponsor = binding.etKodeReferralSponsor.text.toString().trim()

        // Data Rekening
        val noRekening     = binding.etNoRekening.text.toString().trim()
        val atasNama       = binding.etAtasNama.text.toString().trim()

        if (nama.isEmpty()) {
            binding.tilNama.error = "Nama tidak boleh kosong"
            return
        }
        binding.tilNama.error = null

        setLoading(true)

        lifecycleScope.launch {
            try {
                val id = idUser ?: throw Exception("Session tidak ditemukan")

                var idSponsorBaru: String? = null
                if (kodeRefSponsor.isNotEmpty()) {
                    try {
                        val sponsorData = client.postgrest
                            .from("nasabah_data")
                            .select { filter { eq("kode_referral", kodeRefSponsor) } }
                            .decodeSingle<NasabahData>()
                        idSponsorBaru = sponsorData.idNasabah
                    } catch (_: Exception) {
                        setLoading(false)
                        Toast.makeText(requireContext(), "Kode referral sponsor tidak ditemukan", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                // Update tabel users
                client.postgrest.from("users").update(
                    mapOf(
                        "nama_lengkap" to nama,
                        "no_telp"      to noTelp.ifEmpty { null }
                    )
                ) { filter { eq("id_user", id) } }

                // Update tabel nasabah_data (Termasuk Rekening Bank)
                client.postgrest.from("nasabah_data").update(
                    mapOf(
                        "alamat_rumah"       to alamat.ifEmpty { null },
                        "id_sponsor"         to idSponsorBaru,
                        "bank_code"          to bankDipilih,
                        "no_rekening"        to noRekening.ifEmpty { null },
                        "atas_nama_rekening" to atasNama.ifEmpty { null }
                    )
                ) { filter { eq("id_nasabah", id) } }

                AvatarUtils.pasangKeImageView(binding.ivFotoProfil, nama, 300)

                setLoading(false)
                Toast.makeText(requireContext(), "✓ Profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSimpan.isEnabled = !loading
        binding.btnSimpan.text = if (loading) "Menyimpan..." else "Simpan Perubahan"
    }

    private fun setFormLoading(loading: Boolean) {
        binding.progressBar.visibility   = if (loading) View.VISIBLE else View.GONE
        binding.scrollContent.visibility = if (loading) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}