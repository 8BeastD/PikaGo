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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.InputStream
import java.util.UUID

class ProfileRepository(private val context: Context) {

    /* ------------------------------- Queries ------------------------------- */

    suspend fun getDeliveryPartner(): DeliveryPartner? = withContext(Dispatchers.IO) {
        try {
            val userId = App.supabase.auth.currentUserOrNull()?.id ?: return@withContext null

            val result = App.supabase.from("delivery_partners")
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeSingleOrNull<DeliveryPartner>()

            // If no delivery partner exists, create one
            result ?: createDeliveryPartnerRecord()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createDeliveryPartnerRecord(): DeliveryPartner = withContext(Dispatchers.IO) {
        val user = App.supabase.auth.currentUserOrNull()
            ?: throw Exception("User not authenticated")

        // Upsert into users (JsonObject -> no custom serializer needed)
        runCatching {
            val usersUpsert = buildJsonObject {
                put("id", JsonPrimitive(user.id))
                put("email", user.email?.let { JsonPrimitive(it) } ?: JsonNull)
                put("phone", user.phone?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            App.supabase.from("users").upsert(usersUpsert) {
                onConflict = "id"
                ignoreDuplicates = true
            }
        }

        // Minimal partner bootstrap
        val partnerBootstrap = buildJsonObject {
            put("user_id", JsonPrimitive(user.id))
            put("first_name", JsonPrimitive(""))
            put("last_name", JsonPrimitive(""))
            put("address_line1", JsonPrimitive(""))
            put("city", JsonPrimitive(""))
            put("state", JsonPrimitive(""))
            put("pincode", JsonPrimitive(""))
            put("profile_completion_percentage", JsonPrimitive(0))
            put("verification_status", JsonPrimitive("pending"))
            put("is_available", JsonPrimitive(false))
        }

        App.supabase.from("delivery_partners")
            .insert(partnerBootstrap) { select() }
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
        val user = App.supabase.auth.currentUserOrNull()
            ?: throw IllegalStateException("User not logged in")

        // keep users table in sync (optional)
        runCatching {
            val usersUpsert = buildJsonObject {
                put("id", JsonPrimitive(user.id))
                put("email", user.email?.let { JsonPrimitive(it) } ?: JsonNull)
                put("phone", JsonPrimitive(phone))
            }
            App.supabase.from("users").upsert(usersUpsert) {
                onConflict = "id"
                ignoreDuplicates = true
            }
        }

        val body = buildJsonObject {
            put("user_id", JsonPrimitive(user.id))
            put("first_name", JsonPrimitive(firstName))
            put("last_name", JsonPrimitive(lastName))
            put("address_line1", JsonPrimitive(addressLine1))
            put("city", JsonPrimitive(city))
            put("state", JsonPrimitive(state))
            put("pincode", JsonPrimitive(pincode))
            put("profile_completion_percentage", JsonPrimitive(20))
        }

        App.supabase.from("delivery_partners")
            .insert(body) { select() }
            .decodeSingle<DeliveryPartner>()
    }

    suspend fun updateDeliveryPartner(partner: DeliveryPartner): DeliveryPartner =
        withContext(Dispatchers.IO) {
            App.supabase.from("delivery_partners")
                .update(partner) {
                    filter { eq("id", partner.id) }
                    select()
                }
                .decodeSingle<DeliveryPartner>()
        }

    suspend fun updateAvailabilityStatus(isAvailable: Boolean) = withContext(Dispatchers.IO) {
        val userId = App.supabase.auth.currentUserOrNull()?.id ?: return@withContext

        val body = buildJsonObject { put("is_available", JsonPrimitive(isAvailable)) }

        App.supabase.from("delivery_partners")
            .update(body) {
                filter { eq("user_id", userId) }
            }
    }

    suspend fun getVerificationDocuments(): List<VerificationDocument> = withContext(Dispatchers.IO) {
        try {
            val partner = getDeliveryPartner() ?: return@withContext emptyList()

            App.supabase.from("verification_documents")
                .select {
                    filter { eq("delivery_partner_id", partner.id) }
                }
                .decodeList<VerificationDocument>()
        } catch (_: Exception) {
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

        val fileName = "${documentType}_${UUID.randomUUID()}.jpg"
        val bytes = readBytesFromUri(imageUri)

        App.supabase.storage.from("documents").upload(fileName, bytes)
        val documentUrl = App.supabase.storage.from("documents").publicUrl(fileName)

        val body = buildJsonObject {
            put("delivery_partner_id", JsonPrimitive(partner.id))
            put("document_type", JsonPrimitive(documentType))
            put("document_number", JsonPrimitive(documentNumber))
            put("document_url", JsonPrimitive(documentUrl))
        }

        App.supabase.from("verification_documents")
            .insert(body) { select() }
            .decodeSingle<VerificationDocument>()
    }

    suspend fun getBankDetails(): BankDetails? = withContext(Dispatchers.IO) {
        try {
            val partner = getDeliveryPartner() ?: return@withContext null

            App.supabase.from("bank_details")
                .select {
                    filter { eq("delivery_partner_id", partner.id) }
                }
                .decodeSingleOrNull<BankDetails>()
        } catch (_: Exception) {
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

        val body = buildJsonObject {
            put("delivery_partner_id", JsonPrimitive(partner.id))
            put("account_holder_name", JsonPrimitive(accountHolderName))
            put("account_number", JsonPrimitive(accountNumber))
            put("ifsc_code", JsonPrimitive(ifscCode))
            put("bank_name", JsonPrimitive(bankName))
            put("branch_name", branchName?.let { JsonPrimitive(it) } ?: JsonNull)
        }

        App.supabase.from("bank_details")
            .insert(body) { select() }
            .decodeSingle<BankDetails>()
    }

    suspend fun updateBankDetails(
        bankDetails: BankDetails,
        accountHolderName: String,
        accountNumber: String,
        ifscCode: String,
        bankName: String,
        branchName: String? = null
    ): BankDetails = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("account_holder_name", JsonPrimitive(accountHolderName))
            put("account_number", JsonPrimitive(accountNumber))
            put("ifsc_code", JsonPrimitive(ifscCode))
            put("bank_name", JsonPrimitive(bankName))
            put("branch_name", branchName?.let { JsonPrimitive(it) } ?: JsonNull)
        }

        App.supabase.from("bank_details")
            .update(body) {
                filter { eq("id", bankDetails.id) }
                select()
            }
            .decodeSingle<BankDetails>()
    }

    suspend fun getVehicleDetails(): VehicleDetails? = withContext(Dispatchers.IO) {
        try {
            val partner = getDeliveryPartner() ?: return@withContext null

            App.supabase.from("vehicle_details")
                .select {
                    filter { eq("delivery_partner_id", partner.id) }
                }
                .decodeSingleOrNull<VehicleDetails>()
        } catch (_: Exception) {
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

        val body = buildJsonObject {
            put("delivery_partner_id", JsonPrimitive(partner.id))
            put("vehicle_type", JsonPrimitive(vehicleType))
            put("vehicle_make", JsonPrimitive(vehicleMake))
            put("vehicle_model", JsonPrimitive(vehicleModel))
            put("vehicle_number", JsonPrimitive(vehicleNumber))
            put("vehicle_color", JsonPrimitive(vehicleColor))
            put("registration_year", JsonPrimitive(registrationYear))
        }

        App.supabase.from("vehicle_details")
            .insert(body) { select() }
            .decodeSingle<VehicleDetails>()
    }

    suspend fun updateVehicleDetails(
        vehicleDetails: VehicleDetails,
        vehicleType: String,
        vehicleMake: String,
        vehicleModel: String,
        vehicleNumber: String,
        vehicleColor: String,
        registrationYear: Int
    ): VehicleDetails = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("vehicle_type", JsonPrimitive(vehicleType))
            put("vehicle_make", JsonPrimitive(vehicleMake))
            put("vehicle_model", JsonPrimitive(vehicleModel))
            put("vehicle_number", JsonPrimitive(vehicleNumber))
            put("vehicle_color", JsonPrimitive(vehicleColor))
            put("registration_year", JsonPrimitive(registrationYear))
        }

        App.supabase.from("vehicle_details")
            .update(body) {
                filter { eq("id", vehicleDetails.id) }
                select()
            }
            .decodeSingle<VehicleDetails>()
    }

    suspend fun calculateProfileCompletion(): Int = withContext(Dispatchers.IO) {
        val partner = getDeliveryPartner() ?: return@withContext 0

        var completion = 0

        // Basic Profile (30%)
        if (partner.first_name.isNotEmpty() && partner.last_name.isNotEmpty() &&
            partner.address_line1.isNotEmpty() && partner.city.isNotEmpty()
        ) {
            completion += 30
        }

        // Bank Details (25%)
        val bankDetails = getBankDetails()
        if (bankDetails != null) {
            completion += 25
        }

        // Vehicle Details (25%)
        val vehicleDetails = getVehicleDetails()
        if (vehicleDetails != null) {
            completion += 25
        }

        // Documents (20%)
        val documents = getVerificationDocuments()
        val requiredDocs = listOf("aadhar", "pan", "driving_license")
        val completedDocs = documents.count { it.document_type in requiredDocs }
        completion += (completedDocs * 20) / requiredDocs.size

        // Update the completion percentage in database
        val body = buildJsonObject {
            put("profile_completion_percentage", JsonPrimitive(completion))
        }

        App.supabase.from("delivery_partners")
            .update(body) {
                filter { eq("id", partner.id) }
            }

        completion
    }

    /* ------------------------------- Helpers ------------------------------- */

    private fun readBytesFromUri(uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val input: InputStream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open image")
        return input.use { it.readBytes() }
    }
}