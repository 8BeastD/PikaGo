package com.leoworks.pikago

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var tvName: TextView
    private lateinit var tvPhone: TextView
    private lateinit var switchAvailable: Switch
    private lateinit var btnLogout: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        tvName          = view.findViewById(R.id.tvName)
        tvPhone         = view.findViewById(R.id.tvPhone)
        switchAvailable = view.findViewById(R.id.switchAvailable)
        btnLogout       = view.findViewById(R.id.btnLogout)

        // Load user data (stub)
        loadUserProfile()

        // Availability toggle
        switchAvailable.setOnCheckedChangeListener { _, isChecked ->
            Snackbar.make(view,
                if (isChecked) "You are Available" else "You are Offline",
                Snackbar.LENGTH_SHORT
            ).show()
            // TODO: update status in backend
        }

        // Logout
        btnLogout.setOnClickListener {
            Snackbar.make(view, "Logged out", Snackbar.LENGTH_SHORT).show()
            // TODO: clear session + navigate to login
        }

        // Edit profile
        view.findViewById<ImageButton>(R.id.btnEditProfile).setOnClickListener {
            // TODO: launch profile-edit screen
        }

        // Verify now
        view.findViewById<ImageButton>(R.id.btnVerifyNow).setOnClickListener {
            // TODO: launch verification flow
        }

        // Document uploads (example)
        listOf(R.id.btnUploadDoc).forEach { id ->
            view.findViewById<ImageButton>(id).setOnClickListener {
                // TODO: open file picker + upload to Supabase
            }
        }
    }

    private fun loadUserProfile() {
        // Stub data — replace with real fetch
        view?.findViewById<TextView>(R.id.tvName)?.text  = "Rider Rahul"
        view?.findViewById<TextView>(R.id.tvPhone)?.text = "+91 98765 43210"
        // TODO: fetch verification status, earnings, avatar URL, etc.
    }
}
