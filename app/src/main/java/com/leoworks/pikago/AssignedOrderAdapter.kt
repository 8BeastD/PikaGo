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
import kotlin.math.max

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
                try {
                    // Navigate with only the orderId; OrderDetailsFragment fetches everything itself
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
    }
}
