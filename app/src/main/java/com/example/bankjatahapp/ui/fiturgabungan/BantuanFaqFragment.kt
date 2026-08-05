package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.bankjatahapp.R
import com.example.bankjatahapp.databinding.FragmentBantuanFaqBinding

class BantuanFaqFragment : Fragment() {

    private var _binding: FragmentBantuanFaqBinding? = null
    private val binding get() = _binding!!

    // Data FAQ per kategori
    private val faqSetoran = listOf(
        "Bagaimana cara menerima setoran minyak dari nasabah?" to
                "Buka menu Setor Minyak di halaman utama. Scan QR nasabah atau masukkan ID nasabah secara manual. Masukkan berat bersih minyak yang diterima, pilih mode pengiriman (antar sendiri atau UB jemput), lalu konfirmasi. Setoran akan masuk dengan status 'Menunggu Validasi' hingga diverifikasi oleh admin.",

        "Apa itu mode 'UB Jemput'?" to
                "Mode UB Jemput berarti Anda yang mendatangi nasabah untuk mengambil minyak jelantah. Nasabah akan dikenakan potongan biaya jemput per kg, dan Anda mendapatkan tambahan pendapatan dari biaya jemput tersebut.",

        "Berapa lama proses validasi setoran?" to
                "Validasi setoran dilakukan oleh admin dan biasanya memakan waktu 1–2 jam kerja. Setelah divalidasi, saldo nasabah dan komisi UB akan otomatis bertambah.",

        "Mengapa setoran saya ditolak?" to
                "Setoran bisa ditolak jika data berat tidak sesuai, bukti foto tidak jelas, atau nasabah tidak terdaftar aktif. Hubungi admin untuk informasi lebih lanjut.",

        "Apakah saya perlu upload foto bukti setoran?" to
                "Foto bukti bersifat opsional namun sangat disarankan untuk mempermudah proses verifikasi oleh admin dan menghindari sengketa."
    )

    private val faqPencairan = listOf(
        "Bagaimana cara mencairkan saldo komisi saya?" to
                "Buka menu Request Penarikan di halaman utama. Pilih jenis saldo yang ingin ditarik (Tabungan, Komisi, atau Bonus Afiliasi), masukkan nominal, dan konfirmasi. Pastikan rekening bank sudah diatur di Pengaturan Akun.",

        "Mengapa saldo saya tidak bisa ditarik?" to
                "Ada beberapa syarat penarikan: saldo tabungan harus mencapai batas minimum yang ditentukan, level bintang harus memenuhi syarat untuk penarikan komisi dan afiliasi, dan saldo tidak boleh habis (harus menyisakan saldo minimum).",

        "Rekening bank saya belum terdaftar, bagaimana caranya?" to
                "Buka menu Profil → Pengaturan Akun. Scroll ke bagian 'Bank', pilih bank tujuan dari dropdown, isi nomor rekening dan nama pemilik rekening, lalu simpan. Setelah itu Anda bisa mengajukan penarikan.",

        "Berapa lama proses transfer setelah pengajuan?" to
                "Proses transfer membutuhkan waktu 1×24 jam kerja setelah pengajuan disetujui admin. Status penarikan dapat dipantau di menu Riwayat.",

        "Apakah ada biaya admin untuk penarikan?" to
                "Biaya admin pencairan dapat berubah sesuai kebijakan sistem. Anda bisa melihat detail biaya admin saat mengisi nominal penarikan di bagian Ringkasan sebelum konfirmasi.",

        "Apa perbedaan saldo Tabungan, Komisi, dan Bonus Afiliasi?" to
                "Saldo Tabungan berasal dari hasil setor minyak sebagai nasabah. Saldo Komisi berasal dari komisi penerimaan setoran nasabah yang Anda tangani sebagai UB. Saldo Bonus Afiliasi berasal dari jaringan referral dan kemitraan."
    )

    private val faqReward = listOf(
        "Bagaimana cara menukar poin reward?" to
                "Buka menu Reward di halaman utama. Pilih produk reward yang tersedia, pastikan poin Anda mencukupi, lalu ajukan penukaran. Tunjukkan QR redeem yang dihasilkan kepada petugas untuk menukarkan reward Anda.",

        "Dari mana poin reward didapat?" to
                "Poin reward didapatkan dari setiap setoran minyak yang berhasil divalidasi. Jumlah poin dihitung berdasarkan berat minyak yang disetor.",

        "Apakah ada syarat level bintang untuk redeem reward?" to
                "Ya, penukaran reward hanya tersedia untuk nasabah atau UB yang telah mencapai level bintang tertentu sesuai kebijakan sistem.",

        "Bagaimana jika QR redeem saya kadaluarsa?" to
                "QR redeem berlaku selama status redeem masih aktif. Jika redeem ditolak atau kadaluarsa, hubungi admin untuk proses lebih lanjut."
    )

