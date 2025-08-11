package com.leoworks.pikago

import android.app.Application
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object App {
    lateinit var supabase: SupabaseClient
}

class PikaGoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        App.supabase = createSupabaseClient(
            supabaseUrl = "https://grhktiznwdgglzqghjgj.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdyaGt0aXpud2RnZ2x6cWdoamdqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTQzNzU2ODEsImV4cCI6MjA2OTk1MTY4MX0.hjNpd26-8GDXnXvha6zdN3DciaRmRnubNhvwnFxnXtk"
        ) {
            install(Auth){
                alwaysAutoRefresh = true          // ✅ keep token fresh
                autoLoadFromStorage = true
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }
}