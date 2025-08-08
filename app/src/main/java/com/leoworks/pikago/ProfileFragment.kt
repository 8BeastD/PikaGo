package com.leoworks.pikago

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.leoworks.pikago.adapters.DocumentAdapter
import com.leoworks.pikago.adapters.ProfileSectionAdapter
import com.leoworks.pikago.models.*
import com.leoworks.pikago.repository.ProfileRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var tvName: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvVerificationStatus: TextView
    private lateinit var progressProfile: LinearProgressIndicator
    private lateinit var tvProgressText: TextView
    private lateinit var switchAvailable: Switch
    private lateinit var btnLogout: MaterialButton
    private lateinit var btnCompleteProfile: MaterialButton
    private lateinit var cardProfileCompletion: MaterialCardView
    private lateinit var rvProfileSections: RecyclerView
    private lateinit var rvDocuments: RecyclerView

    private lateinit var profileRepository: ProfileRepository
    private lateinit var documentAdapter: DocumentAdapter
    private lateinit var profileSectionAdapter: ProfileSectionAdapter

    private var currentDeliveryPartner: DeliveryPartner? = null
    private var selectedDocumentType: String? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedDocumentType?.let { docType ->
                    showDocumentNumberDialog(docType, uri)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerViews()
        profileRepository = ProfileRepository(requireContext())

        animateEntrance(view)
        loadProfileData()
        setupClickListeners(view)
    }

    private fun initializeViews(view: View) {
        tvName = view.findViewById(R.id.tvName)
        tvPhone = view.findViewById(R.id.tvPhone)
        tvVerificationStatus = view.findViewById(R.id.tvVerificationStatus)
        progressProfile = view.findViewById(R.id.progressProfile)
        tvProgressText = view.findViewById(R.id.tvProgressText)
        switchAvailable = view.findViewById(R.id.switchAvailable)
        btnLogout = view.findViewById(R.id.btnLogout)
        btnCompleteProfile = view.findViewById(R.id.btnCompleteProfile)
        cardProfileCompletion = view.findViewById(R.id.cardProfileCompletion)
        rvProfileSections = view.findViewById(R.id.rvProfileSections)
        rvDocuments = view.findViewById(R.id.rvDocuments)
    }

    private fun setupRecyclerViews() {
        // Profile sections
        profileSectionAdapter = ProfileSectionAdapter { section ->
            handleProfileSectionClick(section)
        }
        rvProfileSections.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = profileSectionAdapter
        }

        // Documents
        documentAdapter = DocumentAdapter { documentType ->
            handleDocumentUpload(documentType)
        }
        rvDocuments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = documentAdapter
        }
    }

    // Replace the loadProfileData method in your ProfileFragment with this simplified version:

    private fun loadProfileData() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                currentDeliveryPartner = profileRepository.getDeliveryPartner()

                if (currentDeliveryPartner != null) {
                    updateUI()
                    loadDocuments()
                    updateProfileSections()
                } else {
                    showError("Failed to load or create profile")
                }

            } catch (e: Exception) {
                showError("Failed to load profile: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateUI() {
        currentDeliveryPartner?.let { partner ->
            tvName.text = "${partner.first_name} ${partner.last_name}"

            val user = App.supabase.auth.currentUserOrNull()
            tvPhone.text = user?.phone ?: "Not available"

            // Update verification status
            when (partner.verification_status) {
                "pending" -> {
                    tvVerificationStatus.text = "Verification Pending"
                    tvVerificationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                }
                "in_progress" -> {
                    tvVerificationStatus.text = "Under Review"
                    tvVerificationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark))
                }
                "verified" -> {
                    tvVerificationStatus.text = "✓ Verified"
                    tvVerificationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                }
                "rejected" -> {
                    tvVerificationStatus.text = "Verification Failed"
                    tvVerificationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                }
            }

            // Update progress
            val progress = partner.profile_completion_percentage
            progressProfile.progress = progress
            tvProgressText.text = "$progress% Complete"

            // Show/hide completion card
            cardProfileCompletion.visibility = if (progress < 100) View.VISIBLE else View.GONE

            // Set availability switch
            switchAvailable.isChecked = partner.is_available
        }
    }

    private fun loadDocuments() {
        lifecycleScope.launch {
            try {
                val documents = profileRepository.getVerificationDocuments()
                documentAdapter.updateDocuments(documents)
            } catch (e: Exception) {
                showError("Failed to load documents: ${e.message}")
            }
        }
    }

    private fun updateProfileSections() {
        lifecycleScope.launch {
            try {
                val sections = mutableListOf<ProfileSection>()

                // Basic Profile
                val basicComplete = currentDeliveryPartner?.let {
                    it.first_name.isNotEmpty() && it.last_name.isNotEmpty() &&
                            it.address_line1.isNotEmpty()
                } ?: false
                sections.add(ProfileSection("Basic Information", basicComplete, if (basicComplete) 100 else 50))

                // Bank Details
                val bankDetails = profileRepository.getBankDetails()
                sections.add(ProfileSection("Bank Details", bankDetails != null, if (bankDetails != null) 100 else 0))

                // Vehicle Details
                val vehicleDetails = profileRepository.getVehicleDetails()
                sections.add(ProfileSection("Vehicle Information", vehicleDetails != null, if (vehicleDetails != null) 100 else 0))

                // Documents
                val documents = profileRepository.getVerificationDocuments()
                val requiredDocs = 3 // Aadhar, PAN, Driving License
                val completedDocs = documents.size
                val docProgress = if (requiredDocs > 0) (completedDocs * 100) / requiredDocs else 0
                sections.add(ProfileSection("Identity Documents", completedDocs >= requiredDocs, docProgress))

                profileSectionAdapter.updateSections(sections)

            } catch (e: Exception) {
                showError("Failed to update profile sections: ${e.message}")
            }
        }
    }

    private fun setupClickListeners(view: View) {
        // Availability toggle
        switchAvailable.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                try {
                    profileRepository.updateAvailabilityStatus(isChecked)
                    Snackbar.make(
                        view,
                        if (isChecked) "You are now Available for deliveries" else "You are now Offline",
                        Snackbar.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    switchAvailable.isChecked = !isChecked
                    showError("Failed to update availability")
                }
            }
        }

        // Complete Profile button
        btnCompleteProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileSetupActivity::class.java))
        }

        // Logout
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun handleProfileSectionClick(section: ProfileSection) {
        when (section.title) {
            "Basic Information" -> {
                startActivity(Intent(requireContext(), BasicProfileActivity::class.java))
            }
            "Bank Details" -> {
                startActivity(Intent(requireContext(), BankDetailsActivity::class.java))
            }
            "Vehicle Information" -> {
                startActivity(Intent(requireContext(), VehicleDetailsActivity::class.java))
            }
            "Identity Documents" -> {
                // Show document upload options
                showDocumentUploadOptions()
            }
        }
    }

    private fun handleDocumentUpload(documentType: String) {
        selectedDocumentType = documentType
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun showDocumentUploadOptions() {
        val documentTypes = arrayOf("Aadhar Card", "PAN Card", "Driving License", "Vehicle Registration", "Vehicle Insurance")

        AlertDialog.Builder(requireContext())
            .setTitle("Upload Document")
            .setItems(documentTypes) { _, which ->
                val docType = when (which) {
                    0 -> "aadhar"
                    1 -> "pan"
                    2 -> "driving_license"
                    3 -> "vehicle_registration"
                    4 -> "vehicle_insurance"
                    else -> "aadhar"
                }
                handleDocumentUpload(docType)
            }
            .show()
    }

    private fun showDocumentNumberDialog(documentType: String, imageUri: Uri) {
        val editText = EditText(requireContext())
        editText.hint = "Enter document number"

        AlertDialog.Builder(requireContext())
            .setTitle("Document Number")
            .setMessage("Please enter the document number")
            .setView(editText)
            .setPositiveButton("Upload") { _, _ ->
                val documentNumber = editText.text.toString().trim()
                if (documentNumber.isNotEmpty()) {
                    uploadDocument(documentType, documentNumber, imageUri)
                } else {
                    showError("Please enter document number")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun uploadDocument(documentType: String, documentNumber: String, imageUri: Uri) {
        lifecycleScope.launch {
            try {
                showLoading(true, "Uploading document...")

                profileRepository.uploadDocument(documentType, documentNumber, imageUri)

                showSuccess("Document uploaded successfully!")
                loadDocuments()
                updateProfileCompletion()

            } catch (e: Exception) {
                showError("Failed to upload document: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showCreateProfileDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Complete Your Profile")
            .setMessage("Please complete your delivery partner profile to start receiving orders.")
            .setPositiveButton("Complete Now") { _, _ ->
                startActivity(Intent(requireContext(), ProfileSetupActivity::class.java))
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                App.supabase.auth.signOut()

                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()

            } catch (e: Exception) {
                showError("Logout failed: ${e.message}")
            }
        }
    }

    private fun updateProfileCompletion() {
        lifecycleScope.launch {
            try {
                val completion = profileRepository.calculateProfileCompletion()
                progressProfile.progress = completion
                tvProgressText.text = "$completion% Complete"
                cardProfileCompletion.visibility = if (completion < 100) View.VISIBLE else View.GONE

                updateProfileSections()
            } catch (e: Exception) {
                // Handle silently
            }
        }
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

    private fun showLoading(show: Boolean, message: String = "Loading...") {
        // You can implement a loading dialog or progress bar here
        btnCompleteProfile.isEnabled = !show
        btnLogout.isEnabled = !show
    }

    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showSuccess(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }
}