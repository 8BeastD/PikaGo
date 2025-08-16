package com.leoworks.pikago.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.leoworks.pikago.App
import com.leoworks.pikago.databinding.SheetOrderDetailsBinding
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.NumberFormat
import java.util.Locale

class OrderDetailsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetOrderDetailsBinding? = null
    private val binding get() = _binding!!

    private val supabase by lazy { App.supabase }

    private val orderId: String by lazy { requireArguments().getString(ARG_ORDER_ID)!! }

    // destination cache
    private var destLat: Double? = null
    private var destLng: Double? = null
    private var orderStatus: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetOrderDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    // NOTE: Removed getTheme() override to avoid unresolved R style issues.

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }
        binding.tvOrderCode.text = orderId

        setupSwipeToNavigate()
        fetchOrderDetails()
    }

    private fun fetchOrderDetails() {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // First get order details with address info
                val orderRows = supabase.from("assigned_orders").select {
                    filter { eq("id", orderId) }
                    limit(1)
                }.decodeList<AssignedOrderRow>()

                val orderRow = orderRows.firstOrNull()
                if (orderRow == null) {
                    Toast.makeText(requireContext(), "Order not found", Toast.LENGTH_LONG).show()
                    dismiss()
                    return@launch
                }

                orderStatus = orderRow.order_status

                // Determine which address to show based on order status
                val addressToShow = determineAddressToShow(orderRow)
                bindAddress(addressToShow)

                // Then get order items
                val itemRows = supabase.from("order_items")
                    .select { filter { eq("order_id", orderId) } }
                    .decodeList<OrderItemRow>()

                val totalItems = itemRows.sumOf { (it.quantity ?: 0) }
                val totalAmount = itemRows.sumOf { (it.total_price ?: 0.0) }
                val currency = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

                binding.tvTotalItems.text = totalItems.toString()
                binding.tvTotalAmount.text = currency.format(totalAmount)

                // Build clean vertical rows
                binding.itemsList.removeAllViews()
                itemRows.forEach { row ->
                    addItemRow(
                        name = row.product_name ?: "Item",
                        qty = row.quantity ?: 0,
                        price = currency.format(row.total_price ?: 0.0)
                    )
                }

                binding.groupContent.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.groupContent.visibility = View.GONE
                Toast.makeText(requireContext(), e.message ?: "Unable to load order details", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private suspend fun determineAddressToShow(orderRow: AssignedOrderRow): JsonObject? {
        val status = orderRow.order_status?.lowercase().orEmpty()

        // Logic:
        // For pickup statuses -> show pickup address (address_details)
        // For delivery statuses -> show delivery address (drop_address)
        val pickupStatuses = setOf("accepted", "assigned", "ready_for_pickup", "processing", "confirmed")
        val deliveryStatuses = setOf("picked_up", "out_for_delivery", "ready_for_delivery", "shipped", "in_transit", "delivered")

        return when {
            status in pickupStatuses -> orderRow.address_details
            status in deliveryStatuses -> orderRow.drop_address
            // Default fallback
            else -> orderRow.address_details ?: orderRow.drop_address
        }
    }

    private fun addItemRow(name: String, qty: Int, price: String) {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 10, 12, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val left = TextView(ctx).apply {
            text = "$name  •  x$qty"
            setTextColor(ContextCompat.getColor(ctx, android.R.color.secondary_text_light))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val right = TextView(ctx).apply {
            text = price
            setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        row.addView(left)
        row.addView(right)
        binding.itemsList.addView(row)
    }

    private fun bindAddress(addressJson: JsonObject?) {
        if (addressJson == null) {
            binding.tvRecipient.text = "—"
            binding.tvPhone.text = "—"
            binding.tvAddress.text = "No address available"
            return
        }

        fillAddressFromJson(addressJson)
    }

    private fun fillAddressFromJson(obj: JsonObject) {
        fun jStr(key: String): String? =
            runCatching { obj[key]?.jsonPrimitive?.content }
                .getOrNull()
                ?.takeIf { it.lowercase(Locale.ROOT) != "null" }

        fun jDbl(key: String): Double? =
            runCatching { obj[key]?.jsonPrimitive?.content?.toDouble() }.getOrNull()

        val recipient = jStr("recipient_name") ?: "—"
        val phone = jStr("phone_number") ?: "—"
        val line1 = jStr("address_line_1") ?: ""
        val line2 = jStr("address_line_2") ?: ""
        val city = jStr("city") ?: ""
        val state = jStr("state") ?: ""
        val pincode = jStr("pincode") ?: ""

        // also accept alt keys
        destLat = jDbl("latitude") ?: jDbl("lat")
        destLng = jDbl("longitude") ?: jDbl("lng") ?: jDbl("lon")

        binding.tvRecipient.text = recipient
        binding.tvPhone.text = phone

        val addressLines = listOf(
            line1,
            line2,
            listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")
        ).filter { it.isNotBlank() }

        binding.tvAddress.text = buildString {
            append(addressLines.joinToString("\n"))
            if (pincode.isNotBlank()) append("\n$pincode")
        }
    }

    private fun setupSwipeToNavigate() {
        binding.sliderNavigate.value = 0f
        binding.sliderNavigate.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (slider.value >= 95f) {
                    startNavigation()
                    slider.value = 0f
                } else {
                    ValueAnimator.ofFloat(slider.value, 0f).apply {
                        duration = 200
                        addUpdateListener { slider.value = it.animatedValue as Float }
                        start()
                    }
                }
            }
        })
    }

    private fun startNavigation() {
        val lat = destLat
        val lng = destLng
        if (lat == null || lng == null) {
            Toast.makeText(requireContext(), "No destination coordinates in address.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(requireContext(), NavigationActivity::class.java).apply {
            putExtra(NavigationActivity.EXTRA_ORDER_ID, orderId)
            putExtra(NavigationActivity.EXTRA_DEST_LAT, lat)
            putExtra(NavigationActivity.EXTRA_DEST_LNG, lng)
        }
        startActivity(intent)
        dismiss()
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ORDER_ID = "order_id"

        fun newInstance(orderId: String): OrderDetailsSheet {
            return OrderDetailsSheet().apply {
                arguments = bundleOf(ARG_ORDER_ID to orderId)
            }
        }

        fun show(activity: FragmentActivity, orderId: String) {
            newInstance(orderId).show(activity.supportFragmentManager, "order_details_sheet")
        }
    }
}

@Serializable
data class OrderItemRow(
    val product_name: String? = null,
    val quantity: Int? = null,
    val total_price: Double? = null
)

@Serializable
data class AssignedOrderRow(
    val id: String,
    val order_status: String? = null,
    val address_details: JsonObject? = null, // pickup address
    val drop_address: JsonObject? = null     // delivery address
)
