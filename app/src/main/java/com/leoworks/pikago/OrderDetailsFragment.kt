package com.leoworks.pikago

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.leoworks.pikago.databinding.FragmentOrderDetailsBinding
import com.leoworks.pikago.App
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class OrderDetailsFragment : Fragment() {

    private var _binding: FragmentOrderDetailsBinding? = null
    private val binding get() = _binding!!

    private var orderId: String = ""

    private val supabase by lazy { App.supabase }

    // Loaded data
    private var assigned: AssignedOrderRow? = null
    private var items: List<OrderItemRow> = emptyList()

    // Address JSONs for call button / display
    private var pickupAddress: JSONObject? = null
    private var deliveryAddress: JSONObject? = null

    private lateinit var orderItemsAdapter: OrderItemsAdapter

    companion object {
        private const val ARG_ORDER_ID = "order_id"

        fun newInstance(orderId: String): OrderDetailsFragment {
            return OrderDetailsFragment().apply {
                arguments = Bundle().apply { putString(ARG_ORDER_ID, orderId) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orderId = arguments?.getString(ARG_ORDER_ID) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupUI()

        lifecycleScope.launch {
            loadAssigned()
            loadItems()
            bindAll()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------------- UI ----------------

    private fun setupRecyclerView() {
        orderItemsAdapter = OrderItemsAdapter()
        binding.recyclerOrderItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = orderItemsAdapter
        }
    }

    private fun setupUI() {
        binding.toolbar?.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        binding.toolbar?.title = "Order #$orderId"

        binding.btnStartNavigation.setOnClickListener { startNavigation() }
        binding.btnCallCustomer.setOnClickListener { callCustomer() }
        binding.btnMarkComplete.setOnClickListener { markOrderComplete() }
        binding.btnCopyCode.setOnClickListener { copyDeliveryCode() }
    }

    private fun bindAll() {
        binding.txtOrderId.text = orderId
        bindHeader()
        bindPayment()
        bindAddresses()
        bindItems()
        bindDeliveryCode()
    }

    private fun bindHeader() {
        val a = assigned ?: run {
            binding.txtDateTime.text = "Date not available"
            binding.txtTotalAmount.text = "₹0"
            return
        }

        val status = a.orderStatus ?: "Pending"
        binding.chipOrderStatus.text = status
        binding.chipOrderStatus.chipBackgroundColor = ContextCompat.getColorStateList(
            requireContext(),
            when (status.lowercase()) {
                "completed" -> R.color.success_color
                "in_progress", "picked_up" -> R.color.primary_color
                "cancelled" -> R.color.error_color
                else -> R.color.warning_color
            }
        )

        val pickupDate = a.pickupDate
        val pickupTime = a.pickupSlotDisplayTime
        val deliveryTime = a.deliverySlotDisplayTime
        binding.txtDateTime.text = formatDateTime(pickupDate, pickupTime, deliveryTime)

        val totalAmount = a.totalAmount ?: 0.0
        binding.txtTotalAmount.text = "₹${formatMoney(totalAmount)}"
    }

    private fun bindPayment() {
        val a = assigned
        val method = (a?.paymentMethod ?: "COD").uppercase()
        binding.txtPaymentMethod.text = when (method) {
            "COD" -> "Cash on Delivery"
            "UPI" -> "UPI Payment"
            "CARD" -> "Card Payment"
            "WALLET" -> "Wallet"
            else -> method
        }

        val statusText = a?.paymentStatus?.ifBlank { "Pending" } ?: "Pending"
        val isPaid = statusText.equals("paid", true) || statusText.equals("success", true)
        binding.chipPaymentStatus.text = statusText.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        binding.chipPaymentStatus.chipBackgroundColor = ContextCompat.getColorStateList(
            requireContext(),
            if (isPaid) R.color.success_color else R.color.warning_color
        )
    }

    private fun bindAddresses() {
        val a = assigned
        val statusRaw = a?.orderStatus

        val pickupJson = a?.addressDetails?.toJSONObject()
        val dropJson = a?.dropAddress?.toJSONObject()

        // Match NavigationActivity logic: if status contains "delivery", swap addresses
        val status = statusRaw?.lowercase().orEmpty()

        if (status.contains("delivery")) {
            // For delivery phase: pickup = dropAddress, delivery = addressDetails
            pickupAddress = dropJson
            deliveryAddress = pickupJson
        } else {
            // For pickup phase: pickup = addressDetails, delivery = dropAddress
            pickupAddress = pickupJson
            deliveryAddress = dropJson
        }

        // Address lines
        binding.txtPickupAddress.text = pickupAddress?.toPrettyAddress() ?: "Address not available"
        binding.txtDeliveryAddress.text = deliveryAddress?.toPrettyAddress() ?: "Address not available"

        // PICKUP contact/phone
        val pName = pickupAddress?.optString("recipient_name").orEmpty().ifBlank { "N/A" }
        val pPhone = pickupAddress?.optString("phone_number").orEmpty().ifBlank { "N/A" }
        binding.txtPickupContactName.text = pName
        binding.txtPickupPhone.text = pPhone

        // DELIVERY contact/phone
        val dName = deliveryAddress?.optString("recipient_name").orEmpty().ifBlank { "N/A" }
        val dPhone = deliveryAddress?.optString("phone_number").orEmpty().ifBlank { "N/A" }
        binding.txtCustomerName.text = dName
        binding.txtCustomerPhone.text = dPhone
    }

    private fun bindItems() {
        val uiItems = items.map {
            OrderItem(
                name = it.productName ?: "-",
                description = listOfNotNull(it.serviceType?.takeIf { s -> s.isNotBlank() }).joinToString(" • "),
                price = (it.totalPrice ?: ((it.productPrice ?: 0.0) + (it.servicePrice ?: 0.0))),
                quantity = it.quantity ?: 0,
                imageUrl = it.productImage.orEmpty(),
                id = it.id,
                orderId = it.orderId,
                productId = it.productId,
                productPrice = it.productPrice ?: 0.0,
                serviceType = it.serviceType,
                servicePrice = it.servicePrice ?: 0.0,
                totalPrice = it.totalPrice ?: 0.0,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }
        orderItemsAdapter.submitList(uiItems)
    }

    private fun bindDeliveryCode() {
        val code = "PIKAGO" + System.currentTimeMillis().toString().takeLast(4)
        binding.txtDeliveryCode.text = code
    }

    // ---------------- Actions ----------------

    private fun startNavigation() {
        try {
            val intent = android.content.Intent(
                requireContext(),
                com.leoworks.pikago.ui.NavigationActivity::class.java
            ).putExtra(com.leoworks.pikago.ui.NavigationActivity.EXTRA_ORDER_ID, orderId)

            startActivity(intent)
        } catch (e: Exception) {
            Log.e("OrderDetailsFragment", "Failed to start nav: ${e.message}")
            Toast.makeText(requireContext(), "Failed to open navigation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun callCustomer() {
        try {
            // Use the currently bound deliveryAddress first; fall back to pickup if needed
            val phone = (deliveryAddress ?: pickupAddress)?.optString("phone_number").orEmpty()
            if (phone.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:$phone")
                }
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Phone number not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("OrderDetailsFragment", "Call error: ${e.message}")
            Toast.makeText(requireContext(), "Failed to make call", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyDeliveryCode() {
        val deliveryCode = binding.txtDeliveryCode.text.toString()
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Delivery Code", deliveryCode))
        Toast.makeText(requireContext(), "Delivery code copied!", Toast.LENGTH_SHORT).show()
    }

    private fun markOrderComplete() {
        // TODO: update order status in Supabase if needed
        Toast.makeText(requireContext(), "Order marked as complete", Toast.LENGTH_SHORT).show()
        requireActivity().supportFragmentManager.popBackStack()
    }

    // ---------------- Data: Supabase ----------------

    private suspend fun loadAssigned() {
        try {
            val rows = supabase.from("assigned_orders").select {
                filter { eq("id", orderId) }
                limit(1)
            }.decodeList<AssignedOrderRow>()
            assigned = rows.firstOrNull()
        } catch (e: Exception) {
            Log.e("OrderDetailsFragment", "loadAssigned error: ${e.message}")
            Toast.makeText(requireContext(), "Failed to load order", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun loadItems() {
        try {
            items = supabase.from("order_items").select {
                filter { eq("order_id", orderId) }
            }.decodeList<OrderItemRow>()
        } catch (e: Exception) {
            Log.e("OrderDetailsFragment", "loadItems error: ${e.message}")
            items = emptyList()
        }
    }

    // ---------------- Helpers ----------------

    private fun formatDateTime(pickupDate: String?, pickupTime: String?, deliveryTime: String?): String {
        return try {
            if (!pickupDate.isNullOrBlank() && !pickupTime.isNullOrBlank()) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = sdf.parse(pickupDate)
                val displaySdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val dateStr = date?.let { displaySdf.format(it) } ?: pickupDate

                val timeStr = if (!deliveryTime.isNullOrBlank()) {
                    "$pickupTime - $deliveryTime"
                } else {
                    pickupTime
                }
                "$dateStr • $timeStr"
            } else {
                "Date not available"
            }
        } catch (_: Exception) {
            "Date not available"
        }
    }

    private fun formatMoney(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.2f", value)
    }

    private fun JsonObject.toJSONObject(): JSONObject {
        return try { JSONObject(this.toString()) } catch (_: Exception) { JSONObject() }
    }

    private fun JSONObject.toPrettyAddress(): String {
        val line1 = optString("address_line_1", "")
        val city = optString("city", "")
        val state = optString("state", "")
        val pincode = optString("pincode", "")
        return buildString {
            if (line1.isNotBlank()) append(line1)
            if (city.isNotBlank()) { if (isNotEmpty()) append(", "); append(city) }
            if (state.isNotBlank()) { if (isNotEmpty()) append(", "); append(state) }
            if (pincode.isNotBlank()) { if (isNotEmpty()) append(" - "); append(pincode) }
        }.ifBlank { "Address not available" }
    }

    // ---------------- DTOs ----------------

    @Serializable
    data class AssignedOrderRow(
        val id: String,
        @SerialName("total_amount") val totalAmount: Double? = null,
        @SerialName("payment_method") val paymentMethod: String? = null,
        @SerialName("payment_status") val paymentStatus: String? = null,
        @SerialName("order_status") val orderStatus: String? = null,

        @SerialName("pickup_date") val pickupDate: String? = null, // "yyyy-MM-dd"
        @SerialName("pickup_slot_display_time") val pickupSlotDisplayTime: String? = null,
        @SerialName("delivery_slot_display_time") val deliverySlotDisplayTime: String? = null,

        // JSONB
        @SerialName("address_details") val addressDetails: JsonObject? = null, // pickup address
        @SerialName("drop_address") val dropAddress: JsonObject? = null       // delivery address
    )

    @Serializable
    data class OrderItemRow(
        val id: String,
        @SerialName("order_id") val orderId: String,
        @SerialName("product_id") val productId: String? = null,
        @SerialName("product_name") val productName: String? = null,
        @SerialName("product_price") val productPrice: Double? = null,
        @SerialName("service_type") val serviceType: String? = null,
        @SerialName("service_price") val servicePrice: Double? = null,
        val quantity: Int? = null,
        @SerialName("total_price") val totalPrice: Double? = null,
        @SerialName("product_image") val productImage: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )
}