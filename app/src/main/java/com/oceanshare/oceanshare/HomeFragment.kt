package com.oceanshare.oceanshare

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.constants.Style
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import kotlinx.android.synthetic.main.fragment_home.*

interface LoadingImplementation {
    fun onFinishedLoading()
}

class HomeFragment : Fragment(), PermissionsListener, LocationEngineListener, LoadingImplementation {
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    private lateinit var permissionManager: PermissionsManager
    private lateinit var originLocation: Location
    private lateinit var mContext: Context

    private var currentMarker: Marker? = null

    private var locationEngine: LocationEngine? = null
    private var locationComponent: LocationComponent? = null

    private lateinit var fadeInAnimation: AlphaAnimation
    private lateinit var fadeOutAnimation: AlphaAnimation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        Mapbox.getInstance(activity!!.applicationContext, getString(R.string.mapbox_access_token))
        mContext = activity!!.applicationContext
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View ,savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLoadingView()

        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapboxMap ->
            map = mapboxMap
            map.setStyle(Style.OUTDOORS)
            enableLocation()

            map.addOnMapClickListener {
                if (currentMarker != null) {
                    val iconFactory = IconFactory.getInstance(context!!)
                    val icon = iconFactory.fromResource(currentMarker!!.markerImage)

                    map.addMarker(MarkerOptions()
                            .position(LatLng(it.latitude, it.longitude))
                            .icon(icon)
                            .title(currentMarker!!.name)
                            .snippet("Description")
                    )
                    currentMarker = null
                }
            }
        }
        setupFadeAnimations()
        setupMarkerMenu()

        centerCameraButton.setOnClickListener {
            val position = CameraPosition.Builder()
                    .target(LatLng(originLocation.latitude, originLocation.longitude))
                    .zoom(12.0)
                    .tilt(20.0)
                    .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000)
        }
    }

    private fun setupFadeAnimations() {
        fadeInAnimation = AlphaAnimation(0.0f, 1.0f)
        fadeInAnimation.duration = 500
        fadeInAnimation.repeatCount = 0

        fadeOutAnimation = AlphaAnimation(1.0f, 0.0f)
        fadeOutAnimation.duration = 500
        fadeOutAnimation.repeatCount = 0
    }

    private fun setupLoadingView() {
        waveLoadingView.progressValue = 0
        LoadingTask(this).execute()
    }

    @SuppressLint("StaticFieldLeak")
    inner class LoadingTask(private val listener: LoadingImplementation) : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg params: Void?): Void? {
            for (i in 0 until 10) { Thread.sleep(100) }
            for (i in 0 until 10) {
                activity?.runOnUiThread {
                    run { waveLoadingView.progressValue += 10 }
                }
                Thread.sleep(100)
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            listener.onFinishedLoading()
        }
    }

    override fun onFinishedLoading() {
        loadingView.startAnimation(fadeOutAnimation)
        loadingView.visibility = View.GONE
    }

    private fun setupMarkerMenu() {
        markerView.alpha = 0.8F
        showHideMarkerMenuButton.setOnClickListener {
            if (markerMenu.visibility == View.GONE) {
                markerMenu.startAnimation(fadeInAnimation)
                markerMenu.visibility = View.VISIBLE
            } else {
                markerMenu.startAnimation(fadeOutAnimation)
                markerMenu.visibility = View.GONE
            }
        }

        val markersList = ArrayList<Marker>()
        markersList.add(Marker("Medusa", R.drawable.medusa, R.drawable.medusa_marker))
        markersList.add(Marker("Diver", R.drawable.diver, R.drawable.diver_marker))
        markersList.add(Marker("Waste", R.drawable.waste, R.drawable.waste_marker))
        markersList.add(Marker("SOS",R.drawable.lifesaver, R.drawable.lifesaver_marker))
        markersList.add(Marker("Dolphin", R.drawable.dolphin, R.drawable.dolphin_marker))
        markersList.add(Marker("Soon", R.drawable.soon, R.drawable.soon_marker))
        val adapter = MarkerAdapter(context!!, markersList)

        markerGridView.adapter = adapter
        markerGridView.setOnItemClickListener { _, _, position, _ ->
            markerMenu.startAnimation(fadeOutAnimation)
            markerMenu.visibility = View.GONE
            currentMarker = markersList[position]
        }
    }

    private fun enableLocation() {
        if (PermissionsManager.areLocationPermissionsGranted(mContext)) {
            initializeLocationEngine()
            initializeLocationComponent()
        } else {
            permissionManager = PermissionsManager(this)
            permissionManager.requestLocationPermissions(activity)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun initializeLocationEngine() {
        locationEngine = LocationEngineProvider(mContext).obtainBestLocationEngineAvailable()
        locationEngine?.priority = LocationEnginePriority.HIGH_ACCURACY
        locationEngine?.activate()

        val lastLocation = locationEngine?.lastLocation
        if (lastLocation != null) {
            originLocation = lastLocation
            setCameraPosition(lastLocation)
        } else {
            locationEngine?.addLocationEngineListener(this)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun initializeLocationComponent() {
        val options = LocationComponentOptions.builder(context)
                .trackingGesturesManagement(true)
                .accuracyColor(ContextCompat.getColor(context!!, R.color.deep_blue))
                .build()
        locationComponent = map.locationComponent
        locationComponent?.activateLocationComponent(mContext, options)
        locationComponent?.isLocationComponentEnabled = true
        locationComponent?.renderMode = RenderMode.COMPASS
        locationComponent?.cameraMode = CameraMode.TRACKING
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), 12.0))
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        // Present a toast or dialog explaining why need to grant access
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            initializeLocationComponent()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onLocationChanged(location: Location?) {
        location?.let {
            originLocation = location
            setCameraPosition(location)
        }
    }

    @SuppressWarnings("MissingPermission")
    override fun onConnected() {
        locationEngine?.requestLocationUpdates()
    }

    @SuppressWarnings("MissingPermission")
    override fun onStart() {
        super.onStart()
        if (PermissionsManager.areLocationPermissionsGranted(mContext)) {
            locationComponent?.onStart()
            locationEngine?.requestLocationUpdates()
        }
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

    }
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
    override fun onStop() {
        super.onStop()
        locationEngine?.removeLocationUpdates()
        locationComponent?.onStop()
        mapView.onStop()

    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
            mapView.onSaveInstanceState(outState)
    }
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()

    }
    override fun onDestroyView() {
        super.onDestroyView()
        locationEngine?.deactivate()
        mapView.onDestroy()
    }
}
