package com.leoworks.pikago

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        MainScope().launch {
            delay(1500) // Splash delay

            try {
                val session = App.supabase.auth.currentSessionOrNull()
                val isSessionValid = session != null &&
                        session.expiresAt > Clock.System.now()

                if (isSessionValid) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                } else {
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                }
            } catch (e: Exception) {
                // If supabase is not initialized, go to login
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }
            finish()
        }
    }
}