package com.leoworks.pikago

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.leoworks.pikago.adapters.AssignedOrderAdapter
import com.leoworks.pikago.databinding.FragmentRideBinding
import com.leoworks.pikago.models.AssignedOrder
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class RideFragment : Fragment() {

    private var _binding: FragmentRideBinding? = null
    private val binding get() = _binding!!

    private lateinit var supabase: SupabaseClient
    private val adapter by lazy { AssignedOrderAdapter() }

    /** Selected day (UTC midnight from the picker). Always non-null; defaults to today. */
    private var selectedEpochDayUtc: Long = MaterialDatePicker.todayInUtcMilliseconds()

    private var tickJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supabase = App.supabase
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initUI()
        fetchOrders()
        startTicker()
    }

    override fun onDestroyView() {
        tickJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private fun initUI() = with(binding) {
        recyclerOrders.layoutManager = LinearLayoutManager(requireContext())
        recyclerOrders.adapter = adapter

        swipeRefresh.setOnRefreshListener { fetchOrders() }

        // Button labels
        btnPickDate.text = formatDateForButton(selectedEpochDayUtc)
        btnPickDate.setOnClickListener { openDatePicker() }
        btnClearDate.setOnClickListener {
            selectedEpochDayUtc = MaterialDatePicker.todayInUtcMilliseconds()
            btnPickDate.text = formatDateForButton(selectedEpochDayUtc)
            fetchOrders()
        }
    }

    private fun openDatePicker() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now()) // block past calendar days
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setCalendarConstraints(constraints)
            .setSelection(selectedEpochDayUtc)
            .build()

        picker.addOnPositiveButtonClickListener { utcMillis ->
            selectedEpochDayUtc = utcMillis
            binding.btnPickDate.text = formatDateForButton(utcMillis)
            fetchOrders()
        }
        picker.show(parentFragmentManager, "date_picker")
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(1000)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.swipeRefresh.isRefreshing = loading
    }

    private fun setEmptyVisible(visible: Boolean) {
        binding.emptyState.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun fetchOrders() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val selectedYmd = toYMD(selectedEpochDayUtc) // "yyyy-MM-dd" of selected date
                val table = supabase.from("assigned_orders")

                // Pull exactly the selected day: (pickup_date = selectedYmd) ∪ (delivery_date = selectedYmd)
                val rowsPickup: List<AssignedOrder> = table.select {
                    filter { eq("pickup_date", selectedYmd) }
                }.decodeList()

                val rowsDelivery: List<AssignedOrder> = table.select {
                    filter { eq("delivery_date", selectedYmd) }
                }.decodeList()

                // Merge and dedupe
                val rows = (rowsPickup + rowsDelivery).distinctBy { it.id }

                // If the selected day is TODAY, hide orders whose START time has already passed.
                val todayYmd = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val now = System.currentTimeMillis()

                val filtered = rows.filter { r ->
                    val startAt = startEpochMillis(r)
                    // Keep if start time exists and (either selected day != today or start >= now)
                    if (startAt == null) true
                    else if (selectedYmd != todayYmd) true  // Fixed: use selectedYmd instead of ymd
                    else startAt >= now
                }

                // Sort by the start time (pickup or delivery depending on status), then id
                val sorted = filtered.sortedWith(
                    compareBy<AssignedOrder> {
                        startEpochMillis(it) ?: Long.MAX_VALUE
                    }.thenBy { it.id }
                )

                adapter.submit(sorted)
                setEmptyVisible(sorted.isEmpty())
            } catch (e: Exception) {
                setEmptyVisible(true)
                Toast.makeText(requireContext(), "Failed to load: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }

    // ---------- Helpers ----------

    private fun toYMD(utcMillis: Long): String {
        val ld = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
        return ld.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun formatDateForButton(utcMillis: Long): String {
        val sel = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        val label = when {
            sel.isEqual(today) -> "Today"
            sel.isEqual(today.plusDays(1)) -> "Tomorrow"
            else -> sel.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault()))
        }
        return label
    }

    /** Decide whether this order is Pickup or Delivery based on status/fields. */
    private fun isPickup(order: AssignedOrder): Boolean {
        val s = (order.order_status ?: "").lowercase(Locale.getDefault())
        val pickupStatuses = setOf("accepted", "assigned", "ready_for_pickup", "processing", "confirmed")
        val deliveryStatuses = setOf("ready_for_delivery", "out_for_delivery", "shipped", "in_transit", "delivered")
        return when {
            s in pickupStatuses -> true
            s in deliveryStatuses -> false
            // fallback: if only pickup fields present -> pickup, otherwise delivery
            !order.pickup_date.isNullOrBlank() && !order.pickup_slot_start_time.isNullOrBlank() -> true
            else -> false
        }
    }

    /** Compute epoch millis of the relevant slot START (pickup vs delivery). */
    private fun startEpochMillis(order: AssignedOrder): Long? {
        val zone = ZoneId.systemDefault()
        val dateStr = if (isPickup(order)) order.pickup_date else order.delivery_date
        val timeStr = if (isPickup(order)) order.pickup_slot_start_time else order.delivery_slot_start_time
        if (dateStr.isNullOrBlank() || timeStr.isNullOrBlank()) return null

        // Parse "HH:mm[:ss]"
        val time = try {
            LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("H:mm[:ss]"))
        } catch (_: Exception) {
            return null
        }
        val date = try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            return null
        }
        return ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
    }
}