    private val faqAfiliasi = listOf(
        "Bagaimana cara mendapatkan bonus afiliasi?" to
                "Bagikan kode referral Anda kepada orang lain. Ketika mereka mendaftar menggunakan kode referral Anda dan melakukan setoran minyak, Anda akan mendapatkan bonus afiliasi secara otomatis.",

        "Di mana saya bisa menemukan kode referral saya?" to
                "Kode referral Anda bisa ditemukan di Profil → Pengaturan Akun, pada bagian Info Akun di bawah 'Kode Referral Saya'. Anda juga bisa menekan tombol 'Bagikan ke Teman' untuk langsung share via aplikasi pesan.",

        "Berapa bonus yang saya dapatkan per referral?" to
                "Besaran bonus afiliasi per generasi ditentukan oleh sistem dan dapat berubah sesuai kebijakan. Bonus dihitung berdasarkan berat setoran minyak dari anggota jaringan Anda.",

        "Berapa level jaringan afiliasi yang didukung?" to
                "Sistem mendukung multi-generasi afiliasi. Anda mendapatkan bonus dari setoran nasabah yang Anda ajak (generasi 1), dan bisa berlanjut ke generasi berikutnya tergantung kebijakan sistem.",

        "Syarat apa yang diperlukan untuk menarik bonus afiliasi?" to
                "Anda harus memiliki level bintang minimal sesuai syarat yang berlaku (biasanya Bintang 3 ke atas) dan saldo bonus afiliasi harus lebih dari 0."
    )

    private val faqUnitBisnis = listOf(
        "Apakah ada biaya untuk mendaftar sebagai Unit Bisnis?" to
                "Ya, pendaftaran Unit Bisnis memerlukan uang deposit yang digunakan sebagai jaminan dan untuk mendapatkan perlengkapan operasional seperti timbangan dan jerigen. Besaran deposit sesuai kebijakan yang berlaku.",

        "Apa saja yang didapatkan setelah mendaftar sebagai UB?" to
                "Setelah mendaftar dan deposit dikonfirmasi, Anda akan mendapatkan perlengkapan operasional (timbangan dan jerigen), akses ke fitur UB di aplikasi, dan bisa mulai menerima setoran minyak dari nasabah di wilayah Anda.",

        "Bagaimana cara mengatur jam dan hari operasional?" to
                "Buka Profil → Pengaturan Akun → bagian 'Ubah Data Unit Bisnis'. Isi jam buka, jam tutup, dan hari operasional, lalu simpan. Informasi ini akan ditampilkan kepada nasabah yang mencari UB terdekat.",

        "Kenapa status verifikasi saya masih 'Menunggu'?" to
                "Proses verifikasi Unit Bisnis dilakukan oleh admin. Pastikan semua data profil sudah lengkap termasuk NIK, wilayah, dan foto lokasi. Hubungi admin jika proses verifikasi memakan waktu lebih dari 3 hari kerja.",

        "Apakah saya bisa beroperasi sebelum diverifikasi?" to
                "Akun UB yang belum diverifikasi memiliki akses terbatas. Beberapa fitur seperti menerima setoran bisa tetap digunakan, namun pencairan komisi baru bisa dilakukan setelah verifikasi selesai."
    )

    private val faqAkun = listOf(
        "Bagaimana cara mengubah data profil saya?" to
                "Buka Profil → Pengaturan Akun. Anda bisa mengubah nama lengkap, nomor telepon, alamat rumah, data rekening bank, dan kode referral sponsor. Data seperti NIK, email, dan kode referral hanya bisa diubah melalui admin.",

        "Bagaimana cara mengubah rekening bank untuk pencairan?" to
                "Buka Profil → Pengaturan Akun → bagian 'Bank'. Pilih bank dari dropdown, isi nomor rekening dan nama pemilik rekening sesuai buku tabungan, lalu simpan perubahan.",

        "Saya lupa password, bagaimana cara reset?" to
                "Di halaman login, tekan 'Lupa Password'. Masukkan email yang terdaftar dan ikuti instruksi yang dikirimkan ke email Anda untuk mereset password.",

        "Apakah kode referral sponsor bisa diubah setelah diisi?" to
                "Tidak. Kode referral sponsor hanya bisa diisi satu kali. Setelah sponsor terdaftar, field tersebut akan terkunci dan tidak bisa diubah. Jika ada kesalahan, hubungi admin.",

        "Bagaimana cara melihat level bintang saya?" to
                "Level bintang Anda tertera di halaman Home dan juga di Profil → Pengaturan Akun pada bagian Info Akun. Level bintang naik seiring dengan total setoran minyak yang Anda lakukan."
    )

