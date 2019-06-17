package com.oceanshare.oceanshare

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.inputmethod.InputMethodManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
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
import java.text.SimpleDateFormat
import java.util.*

interface LoadingImplementation {
    fun onFinishedLoading()
}

class HomeFragment : Fragment(), PermissionsListener, LocationEngineListener, LoadingImplementation {
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    private lateinit var permissionManager: PermissionsManager
    private lateinit var originLocation: Location
    private lateinit var mContext: Context
    private lateinit var database: DatabaseReference


    private var currentMarker: Marker? = null

    private var locationEngine: LocationEngine? = null
    private var locationComponent: LocationComponent? = null
    private var  hashMap : HashMap<String, MarkerData> = HashMap()

    private var fbAuth = FirebaseAuth.getInstance()

    private lateinit var fadeInAnimation: AlphaAnimation
    private lateinit var fadeOutAnimation: AlphaAnimation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        Mapbox.getInstance(activity!!.applicationContext, getString(R.string.mapbox_access_token))
        mContext = activity!!.applicationContext
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private fun showDialogWith(message: String) {
        val builder = AlertDialog.Builder(context!!)
        builder.setTitle(R.string.error)
        builder.setMessage(message)
        builder.setPositiveButton("Ok"){_, _ -> }
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    override fun onViewCreated(view: View ,savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLoadingView()

        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapboxMap ->
            map = mapboxMap
            map.setStyle(Style.OUTDOORS)

            database = FirebaseDatabase.getInstance().reference

            enableLocation()

            initMarker()

            map.addOnMapClickListener {
                if (currentMarker != null) {
                    val pixel = map.projection.toScreenLocation(it)
                    val features = map.queryRenderedFeatures(pixel, "water")
                    //var error = false

                    if (features.isEmpty()) {
                        showDialogWith(getString(R.string.error_marker_land))
                        return@addOnMapClickListener
                    }
                    if (it.distanceTo(LatLng(originLocation.latitude, originLocation.longitude)) > 4000) {
                        showDialogWith(getString(R.string.error_marker_too_far))
                        return@addOnMapClickListener
                    }

                    if (getMarkerSetCount(fbAuth.currentUser?.uid.toString()) < 5)
                    {
                        val storedMarker = MarkerData(null ,it.latitude, it.longitude, currentMarker!!.groupId,
                                currentMarker!!.description, getHour(), fbAuth.currentUser?.uid.toString())
                        database.child("markers").push().setValue(storedMarker)
                    } else {
                        showDialogWith(getString(R.string.error_marker_limit))
                    }

                    currentMarker = null
                }
            }

            map.setOnMarkerClickListener {
                if (!it.isInfoWindowShown) {
                    it.showInfoWindow(map, mapView)
                    true
                } else {
                    it.hideInfoWindow()
                 false
                }
            }

            map.setOnInfoWindowLongClickListener {
                if (fbAuth.currentUser?.uid == hashMap[getMarkerKey(it.id)]?.user) {
                    setupEditingMarkerMenu(it)
                }
                else {
                    showDialogWith(getString(R.string.error_contextual_menu))
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

    private fun getHour() : String {
        return SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH).format(Date())
    }

    private fun getMarkerSetCount(user: String) : Int {
        var count = 0

        for ((k) in hashMap) {
            if (hashMap[k]?.user == user) {
                count += 1
            }
        }
        return count
    }

    private fun getMarkerKey(markerid : Long) : String {
        var markerKey = ""

        for ((k) in hashMap) {
            if (hashMap[k]?.id == markerid) {
                markerKey = k
                break
            }
        }

        return markerKey
    }

    private fun initMarker () {
        database.child("markers").addChildEventListener(
                object : ChildEventListener {

                    override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                        val key = p0.key.toString()
                        if (!hashMap.containsKey(key) && p0.exists() && p0.child("groupId").exists()) {

                            val markerLatitude = p0.child("latitude").value.toString().toDouble()
                            val markerLongitude = p0.child("longitude").value.toString().toDouble()
                            val groupId = p0.child("groupId").value.toString().toInt()
                            val markerDesc = p0.child("description").value.toString()
                            val markerTime = p0.child("time").value.toString()
                            val markerUser = p0.child("user").value.toString()


                            val iconFactory = IconFactory.getInstance(context!!)
                            val icon = iconFactory.fromResource(findMarkerImage(groupId))

                            val markerMap = map.addMarker(MarkerOptions()
                                    .position(LatLng(markerLatitude, markerLongitude))
                                    .icon(icon)
                                    .title(findMarkerTitle(groupId))
                                    .snippet(markerDesc)
                            )

                            hashMap[key] = MarkerData(markerMap.id, markerLatitude,
                                    markerLongitude, groupId,
                                    markerDesc, markerTime, markerUser)
                        }
                    }

                    override fun onChildRemoved(p0: DataSnapshot) {
                        val key = p0.key.toString()

                        if (hashMap.containsKey(key) && p0.exists()){
                            map.getAnnotation(hashMap[key]?.id!!)?.remove()
                            hashMap.remove(key)
                        }
                    }

                    override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                        val key = p0.key.toString()

                        if (hashMap.containsKey(key) && p0.exists() &&
                                (p0.child("description").value.toString() != hashMap[key]?.description)) {
                            map.markers.forEach {
                                if (getMarkerKey(it.id) == key ) {
                                    it.snippet = p0.child("description").value.toString()
                                    hashMap[key]?.description = p0.child("description").value.toString()
                                }
                            }
                        }
                    }

                    override fun onChildMoved(p0: DataSnapshot, p1: String?) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun onCancelled(p0: DatabaseError) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }
                })
    }

