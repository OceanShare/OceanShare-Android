package com.oceanshare.oceanshare

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.os.Looper
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.*
import android.view.inputmethod.InputMethodManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.mapbox.android.core.location.*
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.marker_manager.*
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

interface LoadingImplementation {
    fun onFinishedLoading()
}

class HomeFragment : Fragment(), PermissionsListener, LoadingImplementation {
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    private lateinit var permissionManager: PermissionsManager
    private lateinit var originLocation: Location
    private lateinit var mContext: Context
    private lateinit var database: DatabaseReference
    private lateinit var callback: LocationEngineCallback<LocationEngineResult>


    private var currentMarker: Marker? = null

    private var weather: Weather = Weather()

    private var locationEngine: LocationEngine? = null
    private var locationComponent: LocationComponent? = null
    private var  markerHashMap : HashMap<String, MarkerData> = HashMap()
    private var DEFAULT_INTERVAL_IN_MILLISECONDS : Long = 1000L
    private var DEFAULT_MAX_WAIT_TIME : Long = DEFAULT_INTERVAL_IN_MILLISECONDS * 5

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

            initMarker()

            map.addOnMapClickListener {
                if (currentMarker != null) {
                    val pixel = map.projection.toScreenLocation(it)
                    val features = map.queryRenderedFeatures(pixel, "water")
                    //var error = false

                    if (features.isEmpty()) {
                        showDialogWith(getString(R.string.error_marker_land))
                        return@addOnMapClickListener false
                    }
                    if (it.distanceTo(LatLng(originLocation.latitude, originLocation.longitude)) > 4000) {
                        showDialogWith(getString(R.string.error_marker_too_far))
                        return@addOnMapClickListener false
                    }

                    if (getMarkerSetCount(fbAuth.currentUser?.uid.toString()) < 5)
                    {
                        val storedMarker = MarkerData(null ,it.latitude, it.longitude, currentMarker!!.groupId,
                                currentMarker!!.description, getHour(), fbAuth.currentUser?.uid.toString(),
                                getTimeStamp())
                        database.child("markers").push().setValue(storedMarker)

                        showNotification(getString(R.string.validation_marker_added), false)

                    } else {
                        showDialogWith(getString(R.string.error_marker_limit))
                    }

                    currentMarker = null
                }
                true
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
                setupEditingMarkerMenu(it)
            }