    private val faqBerhenti = listOf(
        "Bagaimana cara mengajukan berhenti sebagai UB?" to
                "Buka Profil → pilih menu 'Ajukan Berhenti'. Isi formulir pengajuan dengan alasan yang jelas dan konfirmasi. Pengajuan akan diproses oleh admin dalam beberapa hari kerja.",

        "Apakah saldo saya dikembalikan jika berhenti?" to
                "Saldo yang masih tersisa akan diproses sesuai kebijakan penonaktifan akun. Pastikan Anda sudah menarik semua saldo sebelum mengajukan berhenti untuk menghindari kendala administratif.",

        "Apakah deposit awal dikembalikan jika saya berhenti?" to
                "Pengembalian deposit bergantung pada syarat dan ketentuan yang berlaku saat pendaftaran dan kondisi peralatan yang dipinjamkan. Detail akan diinformasikan saat proses pengajuan berhenti.",

        "Berapa lama proses penonaktifan akun?" to
                "Proses penonaktifan akun UB biasanya memerlukan 3–7 hari kerja setelah pengajuan disetujui. Selama proses ini akun masih bisa diakses untuk keperluan administrasi.",

        "Apakah saya bisa mendaftar lagi setelah berhenti?" to
                "Pendaftaran ulang setelah berhenti dimungkinkan dengan mengikuti proses pendaftaran dari awal termasuk membayar deposit kembali. Hubungi admin untuk informasi lebih lanjut."
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBantuanFaqBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        buildAllFaq()
        setupKontakWhatsapp()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupKontakWhatsapp() {
        binding.btnHubungiWa.setOnClickListener {
            val nomorAdmin = "6282283884373"
            val pesan = "Halo Admin Bank Jatah, saya ingin bertanya mengenai layanan dan fitur aplikasi. Mohon bantuannya."
            val url = "https://wa.me/$nomorAdmin?text=${Uri.encode(pesan)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun buildAllFaq() {
        buildFaqGroup(binding.containerSetoran,    faqSetoran)
        buildFaqGroup(binding.containerPencairan,  faqPencairan)
        buildFaqGroup(binding.containerReward,     faqReward)
        buildFaqGroup(binding.containerAfiliasi,   faqAfiliasi)
        buildFaqGroup(binding.containerUnitBisnis, faqUnitBisnis)
        buildFaqGroup(binding.containerAkun,       faqAkun)
        buildFaqGroup(binding.containerBerhenti,   faqBerhenti)
    }

    private fun buildFaqGroup(container: LinearLayout, faqList: List<Pair<String, String>>) {
        faqList.forEachIndexed { index, (pertanyaan, jawaban) ->
            val itemView = buatItemFaq(pertanyaan, jawaban)
            container.addView(itemView)

            // Divider kecuali item terakhir
            if (index < faqList.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply {
                        marginStart = (16 * resources.displayMetrics.density).toInt()
                        marginEnd   = (16 * resources.displayMetrics.density).toInt()
                    }
                    setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.gray_border)
                    )
                }
                container.addView(divider)
            }
        }
    }

    private fun buatItemFaq(pertanyaan: String, jawaban: String): LinearLayout {
        val dp4  = (4  * resources.displayMetrics.density).toInt()
        val dp8  = (8  * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp16 = (16 * resources.displayMetrics.density).toInt()

        // Container utama item
        val itemLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(
                requireContext(), android.R.drawable.list_selector_background
            )
        }

        // Row pertanyaan + icon chevron
        val rowPertanyaan = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp16, dp12, dp16, dp12)
        }

        val tvPertanyaan = TextView(requireContext()).apply {
            text = pertanyaan
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            setPadding(0, 0, dp8, 0)
        }

        val ivChevron = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(20.dpToPx(), 20.dpToPx())
            setImageResource(R.drawable.ic_chevron_right)
            alpha = 0.5f
        }

        rowPertanyaan.addView(tvPertanyaan)
        rowPertanyaan.addView(ivChevron)

        // Jawaban (awalnya tersembunyi)
        val tvJawaban = TextView(requireContext()).apply {
            text = jawaban
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text))
            setPadding(dp16, 0, dp16, dp12)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // Latar belakang jawaban sedikit berbeda
            setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.background)
            )
        }

        itemLayout.addView(rowPertanyaan)
        itemLayout.addView(tvJawaban)

        // Toggle expand/collapse saat klik
        itemLayout.setOnClickListener {
            if (tvJawaban.visibility == View.GONE) {
                tvJawaban.visibility = View.VISIBLE
                ivChevron.rotation = 90f
                tvPertanyaan.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.orange_primary)
                )
            } else {
                tvJawaban.visibility = View.GONE
                ivChevron.rotation = 0f
                tvPertanyaan.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.black)
                )
            }
        }

        return itemLayout
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}