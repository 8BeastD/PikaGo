package com.leoworks.pikago

import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leoworks.pikago.models.DeliveryPartner
import com.leoworks.pikago.repository.ProfileRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etDateOfBirth: TextInputEditText
    private lateinit var acGender: MaterialAutoCompleteTextView
    private lateinit var etAddressLine1: TextInputEditText
    private lateinit var etAddressLine2: TextInputEditText
    private lateinit var etCity: TextInputEditText
    private lateinit var acState: MaterialAutoCompleteTextView
    private lateinit var etPincode: TextInputEditText
    private lateinit var etEmergencyName: TextInputEditText
    private lateinit var etEmergencyPhone: TextInputEditText
    private lateinit var btnSaveProfile: MaterialButton
    private lateinit var btnSkip: MaterialButton

    private lateinit var firstNameLayout: TextInputLayout
    private lateinit var lastNameLayout: TextInputLayout
    private lateinit var phoneLayout: TextInputLayout
    private lateinit var addressLayout: TextInputLayout
    private lateinit var cityLayout: TextInputLayout
    private lateinit var pincodeLayout: TextInputLayout

    private lateinit var profileRepository: ProfileRepository
    private var existingProfile: DeliveryPartner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        supportActionBar?.title = "Complete Your Profile"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initializeViews()
        setupDropdowns()
        setupTextWatchers()
        setupClickListeners()

        profileRepository = ProfileRepository(this)

        // Load existing profile data
        loadExistingProfile()
    }

    private fun loadExistingProfile() {
        lifecycleScope.launch {
            try {
                setLoading(true)
                existingProfile = profileRepository.getDeliveryPartner()

                existingProfile?.let { profile ->
                    // Pre-fill existing data
                    etFirstName.setText(profile.first_name)
                    etLastName.setText(profile.last_name)
                    etDateOfBirth.setText(profile.date_of_birth ?: "")
                    if (!profile.gender.isNullOrEmpty()) {
                        acGender.setText(profile.gender, false)
                    }
                    etAddressLine1.setText(profile.address_line1)
                    etAddressLine2.setText(profile.address_line2 ?: "")
                    etCity.setText(profile.city)
                    if (!profile.state.isNullOrEmpty()) {
                        acState.setText(profile.state, false)
                    }
                    etPincode.setText(profile.pincode)
                    etEmergencyName.setText(profile.emergency_contact_name ?: "")
                    etEmergencyPhone.setText(profile.emergency_contact_phone ?: "")

                    // Get phone from auth user
                    val authUser = App.supabase.auth.currentUserOrNull()
                    etPhone.setText(authUser?.phone ?: "")

                    // Update UI for existing profile
                    supportActionBar?.title = "Update Your Profile"
                    btnSaveProfile.text = "Update Profile"
                }
            } catch (e: Exception) {
                showError("Failed to load existing profile: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun initializeViews() {
        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        etPhone = findViewById(R.id.etPhone)
        etDateOfBirth = findViewById(R.id.etDateOfBirth)
        acGender = findViewById(R.id.acGender)
        etAddressLine1 = findViewById(R.id.etAddressLine1)
        etAddressLine2 = findViewById(R.id.etAddressLine2)
        etCity = findViewById(R.id.etCity)
        acState = findViewById(R.id.acState)
        etPincode = findViewById(R.id.etPincode)
        etEmergencyName = findViewById(R.id.etEmergencyName)
        etEmergencyPhone = findViewById(R.id.etEmergencyPhone)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        btnSkip = findViewById(R.id.btnSkip)

        firstNameLayout = findViewById(R.id.tilFirstName)
        lastNameLayout = findViewById(R.id.tilLastName)
        phoneLayout = findViewById(R.id.tilPhone)
        addressLayout = findViewById(R.id.tilAddressLine1)
        cityLayout = findViewById(R.id.tilCity)
        pincodeLayout = findViewById(R.id.tilPincode)

        // Set phone number input type
        etPhone.inputType = InputType.TYPE_CLASS_PHONE
        etEmergencyPhone.inputType = InputType.TYPE_CLASS_PHONE
        etPincode.inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun setupDropdowns() {
        // Gender dropdown
        val genderOptions = arrayOf("Male", "Female", "Other")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderOptions)
        acGender.setAdapter(genderAdapter)

        // State dropdown
        val stateOptions = arrayOf(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
            "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka",
            "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram",
            "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
            "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
            "Delhi", "Puducherry", "Chandigarh", "Dadra and Nagar Haveli", "Daman and Diu",
            "Lakshadweep", "Ladakh", "Jammu and Kashmir"
        )
        val stateAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, stateOptions)
        acState.setAdapter(stateAdapter)
    }

    private fun setupTextWatchers() {
        etFirstName.addTextChangedListener { clearError(firstNameLayout) }
        etLastName.addTextChangedListener { clearError(lastNameLayout) }
        etPhone.addTextChangedListener { clearError(phoneLayout) }
        etAddressLine1.addTextChangedListener { clearError(addressLayout) }
        etCity.addTextChangedListener { clearError(cityLayout) }
        etPincode.addTextChangedListener { clearError(pincodeLayout) }
    }

    private fun clearError(layout: TextInputLayout) {
        layout.error = null
    }

    private fun setupClickListeners() {
        btnSaveProfile.setOnClickListener {
            validateAndSaveProfile()
        }

        btnSkip.setOnClickListener {
            finish()
        }

        // Date picker for DOB
        etDateOfBirth.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val datePicker = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                etDateOfBirth.setText(formattedDate)
            },
            2000, 0, 1
        )
        datePicker.show()
    }

    private fun validateAndSaveProfile() {
        var isValid = true

        // Clear all errors first
        clearAllErrors()

        // Required field validations
        val firstName = etFirstName.text?.toString()?.trim()
        if (firstName.isNullOrEmpty()) {
            firstNameLayout.error = "First name is required"
            isValid = false
        }

        val lastName = etLastName.text?.toString()?.trim()
        if (lastName.isNullOrEmpty()) {
            lastNameLayout.error = "Last name is required"
            isValid = false
        }

        val phone = etPhone.text?.toString()?.trim()
        if (phone.isNullOrEmpty()) {
            phoneLayout.error = "Phone number is required"
            isValid = false
        } else if (phone.length != 10) {
            phoneLayout.error = "Please enter a valid 10-digit phone number"
            isValid = false
        }

        val addressLine1 = etAddressLine1.text?.toString()?.trim()
        if (addressLine1.isNullOrEmpty()) {
            addressLayout.error = "Address is required"
            isValid = false
        }

        val city = etCity.text?.toString()?.trim()
        if (city.isNullOrEmpty()) {
            cityLayout.error = "City is required"
            isValid = false
        }

        val state = acState.text?.toString()?.trim()
        if (state.isNullOrEmpty()) {
            findViewById<TextInputLayout>(R.id.tilState).error = "Please select a state"
            isValid = false
        }

        val pincode = etPincode.text?.toString()?.trim()
        if (pincode.isNullOrEmpty()) {
            pincodeLayout.error = "Pincode is required"
            isValid = false
        } else if (pincode.length != 6) {
            pincodeLayout.error = "Please enter a valid 6-digit pincode"
            isValid = false
        }

        if (!isValid) {
            showError("Please fill all required fields correctly")
            return
        }

        // Save profile
        saveProfile(
            firstName = firstName!!,
            lastName = lastName!!,
            phone = phone!!,
            dateOfBirth = etDateOfBirth.text?.toString()?.trim(),
            gender = acGender.text?.toString()?.trim(),
            addressLine1 = addressLine1!!,
            addressLine2 = etAddressLine2.text?.toString()?.trim(),
            city = city!!,
            state = state!!,
            pincode = pincode!!,
            emergencyName = etEmergencyName.text?.toString()?.trim(),
            emergencyPhone = etEmergencyPhone.text?.toString()?.trim()
        )
    }

    private fun clearAllErrors() {
        firstNameLayout.error = null
        lastNameLayout.error = null
        phoneLayout.error = null
        addressLayout.error = null
        cityLayout.error = null
        pincodeLayout.error = null
        findViewById<TextInputLayout>(R.id.tilState).error = null
    }

    private fun saveProfile(
        firstName: String,
        lastName: String,
        phone: String,
        dateOfBirth: String?,
        gender: String?,
        addressLine1: String,
        addressLine2: String?,
        city: String,
        state: String,
        pincode: String,
        emergencyName: String?,
        emergencyPhone: String?
    ) {
        lifecycleScope.launch {
            try {
                setLoading(true)

                if (existingProfile == null) {
                    // Create new profile
                    profileRepository.createDeliveryPartner(
                        firstName = firstName,
                        lastName = lastName,
                        phone = phone,
                        addressLine1 = addressLine1,
                        city = city,
                        state = state,
                        pincode = pincode
                    )
                } else {
                    // Update existing profile
                    val updatedProfile = existingProfile!!.copy(
                        first_name = firstName,
                        last_name = lastName,
                        date_of_birth = dateOfBirth,
                        gender = gender,
                        address_line1 = addressLine1,
                        address_line2 = addressLine2,
                        city = city,
                        state = state,
                        pincode = pincode,
                        emergency_contact_name = emergencyName,
                        emergency_contact_phone = emergencyPhone
                    )
                    profileRepository.updateDeliveryPartner(updatedProfile)
                }

                // Recalculate completion after saving
                profileRepository.calculateProfileCompletion()

                showSuccess("Profile saved successfully!")

                // Delay to show success message then finish
                kotlinx.coroutines.delay(1500)
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                showError("Failed to save profile: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnSaveProfile.isEnabled = !loading
        btnSkip.isEnabled = !loading

        if (loading) {
            btnSaveProfile.text = if (existingProfile != null) "Updating..." else "Saving..."
        } else {
            btnSaveProfile.text = if (existingProfile != null) "Update Profile" else "Save Profile"
        }
    }

    private fun showError(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}