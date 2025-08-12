package com.leoworks.pikago.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.leoworks.pikago.R
import com.leoworks.pikago.models.AssignedOrder
import com.leoworks.pikago.ui.OrderDetailsSheet
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import org.json.JSONObject
import org.json.JSONArray
import android.util.Log
import kotlinx.serialization.json.JsonObject

class AssignedOrderAdapter(
    private var items: List<AssignedOrder> = emptyList(),
    private var mode: Mode = Mode.PICKUP
) : RecyclerView.Adapter<AssignedOrderAdapter.OrderVH>() {

    enum class Mode { PICKUP, DELIVERY }

    fun submit(list: List<AssignedOrder>, newMode: Mode) {
        mode = newMode
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_assigned_order, parent, false)
        return OrderVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: OrderVH, position: Int) {
        holder.bind(items[position], mode)
    }

    class OrderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val txtOrderId: TextView = v.findViewById(R.id.txtOrderId)
        private val chipKind: Chip = v.findViewById(R.id.chipKind)
        private val txtSlot: TextView = v.findViewById(R.id.txtSlot)
        private val txtAmountMethod: TextView = v.findViewById(R.id.txtAmountMethod)
        private val txtCountdown: TextView = v.findViewById(R.id.txtCountdown)
        private val txtStatus: TextView = v.findViewById(R.id.txtStatus)
        private val btnProceed: MaterialButton? = v.findViewById(R.id.btnProceed)

        fun bind(order: AssignedOrder, mode: Mode) {
            val isPickup = mode == Mode.PICKUP
            txtOrderId.text = order.id
            chipKind.text = if (isPickup) "Pickup" else "Delivery"

            val slotDisplay = if (isPickup) order.pickup_slot_display_time else order.delivery_slot_display_time
            txtSlot.text = "Slot: ${slotDisplay ?: "-"}"

            val amount = order.total_amount ?: 0.0
            val method = order.payment_method?.uppercase() ?: "-"
            txtAmountMethod.text = "₹${formatMoney(amount)} • $method"

            txtStatus.text = order.order_status ?: "-"

            val dateStr = if (isPickup) order.pickup_date else order.delivery_date
            val timeStr = if (isPickup) order.pickup_slot_start_time else order.delivery_slot_start_time
            txtCountdown.text = buildCountdown(dateStr, timeStr)

            btnProceed?.setOnClickListener {
                val activity = itemView.context as? FragmentActivity ?: return@setOnClickListener
                val addressJson = normalizedAddressJson(order)
                if (addressJson.isNullOrBlank()) {
                    Toast.makeText(itemView.context, "Address details not available.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Optional: sanity check
                try {
                    val obj = JSONObject(addressJson)
                    val hasLat = obj.has("latitude") || obj.has("lat")
                    val hasLng = obj.has("longitude") || obj.has("lng")
                    if (!hasLat || !hasLng) {
                        Log.w("OrderAdapter", "Address missing coordinates but proceeding anyway")
                    }
                } catch (e: Exception) {
                    Log.e("OrderAdapter", "Address JSON validation failed: ${e.message}")
                }

                OrderDetailsSheet.show(
                    activity = activity,
                    orderId = order.id,
                    addressJson = addressJson
                )
            }
        }

        private fun formatMoney(value: Double): String {
            return if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.2f", value)
        }

        private fun buildCountdown(dateStr: String?, timeStr: String?): String {
            if (dateStr.isNullOrBlank() || timeStr.isNullOrBlank()) return "Starts in —"
            return try {
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss"))
                val zone = ZoneId.systemDefault()
                val target = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
                val now = System.currentTimeMillis()
                val diff = max(0L, target - now)

                val totalSec = diff / 1000
                val hh = totalSec / 3600
                val mm = (totalSec % 3600) / 60
                val ss = (totalSec % 60)

                if (diff == 0L) "Starting now"
                else "Starts in %02d:%02d:%02d".format(hh, mm, ss)
            } catch (_: Exception) {
                "Starts in —"
            }
        }

        /**
         * Prefer model field; handle String, Map, and kotlinx JsonObject.
         */
        private fun normalizedAddressJson(order: AssignedOrder): String? {
            val addressDetails: Any? = order.address_details
                ?: getAddressDetailsField(order) // legacy fallback via reflection

            // 1) If kotlinx JsonObject (from Supabase jsonb)
            if (addressDetails is JsonObject) {
                return try {
                    val obj = JSONObject(addressDetails.toString())
                    normalizeLatLng(obj).toString()
                } catch (e: Exception) {
                    Log.e("OrderAdapter", "Failed to convert JsonObject: ${e.message}")
                    null
                }
            }

            // 2) If raw JSON String
            if (addressDetails is String && addressDetails.isNotBlank()) {
                return try {
                    val cleanJson = cleanJsonString(addressDetails)
                    val obj = JSONObject(cleanJson)
                    normalizeLatLng(obj).toString()
                } catch (e: Exception) {
                    Log.e("OrderAdapter", "Failed to parse address JSON: ${e.message}")
                    Log.e("OrderAdapter", "Raw address data: $addressDetails")
                    createFallbackAddressJson(addressDetails)
                }
            }

            // 3) If Map from Supabase
            if (addressDetails is Map<*, *>) {
                return try {
                    val obj = mapToJsonObject(addressDetails)
                    normalizeLatLng(obj).toString()
                } catch (e: Exception) {
                    Log.e("OrderAdapter", "Failed to convert Map to JSON: ${e.message}")
                    null
                }
            }

            return null
        }

        private fun cleanJsonString(jsonStr: String): String {
            return jsonStr.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .replace("\\\"", "\"")
                .let { cleaned -> if (!cleaned.startsWith("{")) "{$cleaned" else cleaned }
                .let { cleaned -> if (!cleaned.endsWith("}")) "$cleaned}" else cleaned }
        }

        private fun createFallbackAddressJson(rawAddress: String): String {
            return try {
                JSONObject().apply {
                    put("address_line_1", rawAddress.take(100))
                    put("recipient_name", "")
                    put("phone_number", "")
                    put("city", "")
                    put("state", "")
                    put("pincode", "")
                    put("latitude", 0.0)
                    put("longitude", 0.0)
                    put("lat", 0.0)
                    put("lng", 0.0)
                }.toString()
            } catch (e: Exception) {
                """{"address_line_1":"Address not available","latitude":0.0,"longitude":0.0,"lat":0.0,"lng":0.0}"""
            }
        }

        /**
         * Legacy reflection (kept for safety in case the model isn’t updated in some build flavor)
         */
        private fun getAddressDetailsField(order: AssignedOrder): Any? {
            return try {
                val field = order.javaClass.getDeclaredField("address_details")
                field.isAccessible = true
                field.get(order)
            } catch (e: Exception) {
                try {
                    val field = order.javaClass.getDeclaredField("addressDetails")
                    field.isAccessible = true
                    field.get(order)
                } catch (_: Exception) {
                    null
                }
            }
        }

        private fun mapToJsonObject(map: Map<*, *>): JSONObject {
            val obj = JSONObject()
            for ((k, v) in map) {
                val key = k?.toString() ?: continue
                when (v) {
                    is Map<*, *> -> obj.put(key, mapToJsonObject(v))
                    is List<*> -> obj.put(key, listToJsonArray(v))
                    null -> obj.put(key, JSONObject.NULL)
                    else -> obj.put(key, v)
                }
            }
            return obj
        }

        private fun listToJsonArray(list: List<*>): JSONArray {
            val arr = JSONArray()
            list.forEach { v ->
                when (v) {
                    is Map<*, *> -> arr.put(mapToJsonObject(v))
                    is List<*> -> arr.put(listToJsonArray(v))
                    null -> arr.put(JSONObject.NULL)
                    else -> arr.put(v)
                }
            }
            return arr
        }

        private fun normalizeLatLng(obj: JSONObject): JSONObject {
            val lat = extractDouble(obj, "latitude") ?: extractDouble(obj, "lat")
            val lng = extractDouble(obj, "longitude") ?: extractDouble(obj, "lng")
            if (lat != null) {
                obj.put("latitude", lat)
                obj.put("lat", lat)
            }
            if (lng != null) {
                obj.put("longitude", lng)
                obj.put("lng", lng)
            }
            return obj
        }

        private fun extractDouble(obj: JSONObject, key: String): Double? {
            if (!obj.has(key)) return null
            return try {
                when (val v = obj.get(key)) {
                    is Number -> v.toDouble()
                    is String -> v.toDoubleOrNull()
                    else -> null
                }
            } catch (_: Exception) { null }
        }
    }
}
