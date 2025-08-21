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
import com.leoworks.pikago.OrderDetailsFragment
import com.leoworks.pikago.models.AssignedOrder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class AssignedOrderAdapter(
    private var items: List<AssignedOrder> = emptyList()
) : RecyclerView.Adapter<AssignedOrderAdapter.OrderVH>() {

    fun submit(list: List<AssignedOrder>) {
        // Filter out completed orders
        items = list.filter { order ->
            val status = order.order_status?.lowercase(Locale.getDefault()) ?: ""
            status != "completed"
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_assigned_order, parent, false)
        return OrderVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: OrderVH, position: Int) {
        holder.bind(items[position])
    }

    class OrderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val txtOrderId: TextView = v.findViewById(R.id.txtOrderId)
        private val txtDateLabel: TextView = v.findViewById(R.id.txtDateLabel)
        private val chipKind: Chip = v.findViewById(R.id.chipKind)
        private val txtSlot: TextView = v.findViewById(R.id.txtSlot)
        private val txtSlotDay: TextView = v.findViewById(R.id.txtSlotDay)
        private val txtAmountMethod: TextView = v.findViewById(R.id.txtAmountMethod)
        private val chipPaymentMethod: Chip = v.findViewById(R.id.chipPaymentMethod)
        private val txtCountdown: TextView = v.findViewById(R.id.txtCountdown)
        private val txtCountdownHint: TextView? = v.findViewById(R.id.txtCountdownHint) // may be null if old layout
        private val txtStatus: TextView = v.findViewById(R.id.txtStatus)
        private val btnProceed: MaterialButton? = v.findViewById(R.id.btnProceed)

        fun bind(order: AssignedOrder) {
            val isPickup = isPickup(order)

            txtOrderId.text = order.id
            chipKind.text = if (isPickup) "Pickup" else "Delivery"
            chipKind.contentDescription = "Order type: ${chipKind.text}"

            // Display "Today, Aug 16" style label based on the relevant date
            val dateStr = if (isPickup) order.pickup_date else order.delivery_date
            txtDateLabel.text = prettyDateLong(dateStr)

            // Slot display + small day label (Today/Tomorrow/Date)
            val slotDisplay = if (isPickup) order.pickup_slot_display_time else order.delivery_slot_display_time
            txtSlot.text = slotDisplay ?: "—"
            txtSlotDay.text = prettyDay(dateStr)

            // Amount + method
            val amount = order.total_amount ?: 0.0
            val method = order.payment_method?.uppercase(Locale.getDefault()) ?: "-"
            txtAmountMethod.text = "₹${formatMoney(amount)} • $method"
            chipPaymentMethod.text = method

            // Status
            txtStatus.text = order.order_status?.uppercase(Locale.getDefault()) ?: "-"

            // Countdown & hint
            val timeStr = if (isPickup) order.pickup_slot_start_time else order.delivery_slot_start_time
            txtCountdown.text = buildCountdown(dateStr, timeStr)
            txtCountdownHint?.text = if (isPickup) "Be ready for pickup" else "Be ready for delivery"

            btnProceed?.setOnClickListener {
                val activity = itemView.context as? FragmentActivity ?: return@setOnClickListener
                try {
                    val fragment = OrderDetailsFragment.newInstance(order.id)
                    activity.supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.container, fragment)
                        .addToBackStack("order_details")
                        .commit()
                } catch (_: Exception) {
                    Toast.makeText(itemView.context, "Failed to open order details", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ----- helpers -----

        private fun isPickup(order: AssignedOrder): Boolean {
            val s = (order.order_status ?: "").lowercase(Locale.getDefault())

            // Hide completed orders - they should not be shown
            if (s == "completed") {
                return false // This will be filtered out at adapter level
            }

            // Check if status contains "delivery" anywhere - if so, it's delivery phase
            if (s.contains("delivery")) {
                return false
            }

            // Explicit delivery statuses
            val deliveryStatuses = setOf("out_for_delivery", "shipped", "in_transit", "delivered", "ready_to_delivery")
            if (s in deliveryStatuses) {
                return false
            }

            // Explicit pickup statuses
            val pickupStatuses = setOf("accepted", "assigned", "ready_for_pickup", "processing", "confirmed")
            if (s in pickupStatuses) {
                return true
            }

            // Default fallback: if pickup info exists, assume pickup
            return !order.pickup_date.isNullOrBlank() && !order.pickup_slot_start_time.isNullOrBlank()
        }

        private fun prettyDateLong(dateStr: String?): String {
            if (dateStr.isNullOrBlank()) return "—"
            return try {
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val today = LocalDate.now()
                val base = date.format(DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault()))
                when {
                    date.isEqual(today) -> "Today, $base"
                    date.isEqual(today.plusDays(1)) -> "Tomorrow, $base"
                    else -> base
                }
            } catch (_: Exception) { "—" }
        }

        private fun prettyDay(dateStr: String?): String {
            if (dateStr.isNullOrBlank()) return "—"
            return try {
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val today = LocalDate.now()
                when {
                    date.isEqual(today) -> "Today"
                    date.isEqual(today.plusDays(1)) -> "Tomorrow"
                    else -> date.format(DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault()))
                }
            } catch (_: Exception) { "—" }
        }

        private fun formatMoney(value: Double): String {
            return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.getDefault(),"%.2f", value)
        }

        private fun buildCountdown(dateStr: String?, timeStr: String?): String {
            if (dateStr.isNullOrBlank() || timeStr.isNullOrBlank()) return "Starts in —"
            return try {
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("H:mm[:ss]"))
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
    }
}