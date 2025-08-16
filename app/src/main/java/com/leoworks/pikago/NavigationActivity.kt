package com.leoworks.pikago.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.leoworks.pikago.App
import com.leoworks.pikago.R
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import kotlin.math.abs

class NavigationActivity : AppCompatActivity() {

    private var googleMap: GoogleMap? = null
    private var myLatLng: LatLng? = null
    private var destLatLng: LatLng? = null
    private var orderId: String = "-"

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
        setContentView(R.layout.activity_navigation)

        // Fix for bottom cutoff - handle window insets compatible with API 24+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        // Apply window insets using AndroidX compat library for API 24+ support
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply margin to FABs to avoid cutoff
            val fabStart = findViewById<ExtendedFloatingActionButton>(R.id.fabStart)
            val fabRecenter = findViewById<ExtendedFloatingActionButton>(R.id.fabRecenter)

            val bottomMargin = systemBars.bottom + (16 * resources.displayMetrics.density).toInt() // 16dp extra margin

            fabStart?.let { fab ->
                val params = fab.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                params?.bottomMargin = bottomMargin
                fab.layoutParams = params
            }

            fabRecenter?.let { fab ->
                val params = fab.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                params?.bottomMargin = bottomMargin
                fab.layoutParams = params
            }

            insets
        }

        orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: "-"

        // Toolbar
        findViewById<MaterialToolbar>(R.id.topAppBar).apply {
            title = "Navigate"
            subtitle = orderId
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        findViewById<ExtendedFloatingActionButton>(R.id.fabStart)
            .setOnClickListener { openGoogleMapsTurnByTurn() }
        findViewById<ExtendedFloatingActionButton>(R.id.fabRecenter)
            .setOnClickListener { fitBounds() }

        // Insert/attach the map fragment
        val tag = "nav_map"
        val mapFragment = (supportFragmentManager.findFragmentByTag(tag) as? SupportMapFragment)
            ?: SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it, tag)
                    .commitNow()
            }

        mapFragment.getMapAsync { map ->
            googleMap = map
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = false
            enableMyLocation()

            // 1) If caller passed explicit lat/lng, use them.
            val lat = intent.getDoubleExtra(EXTRA_DEST_LAT, Double.NaN)
            val lng = intent.getDoubleExtra(EXTRA_DEST_LNG, Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
                setDestination(LatLng(lat, lng), "Destination (override)")
            } else {
                // 2) Otherwise resolve from Supabase by order status
                lifecycleScope.launch { resolveDestinationFromSupabase() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh destination each time (in case status changed while away)
        if (destLatLng == null) {
            lifecycleScope.launch { resolveDestinationFromSupabase() }
        }
    }

    // --- Core: choose address based on order_status and update map ---

    private suspend fun resolveDestinationFromSupabase() {
        try {
            val rows = supabase.from("assigned_orders").select {
                filter { eq("id", orderId) }
                limit(1)
            }.decodeList<AssignedOrderRow>()
            val row = rows.firstOrNull()
            if (row == null) {
                toast("Order not found")
                return
            }

            val pickupObj = row.addressDetails?.toJSONObject()
            val dropObj = row.dropAddress?.toJSONObject()

            val status = row.orderStatus?.lowercase().orEmpty()

            // Rules:
            // accepted/pending/assigned/in_progress -> pickup
            // picked_up/out_for_delivery -> drop
            val preferredPickup = setOf("accepted", "pending", "assigned", "in_progress")
            val preferredDrop = setOf("picked_up", "out_for_delivery")

            val candidate = when {
                status in preferredPickup -> pickupObj ?: dropObj
                status in preferredDrop -> dropObj ?: pickupObj
                else -> dropObj ?: pickupObj
            }

            val dest = candidate?.toLatLng()
                ?: pickupObj?.toLatLng()
                ?: dropObj?.toLatLng()

            if (dest == null) {
                toast("No coordinates in order addresses")
                return
            }

            val label = if (candidate === pickupObj) "Pickup" else "Delivery"
            setDestination(dest, label)

        } catch (e: Exception) {
            toast("Failed to load order: ${e.message}")
        }
    }

    private fun setDestination(latLng: LatLng, label: String) {
        destLatLng = latLng

        val map = googleMap ?: return
        map.clear()
        map.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(latLng)
                .title(label)
                .icon(BitmapDescriptorFactory.defaultMarker(
                    if (label.equals("pickup", true))
                        BitmapDescriptorFactory.HUE_ORANGE
                    else
                        BitmapDescriptorFactory.HUE_ROSE
                ))
        )

        fitBounds()
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
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) myLatLng = LatLng(loc.latitude, loc.longitude)
                fitBounds()
            }
            .addOnFailureListener { fitBounds() }
    }

    private fun fitBounds() {
        val map = googleMap ?: return
        val dest = destLatLng ?: return
        val builder = LatLngBounds.Builder().include(dest)
        myLatLng?.let { builder.include(it) }
        val bounds = runCatching { builder.build() }.getOrNull()
        if (bounds != null) map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        else map.animateCamera(CameraUpdateFactory.newLatLngZoom(dest, 15f))
    }

    private fun openGoogleMapsTurnByTurn() {
        val dest = destLatLng ?: run { toast("Destination not ready"); return }

        // Try Google Maps app first with proper URI scheme
        val mapsUri = Uri.parse("google.navigation:q=${dest.latitude},${dest.longitude}&mode=d")
        val mapsIntent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        // Check if Google Maps app can handle the intent
        if (mapsIntent.resolveActivity(packageManager) != null) {
            try {
                startActivity(mapsIntent)
                return
            } catch (e: Exception) {
                // Fall through to web fallback
            }
        }

        // Fallback to web browser with proper https scheme
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${dest.latitude},${dest.longitude}&travelmode=driving")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)

        try {
            startActivity(webIntent)
        } catch (e: Exception) {
            toast("No navigation app available")
        }
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

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_DEST_LAT = "extra_dest_lat" // optional override
        const val EXTRA_DEST_LNG = "extra_dest_lng" // optional override
    }
}