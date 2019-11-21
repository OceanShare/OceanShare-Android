package com.oceanshare.oceanshare

import android.content.Context
import android.location.Location
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.*
import android.view.inputmethod.InputMethodManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
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

class HomeFragment : Fragment(), PermissionsListener, LocationEngineListener {
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    private lateinit var permissionManager: PermissionsManager
    lateinit var originLocation: Location
    private lateinit var mContext: Context
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
        Mapbox.getInstance(activity!!.applicationContext, getString(R.string.mapbox_access_token))
        mContext = activity!!.applicationContext
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private fun showDialogWith(message: String) {
        val builder = AlertDialog.Builder(context!!)
        builder.setTitle(R.string.error)
        builder.setMessage(message)
        builder.setPositiveButton("Ok") { _, _ -> }
        val dialog: AlertDialog = builder.create()
        dialog.show()
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

            initMarker()
            initUsers()

            map.addOnMapClickListener {
                if ((currentMarker != null) && !isEditingMarkerDescription) {
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

                    if (getMarkerSetCount(fbAuth.currentUser?.uid.toString()) < 5) {
                        val storedMarker = MarkerData(null, it.latitude, it.longitude, currentMarker!!.groupId,
                                currentMarker!!.description, getHour(), fbAuth.currentUser?.uid.toString(),
                                fbAuth.currentUser?.email.toString(), getTimeStamp())
                        database.child("markers").push().setValue(storedMarker)
                    } else {
                        showDialogWith(getString(R.string.error_marker_limit))
                    }

                    currentMarker = null
                } else if (isWeatherMarker) {
                    GlobalScope.launch(Dispatchers.Main) {
                        val weatherResponse = apiService.getWeather(it.latitude.toString(),
                                it.longitude.toString())
                        setupWeatherMarkerScreen(weatherResponse)
                    }
                    isWeatherMarker = false
                }
            }

            map.setOnMarkerClickListener {
                setupEditingMarkerMenu(it)
                true
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

        // TO REMOVE - Needed to demonstrate SpeedMeter at delivery
        centerCameraButton.setOnLongClickListener {
            if (speedMeter.visibility == View.VISIBLE) {
                speedMeter.visibility = View.INVISIBLE
            } else if (speedMeter.visibility == View.INVISIBLE) {
                speedMeter.visibility = View.VISIBLE
            }
            if (warningTooFast.visibility == View.VISIBLE) {
                warningTooFast.visibility = View.INVISIBLE
            } else if (warningTooFast.visibility == View.INVISIBLE) {
                warningTooFast.visibility = View.VISIBLE
            }
            return@setOnLongClickListener true
        }
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
                        if (!markerHashMap.containsKey(key) && p0.exists() && p0.child("groupId").exists()) {

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

                            map.getAnnotation(markerHashMap[key]?.id!!)?.remove()
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
                        if (!userHashMap.containsKey(key) && p0.exists() && p0.child("location").exists()) {

                            val userLatitude = p0.child("location").child("latitude").value.toString().toDouble()
                            val userLongitude = p0.child("location").child("longitude").value.toString().toDouble()
                            val userName = p0.child("name").value.toString()
                            val userShipName = p0.child("ship_name").value.toString()
                            val userActive = p0.child("preferences").child("user_active").value.toString().toBoolean()


                            val iconFactory = IconFactory.getInstance(context!!)
                            val icon = iconFactory.fromResource(findMarkerIconMap(9))
                            

                            if (userActive) {
                                 map.addMarker(MarkerOptions()
                                        .position(LatLng(userLatitude, userLongitude))
                                        .icon(icon)
                                )
                            }

                            userHashMap[key] = UserData( 1, userName, userLatitude, userLongitude,
                                    userShipName, userActive)
                        }
                    }

                    override fun onChildRemoved(p0: DataSnapshot) {
                        val key = p0.key.toString()

                        if (markerHashMap.containsKey(key) && p0.exists()) {
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

    private fun findMarkerIconMap(groupId: Int): Int {
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

        return markerImage[groupId]!!
    }

    private fun findMarkerIconMenu(groupId: Int): Int {
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

        return markerImage[groupId]!!
    }

    private fun findMarkerTitle(groupId: Int): String {
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

    private fun closedMarkerManager() {
        val inputMethodManager = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
        markerManager.visibility = View.GONE
        markerManagerEdit.visibility = View.GONE
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

        markerManagerIcon.setImageResource(markerInformation?.markerIcon!!)
        markerManagerTitle.text = findMarkerTitle(markerInformation.groupId)
        markerManagerDescription.text = markerInformation.description
        markerManagerLikeButton.text = markerInformation.upvote.toString()
        markerManagerDislikeButton.text = markerInformation.downvote.toString()
        markerManagerCreationTime.text = getCreationString(markerInformation.timestamp)
        markerManagerId.text = getMarkerKey(mark.id)

        if (fbAuth.currentUser?.uid.toString() == markerInformation.user) {
            markerManagerOwnMarker.text = getString(R.string.user_marker)
            markerManagerOwnMarker.visibility = View.VISIBLE
            markerManagerEditButton.visibility = View.VISIBLE
            markerManagerVoteButtons.visibility = View.GONE

        } else {
            markerManagerOwnMarker.text = "Posé par : " + markerInformation.username
            markerManagerOwnMarker.visibility = View.VISIBLE
            markerManagerEditButton.visibility = View.INVISIBLE
            markerManagerVoteButtons.visibility = View.VISIBLE
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
        }

        markerManagerLikeButton.setOnClickListener {

            when {
                checkVoteMarker(markerInformation.vote!!) == 2 -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                            .setValue(markerInformation.upvote + 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                            .setValue(markerInformation.downvote - 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(1)
                }
                checkVoteMarker(markerInformation.vote!!) == 1 -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                            .setValue(markerInformation.upvote - 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(0)
                }
                else -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                            .setValue(markerInformation.upvote + 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(1)
                }
            }
        }

        markerManagerDislikeButton.setOnClickListener {
            when {
                checkVoteMarker(markerInformation.vote!!) == 2 -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                            .setValue(markerInformation.downvote - 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(0)
                }
                checkVoteMarker(markerInformation.vote!!) == 1 -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                            .setValue(markerInformation.downvote + 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("upvote")
                            .setValue(markerInformation.upvote - 1)
                    database.child("markers").child(getMarkerKey(mark.id)).child("contributors")
                            .child(currentUser).setValue(2)
                }
                else -> {
                    database.child("markers").child(getMarkerKey(mark.id)).child("downvote")
                            .setValue(markerInformation.downvote + 1)
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

        weatherMarker.weatherMarkerIcon.setImageResource(convert.getWeatherIcon(weatherResponse.weather?.weather!![0].id!!))
        weatherMarker.temperatureTextView.text = convert.getTemperature(weatherResponse.weather?.main?.temp)
        weatherMarker.descriptionTextView.text = weatherResponse.weather?.weather!![0].description
        weatherMarker.latitudeTextView.text = weatherResponse.weather!!.coord!!.lat.toString()
        weatherMarker.longitudeTextView.text = weatherResponse.weather!!.coord!!.lon.toString()
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

        println("I'm in SetupWeatherMarkerScreen" + weatherResponse.weather!!.main!!.temp.toString())

        weatherMarker.exitButton.setOnClickListener {
            weatherMarker.visibility = View.GONE
            openMarkerMenuButton.visibility = View.VISIBLE
            centerCameraButton.visibility = View.VISIBLE
            map.uiSettings.setAllGesturesEnabled(true)
        }
    }

    private fun setupDescriptionScreen() {
        isEditingMarkerDescription = true
        markerDescription.background.alpha = 128
        markerDescription.visibility = View.VISIBLE

        cancelMarkerDescription.setOnClickListener {
            markerDescription.visibility = View.GONE
            currentMarker = null
            isEditingMarkerDescription = false
        }

        submitMarkerDescription.setOnClickListener {
            val description = markerTextDescription.text.toString()

            currentMarker?.description = description
            markerTextDescription.text.clear()
            val inputMethodManager = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)

            markerDescription.visibility = View.GONE
            isEditingMarkerDescription = false
        }
    }

    private fun setupMarkerMenu() {
        markerView.alpha = 0.8F
        openMarkerMenuButton.setOnClickListener {
            if (markerMenu.visibility != View.VISIBLE) {
                markerMenu.startAnimation(fadeInAnimation)
                markerMenu.visibility = View.VISIBLE
            }
        }
        closeMarkerMenuButton.setOnClickListener {
            markerMenu.startAnimation(fadeOutAnimation)
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
        val adapter = MarkerAdapter(context!!, markersList)

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
            isWeatherMarker = true
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
