package com.leoworks.pikago.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.slider.Slider
import com.leoworks.pikago.App
import com.leoworks.pikago.R
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class NavigationActivity : AppCompatActivity() {

    private var googleMap: GoogleMap? = null
    private var myLatLng: LatLng? = null
    private var currentDestLatLng: LatLng? = null
    private var orderId: String = "-"

    // Premium tracking features
    private var currentMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private var isTracking = false
    private var distanceToDestination = 0.0
    private var estimatedTime = 0

    // Track navigation state - Updated workflow
    private var currentPhase: NavigationPhase = NavigationPhase.PICKUP_TO_PICKUP
    private var orderData: AssignedOrderRow? = null

    // Location tracking
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null

    // UI elements
    private lateinit var swipeCard: MaterialCardView
    private lateinit var swipeSlider: Slider
    private lateinit var swipeText: TextView
    private lateinit var swipeHint: TextView
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var distanceText: TextView
    private lateinit var etaText: TextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var phaseChip: Chip
    private lateinit var callCustomerBtn: MaterialButton
    private lateinit var fabStart: ExtendedFloatingActionButton
    private lateinit var fabRecenter: ExtendedFloatingActionButton

    // Animation handlers
    private val handler = Handler(Looper.getMainLooper())
    private var swipeAnimator: ValueAnimator? = null
    private var pulseAnimator: ObjectAnimator? = null

    private val supabase by lazy { App.supabase }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grant ->
        val fine = grant[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grant[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) enableMyLocation() else
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation_premium)

        setupImmersiveMode()

        orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: "-"
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupPremiumUI()
        setupPremiumMap()
        startLocationTracking()
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#1A000000")
            window.navigationBarColor = Color.parseColor("#1A000000")
        }

        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val bottomMargin = systemBars.bottom + (16 * resources.displayMetrics.density).toInt()
            val topMargin = systemBars.top + (8 * resources.displayMetrics.density).toInt()

            statusCard.let { card ->
                val params = card.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                params?.topMargin = topMargin
                card.layoutParams = params
            }

            swipeCard.let { card ->
                val params = card.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                params?.bottomMargin = bottomMargin
                card.layoutParams = params
            }

            insets
        }
    }

    private fun setupPremiumUI() {
        findViewById<MaterialToolbar>(R.id.topAppBar).apply {
            title = "Premium Navigation"
            subtitle = "Order #$orderId"
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        swipeCard = findViewById(R.id.swipeCompletionCard)
        swipeSlider = findViewById(R.id.swipeSlider)
        swipeText = findViewById(R.id.swipeText)
        swipeHint = findViewById(R.id.swipeHint)
        statusCard = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        distanceText = findViewById(R.id.distanceText)
        etaText = findViewById(R.id.etaText)
        progressIndicator = findViewById(R.id.progressIndicator)
        phaseChip = findViewById(R.id.phaseChip)
        callCustomerBtn = findViewById(R.id.callCustomerBtn)
        fabStart = findViewById(R.id.fabStart)
        fabRecenter = findViewById(R.id.fabRecenter)

        fabStart.apply {
            setOnClickListener { openGoogleMapsTurnByTurn() }
            scaleX = 0f
            scaleY = 0f
            animate().scaleX(1f).scaleY(1f).setDuration(800)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }

        fabRecenter.apply {
            setOnClickListener { fitBounds() }
            scaleX = 0f
            scaleY = 0f
            animate().scaleX(1f).scaleY(1f).setDuration(800).setStartDelay(200)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }

        callCustomerBtn.setOnClickListener { callCustomer() }

        setupPremiumSwipeSlider()
        startStatusUpdates()

        swipeCard.apply {
            alpha = 0f
            translationY = 100f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(1000)
                .setStartDelay(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun setupPremiumSwipeSlider() {
        swipeSlider.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                when {
                    value in 25f..30f -> triggerHapticFeedback()
                    value in 50f..55f -> triggerHapticFeedback()
                    value in 75f..80f -> triggerHapticFeedback()
                    value >= 95f -> {
                        triggerSuccessHaptic()
                        handleSwipeCompletion()
                        swipeCompletionAnimation()
                    }
                }
                val progress = value / 100f
                slider.alpha = 0.8f + (progress * 0.2f)
            }
        }

        swipeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                startSwipePulse()
                slider.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                stopSwipePulse()
                if (slider.value < 95f) {
                    swipeAnimator = ValueAnimator.ofFloat(slider.value, 0f).apply {
                        duration = 400
                        interpolator = AccelerateDecelerateInterpolator()
                        addUpdateListener { slider.value = it.animatedValue as Float }
                        start()
                    }
                }
            }
        })
    }

    private fun setupPremiumMap() {
        val tag = "premium_nav_map"
        val mapFragment = (supportFragmentManager.findFragmentByTag(tag) as? SupportMapFragment)
            ?: SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it, tag)
                    .commitNow()
            }

        mapFragment.getMapAsync { map ->
            googleMap = map

            map.uiSettings.apply {
                isZoomControlsEnabled = false
                isCompassEnabled = true
                isMyLocationButtonEnabled = false
                isMapToolbarEnabled = false
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
            }

            try {
                if (isDarkMode()) {
                    val darkStyleJson = """
                        [
                          {"elementType":"geometry","stylers":[{"color":"#242f3e"}]},
                          {"elementType":"labels.text.stroke","stylers":[{"color":"#242f3e"}]},
                          {"elementType":"labels.text.fill","stylers":[{"color":"#746855"}]},
                          {"featureType":"road","elementType":"geometry","stylers":[{"color":"#38414e"}]},
                          {"featureType":"water","elementType":"geometry","stylers":[{"color":"#17263c"}]}
                        ]
                    """.trimIndent()
                    val mapStyle = MapStyleOptions(darkStyleJson)
                    map.setMapStyle(mapStyle)
                }
            } catch (_: Exception) { /* ignore */ }

            enableMyLocation()

            val lat = intent.getDoubleExtra(EXTRA_DEST_LAT, Double.NaN)
            val lng = intent.getDoubleExtra(EXTRA_DEST_LNG, Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
                setDestination(LatLng(lat, lng), "Destination (override)")
            } else {
                lifecycleScope.launch { resolveDestinationFromSupabase() }
            }
        }
    }

    private fun isDarkMode(): Boolean {
        val nightMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun startLocationTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val locationRequest = LocationRequest.create().apply {
            interval = 3000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                lastLocation = location
                val newLatLng = LatLng(location.latitude, location.longitude)

                myLatLng = newLatLng
                updateCurrentLocationMarker(newLatLng)

                currentDestLatLng?.let { dest ->
                    distanceToDestination = calculateDistance(newLatLng, dest)
                    estimatedTime = calculateETA(distanceToDestination, location.speed)
                    updateStatusUI()
                    drawRoute(newLatLng, dest)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
        isTracking = true
    }

    private fun startStatusUpdates() {
        handler.post(object : Runnable {
            override fun run() {
                updateStatusUI()
                updatePhaseChip()
                checkArrivalProximity()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun updateStatusUI() {
        runOnUiThread {
            statusText.text = when (currentPhase) {
                NavigationPhase.PICKUP_TO_PICKUP -> "Navigating to Pickup Address"
                NavigationPhase.PICKUP_TO_STORE -> "Delivering to Store"
                NavigationPhase.DELIVERY_FROM_STORE -> "Collecting from Store"
                NavigationPhase.DELIVERY_TO_CUSTOMER -> "Final Delivery to Customer"
            }

            distanceText.text = if (distanceToDestination > 0) {
                if (distanceToDestination < 1.0)
                    "${(distanceToDestination * 1000).toInt()}m away"
                else
                    "${"%.1f".format(distanceToDestination)} km away"
            } else "Calculating distance..."

            etaText.text = if (estimatedTime > 0) {
                if (estimatedTime < 60) "${estimatedTime}min"
                else "${estimatedTime / 60}h ${estimatedTime % 60}m"
            } else "Calculating ETA..."

            val baseProgress = when (currentPhase) {
                NavigationPhase.PICKUP_TO_PICKUP -> 20
                NavigationPhase.PICKUP_TO_STORE -> 40
                NavigationPhase.DELIVERY_FROM_STORE -> 60
                NavigationPhase.DELIVERY_TO_CUSTOMER -> 80
            }
            val proximityBonus =
                if (distanceToDestination > 0 && distanceToDestination < 0.5) 10 else 0
            progressIndicator.setProgressCompat(baseProgress + proximityBonus, true)
        }
    }

    private fun updatePhaseChip() {
        phaseChip.text = when (currentPhase) {
            NavigationPhase.PICKUP_TO_PICKUP -> "PICKUP COLLECTION"
            NavigationPhase.PICKUP_TO_STORE -> "STORE DELIVERY"
            NavigationPhase.DELIVERY_FROM_STORE -> "STORE COLLECTION"
            NavigationPhase.DELIVERY_TO_CUSTOMER -> "CUSTOMER DELIVERY"
        }

        val colorRes = when (currentPhase) {
            NavigationPhase.PICKUP_TO_PICKUP, NavigationPhase.PICKUP_TO_STORE -> R.color.pickup_color
            NavigationPhase.DELIVERY_FROM_STORE, NavigationPhase.DELIVERY_TO_CUSTOMER -> R.color.delivery_color
        }

        runCatching {
            val chipColor = ContextCompat.getColorStateList(this, colorRes)
            chipColor?.let { phaseChip.chipBackgroundColor = it }
        }.onFailure {
            val fallback = ContextCompat.getColorStateList(this, android.R.color.black)
            fallback?.let { phaseChip.chipBackgroundColor = it }
        }
    }

    private fun checkArrivalProximity() {
        if (distanceToDestination > 0 && distanceToDestination < 0.1) { // 100 m
            if (swipeCard.alpha < 1f || swipeCard.scaleX < 1.05f) {
                swipeCard.animate()
                    .alpha(1f)
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(300)
                    .start()
                triggerHapticFeedback()
                toast("🎯 You've arrived! Complete your task.")
                startSwipePulse()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentDestLatLng == null) {
            lifecycleScope.launch { resolveDestinationFromSupabase() }
        }
        if (!isTracking) startLocationTracking()
    }

    override fun onPause() {
        super.onPause()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        isTracking = false
    }

    override fun onDestroy() {
        super.onDestroy()
        swipeAnimator?.cancel()
        pulseAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    // --- Core: Updated workflow logic ---

    private suspend fun resolveDestinationFromSupabase() {
        try {
            progressIndicator.visibility = View.VISIBLE

            val rows = supabase.from("assigned_orders").select {
                filter { eq("id", orderId) }
                limit(1)
            }.decodeList<AssignedOrderRow>()

            val row = rows.firstOrNull()
            if (row == null) {
                toast("Order not found")
                return
            }

            orderData = row
            val pickupObj = row.addressDetails?.toJSONObject()
            val dropObj = row.dropAddress?.toJSONObject()
            val status = row.orderStatus?.lowercase().orEmpty()

            val (phase, destinationObj, label) =
                determineNavigationPhase(status, pickupObj, dropObj)
            currentPhase = phase

            val dest = destinationObj?.toLatLng()
            if (dest == null) {
                toast("No coordinates in order addresses")
                return
            }

            setDestination(dest, label)
            updatePremiumSwipeUI()
        } catch (e: Exception) {
            toast("Failed to load order: ${e.message}")
        } finally {
            progressIndicator.visibility = View.GONE
        }
    }

    private fun determineNavigationPhase(
        status: String,
        pickupObj: JSONObject?,
        dropObj: JSONObject?
    ): Triple<NavigationPhase, JSONObject?, String> {
        return when (status) {
            // Initial pickup from customer
            "assigned", "accepted", "processing", "confirmed" ->
                Triple(NavigationPhase.PICKUP_TO_PICKUP, pickupObj, "Customer Pickup Address")

            // Deliver to store
            "picked_up" ->
                Triple(NavigationPhase.PICKUP_TO_STORE, dropObj, "Store Address")

            // Collect from store (items are ready)
            "received", "ready_for_delivery" ->
                Triple(NavigationPhase.DELIVERY_FROM_STORE, dropObj, "Store Address")

            // Final delivery to customer
            "shipped", "out_for_delivery" ->
                Triple(NavigationPhase.DELIVERY_TO_CUSTOMER, pickupObj, "Customer Delivery Address")

            else ->
                Triple(NavigationPhase.PICKUP_TO_PICKUP, pickupObj ?: dropObj, "Pickup Address")
        }
    }

    private fun updatePremiumSwipeUI() {
        when (currentPhase) {
            NavigationPhase.PICKUP_TO_PICKUP -> {
                swipeText.text = "Swipe to Complete Pickup"
                swipeHint.text = "Items collected - now deliver to store"
            }

            NavigationPhase.PICKUP_TO_STORE -> {
                swipeText.text = "Swipe to Confirm Store Delivery"
                swipeHint.text = "Items delivered to store successfully"
            }

            NavigationPhase.DELIVERY_FROM_STORE -> {
                swipeText.text = "Swipe to Collect from Store"
                swipeHint.text = "Items ready - collect for delivery"
            }

            NavigationPhase.DELIVERY_TO_CUSTOMER -> {
                swipeText.text = "Swipe to Complete Delivery"
                swipeHint.text = "Final delivery - complete the order"
            }
        }

        swipeSlider.value = 0f
        swipeCard.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun handleSwipeCompletion() {
        lifecycleScope.launch {
            when (currentPhase) {
                NavigationPhase.PICKUP_TO_PICKUP -> handlePickupCollection()
                NavigationPhase.PICKUP_TO_STORE -> handleStoreDelivery()      // ⬅ Updated: now deletes from table
                NavigationPhase.DELIVERY_FROM_STORE -> handleStoreCollection()
                NavigationPhase.DELIVERY_TO_CUSTOMER -> handleFinalDelivery()
            }
        }
    }

    private suspend fun handlePickupCollection() {
        try {
            progressIndicator.visibility = View.VISIBLE

            supabase.from("assigned_orders").update({
                set("order_status", "picked_up")
            }) {
                filter { eq("id", orderId) }
            }

            toast("✅ Items collected from pickup!")
            triggerSuccessHaptic()

            currentPhase = NavigationPhase.PICKUP_TO_STORE
            val storeObj = orderData?.dropAddress?.toJSONObject()
            val storeDest = storeObj?.toLatLng()

            if (storeDest != null) {
                transitionToNextDestination(storeDest, "Store Address")
            } else {
                toast("Store address not found")
            }
        } catch (e: Exception) {
            toast("Failed to update pickup status: ${e.message}")
        } finally {
            progressIndicator.visibility = View.GONE
        }
    }

    // ✅ UPDATED: Sets status to "reached" AND deletes from assigned_orders table
    private suspend fun handleStoreDelivery() {
        try {
            progressIndicator.visibility = View.VISIBLE

            // First update the status to "reached"
            supabase.from("assigned_orders").update({
                set("order_status", "reached")
            }) {
                filter { eq("id", orderId) }
            }

            // Then delete the order from assigned_orders table
            supabase.from("assigned_orders").delete {
                filter { eq("id", orderId) }
            }

            toast("✅ Items delivered to store and removed from queue!")
            triggerSuccessHaptic()

            // Show completion message
            swipeText.text = "✓ Store Delivery Complete"
            swipeHint.text = "Order delivered & removed - admin will reassign for delivery"

            // Disable the swipe slider since this phase is complete
            swipeSlider.isEnabled = false
            swipeSlider.alpha = 0.5f

            // Show success message and finish activity
            handler.postDelayed({
                toast("🎉 Order delivered to store successfully!")
                finish()
            }, 2000)

        } catch (e: Exception) {
            toast("Failed to complete store delivery: ${e.message}")
        } finally {
            progressIndicator.visibility = View.GONE
        }
    }

    private suspend fun handleStoreCollection() {
        try {
            progressIndicator.visibility = View.VISIBLE

            // Mark as shipped when rider collects from store for final delivery
            supabase.from("assigned_orders").update({
                set("order_status", "shipped")
            }) {
                filter { eq("id", orderId) }
            }

            toast("✅ Items collected from store for delivery!")
            triggerSuccessHaptic()

            currentPhase = NavigationPhase.DELIVERY_TO_CUSTOMER
            val customerObj = orderData?.addressDetails?.toJSONObject()
            val customerDest = customerObj?.toLatLng()

            if (customerDest != null) {
                transitionToNextDestination(customerDest, "Customer Address")
            } else {
                toast("Customer address not found")
            }
        } catch (e: Exception) {
            toast("Failed to update store collection: ${e.message}")
        } finally {
            progressIndicator.visibility = View.GONE
        }
    }

    private suspend fun handleFinalDelivery() {
        try {
            progressIndicator.visibility = View.VISIBLE

            supabase.from("assigned_orders").update({
                set("order_status", "completed")
            }) {
                filter { eq("id", orderId) }
            }

            // Optional: remove from queue
            supabase.from("assigned_orders").delete {
                filter { eq("id", orderId) }
            }

            toast("🎉 Order completed and removed from queue!")
            triggerSuccessHaptic()
            celebrateCompletion()
        } catch (e: Exception) {
            toast("Failed to complete order: ${e.message}")
        } finally {
            progressIndicator.visibility = View.GONE
        }
    }

    private fun transitionToNextDestination(dest: LatLng, label: String) {
        swipeCard.animate()
            .alpha(0.7f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(300)
            .withEndAction {
                setDestination(dest, label)
                updatePremiumSwipeUI()
            }
            .start()
    }

    private fun celebrateCompletion() {
        swipeCard.animate().alpha(0f).scaleX(1.2f).scaleY(1.2f).setDuration(600).start()
        statusCard.animate().alpha(0f).translationY(-100f).setDuration(400).start()
        fabStart.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(300).start()
        fabRecenter.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(300).start()

        handler.postDelayed({
            toast("🚀 Great job! Returning to dashboard...")
            finish()
        }, 2000)
    }

    private fun setDestination(latLng: LatLng, label: String) {
        currentDestLatLng = latLng
        val map = googleMap ?: return

        destinationMarker?.remove()
        routePolyline?.remove()

        val markerHue = when (currentPhase) {
            NavigationPhase.PICKUP_TO_PICKUP -> BitmapDescriptorFactory.HUE_VIOLET
            NavigationPhase.PICKUP_TO_STORE -> BitmapDescriptorFactory.HUE_ORANGE
            NavigationPhase.DELIVERY_FROM_STORE -> BitmapDescriptorFactory.HUE_CYAN
            NavigationPhase.DELIVERY_TO_CUSTOMER -> BitmapDescriptorFactory.HUE_BLUE
        }

        destinationMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(label)
                .snippet("Tap for details")
                .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
        )

        fitBounds()
        myLatLng?.let { drawRoute(it, latLng) }
    }

    private fun updateCurrentLocationMarker(latLng: LatLng) {
        val map = googleMap ?: return
        currentMarker?.remove()
        currentMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Your Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
    }

    private fun drawRoute(start: LatLng, end: LatLng) {
        val map = googleMap ?: return
        routePolyline?.remove()
        routePolyline = map.addPolyline(
            PolylineOptions()
                .add(start, end)
                .width(10f)
                .color(ContextCompat.getColor(this, android.R.color.black))
                .geodesic(true)
                .pattern(listOf(Dash(20f), Gap(10f)))
        )
    }

    private fun callCustomer() {
        try {
            val phone = when (currentPhase) {
                NavigationPhase.PICKUP_TO_PICKUP ->
                    orderData?.addressDetails?.toJSONObject()?.optString("phone_number")
                NavigationPhase.PICKUP_TO_STORE, NavigationPhase.DELIVERY_FROM_STORE -> {
                    // Try store phone first, fallback to customer phone
                    val storePhone = orderData?.dropAddress?.toJSONObject()?.optString("phone_number")
                    if (storePhone.isNullOrBlank()) {
                        orderData?.addressDetails?.toJSONObject()?.optString("phone_number")
                    } else storePhone
                }
                NavigationPhase.DELIVERY_TO_CUSTOMER ->
                    orderData?.addressDetails?.toJSONObject()?.optString("phone_number")
            }

            if (!phone.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(intent)
            } else {
                toast("Phone number not available")
            }
        } catch (e: Exception) {
            toast("Failed to make call: ${e.message}")
        }
    }

    // --- Location / map helpers ---

    private fun enableMyLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        googleMap?.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                myLatLng = LatLng(loc.latitude, loc.longitude)
                updateCurrentLocationMarker(myLatLng!!)
                fitBounds()
            }
        }
    }

    private fun fitBounds() {
        val map = googleMap ?: return
        val dest = currentDestLatLng ?: return

        val builder = LatLngBounds.Builder().include(dest)
        myLatLng?.let { builder.include(it) }

        val bounds = runCatching { builder.build() }.getOrNull() ?: return

        val mapView = findViewById<View>(R.id.mapContainer)
        if ((mapView?.width ?: 0) <= 0 || (mapView?.height ?: 0) <= 0) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(dest, 15f))
            return
        }

        try {
            val padding = if (mapView.width < 400 || mapView.height < 400) 60 else 120
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        } catch (_: Exception) {
            try {
                val center = LatLng(
                    (bounds.northeast.latitude + bounds.southwest.latitude) / 2,
                    (bounds.northeast.longitude + bounds.southwest.longitude) / 2
                )
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 14f))
            } catch (_: Exception) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(dest, 15f))
            }
        }
    }

    private fun openGoogleMapsTurnByTurn() {
        val dest = currentDestLatLng ?: run { toast("Destination not ready"); return }

        val mapsUri = Uri.parse("google.navigation:q=${dest.latitude},${dest.longitude}&mode=d")
        val mapsIntent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        if (mapsIntent.resolveActivity(packageManager) != null) {
            runCatching { startActivity(mapsIntent) }.onFailure {
                // fall through to web
            }.onSuccess { return }
        }

        val webUri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${dest.latitude},${dest.longitude}&travelmode=driving"
        )
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
        runCatching { startActivity(webIntent) }
            .onFailure { toast("No navigation app available") }
    }

    // --- Premium Helper Functions ---

    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(start.latitude)) *
                cos(Math.toRadians(end.latitude)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun calculateETA(distanceKm: Double, speedMps: Float): Int {
        val speedKmh = if (speedMps > 0) (speedMps * 3.6) else 25.0
        val timeHours = distanceKm / speedKmh
        return (timeHours * 60).toInt()
    }

    private fun triggerHapticFeedback() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = getSystemService<Vibrator>()
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(50)
            }
        }
    }

    private fun triggerSuccessHaptic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = getSystemService<Vibrator>()
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
                )
            } else {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(longArrayOf(0, 100, 50, 100), -1)
            }
        }
    }

    private fun startSwipePulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(swipeCard, "alpha", 1f, 0.8f, 1f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopSwipePulse() {
        pulseAnimator?.cancel()
        swipeCard.animate().alpha(1f).setDuration(200).start()
    }

    private fun swipeCompletionAnimation() {
        swipeSlider.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(200)
            .withEndAction {
                swipeSlider.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }.start()

        swipeText.animate()
            .alpha(0.5f)
            .setDuration(300)
            .withEndAction {
                swipeText.text = "✓ Completed!"
                swipeText.animate().alpha(1f).setDuration(200).start()
            }
            .start()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // --- JSON/DTO helpers ---

    @Serializable
    private data class AssignedOrderRow(
        val id: String,
        @SerialName("order_status") val orderStatus: String? = null,
        @SerialName("address_details") val addressDetails: JsonObject? = null, // customer address
        @SerialName("drop_address") val dropAddress: JsonObject? = null        // store address
    )

    private fun JsonObject.toJSONObject(): JSONObject =
        runCatching { JSONObject(this.toString()) }.getOrElse { JSONObject() }

    private fun JSONObject.toLatLng(): LatLng? {
        fun num(key: String): Double? {
            if (!has(key)) return null
            return when (val v = opt(key)) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull()
                else -> null
            }
        }
        val lat = num("latitude") ?: num("lat")
        val lng = num("longitude") ?: num("lng")
        if (lat == null || lng == null) return null
        if (lat == 0.0 && lng == 0.0) return null
        if (lat.isNaN() || lng.isNaN() || abs(lat) > 90 || abs(lng) > 180) return null
        return LatLng(lat, lng)
    }

    private enum class NavigationPhase {
        PICKUP_TO_PICKUP,    // assigned -> pickup address (customer)
        PICKUP_TO_STORE,     // picked_up -> store address
        DELIVERY_FROM_STORE, // received/ready_for_delivery -> store address (collect)
        DELIVERY_TO_CUSTOMER // shipped -> customer address (final delivery)
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_DEST_LAT = "extra_dest_lat" // optional override
        const val EXTRA_DEST_LNG = "extra_dest_lng" // optional override
    }
}