package com.leoworks.pikago

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.leoworks.pikago.adapters.DocumentAdapter
import com.leoworks.pikago.adapters.ProfileSectionAdapter
import com.leoworks.pikago.models.*
import com.leoworks.pikago.repository.ProfileRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var ivProfileImage: ImageView
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
    private var currentPhotoPath: String = ""

    // Profile image launcher for gallery
    private val profileImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfileImage(uri)
            }
        }
    }

    // Profile image launcher for camera
    private val profileCameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val photoFile = File(currentPhotoPath)
            if (photoFile.exists()) {
                val photoUri = Uri.fromFile(photoFile)
                uploadProfileImage(photoUri)
            }
        }
    }

    // Document image picker launcher
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

    // Activity result launcher for profile activities
    private val profileActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Refresh the profile data when returning from any profile activity
            loadProfileData()
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
        ivProfileImage = view.findViewById(R.id.ivProfilePhoto)
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
        profileSectionAdapter = ProfileSectionAdapter { section ->
            handleProfileSectionClick(section)
        }
        rvProfileSections.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = profileSectionAdapter
        }

        documentAdapter = DocumentAdapter { documentType ->
            handleDocumentUpload(documentType)
        }
        rvDocuments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = documentAdapter
        }
    }

    private fun checkAuthenticationAndRedirect(): Boolean {
        val user = App.supabase.auth.currentUserOrNull()
        if (user == null) {
            showAuthenticationError()
            return false
        }
        return true
    }

    private fun showAuthenticationError() {
        AlertDialog.Builder(requireContext())
            .setTitle("Session Expired")
            .setMessage("Your session has expired. Please login again.")
            .setPositiveButton("Login") { _, _ ->
                redirectToLogin()
            }
            .setCancelable(false)
            .show()
    }

    private fun redirectToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private suspend fun ensureUserAndPartner(): DeliveryPartner? = withContext(Dispatchers.IO) {
        val authUser = App.supabase.auth.currentUserOrNull()
            ?: throw IllegalStateException("Not logged in")

        try {
            val userPayload = mapOf(
                "id" to authUser.id,
                "email" to (authUser.email ?: ""),
                "phone" to authUser.phone
            )
            App.supabase.from("users").upsert(userPayload) {
                onConflict = "id"
                ignoreDuplicates = true
            }
        } catch (_: Exception) {
            // Continue if RLS blocks this
        }

        val existing = try {
            App.supabase.from("delivery_partners")
                .select {
                    filter { eq("user_id", authUser.id) }
                }
                .decodeList<DeliveryPartner>()
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
        if (existing != null) return@withContext existing

        return@withContext try {
            val insertPayload = mapOf(
                "user_id" to authUser.id,
                "verification_status" to "pending",
                "is_available" to false
            )
            App.supabase.from("delivery_partners")
                .insert(insertPayload) { select() }
                .decodeSingle<DeliveryPartner>()
        } catch (e: Exception) {
            null
        }
    }

    private fun loadProfileData() {
        if (!checkAuthenticationAndRedirect()) return

        lifecycleScope.launch {
            try {
                showLoading(true)

                currentDeliveryPartner = try {
                    profileRepository.getDeliveryPartner()
                } catch (e: Exception) {
                    if (e.message?.contains("login") == true) {
                        withContext(Dispatchers.Main) {
                            showAuthenticationError()
                        }
                        return@launch
                    }
                    null
                }

                if (currentDeliveryPartner == null) {
                    currentDeliveryPartner = ensureUserAndPartner()
                }

                if (currentDeliveryPartner != null) {
                    updateUI()
                    loadDocuments()
                    updateProfileSections()
                    // Check and update verification status
                    checkAndUpdateVerificationStatus()
                } else {
                    showError("Failed to load or create profile")
                    showCreateProfileDialog()
                }

            } catch (e: Exception) {
                if (e.message?.contains("login") == true || e.message?.contains("authenticated") == true) {
                    showAuthenticationError()
                } else {
                    showError("Failed to load profile: ${e.message}")
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateUI() {
        currentDeliveryPartner?.let { partner ->
            tvName.text = listOf(partner.first_name, partner.last_name)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Update your name" }

            val authUser = App.supabase.auth.currentUserOrNull()
            tvPhone.text = authUser?.phone ?: authUser?.email ?: "Not available"

            loadProfileImage(partner.profile_photo_url)
            updateVerificationStatusDisplay(partner.verification_status)

            val progress = partner.profile_completion_percentage
            progressProfile.progress = progress
            tvProgressText.text = "$progress% Complete"

            cardProfileCompletion.visibility = if (progress < 100) View.VISIBLE else View.GONE
            switchAvailable.isChecked = partner.is_available
        }
    }

    private fun updateVerificationStatusDisplay(status: String) {
        when (status) {
            "pending" -> {
                tvVerificationStatus.text = "Verification Pending"
                tvVerificationStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
                )
            }
            "in_progress" -> {
                tvVerificationStatus.text = "Under Review"
                tvVerificationStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
                )
            }
            "verified" -> {
                tvVerificationStatus.text = "✓ Verified"
                tvVerificationStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                )
            }
            "rejected" -> {
                tvVerificationStatus.text = "Verification Failed"
                tvVerificationStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                )
            }
            else -> {
                tvVerificationStatus.text = status
            }
        }
    }

    private fun loadProfileImage(imageUrl: String?) {
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(ivProfileImage)
        } else {
            Glide.with(this)
                .load(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(ivProfileImage)
        }
    }

    private fun loadDocuments() {
        lifecycleScope.launch {
            try {
                val documents = profileRepository.getVerificationDocuments()
                documentAdapter.updateDocuments(documents)
            } catch (e: Exception) {
                if (e.message?.contains("login") == true) {
                    showAuthenticationError()
                } else {
                    showError("Failed to load documents: ${e.message}")
                }
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
                sections.add(
                    ProfileSection(
                        "Basic Information",
                        basicComplete,
                        if (basicComplete) 100 else 50
                    )
                )

                // Bank Details
                val bankDetails = try {
                    profileRepository.getBankDetails()
                } catch (e: Exception) {
                    if (e.message?.contains("login") == true) {
                        showAuthenticationError()
                        return@launch
                    }
                    null
                }
                sections.add(
                    ProfileSection(
                        "Bank Details",
                        bankDetails != null,
                        if (bankDetails != null) 100 else 0
                    )
                )

                // Vehicle Details
                val vehicleDetails = try {
                    profileRepository.getVehicleDetails()
                } catch (e: Exception) {
                    if (e.message?.contains("login") == true) {
                        showAuthenticationError()
                        return@launch
                    }
                    null
                }
                sections.add(
                    ProfileSection(
                        "Vehicle Information",
                        vehicleDetails != null,
                        if (vehicleDetails != null) 100 else 0
                    )
                )

                // Documents
                val documents = try {
                    profileRepository.getVerificationDocuments()
                } catch (e: Exception) {
                    if (e.message?.contains("login") == true) {
                        showAuthenticationError()
                        return@launch
                    }
                    emptyList()
                }
                val requiredDocs = 3
                val completedDocs = documents.size
                val docProgress = if (requiredDocs > 0) (completedDocs * 100) / requiredDocs else 0
                sections.add(
                    ProfileSection(
                        "Identity Documents",
                        completedDocs >= requiredDocs,
                        docProgress
                    )
                )

                profileSectionAdapter.updateSections(sections)

            } catch (e: Exception) {
                if (e.message?.contains("login") == true) {
                    showAuthenticationError()
                } else {
                    showError("Failed to update profile sections: ${e.message}")
                }
            }
        }
    }

    private fun setupClickListeners(view: View) {
        ivProfileImage.setOnClickListener {
            if (checkAuthenticationAndRedirect()) {
                showImageUploadOptions()
            }
        }

        switchAvailable.setOnCheckedChangeListener { _, isChecked ->
            if (!checkAuthenticationAndRedirect()) {
                switchAvailable.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }

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
                    if (e.message?.contains("login") == true) {
                        showAuthenticationError()
                    } else {
                        showError("Failed to update availability")
                    }
                }
            }
        }

        btnCompleteProfile.setOnClickListener {
            if (checkAuthenticationAndRedirect()) {
                profileActivityLauncher.launch(Intent(requireContext(), ProfileSetupActivity::class.java))
            }
        }

        btnLogout.setOnClickListener { showLogoutDialog() }

        // Add verification check button (you can add this to your layout or make it a menu item)
        tvVerificationStatus.setOnClickListener {
            if (checkAuthenticationAndRedirect()) {
                checkAndUpdateVerificationStatus()
            }
        }
    }

    private fun showImageUploadOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "View Full Image")

        AlertDialog.Builder(requireContext())
            .setTitle("Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePicture()
                    1 -> pickImageFromGallery()
                    2 -> showFullImage()
                }
            }
            .show()
    }

    private fun takePicture() {
        try {
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath

            val photoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            profileCameraLauncher.launch(intent)
        } catch (e: Exception) {
            showError("Failed to open camera: ${e.message}")
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        profileImagePickerLauncher.launch(intent)
    }

    private fun showFullImage() {
        currentDeliveryPartner?.profile_photo_url?.let { imageUrl ->
            if (imageUrl.isNotEmpty()) {
                val intent = Intent(requireContext(), FullImageViewActivity::class.java)
                intent.putExtra("image_url", imageUrl)
                intent.putExtra("title", "Profile Picture")
                startActivity(intent)
            } else {
                showError("No profile picture available")
            }
        } ?: showError("No profile picture available")
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "PROFILE_$timeStamp"
        val storageDir = requireContext().getExternalFilesDir("Pictures")
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    private fun uploadProfileImage(imageUri: Uri) {
        lifecycleScope.launch {
            try {
                showLoading(true, "Uploading profile picture...")

                val signedUrl = profileRepository.uploadProfileImage(imageUri)
                profileRepository.updateProfileImage(signedUrl)

                currentDeliveryPartner = profileRepository.getDeliveryPartner()
                loadProfileImage(signedUrl)

                showSuccess("Profile picture updated successfully!")

                // Update verification status after profile image upload
                checkAndUpdateVerificationStatus()
            } catch (e: Exception) {
                if (e.message?.contains("login", ignoreCase = true) == true) {
                    showAuthenticationError()
                } else {
                    showError("Failed to upload profile picture: ${e.message}")
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showStorageConfigurationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Storage Configuration Required")
            .setMessage("The storage buckets are not configured properly. Please:\n\n" +
                    "1. Go to Supabase Dashboard\n" +
                    "2. Navigate to Storage\n" +
                    "3. Create buckets: 'profile-images' and 'documents'\n" +
                    "4. Make them public or configure proper RLS policies\n\n" +
                    "Contact your developer if you need help.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun handleProfileSectionClick(section: ProfileSection) {
        if (!checkAuthenticationAndRedirect()) return

        when (section.title) {
            "Basic Information" -> {
                profileActivityLauncher.launch(
                    Intent(requireContext(), ProfileSetupActivity::class.java)
                )
            }
            "Bank Details" -> {
                profileActivityLauncher.launch(
                    Intent(requireContext(), BankDetailsActivity::class.java)
                )
            }
            "Vehicle Information" -> {
                profileActivityLauncher.launch(
                    Intent(requireContext(), VehicleDetailsActivity::class.java)
                )
            }
            "Identity Documents" -> {
                showDocumentUploadOptions()
            }
        }
    }

    private fun handleDocumentUpload(documentType: String) {
        if (!checkAuthenticationAndRedirect()) return

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
                // Check verification status after document upload
                checkAndUpdateVerificationStatus()

            } catch (e: Exception) {
                if (e.message?.contains("login") == true || e.message?.contains("authenticated") == true) {
                    showAuthenticationError()
                } else if (e.message?.contains("bucket") == true || e.message?.contains("Storage") == true) {
                    showStorageConfigurationDialog()
                } else {
                    showError("Failed to upload document: ${e.message}")
                }
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
                if (checkAuthenticationAndRedirect()) {
                    profileActivityLauncher.launch(Intent(requireContext(), ProfileSetupActivity::class.java))
                }
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ -> performLogout() }
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
                val (completion, verificationStatus) = profileRepository.calculateProfileCompletionAndVerification()

                progressProfile.progress = completion
                tvProgressText.text = "$completion% Complete"
                cardProfileCompletion.visibility = if (completion < 100) View.VISIBLE else View.GONE

                updateVerificationStatusDisplay(verificationStatus)
                updateProfileSections()

                currentDeliveryPartner = profileRepository.getDeliveryPartner()
                updateUI()
            } catch (e: Exception) {
                if (e.message?.contains("login") == true) {
                    showAuthenticationError()
                }
            }
        }
    }

    // NEW METHOD: Check and update verification status
    private fun checkAndUpdateVerificationStatus() {
        lifecycleScope.launch {
            try {
                val newStatus = profileRepository.updateVerificationStatus()
                updateVerificationStatusDisplay(newStatus)

                currentDeliveryPartner = profileRepository.getDeliveryPartner()
                updateUI()

                if (newStatus == "verified") {
                    showSuccess("Congratulations! Your profile has been verified.")
                } else if (newStatus == "in_progress") {
                    showSuccess("Your profile is under review.")
                }

            } catch (e: Exception) {
                if (e.message?.contains("login") == true) {
                    showAuthenticationError()
                }
                // Don't show error for verification check failures
            }
        }
    }

    private fun animateEntrance(view: View) {
        view.alpha = 0f
        view.translationY = 50f
        view.animate().alpha(1f).translationY(0f).setDuration(800).start()
    }

    private fun showLoading(show: Boolean, message: String = "Loading...") {
        btnCompleteProfile.isEnabled = !show
        btnLogout.isEnabled = !show
        ivProfileImage.isClickable = !show
        switchAvailable.isEnabled = !show
    }

    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG)
                .setAction("Retry") { loadProfileData() }
                .show()
        }
    }

    private fun showSuccess(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            runCatching {
                App.supabase.auth.loadFromStorage()
                App.supabase.auth.refreshCurrentSession()
            }
            loadProfileData()
        }
    }
}