    private fun findMarkerImage(groupId: Int) : Int{
        val markerImage: HashMap<Int, Int> = HashMap()

        markerImage[0] = R.drawable.marker_map_medusa
        markerImage[1] = R.drawable.marker_map_diver
        markerImage[2] = R.drawable.marker_map_waste
        markerImage[3] = R.drawable.marker_map_warning
        markerImage[4] = R.drawable.marker_map_dolphin
        markerImage[5] = R.drawable.marker_map_position

        return markerImage[groupId]!!
    }

    private fun findMarkerTitle(groupId: Int) : String {
        val markerTitle: HashMap<Int, String> = HashMap()

        markerTitle[0] = getString(R.string.marker_medusa)
        markerTitle[1] = getString(R.string.marker_diver)
        markerTitle[2] = getString(R.string.marker_waste)
        markerTitle[3] = getString(R.string.marker_sos)
        markerTitle[4] = getString(R.string.marker_dolphin)
        markerTitle[5] = getString(R.string.marker_position)

        return markerTitle[groupId]!!
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

    private fun setupEditingMarkerMenu(mark: com.mapbox.mapboxsdk.annotations.Marker) {
        contextualMarkerMenu.background.alpha = 128
        contextualMarkerMenu.visibility = View.VISIBLE

        //to delete

        showHideMarkerMenuButton.hide()
        centerCameraButton.hide()

        deletingMarkerButton.setOnClickListener {

            database.child("markers").child(getMarkerKey(mark.id)).removeValue()

            //to delete

            showHideMarkerMenuButton.show()
            centerCameraButton.show()
            contextualMarkerMenu.visibility = View.GONE
        }

        editingMarkerButton.setOnClickListener {

            contextualMarkerMenu.visibility = View.GONE

            markerDescription.background.alpha = 128
            markerDescription.visibility = View.VISIBLE

            //to delete

            showHideMarkerMenuButton.hide()
            centerCameraButton.hide()

            markerTextDescription.setText(mark.snippet)

            submitMarkerDescription.setOnClickListener {
                database.child("markers").child(getMarkerKey(mark.id)).child("description")
                        .setValue(markerTextDescription.text.toString())

                markerTextDescription.text.clear()
                val inputMethodManager = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)

                //to delete

                showHideMarkerMenuButton.show()
                centerCameraButton.show()

                markerDescription.visibility = View.GONE
            }
        }

    }

    private fun setupDescriptionScreen() {
        markerDescription.background.alpha = 128
        markerDescription.visibility = View.VISIBLE

        //to delete

        showHideMarkerMenuButton.hide()
        centerCameraButton.hide()

            submitMarkerDescription.setOnClickListener {
                val description = markerTextDescription.text.toString()

                currentMarker?.description = description
                markerTextDescription.text.clear()
                val inputMethodManager = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)

                //to delete

                showHideMarkerMenuButton.show()
                centerCameraButton.show()

                markerDescription.visibility = View.GONE
        }
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
        markersList.add(Marker(getString(R.string.marker_medusa), R.drawable.marker_menu_medusa, R.drawable.marker_map_medusa, 0, ""))
        markersList.add(Marker(getString(R.string.marker_diver), R.drawable.marker_menu_diver, R.drawable.marker_map_diver, 1, ""))
        markersList.add(Marker(getString(R.string.marker_waste), R.drawable.marker_menu_waste, R.drawable.marker_map_waste, 2,""))
        markersList.add(Marker(getString(R.string.marker_sos),R.drawable.marker_menu_warning, R.drawable.marker_map_warning, 3,""))
        markersList.add(Marker(getString(R.string.marker_dolphin), R.drawable.marker_menu_dolphin, R.drawable.marker_map_dolphin, 4, ""))
        markersList.add(Marker(getString(R.string.marker_position), R.drawable.marker_menu_position, R.drawable.marker_map_position, 5, ""))
        val adapter = MarkerAdapter(context!!, markersList)

        markerGridView.adapter = adapter
        markerGridView.setOnItemClickListener { _, _, position, _ ->
            markerMenu.startAnimation(fadeOutAnimation)
            markerMenu.visibility = View.GONE
            currentMarker = markersList[position]
            setupDescriptionScreen()
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
