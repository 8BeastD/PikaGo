package com.leoworks.pikago

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.leoworks.pikago.adapters.AssignedOrderAdapter
import com.leoworks.pikago.databinding.FragmentRideBinding
import com.leoworks.pikago.models.AssignedOrder
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RideFragment : Fragment() {

    private var _binding: FragmentRideBinding? = null
    private val binding get() = _binding!!

    private lateinit var supabase: SupabaseClient
    private val adapter by lazy { AssignedOrderAdapter() }

    private var selectedEpochDay: Long? = null // date filter (UTC millis at 00:00)
    private var tickJob: Job? = null
    private var currentMode = AssignedOrderAdapter.Mode.PICKUP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Obtain from your App object
        supabase = App.supabase
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

        // Chips
        chipPickup.isChecked = true
        chipPickup.setOnClickListener { onModeChanged(AssignedOrderAdapter.Mode.PICKUP) }
        chipDelivery.setOnClickListener { onModeChanged(AssignedOrderAdapter.Mode.DELIVERY) }

        // Set today's date initially
        btnPickDate.text = getCurrentDateString()
        btnPickDate.setOnClickListener { openDatePicker() }
        btnClearDate.setOnClickListener {
            selectedEpochDay = null
            btnPickDate.text = getCurrentDateString()
            fetchOrders()
        }
    }

    private fun onModeChanged(newMode: AssignedOrderAdapter.Mode) {
        currentMode = newMode
        (if (newMode == AssignedOrderAdapter.Mode.PICKUP) binding.chipPickup else binding.chipDelivery).isChecked = true
        fetchOrders()
    }

    private fun openDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(selectedEpochDay ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        picker.addOnPositiveButtonClickListener { utcMillis ->
            selectedEpochDay = utcMillis
            binding.btnPickDate.text = formatDateForButton(utcMillis)
            fetchOrders()
        }
        picker.show(parentFragmentManager, "date_picker")
    }

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("'Today', MMM dd", Locale.US)
        return sdf.format(Date())
    }

    private fun formatDateForButton(utcMillis: Long): String {
        val today = Calendar.getInstance()
        val selectedDate = Calendar.getInstance().apply { timeInMillis = utcMillis }

        return when {
            isSameDay(today, selectedDate) -> getCurrentDateString()
            isYesterday(today, selectedDate) -> "Yesterday"
            isTomorrow(today, selectedDate) -> "Tomorrow"
            else -> {
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                sdf.format(Date(utcMillis))
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(today: Calendar, selected: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, selected)
    }

    private fun isTomorrow(today: Calendar, selected: Calendar): Boolean {
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return isSameDay(tomorrow, selected)
    }

    private fun toYMD(utcMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(utcMillis))
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
                // Base query
                val table = supabase.from("assigned_orders")

                // We filter by the mode: for pickup need non-null pickup_date & slot_start;
                // for delivery need non-null delivery_date & slot_start
                val rows: List<AssignedOrder> = if (selectedEpochDay == null) {
                    table.select().decodeList()
                } else {
                    // Date filter
                    val ymd = toYMD(selectedEpochDay!!)
                    if (currentMode == AssignedOrderAdapter.Mode.PICKUP) {
                        table.select {
                            filter {
                                eq("pickup_date", ymd)
                            }
                        }.decodeList()
                    } else {
                        table.select {
                            filter {
                                eq("delivery_date", ymd)
                            }
                        }.decodeList()
                    }
                }

                // Mode-based filtering and sorting in-memory (simple & clear)
                val filtered = rows.filter { r ->
                    if (currentMode == AssignedOrderAdapter.Mode.PICKUP)
                        !r.pickup_date.isNullOrBlank() && !r.pickup_slot_start_time.isNullOrBlank()
                    else
                        !r.delivery_date.isNullOrBlank() && !r.delivery_slot_start_time.isNullOrBlank()
                }.sortedWith(compareBy(
                    { if (currentMode == AssignedOrderAdapter.Mode.PICKUP) rDateTimeMillis(it.pickup_date, it.pickup_slot_start_time) else rDateTimeMillis(it.delivery_date, it.delivery_slot_start_time) },
                    { it.id }
                ))

                adapter.submit(filtered, currentMode)
                setEmptyVisible(filtered.isEmpty())
            } catch (e: Exception) {
                setEmptyVisible(true)
                Toast.makeText(requireContext(), "Failed to load: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun rDateTimeMillis(dateStr: String?, timeStr: String?): Long {
        return try {
            if (dateStr.isNullOrBlank() || timeStr.isNullOrBlank()) Long.MAX_VALUE
            else {
                val ymd = dateStr.split("-").map { it.toInt() }
                val hms = timeStr.split(":").map { it.toInt() }
                val zone = TimeZone.getDefault().toZoneId()
                val zdt = java.time.ZonedDateTime.of(
                    ymd[0], ymd[1], ymd[2],
                    hms[0], hms[1], hms[2],
                    0, zone
                )
                zdt.toInstant().toEpochMilli()
            }
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }
}