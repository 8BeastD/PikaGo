package com.leoworks.pikago.models

import kotlinx.serialization.Serializable

@Serializable
data class AssignedOrder(
    val id: String,
    val user_id: String? = null,
    val other_db_user_id: String? = null,
    val total_amount: Double? = 0.0,
    val payment_method: String? = null,
    val payment_status: String? = null,
    val payment_id: String? = null,
    val order_status: String? = null,

    val pickup_date: String? = null,                 // "2025-08-04"
    val pickup_slot_id: String? = null,
    val pickup_slot_display_time: String? = null,    // "08:00AM - 10:00AM"
    val pickup_slot_start_time: String? = null,      // "08:00:00"
    val pickup_slot_end_time: String? = null,        // "10:00:00"

    val delivery_date: String? = null,               // "2025-08-04"
    val delivery_slot_id: String? = null,
    val delivery_slot_display_time: String? = null,  // "08:00 PM - 10:00 PM"
    val delivery_slot_start_time: String? = null,    // "20:00:00"
    val delivery_slot_end_time: String? = null,      // "22:00:00"

    val delivery_type: String? = null,
    val delivery_address: String? = null,

    val status: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)
