package com.leoworks.pikago.models

import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class User(
    val id: String,
    val email: String,
    val phone: String,
    val created_at: String,
    val updated_at: String
)

@Serializable
data class DeliveryPartner(
    val id: String,
    val user_id: String,
    val first_name: String,
    val last_name: String,
    val date_of_birth: String?,
    val gender: String?,
    val profile_photo_url: String?,
    val address_line1: String,
    val address_line2: String?,
    val city: String,
    val state: String,
    val pincode: String,
    val emergency_contact_name: String?,
    val emergency_contact_phone: String?,
    val is_available: Boolean = false,
    val verification_status: String = "pending",
    val profile_completion_percentage: Int = 0,
    val created_at: String,
    val updated_at: String
)

@Serializable
data class VerificationDocument(
    val id: String,
    val delivery_partner_id: String,
    val document_type: String,
    val document_number: String?,
    val document_url: String?,
    val verification_status: String = "pending",
    val remarks: String?,
    val uploaded_at: String,
    val verified_at: String?
)

@Serializable
data class BankDetails(
    val id: String,
    val delivery_partner_id: String,
    val account_holder_name: String,
    val account_number: String,
    val ifsc_code: String,
    val bank_name: String,
    val branch_name: String?,
    val account_type: String = "savings",
    val is_verified: Boolean = false,
    val created_at: String,
    val updated_at: String
)

@Serializable
data class VehicleDetails(
    val id: String,
    val delivery_partner_id: String,
    val vehicle_type: String,
    val vehicle_make: String?,
    val vehicle_model: String?,
    val vehicle_number: String,
    val vehicle_color: String?,
    val registration_year: Int?,
    val is_verified: Boolean = false,
    val created_at: String,
    val updated_at: String
)

// Helper data classes for UI
data class ProfileSection(
    val title: String,
    val isCompleted: Boolean,
    val progress: Int
)

data class DocumentUploadRequest(
    val documentType: String,
    val documentNumber: String,
    val imageUri: String
)

enum class DocumentType(val value: String, val displayName: String) {
    AADHAR("aadhar", "Aadhar Card"),
    PAN("pan", "PAN Card"),
    DRIVING_LICENSE("driving_license", "Driving License"),
    VEHICLE_REGISTRATION("vehicle_registration", "Vehicle Registration"),
    VEHICLE_INSURANCE("vehicle_insurance", "Vehicle Insurance")
}

enum class VerificationStatus(val value: String, val displayName: String) {
    PENDING("pending", "Pending"),
    IN_PROGRESS("in_progress", "In Progress"),
    VERIFIED("verified", "Verified"),
    REJECTED("rejected", "Rejected")
}

enum class VehicleType(val value: String, val displayName: String) {
    BIKE("bike", "Bike"),
    SCOOTER("scooter", "Scooter"),
    BICYCLE("bicycle", "Bicycle"),
    CAR("car", "Car")
}