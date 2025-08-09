package com.leoworks.pikago

import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leoworks.pikago.models.VehicleDetails
import com.leoworks.pikago.models.VehicleType
import com.leoworks.pikago.repository.ProfileRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class VehicleDetailsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etVehicleMake: TextInputEditText
    private lateinit var etVehicleModel: TextInputEditText
    private lateinit var etVehicleNumber: TextInputEditText
    private lateinit var etVehicleColor: TextInputEditText
    private lateinit var etRegistrationYear: TextInputEditText
    private lateinit var acVehicleType: MaterialAutoCompleteTextView
    private lateinit var btnSaveVehicle: MaterialButton
    private lateinit var btnSkip: MaterialButton

    private lateinit var tilVehicleMake: TextInputLayout
    private lateinit var tilVehicleModel: TextInputLayout
    private lateinit var tilVehicleNumber: TextInputLayout
    private lateinit var tilVehicleColor: TextInputLayout
    private lateinit var tilRegistrationYear: TextInputLayout
    private lateinit var tilVehicleType: TextInputLayout

    private lateinit var profileRepository: ProfileRepository
    private var existingVehicle: VehicleDetails? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_vehicle_details)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupToolbar()
        setupDropdowns()
        setupTextWatchers()
        setupClickListeners()

        profileRepository = ProfileRepository(this)
        loadExistingVehicleDetails()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        etVehicleMake = findViewById(R.id.etVehicleMake)
        etVehicleModel = findViewById(R.id.etVehicleModel)
        etVehicleNumber = findViewById(R.id.etVehicleNumber)
        etVehicleColor = findViewById(R.id.etVehicleColor)
        etRegistrationYear = findViewById(R.id.etRegistrationYear)
        acVehicleType = findViewById(R.id.acVehicleType)
        btnSaveVehicle = findViewById(R.id.btnSaveVehicle)
        btnSkip = findViewById(R.id.btnSkip)

        tilVehicleMake = findViewById(R.id.tilVehicleMake)
        tilVehicleModel = findViewById(R.id.tilVehicleModel)
        tilVehicleNumber = findViewById(R.id.tilVehicleNumber)
        tilVehicleColor = findViewById(R.id.tilVehicleColor)
        tilRegistrationYear = findViewById(R.id.tilRegistrationYear)
        tilVehicleType = findViewById(R.id.tilVehicleType)

        // Set input types
        etRegistrationYear.inputType = InputType.TYPE_CLASS_NUMBER
        etVehicleNumber.inputType = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Vehicle Details"

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupDropdowns() {
        val vehicleTypes = VehicleType.values().map { it.displayName }.toTypedArray()
        val vehicleTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vehicleTypes)
        acVehicleType.setAdapter(vehicleTypeAdapter)
    }

    private fun setupTextWatchers() {
        etVehicleMake.addTextChangedListener { clearError(tilVehicleMake) }
        etVehicleModel.addTextChangedListener { clearError(tilVehicleModel) }
        etVehicleNumber.addTextChangedListener { clearError(tilVehicleNumber) }
        etVehicleColor.addTextChangedListener { clearError(tilVehicleColor) }
        etRegistrationYear.addTextChangedListener { clearError(tilRegistrationYear) }
        acVehicleType.addTextChangedListener { clearError(tilVehicleType) }
    }

    private fun clearError(layout: TextInputLayout) {
        layout.error = null
    }

    private fun setupClickListeners() {
        btnSaveVehicle.setOnClickListener {
            validateAndSaveVehicle()
        }

        btnSkip.setOnClickListener {
            finish()
        }
    }

    private fun loadExistingVehicleDetails() {
        lifecycleScope.launch {
            try {
                setLoading(true)
                existingVehicle = profileRepository.getVehicleDetails()

                existingVehicle?.let { vehicle ->
                    // Pre-fill existing data
                    val vehicleTypeDisplay = VehicleType.values().find { it.value == vehicle.vehicle_type }?.displayName ?: vehicle.vehicle_type
                    acVehicleType.setText(vehicleTypeDisplay, false)
                    etVehicleMake.setText(vehicle.vehicle_make ?: "")
                    etVehicleModel.setText(vehicle.vehicle_model ?: "")
                    etVehicleNumber.setText(vehicle.vehicle_number)
                    etVehicleColor.setText(vehicle.vehicle_color ?: "")
                    etRegistrationYear.setText(vehicle.registration_year?.toString() ?: "")

                    // Update UI for existing vehicle
                    supportActionBar?.title = "Update Vehicle Details"
                    btnSaveVehicle.text = "Update Vehicle"
                }
            } catch (e: Exception) {
                showError("Failed to load existing vehicle details: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun validateAndSaveVehicle() {
        var isValid = true
        clearAllErrors()

        val vehicleType = acVehicleType.text?.toString()?.trim()
        if (vehicleType.isNullOrEmpty()) {
            tilVehicleType.error = "Please select vehicle type"
            isValid = false
        }

        val vehicleMake = etVehicleMake.text?.toString()?.trim()
        if (vehicleMake.isNullOrEmpty()) {
            tilVehicleMake.error = "Vehicle make is required"
            isValid = false
        }

        val vehicleModel = etVehicleModel.text?.toString()?.trim()
        if (vehicleModel.isNullOrEmpty()) {
            tilVehicleModel.error = "Vehicle model is required"
            isValid = false
        }

        val vehicleNumber = etVehicleNumber.text?.toString()?.trim()?.uppercase()
        if (vehicleNumber.isNullOrEmpty()) {
            tilVehicleNumber.error = "Vehicle number is required"
            isValid = false
        } else if (!isValidVehicleNumber(vehicleNumber)) {
            tilVehicleNumber.error = "Please enter a valid vehicle number"
            isValid = false
        }

        val vehicleColor = etVehicleColor.text?.toString()?.trim()
        if (vehicleColor.isNullOrEmpty()) {
            tilVehicleColor.error = "Vehicle color is required"
            isValid = false
        }

        val registrationYearStr = etRegistrationYear.text?.toString()?.trim()
        val registrationYear = registrationYearStr?.toIntOrNull()
        if (registrationYear == null && !registrationYearStr.isNullOrEmpty()) {
            tilRegistrationYear.error = "Please enter a valid year"
            isValid = false
        } else if (registrationYear != null) {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (registrationYear < 1990 || registrationYear > currentYear) {
                tilRegistrationYear.error = "Year should be between 1990 and $currentYear"
                isValid = false
            }
        }

        if (!isValid) {
            showError("Please fill all fields correctly")
            return
        }

        // Save vehicle details
        saveVehicleDetails(
            vehicleType = getVehicleTypeValue(vehicleType!!),
            vehicleMake = vehicleMake,
            vehicleModel = vehicleModel,
            vehicleNumber = vehicleNumber!!,
            vehicleColor = vehicleColor,
            registrationYear = registrationYear
        )
    }

    private fun isValidVehicleNumber(vehicleNumber: String): Boolean {
        // Basic validation for Indian vehicle numbers
        // Format: XX00XX0000 or XX-00-XX-0000
        val pattern = """^[A-Z]{2}[-]?[0-9]{2}[-]?[A-Z]{1,2}[-]?[0-9]{4}$""".toRegex()
        return pattern.matches(vehicleNumber.replace(" ", ""))
    }

    private fun saveVehicleDetails(
        vehicleType: String,
        vehicleMake: String?,
        vehicleModel: String?,
        vehicleNumber: String,
        vehicleColor: String?,
        registrationYear: Int?
    ) {
        lifecycleScope.launch {
            try {
                setLoading(true)

                if (existingVehicle == null) {
                    // Create new vehicle details
                    profileRepository.saveVehicleDetails(
                        vehicleType = vehicleType,
                        vehicleMake = vehicleMake ?: "",
                        vehicleModel = vehicleModel ?: "",
                        vehicleNumber = vehicleNumber,
                        vehicleColor = vehicleColor ?: "",
                        registrationYear = registrationYear ?: 0
                    )
                } else {
                    // Update existing vehicle details
                    profileRepository.updateVehicleDetails(
                        vehicleDetails = existingVehicle!!,
                        vehicleType = vehicleType,
                        vehicleMake = vehicleMake ?: "",
                        vehicleModel = vehicleModel ?: "",
                        vehicleNumber = vehicleNumber,
                        vehicleColor = vehicleColor ?: "",
                        registrationYear = registrationYear ?: 0
                    )
                }

                showSuccess("Vehicle details saved successfully!")

                // Delay to show success message then finish
                kotlinx.coroutines.delay(1500)
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                showError("Failed to save vehicle details: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun getVehicleTypeValue(displayName: String): String {
        return VehicleType.values().find { it.displayName == displayName }?.value ?: displayName.lowercase()
    }

    private fun clearAllErrors() {
        tilVehicleType.error = null
        tilVehicleMake.error = null
        tilVehicleModel.error = null
        tilVehicleNumber.error = null
        tilVehicleColor.error = null
        tilRegistrationYear.error = null
    }

    private fun setLoading(loading: Boolean) {
        btnSaveVehicle.isEnabled = !loading
        btnSkip.isEnabled = !loading

        if (loading) {
            btnSaveVehicle.text = if (existingVehicle != null) "Updating..." else "Saving..."
        } else {
            btnSaveVehicle.text = if (existingVehicle != null) "Update Vehicle" else "Save Vehicle"
        }
    }

    private fun showError(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}