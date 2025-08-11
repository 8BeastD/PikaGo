// ProfileRepository.kt - Complete updated version
// =============================================================================

package com.leoworks.pikago.repository

import android.content.Context
import android.net.Uri
import com.leoworks.pikago.App
import com.leoworks.pikago.models.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.InputStream
import java.util.UUID
import kotlin.time.Duration.Companion.days

class ProfileRepository(private val context: Context) {

    private suspend fun ensureAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        try {
            var user = App.supabase.auth.currentUserOrNull()

            if (user == null) {
                runCatching { App.supabase.auth.loadFromStorage() }
                user = App.supabase.auth.currentUserOrNull()
            }

            if (user == null) return@withContext false

            suspend fun isQueryOK(): Boolean {
                return runCatching {
                    App.supabase.from("delivery_partners")
                        .select(Columns.list("id")) {
                            filter { eq("user_id", user!!.id) }
                        }
                    true
                }.getOrElse { false }
            }

            if (isQueryOK()) return@withContext true

            runCatching { App.supabase.auth.refreshCurrentSession() }
            user = App.supabase.auth.currentUserOrNull() ?: return@withContext false

            return@withContext isQueryOK()

        } catch (_: Exception) {
            false
        }
    }

    suspend fun isSessionValid(): Boolean {
        return try {
            ensureAuthenticated()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getDeliveryPartner(): DeliveryPartner? = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuthenticated()) {
                throw Exception("Please login again")
            }

            val userId = App.supabase.auth.currentUserOrNull()?.id ?: return@withContext null

            val result = App.supabase.from("delivery_partners")
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeSingleOrNull<DeliveryPartner>()

            result ?: createDeliveryPartnerRecord()
        } catch (e: Exception) {
            if (e.message?.contains("login") == true) {
                throw e
            }
            null
        }
    }

    suspend fun createDeliveryPartnerRecord(): DeliveryPartner = withContext(Dispatchers.IO) {
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

        val user = App.supabase.auth.currentUserOrNull()
            ?: throw Exception("User not authenticated")

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
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

        val user = App.supabase.auth.currentUserOrNull()
            ?: throw IllegalStateException("User not logged in")

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
            if (!ensureAuthenticated()) {
                throw Exception("Please login again")
            }

            App.supabase.from("delivery_partners")
                .update(partner) {
                    filter { eq("id", partner.id) }
                    select()
                }
                .decodeSingle<DeliveryPartner>()
        }

    suspend fun updateAvailabilityStatus(isAvailable: Boolean) = withContext(Dispatchers.IO) {
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

        val userId = App.supabase.auth.currentUserOrNull()?.id ?: return@withContext

        val body = buildJsonObject { put("is_available", JsonPrimitive(isAvailable)) }

        App.supabase.from("delivery_partners")
            .update(body) {
                filter { eq("user_id", userId) }
            }
    }

    private suspend fun ensureBucketExists(bucketName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            App.supabase.storage.from(bucketName).list()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadProfileImage(imageUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuthenticated()) {
                throw Exception("Please login again to upload profile image")
            }

            val authUser = App.supabase.auth.currentUserOrNull()
                ?: throw Exception("User session expired. Please login again")

            val fileName = "profile_${authUser.id}_${System.currentTimeMillis()}.jpg"
            val bucketName = "profile-images"

            if (!ensureBucketExists(bucketName)) {
                throw Exception("Storage not configured. Please create '$bucketName' bucket in Supabase dashboard")
            }

            val imageBytes = readBytesFromUri(imageUri)

            if (imageBytes.size > 5 * 1024 * 1024) {
                throw Exception("Image size too large. Please select an image smaller than 5MB")
            }
            if (imageBytes.isEmpty()) {
                throw Exception("Invalid image file. Please select a different image")
            }

            App.supabase.storage.from(bucketName).upload(fileName, imageBytes) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }

            val signedUrl = App.supabase.storage.from(bucketName)
                .createSignedUrl(fileName, 365.days)

            return@withContext signedUrl

        } catch (e: Exception) {
            when {
                e.message?.contains("not found") == true || e.message?.contains("bucket") == true -> {
                    throw Exception("Storage bucket 'profile-images' not found. Please create it in Supabase dashboard.")
                }
                e.message?.contains("login") == true || e.message?.contains("authenticated") == true -> {
                    throw Exception("Please login again to upload images")
                }
                e.message?.contains("size") == true -> throw e
                e.message?.contains("Invalid") == true -> throw e
                else -> throw Exception("Upload failed: ${e.message}")
            }
        }
    }

    suspend fun updateProfileImage(imageUrl: String) = withContext(Dispatchers.IO) {
        if (!ensureAuthenticated()) throw Exception("Please login again")

        val authUser = App.supabase.auth.currentUserOrNull()
            ?: throw Exception("User session expired. Please login again")

        val body = buildJsonObject {
            put("profile_photo_url", JsonPrimitive(imageUrl))
        }

        App.supabase.from("delivery_partners")
            .update(body) { filter { eq("user_id", authUser.id) } }
    }

    suspend fun getVerificationDocuments(): List<VerificationDocument> = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuthenticated()) {
                throw Exception("Please login again")
            }

            val partner = getDeliveryPartner() ?: return@withContext emptyList()

            App.supabase.from("verification_documents")
                .select {
                    filter { eq("delivery_partner_id", partner.id) }
                }
                .decodeList<VerificationDocument>()
        } catch (e: Exception) {
            if (e.message?.contains("login") == true) {
                throw e
            }
            emptyList()
        }
    }

    suspend fun uploadDocument(
        documentType: String,
        documentNumber: String,
        imageUri: Uri
    ): VerificationDocument = withContext(Dispatchers.IO) {
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

        val partner = getDeliveryPartner()
            ?: throw IllegalStateException("Delivery partner profile not found")

        val bucketName = "documents"

        if (!ensureBucketExists(bucketName)) {
            throw Exception("Documents storage not configured. Please create '$bucketName' bucket in Supabase dashboard")
        }

        val fileName = "${documentType}_${UUID.randomUUID()}.jpg"
        val bytes = readBytesFromUri(imageUri)

        if (bytes.size > 10 * 1024 * 1024) {
            throw Exception("Document size too large. Please select a smaller image")
        }

        App.supabase.storage.from(bucketName).upload(fileName, bytes) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }

        val documentUrl = App.supabase.storage.from(bucketName).publicUrl(fileName)

        val body = buildJsonObject {
            put("delivery_partner_id", JsonPrimitive(partner.id))
            put("document_type", JsonPrimitive(documentType))
            put("document_number", JsonPrimitive(documentNumber))
            put("document_url", JsonPrimitive(documentUrl))
            put("verification_status", JsonPrimitive("verified")) // Auto-verify for now
        }

        App.supabase.from("verification_documents")
            .insert(body) { select() }
            .decodeSingle<VerificationDocument>()
    }

    suspend fun getBankDetails(): BankDetails? = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuthenticated()) {
                throw Exception("Please login again")
            }

            val partner = getDeliveryPartner() ?: return@withContext null

            App.supabase.from("bank_details")
                .select {
                    filter { eq("delivery_partner_id", partner.id) }
                }
                .decodeSingleOrNull<BankDetails>()
        } catch (e: Exception) {
            if (e.message?.contains("login") == true) {
                throw e
            }
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
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

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
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

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
            if (!ensureAuthenticated()) {
                throw Exception("Please login again")
            }

            val partner = getDeliveryPartner() ?: return@withContext null

            App.supabase.from("vehicle_details")
                .select {
                    filter { eq("delivery_partner_id", partner.id) }
                }
                .decodeSingleOrNull<VehicleDetails>()
        } catch (e: Exception) {
            if (e.message?.contains("login") == true) {
                throw e
            }
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
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

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
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

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

    // NEW METHODS FOR VERIFICATION STATUS

    /**
     * Check if all required documents are verified and update verification status
     */
    suspend fun updateVerificationStatus(): String = withContext(Dispatchers.IO) {
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

        val partner = getDeliveryPartner()
            ?: throw IllegalStateException("Delivery partner profile not found")

        val documents = getVerificationDocuments()
        val requiredDocTypes = listOf("aadhar", "pan", "driving_license")

        // Check if all required documents are uploaded
        val uploadedDocTypes = documents.map { it.document_type }
        val allRequiredDocsUploaded = requiredDocTypes.all { it in uploadedDocTypes }

        // Check if all documents are verified
        val allDocsVerified = documents.filter { it.document_type in requiredDocTypes }
            .all { it.verification_status == "verified" }

        // Check profile completion
        val profileComplete = partner.first_name.isNotEmpty() &&
                partner.last_name.isNotEmpty() &&
                partner.address_line1.isNotEmpty() &&
                partner.city.isNotEmpty()

        val bankDetails = getBankDetails()
        val vehicleDetails = getVehicleDetails()

        val newStatus = when {
            // All conditions met for verification
            profileComplete && bankDetails != null && vehicleDetails != null &&
                    allRequiredDocsUploaded && allDocsVerified -> "verified"

            // Documents uploaded but not all verified, or profile incomplete
            allRequiredDocsUploaded && profileComplete && bankDetails != null && vehicleDetails != null -> "in_progress"

            // Some progress made
            profileComplete || bankDetails != null || vehicleDetails != null || documents.isNotEmpty() -> "in_progress"

            // Default pending status
            else -> "pending"
        }

        // Update verification status in database
        val body = buildJsonObject {
            put("verification_status", JsonPrimitive(newStatus))
        }

        App.supabase.from("delivery_partners")
            .update(body) {
                filter { eq("id", partner.id) }
            }

        newStatus
    }

    /**
     * Manual verification method (for admin use or testing)
     */
    suspend fun manuallyVerifyPartner(): DeliveryPartner = withContext(Dispatchers.IO) {
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

        val partner = getDeliveryPartner()
            ?: throw IllegalStateException("Delivery partner profile not found")

        val body = buildJsonObject {
            put("verification_status", JsonPrimitive("verified"))
        }

        App.supabase.from("delivery_partners")
            .update(body) {
                filter { eq("id", partner.id) }
                select()
            }
            .decodeSingle<DeliveryPartner>()
    }

    /**
     * Check individual document verification status
     */
    suspend fun checkDocumentVerificationStatus(documentId: String): String = withContext(Dispatchers.IO) {
        if (!ensureAuthenticated()) {
            throw Exception("Please login again")
        }

        val document = App.supabase.from("verification_documents")
            .select {
                filter { eq("id", documentId) }
            }
            .decodeSingleOrNull<VerificationDocument>()

        document?.verification_status ?: "pending"
    }

    /**
     * Updated calculateProfileCompletion that also updates verification status
     */
    suspend fun calculateProfileCompletionAndVerification(): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuthenticated()) {
                throw Exception("Please login again")
            }

            val partner = getDeliveryPartner() ?: return@withContext Pair(0, "pending")

            var completion = 0

            // Basic Profile (25%)
            val basicComplete = partner.first_name.isNotEmpty() &&
                    partner.last_name.isNotEmpty() &&
                    partner.address_line1.isNotEmpty() &&
                    partner.city.isNotEmpty()
            if (basicComplete) completion += 25

            // Profile Image (5%)
            if (!partner.profile_photo_url.isNullOrEmpty()) {
                completion += 5
            }

            // Bank Details (25%)
            val bankDetails = getBankDetails()
            if (bankDetails != null) completion += 25

            // Vehicle Details (25%)
            val vehicleDetails = getVehicleDetails()
            if (vehicleDetails != null) completion += 25

            // Documents (20%)
            val documents = getVerificationDocuments()
            val requiredDocs = listOf("aadhar", "pan", "driving_license")
            val completedDocs = documents.count { it.document_type in requiredDocs }
            completion += (completedDocs * 20) / requiredDocs.size

            // Determine verification status
            val allDocsVerified = documents.filter { it.document_type in requiredDocs }
                .all { it.verification_status == "verified" }

            val verificationStatus = when {
                completion == 100 && allDocsVerified -> "verified"
                completion >= 80 -> "in_progress"
                completion > 0 -> "in_progress"
                else -> "pending"
            }

            // Update both completion and verification status
            val body = buildJsonObject {
                put("profile_completion_percentage", JsonPrimitive(completion))
                put("verification_status", JsonPrimitive(verificationStatus))
            }

            App.supabase.from("delivery_partners")
                .update(body) {
                    filter { eq("id", partner.id) }
                }

            Pair(completion, verificationStatus)
        } catch (e: Exception) {
            if (e.message?.contains("login") == true) {
                throw e
            }
            Pair(0, "pending")
        }
    }

    /**
     * Legacy method - use calculateProfileCompletionAndVerification instead
     */
    suspend fun calculateProfileCompletion(): Int = withContext(Dispatchers.IO) {
        val (completion, _) = calculateProfileCompletionAndVerification()
        completion
    }

    /**
     * Verify all documents for a partner (for admin/testing)
     */
    suspend fun verifyAllDocuments(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!ensureAuthenticated()) {
                throw Exception("Please login again")
            }

            val partner = getDeliveryPartner() ?: return@withContext false

            val body = buildJsonObject {
                put("verification_status", JsonPrimitive("verified"))
            }

            App.supabase.from("verification_documents")
                .update(body) {
                    filter { eq("delivery_partner_id", partner.id) }
                }

            // Also update partner status
            updateVerificationStatus()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun readBytesFromUri(uri: Uri): ByteArray {
        return try {
            val resolver = context.contentResolver
            val input: InputStream = resolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open image from URI")
            input.use { it.readBytes() }
        } catch (e: Exception) {
            throw Exception("Failed to read image file: ${e.message}")
        }
    }
}