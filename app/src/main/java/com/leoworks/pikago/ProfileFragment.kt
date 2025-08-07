package com.leoworks.pikago

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var tvName: TextView
    private lateinit var tvPhone: TextView
    private lateinit var switchAvailable: Switch
    private lateinit var btnLogout: Button
    private lateinit var btnEditProfile: ImageButton
    private lateinit var btnVerifyNow: ImageButton
    private lateinit var btnUploadDoc: ImageButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        tvName = view.findViewById(R.id.tvName)
        tvPhone = view.findViewById(R.id.tvPhone)
        switchAvailable = view.findViewById(R.id.switchAvailable)
        btnLogout = view.findViewById(R.id.btnLogout)
        btnEditProfile = view.findViewById(R.id.btnEditProfile)
        btnVerifyNow = view.findViewById(R.id.btnVerifyNow)
        btnUploadDoc = view.findViewById(R.id.btnUploadDoc)

        // Animate entrance
        animateEntrance(view)

        // Load user data
        loadUserProfile()

        // Availability toggle
        switchAvailable.setOnCheckedChangeListener { _, isChecked ->
            Snackbar.make(
                view,
                if (isChecked) "You are Available" else "You are Offline",
                Snackbar.LENGTH_SHORT
            ).show()
            // TODO: update availability on backend
        }

        // Logout logic
        btnLogout.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    App.supabase.auth.signOut()

                    withContext(Dispatchers.Main) {
                        val intent = Intent(requireContext(), LoginActivity::class.java)
                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(view, "Logout failed: ${e.message}", Snackbar.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }

        // Edit profile click
        btnEditProfile.setOnClickListener {
            Snackbar.make(view, "Edit Profile clicked", Snackbar.LENGTH_SHORT).show()
            // TODO: launch profile edit screen
        }

        // Verify now click
        btnVerifyNow.setOnClickListener {
            Snackbar.make(view, "Verification flow coming soon", Snackbar.LENGTH_SHORT).show()
            // TODO: launch verification screen
        }

        // Upload document click
        btnUploadDoc.setOnClickListener {
            Snackbar.make(view, "Upload Document clicked", Snackbar.LENGTH_SHORT).show()
            // TODO: open file picker + upload to Supabase
        }
    }

    private fun loadUserProfile() {
        // TODO: Replace with real Supabase fetch
        tvName.text = "Rider Rahul"
        tvPhone.text = "+91 98765 43210"
    }

    private fun animateEntrance(view: View) {
        view.alpha = 0f
        view.translationY = 50f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .start()
    }
}
