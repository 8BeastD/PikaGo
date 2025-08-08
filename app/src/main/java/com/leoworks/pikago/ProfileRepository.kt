package com.leoworks.pikago.repository

import android.content.Context
import android.net.Uri
import com.leoworks.pikago.App
import com.leoworks.pikago.models.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class ProfileRepository(private val context: Context) {

    suspend fun getDeliveryPartner(): DeliveryPartner? = withContext(Dispatchers.IO) {
        try {
            val userId = App.supabase.auth.currentUserOrNull()?.id ?: return@withContext null

            val result = App.supabase.from("delivery_partners")
                .select(){
                    filter {  eq("user_id", userId)  }
                }
                .decodeSingleOrNull<DeliveryPartner>()

            // If no delivery partner exists, create one
            if (result == null) {
                return@withContext createDeliveryPartnerRecord()
            }

            result
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createDeliveryPartnerRecord(): DeliveryPartner = withContext(Dispatchers.IO) {
        val user = App.supabase.auth.currentUserOrNull()
            ?: throw Exception("User not authenticated")

        // First, ensure user exists in users table
        try {
            App.supabase.from("users").insert(mapOf(
                "id" to user.id,
                "email" to (user.email ?: ""),
                "phone" to (user.phone ?: "")
            ))
        } catch (e: Exception) {
            // User might already exist, continue
        }

        // Create delivery partner record with minimal data
        val deliveryPartnerData = mapOf(
            "user_id" to user.id,
            "first_name" to "",
            "last_name" to "",
            "address_line1" to "",
            "city" to "",
            "state" to "",
            "pincode" to "",
            "profile_completion_percentage" to 0,
            "verification_status" to "pending",
            "is_available" to false
        )

        App.supabase.from("delivery_partners")
            .insert(deliveryPartnerData)
            .decodeSingle<DeliveryPartner>()
    }

    suspend fun createDeliveryPartner(
        firstName: String,
        lastName: String,
        phone: String,
        addressLine1: String,
        city: String,
        state: String,
        pincode: String
    ): DeliveryPartner = withContext(Dispatchers.IO) {
        val userId = App.supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User not logged in")

        val deliveryPartner = mapOf(
            "user_id" to userId,
            "first_name" to firstName,
            "last_name" to lastName,
            "address_line1" to addressLine1,
            "city" to city,
            "state" to state,
            "pincode" to pincode,
            "profile_completion_percentage" to 20
        )

        App.supabase.from("delivery_partners")
            .insert(deliveryPartner)
            .decodeSingle<DeliveryPartner>()
    }

    suspend fun updateDeliveryPartner(partner: DeliveryPartner): DeliveryPartner = withContext(Dispatchers.IO) {
        App.supabase.from("delivery_partners")
            .update(partner){
                filter {   eq("id", partner.id)}
            }
            .decodeSingle<DeliveryPartner>()
    }

    suspend fun updateAvailabilityStatus(isAvailable: Boolean) = withContext(Dispatchers.IO) {
        val userId = App.supabase.auth.currentUserOrNull()?.id ?: return@withContext

        App.supabase.from("delivery_partners")
            .update(mapOf("is_available" to isAvailable)){
                filter {  eq("user_id", userId) }
            }
    }

    suspend fun getVerificationDocuments(): List<VerificationDocument> = withContext(Dispatchers.IO) {
        try {
            val partner = getDeliveryPartner() ?: return@withContext emptyList()

            App.supabase.from("verification_documents")
                .select(){
                    filter {  eq("delivery_partner_id", partner.id) }
                }
                .decodeList<VerificationDocument>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun uploadDocument(
        documentType: String,
        documentNumber: String,
        imageUri: Uri
    ): VerificationDocument = withContext(Dispatchers.IO) {
        val partner = getDeliveryPartner()
            ?: throw IllegalStateException("Delivery partner profile not found")

        // Upload image to Supabase Storage
        val fileName = "${documentType}_${UUID.randomUUID()}.jpg"
        val file = File(imageUri.path!!)

        App.supabase.storage.from("documents").upload(fileName, file.readBytes())

        val documentUrl = App.supabase.storage.from("documents").publicUrl(fileName)

        val document = mapOf(
            "delivery_partner_id" to partner.id,
            "document_type" to documentType,
            "document_number" to documentNumber,
            "document_url" to documentUrl
        )

        App.supabase.from("verification_documents")
            .insert(document)
            .decodeSingle<VerificationDocument>()
    }

    suspend fun getBankDetails(): BankDetails? = withContext(Dispatchers.IO) {
        try {
            val partner = getDeliveryPartner() ?: return@withContext null

            App.supabase.from("bank_details")
                .select(){
                    filter {   eq("delivery_partner_id", partner.id) }
                }
                .decodeSingleOrNull<BankDetails>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveBankDetails(
        accountHolderName: String,
        accountNumber: String,
        ifscCode: String,
        bankName: String,
        branchName: String? = null
    ): BankDetails = withContext(Dispatchers.IO) {
        val partner = getDeliveryPartner()
            ?: throw IllegalStateException("Delivery partner profile not found")

        val bankDetails = mapOf(
            "delivery_partner_id" to partner.id,
            "account_holder_name" to accountHolderName,
            "account_number" to accountNumber,
            "ifsc_code" to ifscCode,
            "bank_name" to bankName,
            "branch_name" to branchName
        )

        App.supabase.from("bank_details")
            .insert(bankDetails)
            .decodeSingle<BankDetails>()
    }

    suspend fun getVehicleDetails(): VehicleDetails? = withContext(Dispatchers.IO) {
        try {
            val partner = getDeliveryPartner() ?: return@withContext null

            App.supabase.from("vehicle_details")
                .select(){
                    filter {   eq("delivery_partner_id", partner.id) }
                }
                .decodeSingleOrNull<VehicleDetails>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveVehicleDetails(
        vehicleType: String,
        vehicleMake: String,
        vehicleModel: String,
        vehicleNumber: String,
        vehicleColor: String,
        registrationYear: Int
    ): VehicleDetails = withContext(Dispatchers.IO) {
        val partner = getDeliveryPartner()
            ?: throw IllegalStateException("Delivery partner profile not found")

        val vehicleDetails = mapOf(
            "delivery_partner_id" to partner.id,
            "vehicle_type" to vehicleType,
            "vehicle_make" to vehicleMake,
            "vehicle_model" to vehicleModel,
            "vehicle_number" to vehicleNumber,
            "vehicle_color" to vehicleColor,
            "registration_year" to registrationYear
        )

        App.supabase.from("vehicle_details")
            .insert(vehicleDetails)
            .decodeSingle<VehicleDetails>()
    }

    suspend fun calculateProfileCompletion(): Int = withContext(Dispatchers.IO) {
        val partner = getDeliveryPartner() ?: return@withContext 0
        val documents = getVerificationDocuments()
        val bankDetails = getBankDetails()
        val vehicleDetails = getVehicleDetails()

        var completion = 0

        // Basic profile info (30%)
        if (partner.first_name.isNotEmpty() && partner.last_name.isNotEmpty() &&
            partner.address_line1.isNotEmpty() && partner.city.isNotEmpty()) {
            completion += 30
        }

        // Required documents (40%)
        val requiredDocs = listOf("aadhar", "pan", "driving_license")
        val completedDocs = documents.filter { it.document_type in requiredDocs }.size
        completion += (completedDocs * 40) / requiredDocs.size

        // Bank details (15%)
        if (bankDetails != null) {
            completion += 15
        }

        // Vehicle details (15%)
        if (vehicleDetails != null) {
            completion += 15
        }

        // Update completion percentage in database
        App.supabase.from("delivery_partners")
            .update(mapOf("profile_completion_percentage" to completion)){
                filter {  eq("id", partner.id)  }
            }

        completion
    }
}