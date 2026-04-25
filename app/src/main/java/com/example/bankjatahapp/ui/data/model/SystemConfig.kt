package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemConfig(

    @SerialName("id_config")
    val idConfig: Int = 1,

    @SerialName("bonus_ub_kelurahan")
    val bonusUbKelurahan: Double = 240.0,

    @SerialName("bonus_ub_kabupaten")
    val bonusUbKabupaten: Double = 140.0,

    @SerialName("min_penarikan_nasabah_aktif")
    val minPenarikanNasabahAktif: Double = 15000.0,

    @SerialName("min_penarikan_nasabah_pasif")
    val minPenarikanNasabahPasif: Double = 25000.0,

    @SerialName("min_penarikan_unit")
    val minPenarikanUnit: Double = 50000.0,

    @SerialName("min_penarikan_komisi")
    val minPenarikanKomisi: Double = 10000.0,

    @SerialName("nilai_poin")
    val nilaiPoin: Int = 1,

    @SerialName("batas_poin_kg")
    val batasPoinKg: Double = 15.0,

    // 3 kolom baru dari rekan kamu
    @SerialName("threshold_saldo_nasabah_aktif")
    val thresholdSaldoNasabahAktif: Double = 120000.0,

    @SerialName("min_sisa_saldo_nasabah_aktif")
    val minSisaSaldoNasabahAktif: Double = 20000.0,

    @SerialName("min_bintang_penarikan")
    val minBintangPenarikan: Int = 3
)