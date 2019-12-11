package com.oceanshare.oceanshare

import android.content.Context
import android.location.Location
import android.os.Bundle
import android.os.Handler
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
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.oceanshare.oceanshare.utils.isConnectedToNetwork
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.marker_entry.view.*
import kotlinx.android.synthetic.main.marker_manager.*
import kotlinx.android.synthetic.main.marker_manager.exitButton
import kotlinx.android.synthetic.main.weather_marker.*
import kotlinx.android.synthetic.main.weather_marker.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class HomeFragment : Fragment(), LocationEngineListener {
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    lateinit var originLocation: Location
    private var mContext: Context? = null
    private lateinit var database: DatabaseReference

    private var currentMarker: Marker? = null

    private var locationEngine: LocationEngine? = null
    private var locationComponent: LocationComponent? = null
    private var markerHashMap: HashMap<String, MarkerData> = HashMap()
    private var userHashMap: HashMap<String, UserData> = HashMap()
    private var isEditingMarkerDescription = false
    private var isWeatherMarker = false
    private var apiService = IWeatherApi()

    private var fbAuth = FirebaseAuth.getInstance()

    private lateinit var fadeInAnimation: AlphaAnimation
    private lateinit var fadeOutAnimation: AlphaAnimation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        activity?.applicationContext?.let { Mapbox.getInstance(it, getString(R.string.mapbox_access_token)) }
        mContext = activity?.applicationContext
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private fun showDialogWith(message: String) {
        val builder = context?.let { AlertDialog.Builder(it) }
        builder?.setTitle(R.string.error)
        builder?.setMessage(message)
        builder?.setPositiveButton("Ok") { _, _ -> }
        val dialog: AlertDialog? = builder?.create()
        dialog?.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapboxMap ->
            map = mapboxMap
            map.setMinZoomPreference(16.00)
            map.setStyleUrl("mapbox://styles/oceanshare06/ck36dulje0m7c1cpih4atqiab")

            database = FirebaseDatabase.getInstance().reference

            enableLocation()

            map.addOnMapClickListener {
                if ((currentMarker != null) && !isEditingMarkerDescription) {

                    if (isOnWater(it)) {
                        showDialogWith(getString(R.string.error_marker_land))
                        return@addOnMapClickListener
                    }
                    if (it.distanceTo(LatLng(originLocation.latitude, originLocation.longitude)) > 4000) {
                        showDialogWith(getString(R.string.error_marker_too_far))
                        return@addOnMapClickListener
                    }

                    if (getMarkerSetCount(fbAuth.currentUser?.uid.toString()) < 5) {
                        val storedMarker = currentMarker?.groupId?.let { it1 ->
                            currentMarker?.description?.let { it2 ->
                                MarkerData(null, it.latitude, it.longitude, it1,
                                        it2, getHour(), fbAuth.currentUser?.uid.toString(),
                                        fbAuth.currentUser?.email.toString(), getTimeStamp())
                            }
                        }
                        database.child("markers").push().setValue(storedMarker)
                    } else {
                        showDialogWith(getString(R.string.error_marker_limit))
                    }

                    currentMarker = null
                } else if (isWeatherMarker) {
                    GlobalScope.launch(Dispatchers.Main) {
                        if (context != null && context!!.isConnectedToNetwork()) {
                            val weatherResponse = apiService.getWeather(it.latitude.toString(),
                                    it.longitude.toString())
                            setupWeatherMarkerScreen(weatherResponse)
                        }
                    }
                    isWeatherMarker = false
                }
            }

            map.setOnMarkerClickListener {
                setupEditingMarkerMenu(it)
                true
            }
        }

        mapView.addOnDidFinishLoadingMapListener {
            Handler().postDelayed({
                (activity as MainActivity).showBottomNavigationView()
                splashScreen.animate().alpha(0.0f)
                initMarker()
                initUsers()
            }, 2000)
        }

        setupFadeAnimations()
        setupMarkerMenu()

        centerCameraButton.setOnClickListener {
            if (::originLocation.isInitialized) {
                val position = CameraPosition.Builder()
                        .target(LatLng(originLocation.latitude, originLocation.longitude))
                        .zoom(16.0)
                        .tilt(20.0)
                        .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000)
            }
        }
    }

    private fun getMarkerDistance(lat1: Double, long1: Double, lat2: Double, long2: Double): Float {
        val location1 = Location("")
        location1.latitude = lat1
        location1.longitude = long1

        val location2 = Location("")
        location2.latitude = lat2
        location2.longitude = long2

        return location1.distanceTo(location2)
    }

    private fun getHour(): String {
        return SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ENGLISH).format(Date())
    }

    private fun getTimeStamp(): Long {
        return (System.currentTimeMillis())
    }

    private fun getCreationString(timestamp: Long): String {
        val intervalTime = (System.currentTimeMillis() / 1000 - timestamp / 1000)

        when {
            (intervalTime) < 60 -> {
                return (getString(R.string.marker_placed) + " " + intervalTime.toString() + " " +
                        getString(R.string.seconds))
            }
            (intervalTime / 60) < 60 -> {
                return (getString(R.string.marker_placed) + " " + (intervalTime / 60).toString() + " " +
                        getString(R.string.minutes))
            }
            (intervalTime / 3600) < 24 -> {
                return (getString(R.string.marker_placed) + " " + (intervalTime / 3600).toString() + " " +
                        getString(R.string.hours))
            }
            else -> return (getString(R.string.marker_placed) + " " + (intervalTime / 86400).toString() + " " +
                    getString(R.string.days))
        }

    }

    private fun getMarkerSetCount(user: String): Int {
        var count = 0

        for ((k) in markerHashMap) {
            if (markerHashMap[k]?.user == user) {
                count += 1
            }
        }
        return count
    }

    private fun getMarkerKey(markerid: Long): String {
        var markerKey = ""

        for ((k) in markerHashMap) {
            if (markerHashMap[k]?.id == markerid) {
                markerKey = k
                break
            }
        }

        return markerKey
    }

    private fun initMarker() {
        database.child("markers").addChildEventListener(
                object : ChildEventListener {

                    override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                        val key = p0.key.toString()

                        val markerLatitude = p0.child("latitude").value.toString().toDouble()
                        val markerLongitude = p0.child("longitude").value.toString().toDouble()
                        val groupId = p0.child("groupId").value.toString().toInt()
                        val markerDesc = p0.child("description").value.toString()
                        val markerTime = p0.child("time").value.toString()
                        val markerUser = p0.child("user").value.toString()
                        val markerUsername = "Joseph"
                        val upvote = p0.child("upvote").value.toString().toInt()
                        val downvote = p0.child("downvote").value.toString().toInt()
                        val contributor = p0.child("contributors").value.toString()
                        val timestamp = p0.child("timestamp").value.toString().toLong()

                        if (!markerHashMap.containsKey(key) && p0.exists() && p0.child("groupId").exists()
                                && getMarkerDistance(markerLatitude, markerLongitude, originLocation.latitude, originLocation.longitude) < 20000) {

                            val userVotes = fillLikedArray(contributor)

                            val markerIcon = findMarkerIconMenu(groupId)
                            val iconFactory = context?.let { IconFactory.getInstance(it) }
                            val icon = findMarkerIconMap(groupId)?.let { iconFactory?.fromResource(it) }

                            val markerMap = map.addMarker(MarkerOptions()
                                    .position(LatLng(markerLatitude, markerLongitude))
                                    .icon(icon)
                                    .title(findMarkerTitle(groupId))
                                    .snippet(markerDesc)
                            )

                            markerHashMap[key] = MarkerData(markerMap.id, markerLatitude,
                                    markerLongitude, groupId, markerDesc, markerTime, markerUser,
                                    markerUsername, timestamp, markerIcon, upvote, downvote,
                                    userVotes)
                        }
                    }

                    override fun onChildRemoved(p0: DataSnapshot) {
                        val key = p0.key.toString()

                        if (markerHashMap.containsKey(key) && p0.exists()) {
                            if (markerManagerId.text == key) {
                                markerManager.visibility = View.GONE
                                closedMarkerManager()
                            }

                            markerHashMap[key]?.id?.let { map.getAnnotation(it)?.remove() }
                            markerHashMap.remove(key)
                        }
                    }

                    override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                        val key = p0.key.toString()



                        if (markerHashMap.containsKey(key) && p0.exists() &&
                                (fillLikedArray(p0.child("contributors").value.toString()) != markerHashMap[key]?.vote)) {
                            map.markers.forEach {
                                if (getMarkerKey(it.id) == key) {
                                    markerHashMap[key]?.vote = fillLikedArray(p0.child("contributors").value.toString())
                                }
                            }
                        }

                        if (markerHashMap.containsKey(key) && p0.exists() &&
                                (p0.child("description").value.toString() != markerHashMap[key]?.description)) {
                            map.markers.forEach {
                                if (getMarkerKey(it.id) == key) {
                                    markerHashMap[key]?.description = p0.child("description").value.toString()
                                    it.snippet = p0.child("description").value.toString()
                                    markerManagerDescription.text = markerHashMap[key]?.description
                                }
                            }
                        }

                        if (markerHashMap.containsKey(key) && p0.exists() &&
                                (p0.child("upvote").value.toString().toInt() != markerHashMap[key]?.upvote)) {
                            map.markers.forEach {
                                if (getMarkerKey(it.id) == key) {
                                    markerHashMap[key]?.upvote = p0.child("upvote").value.toString().toInt()
                                    markerManagerLikeButton.text = markerHashMap[key]?.upvote.toString()
                                }
                            }
                        }

                        if (markerHashMap.containsKey(key) && p0.exists() &&
                                (p0.child("downvote").value.toString().toInt() != markerHashMap[key]?.downvote)) {
                            map.markers.forEach {
                                if (getMarkerKey(it.id) == key) {
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


    private fun initUsers() {
        database.child("users").addChildEventListener(
                object : ChildEventListener {

                    override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                        val key = p0.key.toString()

                        if (!userHashMap.containsKey(key) && p0.exists() && p0.child("location").exists()
                                && p0.child("preferences").child("user_active").value.toString().toBoolean()
                                && isOnWater(LatLng(p0.child("location").child("latitude").value.toString().toDouble(),
                                        p0.child("location").child("longitude").value.toString().toDouble()))
                                && getMarkerDistance(p0.child("location").child("latitude").value.toString().toDouble(),
                                        p0.child("location").child("longitude").value.toString().toDouble(),
                                        originLocation.latitude, originLocation.longitude) < 20000) {

                            val userLatitude = p0.child("location").child("latitude").value.toString().toDouble()
                            val userLongitude = p0.child("location").child("longitude").value.toString().toDouble()
                            val userName = p0.child("name").value.toString()
                            val userShipName = p0.child("ship_name").value.toString()
                            val userActive = p0.child("preferences").child("user_active").value.toString().toBoolean()

                            println("Latitude: " + userLatitude + ", Longitude: " + userLongitude + ", LatLng: " + LatLng(userLatitude, userLongitude) + ", isOnWater: " + isOnWater(LatLng(userLatitude, userLongitude)))

                            val iconFactory = context?.let { IconFactory.getInstance(it) }
                            val icon = findMarkerIconMap(9)?.let { iconFactory?.fromResource(it) }

                            val markerId = map.addMarker(MarkerOptions()
                                    .position(LatLng(userLatitude, userLongitude))
                                    .icon(icon)).id

                            userHashMap[key] = UserData(markerId, userName, userLatitude, userLongitude,
                                    userShipName, userActive)
                        }
                    }

                    override fun onChildRemoved(p0: DataSnapshot) {
                        val key = p0.key.toString()

                        if (!userHashMap.containsKey(key) && p0.exists() && p0.child("location").exists()
                                && p0.child("preferences").child("user_active").value.toString().toBoolean()
                                && isOnWater(LatLng(p0.child("location").child("latitude").value.toString().toDouble(),
                                        p0.child("location").child("longitude").value.toString().toDouble()))) {
                            markerHashMap[key]?.id?.let { map.getAnnotation(it)?.remove() }
                            markerHashMap.remove(key)
                        }
                    }

                    override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                        val key = p0.key.toString()

                        if (p0.exists()
                                && p0.child("preferences").child("user_active").value.toString().toBoolean()
                                && isOnWater(LatLng(p0.child("location").child("latitude").value.toString().toDouble(),
                                        p0.child("location").child("longitude").value.toString().toDouble()))) {

                            val userLatitude = p0.child("location").child("latitude").value.toString().toDouble()
                            val userLongitude = p0.child("location").child("longitude").value.toString().toDouble()
                            val userName = p0.child("name").value.toString()
                            val userShipName = p0.child("ship_name").value.toString()
                            val userActive = p0.child("preferences").child("user_active").value.toString().toBoolean()

                            val iconFactory = IconFactory.getInstance(context!!)
                            val icon = findMarkerIconMap(9)?.let { iconFactory?.fromResource(it) }
                            val markerId = map.addMarker(MarkerOptions()
                                    .position(LatLng(userLatitude, userLongitude))
                                    .icon(icon)).id

                            if (userHashMap.containsKey(key) &&
                                    (userHashMap[key]?.longitude != userLongitude || userHashMap[key]?.latitude != userLatitude)) {
                                map.getAnnotation(userHashMap[key]?.markerId!!)?.remove()

                                userHashMap[key]?.longitude = userLongitude
                                userHashMap[key]?.latitude = userLatitude
                                userHashMap[key]?.markerId = markerId
                            } else {
                                userHashMap[key] = UserData(markerId, userName, userLatitude, userLongitude,
                                        userShipName, userActive)
                            }
                        } else if (userHashMap.containsKey(key) && p0.exists() &&
                                (!p0.child("preferences").child("user_active").value.toString().toBoolean()
                                        || !isOnWater(LatLng(p0.child("location").child("latitude").value.toString().toDouble(),
                                        p0.child("location").child("longitude").value.toString().toDouble())))) {
                            map.getAnnotation(userHashMap[key]?.markerId!!)?.remove()
                            userHashMap.remove(key)
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


    private fun fillLikedArray(userList: String): MutableList<MarkerVote> {
        val vote: MutableList<MarkerVote> = mutableListOf()

        if (userList != "null") {
            val lines = userList.lines()
            lines.forEach { line ->

                var userVotes = line.replace("{", "")
                userVotes = userVotes.replace("}", "")

                if (userVotes.contains(",")) {
                    val userVote = userVotes.split(",")
                    userVote.forEach {
                        val expl = it.split("=")
                        vote.add(MarkerVote(expl[0], expl[1].toInt()))
                    }
                } else {
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

    private fun findMarkerIconMap(groupId: Int): Int? {
        val markerImage: HashMap<Int, Int> = HashMap()

        markerImage[0] = R.drawable.marker_map_medusa
        markerImage[1] = R.drawable.marker_map_diver
        markerImage[2] = R.drawable.marker_map_waste
        markerImage[3] = R.drawable.marker_map_warning
        markerImage[4] = R.drawable.marker_map_dolphin
        markerImage[5] = R.drawable.marker_map_position
        markerImage[6] = R.drawable.marker_map_buoy
        markerImage[7] = R.drawable.marker_map_cost_guard
        markerImage[8] = R.drawable.marker_map_fishes
        markerImage[9] = R.drawable.mini_yatcht

        return markerImage[groupId]
    }

    private fun findMarkerIconMenu(groupId: Int): Int? {
        val markerImage: HashMap<Int, Int> = HashMap()

        markerImage[0] = R.drawable.marker_menu_medusa
        markerImage[1] = R.drawable.marker_menu_diver
        markerImage[2] = R.drawable.marker_menu_waste
        markerImage[3] = R.drawable.marker_menu_warning
        markerImage[4] = R.drawable.marker_menu_dolphin
        markerImage[5] = R.drawable.marker_menu_position
        markerImage[6] = R.drawable.marker_menu_buoy
        markerImage[7] = R.drawable.marker_menu_cost_guard
        markerImage[8] = R.drawable.marker_menu_fishes

        return markerImage[groupId]
    }

    private fun findMarkerTitle(groupId: Int): String? {
        val markerTitle: HashMap<Int, String> = HashMap()

        markerTitle[0] = getString(R.string.marker_medusa)
        markerTitle[1] = getString(R.string.marker_diver)
        markerTitle[2] = getString(R.string.marker_waste)
        markerTitle[3] = getString(R.string.marker_sos)
        markerTitle[4] = getString(R.string.marker_dolphin)
        markerTitle[5] = getString(R.string.marker_position)
        markerTitle[6] = getString(R.string.marker_buoy)
        markerTitle[7] = getString(R.string.marker_cost_guard)
        markerTitle[8] = getString(R.string.marker_fishes)
        return markerTitle[groupId]
    }

    private fun setupFadeAnimations() {
        fadeInAnimation = AlphaAnimation(0.0f, 1.0f)
        fadeInAnimation.duration = 500
        fadeInAnimation.repeatCount = 0

        fadeOutAnimation = AlphaAnimation(1.0f, 0.0f)
        fadeOutAnimation.duration = 500
        fadeOutAnimation.repeatCount = 0
    }

    private fun closedMarkerManager() {
        val inputMethodManager = mContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
        markerManager.visibility = View.GONE
        markerEditionContainer.visibility = View.GONE
        markerManagerDescription.visibility = View.VISIBLE
        openMarkerMenuButton.visibility = View.VISIBLE
        centerCameraButton.visibility = View.VISIBLE
        map.uiSettings.setAllGesturesEnabled(true)
    }

    private fun setupEditingMarkerMenu(mark: com.mapbox.mapboxsdk.annotations.Marker) {
        val markerInformation = markerHashMap[getMarkerKey(mark.id)]
        val currentUser = fbAuth.currentUser?.uid.toString()

        markerManager.visibility = View.VISIBLE
        openMarkerMenuButton.visibility = View.GONE
        centerCameraButton.visibility = View.GONE
        mark.hideInfoWindow()
        map.uiSettings.setAllGesturesEnabled(false)

        markerInformation?.markerIcon?.let { markerManagerIcon.setImageResource(it) }
        markerManagerTitle.text = markerInformation?.groupId?.let { findMarkerTitle(it) }
        markerManagerDescription.text = markerInformation?.description
        markerManagerLikeButton.text = markerInformation?.upvote.toString()
        markerManagerDislikeButton.text = markerInformation?.downvote.toString()
        markerManagerCreationTime.text = markerInformation?.timestamp?.let { getCreationString(it) }
        markerManagerId.text = getMarkerKey(mark.id)

        if (fbAuth.currentUser?.uid.toString() == markerInformation?.user) {
            markerManagerOwnMarker.text = getString(R.string.user_marker)
            markerManagerOwnMarker.visibility = View.VISIBLE
            markerManagerEditButton.visibility = View.VISIBLE
            markerManagerVoteButtons.visibility = View.GONE

        } else {
            markerManagerOwnMarker.text = String.format(resources.getString(R.string.marker_put_by), markerInformation?.username)
            markerManagerOwnMarker.visibility = View.VISIBLE
            markerManagerEditButton.visibility = View.INVISIBLE
            markerManagerVoteButtons.visibility = View.VISIBLE
        }

        markerManagerEditButton.setOnClickListener {
            if (markerEditionContainer.visibility != View.VISIBLE) {
                markerDescriptionText.setText(mark.snippet)
                markerEditionContainer.visibility = View.VISIBLE
                markerManagerDescription.visibility = View.GONE
            }
        }

        saveMarkerEdition.setOnClickListener {
            database.child("markers").child(getMarkerKey(mark.id)).child("description")
                    .setValue(markerDescriptionText.text.toString())
            val inputMethodManager = mContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
            markerEditionContainer.visibility = View.GONE
            markerManagerDescription.visibility = View.VISIBLE
            markerDescriptionText.text.clear()
            //showNotification(getString(R.string.validation_marker_edited))
        }

        cancelMarkerEdition.setOnClickListener {
            val inputMethodManager = mContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
            markerEditionContainer.visibility = View.GONE
            markerManagerDescription.visibility = View.VISIBLE
        }

        removeMarker.setOnClickListener {
            closedMarkerManager()
            database.child("markers").child(getMarkerKey(mark.id)).removeValue()
        }

        markerManagerLikeButton.setOnClickListener {

            when {
                markerInformation?.vote?.let { it1 -> checkVoteMarker(it1) } == 2 -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                            .setValue(markerInformation.upvote + 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                            .setValue(markerInformation.downvote - 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(1)
                }
                markerInformation?.vote?.let { it1 -> checkVoteMarker(it1) } == 1 -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                            .setValue(markerInformation.upvote - 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(0)
                }
                else -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                            .setValue(markerInformation?.upvote?.plus(1))
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(1)
                }
            }
        }

        markerManagerDislikeButton.setOnClickListener {
            when {
                markerInformation?.vote?.let { it1 -> checkVoteMarker(it1) } == 2 -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                            .setValue(markerInformation.downvote - 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(0)
                }
                markerInformation?.vote?.let { it1 -> checkVoteMarker(it1) } == 1 -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                            .setValue(markerInformation.downvote + 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                            .setValue(markerInformation.upvote - 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(2)
                }
                else -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                            .setValue(markerInformation?.downvote?.plus(1))
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(2)
                }
            }
        }

        exitButton.setOnClickListener {
            closedMarkerManager()
        }

    }

    private fun setupWeatherMarkerScreen(weatherResponse: FullWeather) {
        val convert = WeatherConverter()

        weatherResponse.weather?.weather?.get(0)?.id?.let { convert.getWeatherIcon(it) }?.let { weatherMarker.weatherMarkerIcon.setImageResource(it) }
        val sharedPref = activity?.getSharedPreferences("OCEANSHARE_SHARED_PREFERENCES", Context.MODE_PRIVATE)
                ?: return
        if (sharedPref.getInt("temperature_preferences", 0) == 0) {
            weatherMarker.temperatureTextView.text = convert.getTemperature(weatherResponse.weather?.main?.temp)
        } else {
            weatherMarker.temperatureTextView.text = convert.getFTemperature(weatherResponse.weather?.main?.temp)
        }
        weatherMarker.descriptionTextView.text = weatherResponse.weather?.weather?.get(0)?.description
        weatherMarker.latitudeTextView.text = weatherResponse.weather?.coord?.lat.toString()
        weatherMarker.longitudeTextView.text = weatherResponse.weather?.coord?.lon.toString()
        weatherMarker.sunriseTextView.text = convert.getTime(weatherResponse.weather?.sys?.sunrise)
        weatherMarker.sunsetTextView.text = convert.getTime(weatherResponse.weather?.sys?.sunset)
        weatherMarker.cloudCoverTextView.text = convert.getCloudyValue(weatherResponse.weather?.clouds?.all)
        weatherMarker.windTextView.text = convert.getWindData(weatherResponse.weather?.wind?.deg, weatherResponse.weather?.wind?.speed)
        weatherMarker.humidityTextView.text = convert.getHumidity(weatherResponse.weather?.main?.humidity)
        weatherMarker.uvIndiceTextView.text = convert.getUv(weatherResponse.uv?.value)
        weatherMarker.visibilityTextView.text = convert.getVisibility(weatherResponse.weather?.visibility)

        weatherMarker.visibility = View.VISIBLE
        openMarkerMenuButton.visibility = View.GONE
        centerCameraButton.visibility = View.GONE
        map.uiSettings.setAllGesturesEnabled(false)

        weatherMarker.exitButton.setOnClickListener {
            weatherMarker.visibility = View.GONE
            openMarkerMenuButton.visibility = View.VISIBLE
            centerCameraButton.visibility = View.VISIBLE
            map.uiSettings.setAllGesturesEnabled(true)
        }
    }

    private fun isOnWater(it: LatLng): Boolean {
        val pixel = map.projection.toScreenLocation(it)
        val features = map.queryRenderedFeatures(pixel, "water")

        if (features.isEmpty())
            return true

        return false
    }

    private fun setupDescriptionScreen() {
        isEditingMarkerDescription = true
        markerDescription.background.alpha = 128
        markerDescription.visibility = View.VISIBLE
        map.uiSettings.setAllGesturesEnabled(false)

        cancelMarkerDescription.setOnClickListener {
            markerDescription.visibility = View.GONE
            map.uiSettings.setAllGesturesEnabled(true)
            currentMarker = null
            isEditingMarkerDescription = false
        }

        submitMarkerDescription.setOnClickListener {
            val description = markerTextDescription.text.toString()

            currentMarker?.description = description
            markerTextDescription.text.clear()
            val inputMethodManager = mContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)

            markerDescription.visibility = View.GONE
            map.uiSettings.setAllGesturesEnabled(true)
            isEditingMarkerDescription = false
        }
    }

    private fun setupMarkerMenu() {
        markerView.alpha = 0.8F
        openMarkerMenuButton.setOnClickListener {
            if (markerMenu.visibility != View.VISIBLE) {
                markerMenu.startAnimation(fadeInAnimation)
                markerMenu.visibility = View.VISIBLE
                map.uiSettings.setAllGesturesEnabled(false)
            }
        }
        closeMarkerMenuButton.setOnClickListener {
            markerMenu.startAnimation(fadeOutAnimation)
            map.uiSettings.setAllGesturesEnabled(true)
            markerMenu.visibility = View.GONE
        }

        val markersList = ArrayList<Marker>()
        markersList.add(Marker(getString(R.string.marker_medusa), R.drawable.marker_menu_medusa, R.drawable.marker_map_medusa, 0, ""))
        markersList.add(Marker(getString(R.string.marker_diver), R.drawable.marker_menu_diver, R.drawable.marker_map_diver, 1, ""))
        markersList.add(Marker(getString(R.string.marker_waste), R.drawable.marker_menu_waste, R.drawable.marker_map_waste, 2, ""))
        markersList.add(Marker(getString(R.string.marker_sos), R.drawable.marker_menu_warning, R.drawable.marker_map_warning, 3, ""))
        markersList.add(Marker(getString(R.string.marker_dolphin), R.drawable.marker_menu_dolphin, R.drawable.marker_map_dolphin, 4, ""))
        markersList.add(Marker(getString(R.string.marker_position), R.drawable.marker_menu_position, R.drawable.marker_map_position, 5, ""))
        markersList.add(Marker(getString(R.string.marker_buoy), R.drawable.marker_menu_buoy, R.drawable.marker_map_buoy, 6, ""))
        markersList.add(Marker(getString(R.string.marker_cost_guard), R.drawable.marker_menu_cost_guard, R.drawable.marker_map_cost_guard, 7, ""))
        markersList.add(Marker(getString(R.string.marker_fishes), R.drawable.marker_menu_fishes, R.drawable.marker_map_fishes, 8, ""))
        val adapter = context?.let { MarkerAdapter(it, markersList) }

        markerGridView.adapter = adapter
        markerGridView.setOnItemClickListener { _, _, position, _ ->
            markerMenu.startAnimation(fadeOutAnimation)
            markerMenu.visibility = View.GONE
            currentMarker = markersList[position]
            setupDescriptionScreen()
        }

        meteoMarker.markerName.setText(R.string.marker_meteo)
        meteoMarker.markerImage.setImageResource(R.drawable.marker_menu_meteo)

        meteoMarker.setOnClickListener {
            markerMenu.startAnimation(fadeOutAnimation)
            markerMenu.visibility = View.GONE
            map.uiSettings.setAllGesturesEnabled(true)
            isWeatherMarker = true
        }
    }

    private fun enableLocation() {
        initializeLocationEngine()
        initializeLocationComponent()
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
        val options = context?.let { ContextCompat.getColor(it, R.color.deep_blue) }?.let {
            LocationComponentOptions.builder(context)
                    .trackingGesturesManagement(true)
                    .accuracyColor(it)
                    .build()
        }
        locationComponent = map.locationComponent
        mContext?.let { options?.let { it1 -> locationComponent?.activateLocationComponent(it, it1) } }
        locationComponent?.isLocationComponentEnabled = true
        locationComponent?.renderMode = RenderMode.COMPASS
        locationComponent?.cameraMode = CameraMode.TRACKING
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), 16.0))
    }

    override fun onLocationChanged(location: Location?) {
        location?.let {
            originLocation = location
            if (originLocation.speed < 1) {
                speedMeter.visibility = View.INVISIBLE
                warningTooFast.visibility = View.INVISIBLE
            } else {
                val speedKilometersHours = location.speed * 3.6
                val speedNds = speedKilometersHours * 0.54
                speedText.text = speedNds.toInt().toString()
                speedMeter.visibility = View.VISIBLE
                if (speedNds > 5) {
                    warningTooFast.visibility = View.VISIBLE
                } else {
                    warningTooFast.visibility = View.INVISIBLE
                }
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    override fun onConnected() {
        locationEngine?.requestLocationUpdates()
    }

    @SuppressWarnings("MissingPermission")
    override fun onStart() {
        super.onStart()
        locationComponent?.onStart()
        locationEngine?.requestLocationUpdates()
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
