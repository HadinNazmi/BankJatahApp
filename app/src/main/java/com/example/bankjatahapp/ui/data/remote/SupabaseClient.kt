package com.example.bankjatahapp.data.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.logging.LogLevel
import com.example.bankjatahapp.BuildConfig

object SupabaseClient {
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_KEY = BuildConfig.SUPABASE_KEY

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        defaultLogLevel = LogLevel.NONE

        install(Auth) {
            // Simpan session ke storage permanen (SharedPreferences)
            // sehingga user tidak perlu login ulang walau app di-kill
            autoSaveToStorage   = true
            autoLoadFromStorage = true
            alwaysAutoRefresh   = true
            flowType            = FlowType.PKCE
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}