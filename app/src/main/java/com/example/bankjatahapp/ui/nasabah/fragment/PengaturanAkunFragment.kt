package com.example.bankjatahapp.ui.nasabah.fragment

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.MasterBank
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.DialogEditFieldBinding
import com.example.bankjatahapp.databinding.FragmentPengaturanAkunBinding
import com.example.bankjatahapp.ui.component.AvatarUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PengaturanAkunFragment : Fragment() {

    private var _binding: FragmentPengaturanAkunBinding? = null
    private val binding get() = _binding!!

    private var idUser: String? = null
    private var cachedUser: User? = null
    private var cachedNasabah: NasabahData? = null
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

                // 1. Ambil data Tabel Users
                cachedUser = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser!!) } }
                    .decodeSingle<User>()

                // 2. Ambil data Tabel Nasabah
                cachedNasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser!!) } }
                    .decodeSingle<NasabahData>()

                // 3. Ambil Master Daftar Bank Komersial Aktif
                try {
                    listBank = client.postgrest.from("master_bank")
                        .select { filter { eq("status_bank", "aktif") } }
                        .decodeList<MasterBank>()

                    val currentBank = listBank.find { it.kodeBank == cachedNasabah?.bankCode }
                    binding.tvBankValue.text = currentBank?.namaBank ?: "Belum Memilih Bank"
                    bankDipilih = cachedNasabah?.bankCode
                } catch (e: Exception) {
                    binding.tvBankValue.text = "Gagal memuat opsi bank"
                }

                // Terapkan nilai ke UI komponen teks
                updateUiTexts()

                setFormLoading(false)
            } catch (e: Exception) {
                setFormLoading(false)
                Toast.makeText(requireContext(), "Gagal sinkronisasi data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUiTexts() {
        val user = cachedUser ?: return
        val nasabah = cachedNasabah ?: return

        // Pasang avatar awal grafis nama
        AvatarUtils.pasangKeImageView(binding.ivFotoProfil, user.namaLengkap, 300)

        // Teks Bagian Daftar yang dapat Diedit
        binding.tvNamaValue.text = user.namaLengkap
        binding.tvNoTelpValue.text = user.noTelp ?: "Belum diisi"
        binding.tvAlamatValue.text = nasabah.alamatRumah ?: "Belum diisi"
        binding.tvNoRekeningValue.text = nasabah.noRekening ?: "Belum diisi"
        binding.tvAtasNamaValue.text = nasabah.atasNamaRekening ?: "Belum diisi"

        // Teks Bagian Informasi Read Only Sistem
        binding.tvEmailValue.text = user.email
        binding.tvNikValue.text = nasabah.nik ?: "-"
        binding.tvKodeReferralValue.text = nasabah.kodeReferral ?: "-"
        binding.tvLevelBintangValue.text = "Bintang ${nasabah.levelBintang ?: 1}"
        binding.tvKategoriValue.text = (nasabah.kategoriNasabah ?: "pasif").replaceFirstChar { it.uppercase() }
        binding.tvStatusAkunValue.text = user.statusAkun.replaceFirstChar { it.uppercase() }

        val statusColor = when (user.statusAkun.lowercase()) {
            "aktif" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
            "dibekukan" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
            "menunggu_verifikasi" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
            else -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
        }
        binding.tvStatusAkunValue.setBackgroundColor(statusColor)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // Fitur menyalin kode referral langsung ke clipboard android
        binding.tvBagikanKeTeman.setOnClickListener {
            val textToCopy = binding.tvKodeReferralValue.text.toString()
            if (textToCopy != "-") {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("Kode Referral", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Kode referral disalin ke clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        binding.itemEditNama.setOnClickListener {
            tampilkanDialogEdit("Nama Lengkap", cachedUser?.namaLengkap ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) { nilaiBaru ->
                if (nilaiBaru.isEmpty()) return@tampilkanDialogEdit "Nama tidak boleh kosong"
                val payload = buildJsonObject { put("nama_lengkap", nilaiBaru) }
                updateKeSupabase("users", payload)
                cachedUser = cachedUser?.copy(namaLengkap = nilaiBaru)
                null
            }
        }

        binding.itemEditNoTelp.setOnClickListener {
            tampilkanDialogEdit("Nomor Telepon", cachedUser?.noTelp ?: "", InputType.TYPE_CLASS_PHONE) { nilaiBaru ->
                val payload = buildJsonObject {
                    put("no_telp", nilaiBaru.ifEmpty { null })
                }
                updateKeSupabase("users", payload)
                cachedUser = cachedUser?.copy(noTelp = nilaiBaru.ifEmpty { null })
                null
            }
        }

        binding.itemEditAlamat.setOnClickListener {
            tampilkanDialogEdit("Alamat Rumah", cachedNasabah?.alamatRumah ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) { nilaiBaru ->
                val payload = buildJsonObject { put("alamat_rumah", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("nasabah_data", payload)
                cachedNasabah = cachedNasabah?.copy(alamatRumah = nilaiBaru.ifEmpty { null })
                null
            }
        }

        binding.itemEditNoRekening.setOnClickListener {
            tampilkanDialogEdit("Nomor Rekening", cachedNasabah?.noRekening ?: "", InputType.TYPE_CLASS_NUMBER) { nilaiBaru ->
                val payload = buildJsonObject { put("no_rekening", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("nasabah_data", payload)
                cachedNasabah = cachedNasabah?.copy(noRekening = nilaiBaru.ifEmpty { null })
                null
            }
        }

        binding.itemEditAtasNama.setOnClickListener {
            tampilkanDialogEdit("Atas Nama Rekening", cachedNasabah?.atasNamaRekening ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) { nilaiBaru ->
                val payload = buildJsonObject { put("atas_nama_rekening", nilaiBaru.ifEmpty { null }) }
                updateKeSupabase("nasabah_data", payload)
                cachedNasabah = cachedNasabah?.copy(atasNamaRekening = nilaiBaru.ifEmpty { null })
                null
            }
        }

        binding.itemEditBank.setOnClickListener {
            tampilkanDialogEditBank()
        }
    }

    // ================= FUNGSI GENERIK DIALOG INPUT POP UP TEXT =================
    private fun tampilkanDialogEdit(
        judul: String,
        nilaiSekarang: String,
        jenisInput: Int,
        onSimpanDitekan: (String) -> String?
    ) {
        val dialogBinding = DialogEditFieldBinding.inflate(LayoutInflater.from(requireContext()))

        dialogBinding.tvDialogTitle.text = "Ubah $judul"
        dialogBinding.etDialogInput.setText(nilaiSekarang)
        dialogBinding.etDialogInput.inputType = jenisInput
        dialogBinding.etDialogInput.setSelection(dialogBinding.etDialogInput.text?.length ?: 0)

        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnDialogBatal.setOnClickListener { builder.dismiss() }

        dialogBinding.btnDialogSimpan.setOnClickListener {
            val textInput = dialogBinding.etDialogInput.text.toString().trim()

            // Eksekusi fungsi validasi lokal sebelum menyimpan
            val pesanError = onSimpanDitekan(textInput)
            if (pesanError != null) {
                dialogBinding.tilDialogInput.error = pesanError
            } else {
                builder.dismiss()
            }
        }
        builder.show()
    }

    // ================= FUNGSI KHUSUS DIALOG POP UP SPINNER SELEKSI BANK =================
    private fun tampilkanDialogEditBank() {
        val dialogBinding = DialogEditFieldBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.tvDialogTitle.text = "Pilih Bank Pencairan"

        // Sembunyikan Input Teks, Munculkan Opsi Spinner
        dialogBinding.tilDialogInput.visibility = View.GONE
        dialogBinding.spinnerDialogBank.visibility = View.VISIBLE

        val namaBankList = listBank.map { it.namaBank }
        val adapterBank = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, namaBankList)
        adapterBank.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerDialogBank.adapter = adapterBank

        val indexBank = listBank.indexOfFirst { it.kodeBank == bankDipilih }
        if (indexBank >= 0) dialogBinding.spinnerDialogBank.setSelection(indexBank)

        var bankTemp: String? = bankDipilih
        dialogBinding.spinnerDialogBank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                bankTemp = listBank[pos].kodeBank
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val builder = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
        dialogBinding.btnDialogBatal.setOnClickListener { builder.dismiss() }
        dialogBinding.btnDialogSimpan.setOnClickListener {
            bankDipilih = bankTemp
            val selectedBankName = listBank.find { it.kodeBank == bankDipilih }?.namaBank ?: "-"
            binding.tvBankValue.text = selectedBankName

            val payload = buildJsonObject { put("bank_code", bankDipilih) }
            updateKeSupabase("nasabah_data", payload)
            cachedNasabah = cachedNasabah?.copy(bankCode = bankDipilih)

            builder.dismiss()
        }
        builder.show()
    }

    // ================= LOGIK PROSES AKSES DATA KE SUPABASE BACKEND =================
    private fun updateKeSupabase(namaTabel: String, dataPayload: kotlinx.serialization.json.JsonObject) {
        setFormLoading(true)
        lifecycleScope.launch {
            try {
                val id = idUser ?: throw Exception("Sesi kedaluwarsa")
                val kolomKunci = if (namaTabel == "users") "id_user" else "id_nasabah"

                client.postgrest.from(namaTabel).update(dataPayload) {
                    filter { eq(kolomKunci, id) }
                }

                updateUiTexts()
                setFormLoading(false)
                Toast.makeText(requireContext(), "✓ Perubahan berhasil diterapkan secara instan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                setFormLoading(false)
                Toast.makeText(requireContext(), "Gagal sinkronisasi data: ${e.message}", Toast.LENGTH_LONG).show()
                loadData()
            }
        }
    }

    private fun setFormLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.scrollContent.visibility = if (loading) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}