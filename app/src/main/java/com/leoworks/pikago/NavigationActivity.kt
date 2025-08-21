package com.leoworks.pikago.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
import android.widget.TextView
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import androidx.core.content.getSystemService
import android.view.HapticFeedbackConstants
import com.google.android.material.chip.Chip

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

    // Track navigation state
    private var currentPhase: NavigationPhase = NavigationPhase.PICKUP
    private var orderData: AssignedOrderRow? = null

    // Location tracking
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null

    // UI elements for premium experience
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

    // Premium animation handlers
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

        // Premium immersive experience
        setupImmersiveMode()

        orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: "-"
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupPremiumUI()
        setupPremiumMap()
        startLocationTracking()
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#1A000000") // Semi-transparent
            window.navigationBarColor = Color.parseColor("#1A000000")
        }

        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply dynamic margins to all floating elements
            val bottomMargin = systemBars.bottom + (16 * resources.displayMetrics.density).toInt()
            val topMargin = systemBars.top + (8 * resources.displayMetrics.density).toInt()

            // Status card with top margin
            statusCard?.let { card ->
                val params = card.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                params?.topMargin = topMargin
                card.layoutParams = params
            }

            // Swipe card at bottom
            swipeCard?.let { card ->
                val params = card.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                params?.bottomMargin = bottomMargin
                card.layoutParams = params
            }

            insets
        }
    }

    private fun setupPremiumUI() {
        // Toolbar with premium styling
        findViewById<MaterialToolbar>(R.id.topAppBar).apply {
            title = "Premium Navigation"
            subtitle = "Order #$orderId"
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        // Initialize all premium UI elements
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

        // Premium FAB setup with animations
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

        // Make swipe card initially visible but with subtle animation
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
        // Premium swipe with haptic feedback and animations
        swipeSlider.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                // Haptic feedback at milestones
                when {
                    value >= 25f && value < 30f -> triggerHapticFeedback()
                    value >= 50f && value < 55f -> triggerHapticFeedback()
                    value >= 75f && value < 80f -> triggerHapticFeedback()
                    value >= 95f -> {
                        triggerSuccessHaptic()
                        handleSwipeCompletion()
                        swipeCompletionAnimation()
                    }
                }

                // Update slider appearance based on progress
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
                    // Smooth reset animation
                    swipeAnimator = ValueAnimator.ofFloat(slider.value, 0f).apply {
                        duration = 400
                        interpolator = AccelerateDecelerateInterpolator()
                        addUpdateListener {
                            slider.value = it.animatedValue as Float
                        }
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

            // Premium map styling
            map.uiSettings.apply {
                isZoomControlsEnabled = false
                isCompassEnabled = true
                isMyLocationButtonEnabled = false
                isMapToolbarEnabled = false
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
            }

            // Set map style for premium look (optional dark mode)
            try {
                if (isDarkMode()) {
                    // Try to load dark map style if available
                    val darkStyleJson = """
                        [
                          {
                            "elementType": "geometry",
                            "stylers": [{"color": "#242f3e"}]
                          },
                          {
                            "elementType": "labels.text.stroke",
                            "stylers": [{"color": "#242f3e"}]
                          },
                          {
                            "elementType": "labels.text.fill",
                            "stylers": [{"color": "#746855"}]
                          },
                          {
                            "featureType": "road",
                            "elementType": "geometry",
                            "stylers": [{"color": "#38414e"}]
                          },
                          {
                            "featureType": "water",
                            "elementType": "geometry",
                            "stylers": [{"color": "#17263c"}]
                          }
                        ]
                    """.trimIndent()

                    val mapStyle = com.google.android.gms.maps.model.MapStyleOptions(darkStyleJson)
                    map.setMapStyle(mapStyle)
                }
            } catch (e: Exception) {
                // Style loading failed, continue with default
            }

            enableMyLocation()

            // Load destination with premium experience
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
        val nightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun startLocationTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val locationRequest = LocationRequest.create().apply {
            interval = 3000 // 3 seconds for premium real-time tracking
            fastestInterval = 1000 // 1 second fastest
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                lastLocation = location
                val newLatLng = LatLng(location.latitude, location.longitude)

                // Update my location
                myLatLng = newLatLng
                updateCurrentLocationMarker(newLatLng)

                // Calculate distance and ETA
                currentDestLatLng?.let { dest ->
                    distanceToDestination = calculateDistance(newLatLng, dest)
                    estimatedTime = calculateETA(distanceToDestination, location.speed)
                    updateStatusUI()
                }

                // Auto-update route
                currentDestLatLng?.let { dest ->
                    drawRoute(newLatLng, dest)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        isTracking = true
    }

    private fun startStatusUpdates() {
        // Premium real-time status updates every 2 seconds
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
                NavigationPhase.PICKUP -> "Navigating to Pickup"
                NavigationPhase.DELIVERY -> "Navigating to Delivery"
            }

            distanceText.text = if (distanceToDestination > 0) {
                when {
                    distanceToDestination < 1.0 -> "${(distanceToDestination * 1000).toInt()}m away"
                    else -> "${"%.1f".format(distanceToDestination)} km away"
                }
            } else {
                "Calculating distance..."
            }

            etaText.text = if (estimatedTime > 0) {
                when {
                    estimatedTime < 60 -> "${estimatedTime}min"
                    else -> "${estimatedTime / 60}h ${estimatedTime % 60}m"
                }
            } else {
                "Calculating ETA..."
            }

            // Update progress indicator based on phase and proximity
            val baseProgress = when (currentPhase) {
                NavigationPhase.PICKUP -> 25
                NavigationPhase.DELIVERY -> 75
            }
            val proximityBonus = if (distanceToDestination > 0 && distanceToDestination < 0.5) 15 else 0
            progressIndicator.setProgressCompat(baseProgress + proximityBonus, true)
        }
    }

    private fun updatePhaseChip() {
        phaseChip.text = when (currentPhase) {
            NavigationPhase.PICKUP -> "PICKUP PHASE"
            NavigationPhase.DELIVERY -> "DELIVERY PHASE"
        }

        val colorRes = when (currentPhase) {
            NavigationPhase.PICKUP -> R.color.pickup_color
            NavigationPhase.DELIVERY -> R.color.delivery_color
        }

        try {
            val chipColor = ContextCompat.getColorStateList(this, colorRes)
            chipColor?.let { phaseChip.chipBackgroundColor = it }
        } catch (e: Exception) {
            // Fallback to default colors
            val fallbackColor = ContextCompat.getColorStateList(this, android.R.color.black)
            fallbackColor?.let { phaseChip.chipBackgroundColor = it }
        }
    }

    private fun checkArrivalProximity() {
        if (distanceToDestination > 0 && distanceToDestination < 0.1) { // Within 100 meters
            // Premium arrival notification
            if (swipeCard.alpha < 1f || swipeCard.scaleX < 1.05f) {
                swipeCard.animate()
                    .alpha(1f)
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(300)
                    .start()
                triggerHapticFeedback()
                toast("🎯 You've arrived! Complete your task.")

                // Pulse animation for attention
                startSwipePulse()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentDestLatLng == null) {
            lifecycleScope.launch { resolveDestinationFromSupabase() }
        }
        if (!isTracking) {
            startLocationTracking()
        }
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

    // --- Core: choose address based on order_status and update map ---

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

            val (phase, destinationObj, label) = determineNavigationPhase(status, pickupObj, dropObj)
            currentPhase = phase

            val dest = destinationObj?.toLatLng()
            if (dest == null) {
                toast("No coordinates in order addresses")
                return
            }

            setDestination(dest, label)
            updatePremiumSwipeUI()
            progressIndicator.visibility = View.GONE

        } catch (e: Exception) {
            toast("Failed to load order: ${e.message}")
            progressIndicator.visibility = View.GONE
        }
    }

    private fun determineNavigationPhase(
        status: String,
        pickupObj: JSONObject?,
        dropObj: JSONObject?
    ): Triple<NavigationPhase, JSONObject?, String> {

        // FIXED: Handle "ready for delivery" and similar statuses
        val preferredPickup = setOf("accepted", "pending", "assigned", "in_progress")
        val preferredDrop = setOf("picked_up", "out_for_delivery", "ready_for_delivery", "ready for delivery")

        return when {
            status in preferredDrop -> {
                // For delivery phase: pickup = dropAddress, delivery = addressDetails
                Triple(NavigationPhase.DELIVERY, dropObj ?: pickupObj, "Delivery Location")
            }
            status in preferredPickup -> {
                // For pickup phase: pickup = addressDetails, delivery = dropAddress
                Triple(NavigationPhase.PICKUP, pickupObj ?: dropObj, "Pickup Location")
            }
            else -> {
                // Default logic based on available addresses
                if (pickupObj != null && status != "delivered" && status != "completed") {
                    Triple(NavigationPhase.PICKUP, pickupObj, "Pickup Location")
                } else {
                    Triple(NavigationPhase.DELIVERY, dropObj ?: pickupObj, "Delivery Location")
                }
            }
        }
    }

    private fun updatePremiumSwipeUI() {
        when (currentPhase) {
            NavigationPhase.PICKUP -> {
                swipeText.text = "Swipe to Complete Pickup"
                swipeHint.text = "Mark items as collected and move to delivery"
                swipeCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
            }
            NavigationPhase.DELIVERY -> {
                swipeText.text = "Swipe to Complete Delivery"
                swipeHint.text = "Mark order as delivered and complete"
                swipeCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
            }
        }

        // Reset slider
        swipeSlider.value = 0f

        // Premium entrance animation for swipe card
        swipeCard.apply {
            animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun handleSwipeCompletion() {
        lifecycleScope.launch {
            when (currentPhase) {
                NavigationPhase.PICKUP -> handlePickupCompletion()
                NavigationPhase.DELIVERY -> handleDeliveryCompletion()
            }
        }
    }

    private suspend fun handlePickupCompletion() {
        try {
            progressIndicator.visibility = View.VISIBLE

            supabase.from("assigned_orders").update({
                set("order_status", "ready_for_delivery")
            }) {
                filter { eq("id", orderId) }
            }

            toast("✅ Pickup completed successfully!")
            triggerSuccessHaptic()

            currentPhase = NavigationPhase.DELIVERY

            // FIXED: For delivery phase after pickup, use dropAddress as destination
            val deliveryObj = orderData?.dropAddress?.toJSONObject()
            val deliveryDest = deliveryObj?.toLatLng()

            if (deliveryDest != null) {
                // Premium transition animation
                swipeCard.animate()
                    .alpha(0.7f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(300)
                    .withEndAction {
                        setDestination(deliveryDest, "Delivery Location")
                        updatePremiumSwipeUI()
                    }
                    .start()
            } else {
                toast("Delivery address not found")
            }

            progressIndicator.visibility = View.GONE

        } catch (e: Exception) {
            toast("Failed to update pickup status: ${e.message}")
            progressIndicator.visibility = View.GONE
        }
    }

    private suspend fun handleDeliveryCompletion() {
        try {
            progressIndicator.visibility = View.VISIBLE

            supabase.from("assigned_orders").update({
                set("order_status", "completed")
            }) {
                filter { eq("id", orderId) }
            }

            toast("🎉 Order completed successfully!")
            triggerSuccessHaptic()

            // Premium completion celebration
            celebrateCompletion()

        } catch (e: Exception) {
            toast("Failed to complete order: ${e.message}")
            progressIndicator.visibility = View.GONE
        }
    }

    private fun celebrateCompletion() {
        // Hide UI elements with celebration animation
        swipeCard.animate()
            .alpha(0f)
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(600)
            .start()

        statusCard.animate()
            .alpha(0f)
            .translationY(-100f)
            .setDuration(400)
            .start()

        fabStart.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(300).start()
        fabRecenter.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(300).start()

        // Show success message and exit
        handler.postDelayed({
            toast("🚀 Great job! Returning to dashboard...")
            finish()
        }, 2000)
    }

    private fun setDestination(latLng: LatLng, label: String) {
        currentDestLatLng = latLng
        val map = googleMap ?: return

        // Clear existing markers and routes
        destinationMarker?.remove()
        routePolyline?.remove()

        // Add premium destination marker with custom icons
        val markerColor = if (label.contains("pickup", true)) {
            BitmapDescriptorFactory.HUE_VIOLET
        } else {
            BitmapDescriptorFactory.HUE_BLUE
        }

        destinationMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(label)
                .snippet("Tap for details")
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
        )

        fitBounds()

        // Draw route if we have current location
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
                .pattern(listOf(
                    com.google.android.gms.maps.model.Dash(20f),
                    com.google.android.gms.maps.model.Gap(10f)
                ))
        )
    }

    private fun callCustomer() {
        try {
            val phone = when (currentPhase) {
                NavigationPhase.PICKUP -> orderData?.addressDetails?.toJSONObject()?.optString("phone_number")
                NavigationPhase.DELIVERY -> orderData?.dropAddress?.toJSONObject()?.optString("phone_number")
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
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
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

        // Check if map view is ready and has valid dimensions
        val mapView = findViewById<View>(R.id.mapContainer)
        if (mapView?.width ?: 0 <= 0 || mapView?.height ?: 0 <= 0) {
            // Map view not ready, fallback to simple zoom
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(dest, 15f))
            return
        }

        try {
            val padding = 120
            val effectivePadding = when {
                mapView.width < 400 || mapView.height < 400 -> 60
                else -> padding
            }

            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, effectivePadding))
        } catch (e: Exception) {
            // Fallback to simple zoom
            try {
                val center = LatLng(
                    (bounds.northeast.latitude + bounds.southwest.latitude) / 2,
                    (bounds.northeast.longitude + bounds.southwest.longitude) / 2
                )
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 14f))
            } catch (fallbackException: Exception) {
                // Final fallback - just zoom to destination
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
            try {
                startActivity(mapsIntent)
                return
            } catch (e: Exception) {
                // Fall through to web fallback
            }
        }

        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${dest.latitude},${dest.longitude}&travelmode=driving")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)

        try {
            startActivity(webIntent)
        } catch (e: Exception) {
            toast("No navigation app available")
        }
    }

    // --- Premium Helper Functions ---

    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val earthRadius = 6371.0 // Earth's radius in km

        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)

        val a = sin(dLat/2).pow(2) + cos(Math.toRadians(start.latitude)) *
                cos(Math.toRadians(end.latitude)) * sin(dLon/2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))

        return earthRadius * c
    }

    private fun calculateETA(distanceKm: Double, speedMps: Float): Int {
        // Convert speed from m/s to km/h, with fallback to average city speed
        val speedKmh = if (speedMps > 0) (speedMps * 3.6) else 25.0 // 25 km/h default
        val timeHours = distanceKm / speedKmh
        return (timeHours * 60).toInt() // Convert to minutes
    }

    private fun triggerHapticFeedback() {
        // Check if VIBRATE permission is available
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) ==
            PackageManager.PERMISSION_GRANTED) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = getSystemService<Vibrator>()
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(50)
            }
        }
    }

    private fun triggerSuccessHaptic() {
        // Check if VIBRATE permission is available
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) ==
            PackageManager.PERMISSION_GRANTED) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = getSystemService<Vibrator>()
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1))
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
        // Premium completion animation with multiple effects
        swipeSlider.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(200)
            .withEndAction {
                swipeSlider.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()

        // Text animation
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
        @SerialName("address_details") val addressDetails: JsonObject? = null, // pickup
        @SerialName("drop_address") val dropAddress: JsonObject? = null        // delivery
    )

    private fun JsonObject.toJSONObject(): JSONObject =
        try { JSONObject(this.toString()) } catch (_: Exception) { JSONObject() }

    private fun JSONObject.toLatLng(): LatLng? {
        // Accept latitude/longitude or lat/lng, as number or numeric string.
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
        PICKUP, DELIVERY
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_DEST_LAT = "extra_dest_lat" // optional override
        const val EXTRA_DEST_LNG = "extra_dest_lng" // optional override
    }
}