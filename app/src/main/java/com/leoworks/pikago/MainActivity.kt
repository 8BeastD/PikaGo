package com.leoworks.pikago

import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {
    private lateinit var navHome: ImageButton
    private lateinit var navRide: ImageButton
    private lateinit var navProfile: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Apply edge-to-edge padding on the root view (must have id="@+id/main")
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        // Bind bottom-nav buttons
        navHome    = findViewById(R.id.nav_home)
        navRide    = findViewById(R.id.nav_ride)
        navProfile = findViewById(R.id.nav_profile)

        // Default to Home
        selectNav(navHome, HomeFragment())

        // Tab click listeners
        navHome   .setOnClickListener { selectNav(navHome,    HomeFragment()) }
        navRide   .setOnClickListener { selectNav(navRide,    RideFragment()) }
        navProfile.setOnClickListener { selectNav(navProfile, ProfileFragment()) }
    }

    private fun selectNav(button: ImageButton, fragment: Fragment) {
        // 1) Swap in the fragment
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, fragment)
            .commit()

        // 2) Reset all icons to white-on-transparent
        val white = ContextCompat.getColor(this, R.color.white)
        listOf(navHome, navRide, navProfile).forEach {
            it.setColorFilter(white)
            it.background = ContextCompat.getDrawable(this, R.drawable.nav_item_bg_unselected)
        }

        // 3) Highlight the selected tab: black icon on white circle
        val black = ContextCompat.getColor(this, R.color.black)
        button.setColorFilter(black)
        button.background = ContextCompat.getDrawable(this, R.drawable.nav_item_bg_selected)
    }
}
