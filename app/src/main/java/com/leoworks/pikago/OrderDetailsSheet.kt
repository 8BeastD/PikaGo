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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.NumberFormat
import java.util.Locale

class OrderDetailsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetOrderDetailsBinding? = null
    private val binding get() = _binding!!

    private val supabase by lazy { App.supabase }

    private val orderId: String by lazy { requireArguments().getString(ARG_ORDER_ID)!! }
    private val addressRaw: String? by lazy { requireArguments().getString(ARG_ADDRESS_JSON) }

    // destination cache
    private var destLat: Double? = null
    private var destLng: Double? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetOrderDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun getTheme(): Int = com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }
        binding.tvOrderCode.text = orderId

        bindAddress(addressRaw)
        setupSwipeToNavigate()
        fetchTotals()
    }

    private fun fetchTotals() {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = supabase.from("order_items")
                    .select { filter { eq("order_id", orderId) } }
                    .decodeList<OrderItemRow>()

                val totalItems = rows.sumOf { (it.quantity ?: 0) }
                val totalAmount = rows.sumOf { (it.total_price ?: 0.0) }
                val currency = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

                binding.tvTotalItems.text = totalItems.toString()
                binding.tvTotalAmount.text = currency.format(totalAmount)

                // Build clean vertical rows
                binding.itemsList.removeAllViews()
                rows.forEach { row ->
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
            setTextColor(resources.getColor(android.R.color.secondary_text_light, null))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val right = TextView(ctx).apply {
            text = price
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        row.addView(left)
        row.addView(right)
        binding.itemsList.addView(row)
    }

    private fun bindAddress(raw: String?) {
        // 1) Try strict JSON
        val jsonObj: JsonObject? = runCatching { raw?.let { Json.parseToJsonElement(it).jsonObject } }.getOrNull()

        if (jsonObj != null) {
            fillAddressFromJson(jsonObj)
            return
        }

        // 2) Fallback: parse "key=value, key=value" style or anything similar
        val lat = extractDoubleFallback(raw, "latitude", "lat")
        val lng = extractDoubleFallback(raw, "longitude", "lng", "lon")
        val recipient = extractStringFallback(raw, "recipient_name")
        val phone = extractStringFallback(raw, "phone_number")
        val line1 = extractStringFallback(raw, "address_line_1")
        val line2 = extractStringFallback(raw, "address_line_2")
        val city = extractStringFallback(raw, "city")
        val state = extractStringFallback(raw, "state")
        val pincode = extractStringFallback(raw, "pincode")

        destLat = lat
        destLng = lng

        binding.tvRecipient.text = recipient ?: "-"
        binding.tvPhone.text = phone ?: "-"

        val addressLines = listOf(
            line1 ?: "",
            line2 ?: "",
            listOfNotNull(city, state).filter { it.isNotBlank() }.joinToString(", ")
        ).filter { it.isNotBlank() }

        binding.tvAddress.text = buildString {
            append(addressLines.joinToString("\n"))
            if (!pincode.isNullOrBlank()) append("\n$pincode")
        }
    }

    private fun fillAddressFromJson(obj: JsonObject) {
        fun jStr(key: String): String? =
            runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.lowercase(Locale.ROOT) != "null" }

        fun jDbl(key: String): Double? =
            runCatching { obj[key]?.jsonPrimitive?.content?.toDouble() }.getOrNull()

        val recipient = jStr("recipient_name") ?: "-"
        val phone = jStr("phone_number") ?: "-"
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
        private const val ARG_ADDRESS_JSON = "address_json"

        fun newInstance(orderId: String, addressJson: String?): OrderDetailsSheet {
            return OrderDetailsSheet().apply {
                arguments = bundleOf(
                    ARG_ORDER_ID to orderId,
                    ARG_ADDRESS_JSON to addressJson
                )
            }
        }

        fun show(activity: FragmentActivity, orderId: String, addressJson: String?) {
            newInstance(orderId, addressJson).show(activity.supportFragmentManager, "order_details_sheet")
        }
    }
}

@Serializable
data class OrderItemRow(
    val product_name: String? = null,
    val quantity: Int? = null,
    val total_price: Double? = null
)

// ----------------------
// Fallback parsers
// ----------------------

private val NUMBER_RE = """[-+]?\d+(?:\.\d+)?"""
private fun extractDoubleFallback(src: String?, vararg keys: String): Double? {
    if (src.isNullOrBlank()) return null
    keys.forEach { key ->
        // match: key : 12.34  OR  key=12.34  (case-insensitive)
        val rx = Regex("""(?i)\b${Regex.escape(key)}\b\s*[:=]\s*($NUMBER_RE)""")
        rx.find(src)?.let { m ->
            return m.groupValues[1].toDoubleOrNull()
        }
        // also try quoted numbers "12.34"
        val rxQ = Regex("""(?i)\b${Regex.escape(key)}\b\s*[:=]\s*"?($NUMBER_RE)"?""")
        rxQ.find(src)?.let { m ->
            return m.groupValues[1].toDoubleOrNull()
        }
    }
    return null
}

private fun extractStringFallback(src: String?, vararg keys: String): String? {
    if (src.isNullOrBlank()) return null
    keys.forEach { key ->
        // key : "value"
        val rxQuoted = Regex("""(?i)\b${Regex.escape(key)}\b\s*[:=]\s*"([^"]+)"""")
        rxQuoted.find(src)?.let { return it.groupValues[1].trim() }

        // key : value (until comma/brace)
        val rxBare = Regex("""(?i)\b${Regex.escape(key)}\b\s*[:=]\s*([^,}\n\r]+)""")
        rxBare.find(src)?.let { return it.groupValues[1].trim() }
    }
    return null
}
