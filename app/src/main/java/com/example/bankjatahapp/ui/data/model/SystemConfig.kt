package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemConfig(

    @SerialName("id_config")
    val idConfig: Int = 1,

    // ==========================================
    // TOLERANSI & BATAS UTANG
    // ==========================================

    @SerialName("batas_toleransi_susut_persen")
    val batasTolereansiSusutPersen: Double = 0.5,

    @SerialName("batas_maksimal_utang")
    val batasMaksimalUtang: Double = -500000.0,

    // ==========================================
    // BATAS MINIMUM PENARIKAN
    // ==========================================

    @SerialName("min_penarikan_unit")
    val minPenarikanUnit: Double = 50000.0,

    @SerialName("min_penarikan_nasabah_pasif")
    val minPenarikanNasabahPasif: Double = 25000.0,

    @SerialName("min_penarikan_nasabah_aktif")
    val minPenarikanNasabahAktif: Double = 15000.0,

    @SerialName("min_penarikan_komisi")
    val minPenarikanKomisi: Double = 10000.0,

    // ==========================================
    // TARIF KOMISI AFILIASI
    // ==========================================

    @SerialName("tarif_kemitraan_per_generasi")
    val tarifKemitraanPerGenerasi: Double = 500.0,

    @SerialName("tarif_resaving_per_generasi")
    val tarifResavingPerGenerasi: Double = 500.0,

    // ==========================================
    // SISTEM POIN
    // ==========================================

    @SerialName("nilai_poin")
    val nilaiPoin: Int = 1,

    @SerialName("batas_poin_kg")
    val batasPoinKg: Double = 15.0,

    // ==========================================
    // BONUS UNIT BISNIS
    // ==========================================

    @SerialName("bonus_ub_kelurahan")
    val bonusUbKelurahan: Double = 240.0,

    @SerialName("bonus_ub_kabupaten")
    val bonusUbKabupaten: Double = 140.0,

    // ==========================================
    // UPAH JEMPUT
    // ==========================================

    @SerialName("biaya_jemput_per_kg")
    val biayaJemputPerKg: Double = 500.0,

    // ==========================================
    // SYARAT LEVEL BINTANG
    // ==========================================

    @SerialName("min_bintang_afiliasi")
    val minBintangAfiliasi: Int = 3,

    @SerialName("min_bintang_resaving")
    val minBintangResaving: Int = 3,

    @SerialName("min_bintang_penarikan")
    val minBintangPenarikan: Int = 3,

    @SerialName("min_bintang_kemitraan")
    val minBintangKemitraan: Int = 3,

    @SerialName("min_bintang_redeem")
    val minBintangRedeem: Int = 8,

    // ==========================================
    // SYARAT PENARIKAN NASABAH AKTIF
    // ==========================================

    @SerialName("threshold_saldo_nasabah_aktif")
    val thresholdSaldoNasabahAktif: Double = 120000.0,

    @SerialName("min_sisa_saldo_nasabah_aktif")
    val minSisaSaldoNasabahAktif: Double = 20000.0,

    // ==========================================
    // SYARAT REDEEM REWARD
    // ==========================================

    @SerialName("min_kg_pribadi_redeem")
    val minKgPribadiRedeem: Double = 60.0,

    // ==========================================
    // LOCK WITHDRAW & PENDAFTARAN UB
    // ==========================================

    @SerialName("bulan_lock_withdraw")
    val bulanLockWithdraw: Int = 3,

    @SerialName("biaya_pendaftaran_ub")
    val biayaPendaftaranUb: Double = 100000.0,

    // ==========================================
    // BIAYA ADMIN PENCAIRAN
    // ==========================================

    @SerialName("biaya_admin_pencairan")
    val biayaAdminPencairan: Double = 0.0,

    // ==========================================
    // AUDIT
    // ==========================================

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)