            mapView.addOnDidFinishRenderingMapListener {
                enableLocation()
            }

        }

        setupFadeAnimations()
        setupMarkerMenu()

        centerCameraButton.setOnClickListener {
            val position = CameraPosition.Builder()
                    .target(LatLng(originLocation.latitude, originLocation.longitude))
                    .zoom(16.0)
                    .tilt(20.0)
                    .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000)
        }
    }

    private fun getHour() : String {
        return SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ENGLISH).format(Date())
    }

    private fun getTimeStamp() : Long {
        return (System.currentTimeMillis())
    }

    private fun getCreationString(timestamp: Long) : String {
        val intervalTime = (System.currentTimeMillis() /1000 - timestamp / 1000)

        if ((intervalTime) < 60) {
            return (getString(R.string.marker_placed) + " " + intervalTime.toString() + " " +
                    getString(R.string.seconds))
        }
        else if ((intervalTime / 60) < 60) {
            return (getString(R.string.marker_placed) + " " + (intervalTime / 60).toString() + " " +
                    getString(R.string.minutes))
        }
        else if ((intervalTime / 3600) < 24) {
            return (getString(R.string.marker_placed) + " " + (intervalTime / 3600).toString() + " " +
                    getString(R.string.hours))
        }

        return (getString(R.string.marker_placed) + " " + (intervalTime / 86400).toString() + " " +
                getString(R.string.days))
    }

    private fun getMarkerSetCount(user: String) : Int {
        var count = 0

        for ((k) in markerHashMap) {
            if (markerHashMap[k]?.user == user) {
                count += 1
            }
        }
        return count
    }

    private fun getMarkerKey(markerid : Long) : String {
        var markerKey = ""

        for ((k) in markerHashMap) {
            if (markerHashMap[k]?.id == markerid) {
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
                        if (!markerHashMap.containsKey(key) && p0.exists() && p0.child("groupId").exists()) {

                            val markerLatitude = p0.child("latitude").value.toString().toDouble()
                            val markerLongitude = p0.child("longitude").value.toString().toDouble()
                            val groupId = p0.child("groupId").value.toString().toInt()
                            val markerDesc = p0.child("description").value.toString()
                            val markerTime = p0.child("time").value.toString()
                            val markerUser = p0.child("user").value.toString()
                            val upvote = p0.child("upvote").value.toString().toInt()
                            val downvote = p0.child("downvote").value.toString().toInt()
                            val contributor = p0.child("contributors").value.toString()
                            val timestamp = p0.child("timestamp").value.toString().toLong()

                            val userVotes = fillLikedArray(contributor)

                            val markerIcon = findMarkerIconMenu(groupId)
                            val iconFactory = IconFactory.getInstance(context!!)
                            val icon = iconFactory.fromResource(findMarkerIconMap(groupId))

                            val markerMap = map.addMarker(MarkerOptions()
                                    .position(LatLng(markerLatitude, markerLongitude))
                                    .icon(icon)
                                    .title(findMarkerTitle(groupId))
                                    .snippet(markerDesc)
                            )

                            markerHashMap[key] = MarkerData(markerMap.id, markerLatitude,
                                    markerLongitude, groupId,
                                    markerDesc, markerTime, markerUser, timestamp, markerIcon, upvote, downvote,
                                    userVotes)
                        }
                    }

                    override fun onChildRemoved(p0: DataSnapshot) {
                        val key = p0.key.toString()

                        if (markerHashMap.containsKey(key) && p0.exists()){
                            if (markerManagerId.text == key) {
                                markerManager.visibility = View.GONE
                                closedMarkerManager()
                            }

                            map.getAnnotation(markerHashMap[key]?.id!!)?.remove()
                            markerHashMap.remove(key)
                        }
                    }

                    override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                        val key = p0.key.toString()



                        if (markerHashMap.containsKey(key) && p0.exists() &&
                                (fillLikedArray(p0.child("contributors").value.toString()) != markerHashMap[key]?.vote)) {
                            map.markers.forEach {
                                if (getMarkerKey(it.id) == key ) {
                                    markerHashMap[key]?.vote = fillLikedArray(p0.child("contributors").value.toString())
                                }
                            }
                        }

                        if (markerHashMap.containsKey(key) && p0.exists() &&
                                (p0.child("description").value.toString() != markerHashMap[key]?.description)) {
                            map.markers.forEach {
                                if (getMarkerKey(it.id) == key ) {
                                    markerHashMap[key]?.description = p0.child("description").value.toString()
                                    it.snippet = p0.child("description").value.toString()
                                    markerManagerDescription.text = markerHashMap[key]?.description
                                }
                            }
                        }

                        if (markerHashMap.containsKey(key) && p0.exists() &&
                                (p0.child("upvote").value.toString().toInt() != markerHashMap[key]?.upvote)) {
                            map.markers.forEach {
                                if (getMarkerKey(it.id) == key ) {
                                    markerHashMap[key]?.upvote = p0.child("upvote").value.toString().toInt()
                                    markerManagerLikeButton.text = markerHashMap[key]?.upvote.toString()
                                }
                            }
                        }

                        if (markerHashMap.containsKey(key) && p0.exists() &&
                                (p0.child("downvote").value.toString().toInt() != markerHashMap[key]?.downvote)) {
                            map.markers.forEach {
                                if (getMarkerKey(it.id) == key ) {
                                    markerHashMap[key]?.downvote = p0.child("downvote").value.toString().toInt()
                                    markerManagerDislikeButton.text = markerHashMap[key]?.downvote.toString()
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

    private fun fillLikedArray(userList: String) : MutableList<MarkerVote>  {
        val vote: MutableList<MarkerVote> = mutableListOf()

        if (userList != "null") {
            val lines = userList.lines()
            lines.forEach {

                var userVotes = it.replace("{", "")
                userVotes = userVotes.replace("}", "")

                if (userVotes.contains(",")) {
                    val userVote = userVotes.split(",")
                    userVote.forEach {
                        val expl = it.split("=")
                        vote.add(MarkerVote(expl[0], expl[1].toInt()))
                    }
                }
                else {
                    val expl = userVotes.split("=")
                    vote.add(MarkerVote(expl[0], expl[1].toInt()))
                }
            }
        }
        return vote
    }

    private fun checkVoteMarker(userList: MutableList<MarkerVote>): Int {
        val user = fbAuth.currentUser?.uid

        userList.forEach {
            if (user == it.userId)
                return it.choice
        }
        return 0
    }

    private fun findMarkerIconMap(groupId: Int) : Int{
        val markerImage: HashMap<Int, Int> = HashMap()

        markerImage[0] = R.drawable.marker_map_medusa
        markerImage[1] = R.drawable.marker_map_diver
        markerImage[2] = R.drawable.marker_map_waste
        markerImage[3] = R.drawable.marker_map_warning
        markerImage[4] = R.drawable.marker_map_dolphin
        markerImage[5] = R.drawable.marker_map_position

        return markerImage[groupId]!!
    }

    private fun findMarkerIconMenu(groupId: Int) : Int{
        val markerImage: HashMap<Int, Int> = HashMap()

        markerImage[0] = R.drawable.marker_menu_medusa
        markerImage[1] = R.drawable.marker_menu_diver
        markerImage[2] = R.drawable.marker_menu_waste
        markerImage[3] = R.drawable.marker_menu_warning
        markerImage[4] = R.drawable.marker_menu_dolphin
        markerImage[5] = R.drawable.marker_menu_position

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

    private fun showNotification(notificationMessage: String, error: Boolean) {

        /*if (error) {
            notificationMarker.backgroundTintList = R.color.red
        }*/

        notificationMarker.text = notificationMessage

        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.interpolator = DecelerateInterpolator() //add this
        fadeIn.duration = 3000

        fadeIn.setAnimationListener(object: Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                notificationMarker.visibility = View.VISIBLE
            }

            override fun onAnimationRepeat(p0: Animation?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onAnimationEnd(p0: Animation?) {
                //toto
            }
        })

        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.interpolator = AccelerateInterpolator()
        fadeOut.startOffset = 5000
        fadeOut.duration = 3000

        fadeOut.setAnimationListener(object: Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                //toto
            }

            override fun onAnimationRepeat(p0: Animation?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onAnimationEnd(p0: Animation?) {
                notificationMarker.visibility = View.INVISIBLE
            }
        })

        var animations = AnimationSet(false)

        animations.addAnimation(fadeOut)
        animations.addAnimation(fadeIn)

        notificationMarker.startAnimation(animations)
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

    private fun setupLocationDisplay() {
        longDisplay.text = getText(R.string.longitude).toString() + originLocation.longitude
                .toBigDecimal().setScale(4, RoundingMode.UP).toString()
        latDisplay.text = getText(R.string.latitude).toString() + originLocation.latitude
                .toBigDecimal().setScale(4, RoundingMode.UP).toString()
    }

    private fun closedMarkerManager() {
        val inputMethodManager = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
        markerManager.visibility = View.GONE
        markerManagerEdit.visibility = View.GONE
        markerManagerDescription.visibility = View.VISIBLE
        showHideMarkerMenuButton.show()
        centerCameraButton.show()
        map.uiSettings.setAllGesturesEnabled(true)
    }

    private fun setupEditingMarkerMenu(mark: com.mapbox.mapboxsdk.annotations.Marker) {

        val markerInformation = markerHashMap[getMarkerKey(mark.id)]
        val currentUser = fbAuth.currentUser?.uid.toString()


        markerManager.visibility = View.VISIBLE
        showHideMarkerMenuButton.hide()
        centerCameraButton.hide()
        mark.hideInfoWindow()
        map.uiSettings.setAllGesturesEnabled(false)



        markerManagerIcon.setImageResource(markerInformation?.markerIcon!!)
        markerManagerTitle.text = findMarkerTitle(markerInformation.groupId)
        markerManagerDescription.text = markerInformation.description
        markerManagerLikeButton.text = markerInformation.upvote.toString()
        markerManagerDislikeButton.text = markerInformation.downvote.toString()
        markerManagerCreationTime.text = getCreationString(markerInformation.timestamp)
        markerManagerId.text = getMarkerKey(mark.id)

        if (fbAuth.currentUser?.uid.toString() == markerInformation.user) {
            markerManagerOwnMarker.visibility = View.VISIBLE
            markerManagerEditButton.visibility = View.VISIBLE

        } else {
            markerManagerOwnMarker.visibility = View.GONE
            markerManagerEditButton.visibility = View.INVISIBLE
        }

        markerManagerEditButton.setOnClickListener {
            if (markerManagerEdit.visibility != View.VISIBLE) {
                editMarkerDescritionField.setText(mark.snippet)
                markerManagerEdit.visibility = View.VISIBLE
                markerManagerDescription.visibility = View.GONE
            }
        }

        submitMarkerEditedDescription.setOnClickListener {
            database.child("markers").child(getMarkerKey(mark.id)).child("description")
                    .setValue(editMarkerDescritionField.text.toString())
            val inputMethodManager = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
            markerManagerEdit.visibility = View.GONE
            markerManagerDescription.visibility = View.VISIBLE
            editMarkerDescritionField.text.clear()
            //showNotification(getString(R.string.validation_marker_edited))
        }

        cancelEditMarkerButton.setOnClickListener {
            val inputMethodManager = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
            markerManagerEdit.visibility = View.GONE
            markerManagerDescription.visibility = View.VISIBLE
        }

        deleteMarkerButton.setOnClickListener {
            closedMarkerManager()
            database.child("markers").child(getMarkerKey(mark.id)).removeValue()
            showNotification(getString(R.string.validation_marker_deleted), false)
        }

        markerManagerLikeButton.setOnClickListener {

            if (checkVoteMarker(markerInformation.vote!!) == 2)
            {
                database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                        .setValue(markerInformation.upvote + 1)
                database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                        .setValue(markerInformation.downvote - 1)
                database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                        .child(currentUser).setValue(1)
            } else if (checkVoteMarker(markerInformation.vote!!) == 1) {
                database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                        .setValue(markerInformation.upvote - 1)
                database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                        .child(currentUser).setValue(0)
            } else {
                database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                        .setValue(markerInformation.upvote + 1)
                database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                        .child(currentUser).setValue(1)
            }
        }

        markerManagerDislikeButton.setOnClickListener {
            if (checkVoteMarker(markerInformation.vote!!) == 2)
            {
                database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                        .setValue(markerInformation.downvote - 1)
                database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                        .child(currentUser).setValue(0)
            } else if (checkVoteMarker(markerInformation.vote!!) == 1) {
                database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                        .setValue(markerInformation.downvote + 1)
                database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                        .setValue(markerInformation.upvote - 1)
                database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                        .child(currentUser).setValue(2)
            } else {
                database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                        .setValue(markerInformation.downvote + 1)
                database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                        .child(currentUser).setValue(2)
            }
        }

        exitButton.setOnClickListener {
            closedMarkerManager()
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
HomeFragment().
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
            initializeLocationComponent()
            initializeLocationEngine()
        } else {
            permissionManager = PermissionsManager(this)
            permissionManager.requestLocationPermissions(activity)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun initializeLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(mContext)
        val request : LocationEngineRequest =
                LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                        .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME).build()

        callback = object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult) {
                var location: Location = result.lastLocation!!

                if (location === null) {
                    return
                }

                if (map != null && location != null) {
                    map.locationComponent.forceLocationUpdate(location)
                    onLocationChanged(location)
                }

                Log.d("SuccessResult", "Latitude".plus(result.lastLocation?.latitude.toString()).plus( ", Longitude:").plus(result.lastLocation?.longitude.toString()))

            }

            override fun onFailure(exception: Exception) {
                Log.d("FAIL", exception.toString())
            }
        }

        locationEngine?.requestLocationUpdates(request, callback, Looper.getMainLooper())
        locationEngine?.getLastLocation(callback)
    }

    @SuppressWarnings("MissingPermission")
    private fun initializeLocationComponent() {
        locationComponent = map.locationComponent
        var locationComponentActivationOptions : LocationComponentActivationOptions =
                LocationComponentActivationOptions.builder(mContext, map.style!!)
                        .useDefaultLocationEngine(false)
                        .build()
        locationComponent?.activateLocationComponent(locationComponentActivationOptions)
        locationComponent?.isLocationComponentEnabled = true
        locationComponent?.renderMode = RenderMode.COMPASS
        locationComponent?.cameraMode = CameraMode.TRACKING
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), 16.0))
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

    fun onLocationChanged(lastLocation : Location) {
        originLocation = lastLocation
        setCameraPosition(lastLocation)
        setupLocationDisplay()
        weather.receiveWeatherData("5", "6")
    }

    @SuppressWarnings("MissingPermission")
    override fun onStart() {
        super.onStart()
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
        if (locationEngine != null) {
            locationEngine?.removeLocationUpdates(callback)
        }
        mapView.onDestroy()
    }
}
