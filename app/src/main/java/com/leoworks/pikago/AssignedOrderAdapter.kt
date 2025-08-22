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
            val currentPhase = determineCurrentPhase(order)

            txtOrderId.text = order.id
            chipKind.text = when (currentPhase.phase) {
                OrderPhase.PICKUP_TO_PICKUP -> "Pickup Collection"
                OrderPhase.PICKUP_TO_STORE -> "To Store"
                OrderPhase.DELIVERY_FROM_STORE -> "From Store"
                OrderPhase.DELIVERY_TO_CUSTOMER -> "Final Delivery"
            }
            chipKind.contentDescription = "Order type: ${chipKind.text}"

            // Display date based on the current phase
            val dateStr = when (currentPhase.phase) {
                OrderPhase.PICKUP_TO_PICKUP, OrderPhase.PICKUP_TO_STORE -> order.pickup_date
                OrderPhase.DELIVERY_FROM_STORE, OrderPhase.DELIVERY_TO_CUSTOMER -> order.delivery_date
            }
            txtDateLabel.text = prettyDateLong(dateStr)

            // Slot display + small day label
            val slotDisplay = when (currentPhase.phase) {
                OrderPhase.PICKUP_TO_PICKUP, OrderPhase.PICKUP_TO_STORE -> order.pickup_slot_display_time
                OrderPhase.DELIVERY_FROM_STORE, OrderPhase.DELIVERY_TO_CUSTOMER -> order.delivery_slot_display_time
            }
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
            val timeStr = when (currentPhase.phase) {
                OrderPhase.PICKUP_TO_PICKUP, OrderPhase.PICKUP_TO_STORE -> order.pickup_slot_start_time
                OrderPhase.DELIVERY_FROM_STORE, OrderPhase.DELIVERY_TO_CUSTOMER -> order.delivery_slot_start_time
            }
            txtCountdown.text = buildCountdown(dateStr, timeStr)

            txtCountdownHint?.text = when (currentPhase.phase) {
                OrderPhase.PICKUP_TO_PICKUP -> "Ready to collect from pickup"
                OrderPhase.PICKUP_TO_STORE -> "Ready to deliver to store"
                OrderPhase.DELIVERY_FROM_STORE -> "Ready to collect from store"
                OrderPhase.DELIVERY_TO_CUSTOMER -> "Ready for final delivery"
            }

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

        private fun determineCurrentPhase(order: AssignedOrder): CurrentPhase {
            val status = (order.order_status ?: "").lowercase(Locale.getDefault())

            return when (status) {
                "assigned", "accepted", "processing", "confirmed" ->
                    CurrentPhase(OrderPhase.PICKUP_TO_PICKUP, "Go to pickup address")
                "picked_up" ->
                    CurrentPhase(OrderPhase.PICKUP_TO_STORE, "Deliver to store")
                "received", "ready_for_delivery" ->
                    CurrentPhase(OrderPhase.DELIVERY_FROM_STORE, "Collect from store")
                "shipped", "out_for_delivery" ->
                    CurrentPhase(OrderPhase.DELIVERY_TO_CUSTOMER, "Deliver to customer")
                else ->
                    CurrentPhase(OrderPhase.PICKUP_TO_PICKUP, "Unknown status")
            }
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

        private data class CurrentPhase(
            val phase: OrderPhase,
            val description: String
        )

        private enum class OrderPhase {
            PICKUP_TO_PICKUP,    // assigned -> pickup address
            PICKUP_TO_STORE,     // picked_up -> store
            DELIVERY_FROM_STORE, // ready_for_delivery -> store
            DELIVERY_TO_CUSTOMER // shipped -> customer
        }
    }
}