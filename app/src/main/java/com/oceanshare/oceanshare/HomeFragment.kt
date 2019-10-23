package com.oceanshare.oceanshare

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.location.Location
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.StrictMode
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.Gravity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.beust.klaxon.Klaxon
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
import kotlinx.android.synthetic.main.dialog_not_implemented.view.*
import kotlinx.android.synthetic.main.dialog_not_implemented.view.dialogCancelBtn
import kotlinx.android.synthetic.main.dialog_tmp.view.*
import kotlinx.android.synthetic.main.custom_toast.*
import kotlinx.android.synthetic.main.custom_toast.view.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.marker_manager.*
import kotlinx.android.synthetic.main.marker_manager.view.*
import kotlinx.android.synthetic.main.fragment_profile.view.*
import kotlinx.android.synthetic.main.marker_entry.view.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.collections.HashMap

interface LoadingImplementation {
    fun onFinishedLoading()
}

class UV {
    var lat: Double? = null
    var lon: Double? = null
    var date: Int? = null
    var value: Double? = null
}

class Weather {
    var coor: Coord? = null
    var weather: ArrayList<Weather2>? = null
    var base: String? = null
    var main: Main? = null
    var wind: Wind? = null
    var clouds: Clouds? = null
    var dt: Int? = null
    var sys: Sys? = null
    var timezone: Int? = null
    var id: Int? = null
    var name: String? = null
    var cod: Int? = null
}

class Weather2 {
    var id: Int? = null
    var main: String? = null
    var description: String? = null
    var icon: String? = null
}

class Coord {
    var lon: Double? = null
    var lat: Double? = null
}

class Main {
    var temp: Double? = null
    var pressure: Int? = null
    var humidity: Int? = null
    var temp_min: Double? = null
    var temp_max: Double? = null
    var sea_level: Int? = null
    var grnd_level: Int? = null
}

class Wind {
    var speed: Double? = null
    var deg: Double? = null
}

class Clouds {
    var all: Int? = null
}

class FullWeather {
    var uv: String? = null
    var weather: String? = null
}

class Sys {
    var message: Double? = null
    var sunrise: Long? = null
    var sunset: Long? = null
}

interface IWeatherApi {
    @Headers(
            "Accept: application/json",
            "Content-type: application/json",
            "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJVc2VySWQiOjF9.Vcp2grZ53t_OG3jwSXsRwfc_UUjboNgZarkAGiX0jgM"
    )
    @GET("api/weather")
    fun getWeather(@Query("lat") lat: String?, @Query("lng") lng: String?): Call<FullWeather>
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
    private var  markerHashMap : HashMap<String, MarkerData> = HashMap()
    private var hashMap: HashMap<String, MarkerData> = HashMap()
    private var isEditingMarkerDescription = false

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
            map.setMinZoomPreference(16.00)

            database = FirebaseDatabase.getInstance().reference

            enableLocation()

            initMarker()

            map.addOnMapClickListener {
                if (currentMarker != null && isEditingMarkerDescription == false) {
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
                                currentMarker!!.description, getHour(), fbAuth.currentUser?.uid.toString(),
                                getTimeStamp())
                        database.child("markers").push().setValue(storedMarker)

                        showNotification(getString(R.string.validation_marker_added), false)

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
                setupEditingMarkerMenu(it)
            }

        }
        setupFadeAnimations()
        setupMarkerMenu()

        centerCameraButton.setOnClickListener {


            Log.e("DEBUG","1")
            val url = "http://35.198.134.25:5000/"
            val retrofit = Retrofit.Builder()
                    .baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

            val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)

            Log.e("DEBUG","2")
            val service = retrofit.create(IWeatherApi::class.java)
            val weather = service.getWeather(originLocation.latitude.toString(), originLocation.longitude.toString())
            //val okok = weather.execute()
            //val ok = okok.body()
            //val okk = okok.raw()
            //val okkk = okok.errorBody()

            Log.e("DEBUG","3")
            weather.enqueue(object: Callback<FullWeather> {
                @SuppressLint("NewApi")
                override fun onResponse(call: Call<FullWeather>, response: Response<FullWeather>) {
                    val fullWeather = response.body()

                    val uv = Klaxon().parse<UV>(fullWeather!!.uv!!)
                    val weather = Klaxon().parse<Weather>(fullWeather!!.weather!!)


                    val mDialogView = LayoutInflater.from(context).inflate(R.layout.dialog_tmp, null)
                    val mBuilder = AlertDialog.Builder(context!!, R.style.DialogTheme)
                            .setView(mDialogView)
                    val  mAlertDialog = mBuilder.show()
                    mDialogView.dialogCancelBtn.setOnClickListener {
                        mAlertDialog.dismiss()
                    }

                    mDialogView.nameTextView.text = "City: " + weather!!.name

                    mDialogView.temperatureTextView.text = "Temperature: " + BigDecimal(weather!!.main!!.temp!! - 273.15).setScale(1, RoundingMode.HALF_EVEN) + " °C"
                    mDialogView.descriptionTextView.text = "Description: " + weather!!.weather!!.first().description

                    mDialogView.longitudeTextView.text = "Longitude: " + uv!!.lon
                    mDialogView.latitudeTextView.text = "Latitude: " + uv!!.lat

                    val sunriseDate = Instant.ofEpochSecond(weather!!.sys!!.sunrise!!).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    val sunsetDate = Instant.ofEpochSecond(weather!!.sys!!.sunset!!).atZone(ZoneId.systemDefault()).toLocalDateTime()

                    var sunriseHour = sunriseDate.hour.toString()
                    if (sunriseDate.hour.toString().length == 1) {
                        sunriseHour = "0" + sunriseHour
                    }
                    var sunriseMinute = sunriseDate.minute.toString()
                    if (sunriseDate.minute.toString().length == 1) {
                        sunriseMinute = "0" + sunriseMinute
                    }
                    var sunsetHour = sunsetDate.hour.toString()
                    if (sunsetDate.hour.toString().length == 1) {
                        sunsetHour = "0" + sunsetHour
                    }
                    var sunsetMinute = sunsetDate.minute.toString()
                    if (sunsetDate.minute.toString().length == 1) {
                        sunsetMinute = "0" + sunsetMinute
                    }
                    mDialogView.sunriseTextView.text = "Sunrise: " + sunriseHour + ":" + sunriseMinute + " AM"
                    mDialogView.sunsetTextView.text = "Sunset: " + sunsetHour + ":" + sunsetMinute + " PM"

                    mDialogView.cloudCoverTextView.text = "Cloud Cover: " + "0 %"
                    mDialogView.waterTemperatureTextView.text = "Water Temp.: " + "-- °C"

                    mDialogView.windTextView.text = "Wind: " + weather!!.wind!!.speed + " km/h"
                    mDialogView.humidityTextView.text = "Humidity: " + weather!!.main!!.humidity + "%"

                    mDialogView.visibilityTextView.text = "Visibility: " + "10.0 km/h"
                    mDialogView.uvIndiceTextView.text = "UV Indice: " + uv!!.value

                    Log.e("DEBUG", "OKOK")
                }
                override fun onFailure(call: Call<FullWeather>, t: Throwable) {
                    Log.e("TAN", "Error : $t")
                    Log.e("DEBUG","6")
                }
            })

            Log.e("DEBUG","7")
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

        val colorFade = ValueAnimator.ofArgb(resources.getColor(R.color.opaque_white), resources.getColor(R.color.opaque_green))
        colorFade.duration = 3000
        colorFade.addUpdateListener {
            topBarStatus.setBackgroundColor(it.animatedValue as Int)
        }

        val opacityFade = ValueAnimator.ofFloat(1f, 0f)
        opacityFade.duration = 3000
        opacityFade.addUpdateListener {
            notificationLogo.alpha = it.animatedValue as Float
        }

        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.interpolator = DecelerateInterpolator() //add this
        fadeIn.duration = 3000
        fadeIn.setAnimationListener(object: Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                notificationMarker.visibility = View.VISIBLE
            }
            override fun onAnimationRepeat(p0: Animation?) {}
            override fun onAnimationEnd(p0: Animation?) {
                colorFade.reverse()
                opacityFade.reverse()
            }
        })

        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.interpolator = AccelerateInterpolator()
        fadeOut.startOffset = 5000
        fadeOut.duration = 3000
        fadeOut.setAnimationListener(object: Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                colorFade.start()
                opacityFade.start()
            }
            override fun onAnimationRepeat(p0: Animation?) {}
            override fun onAnimationEnd(p0: Animation?) {
                notificationMarker.visibility = View.INVISIBLE
            }
        })

        val markerAnimations = AnimationSet(false)
        markerAnimations.addAnimation(fadeOut)
        markerAnimations.addAnimation(fadeIn)
        notificationMarker.startAnimation(markerAnimations)
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
        longDisplay.text = getText(R.string.longitude).toString() + "\t" + "%.4f".format(originLocation.longitude)
        latDisplay.text = getText(R.string.latitude).toString() + "\t\t" + "%.4f".format(originLocation.latitude)
    }

    private fun closedMarkerManager() {
        val inputMethodManager = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
        markerManager.visibility = View.GONE
        markerManagerEdit.visibility = View.GONE
        markerManagerDescription.visibility = View.VISIBLE
        showHideMarkerMenuButton.visibility = View.VISIBLE
        centerCameraButton.visibility = View.VISIBLE
        map.uiSettings.setAllGesturesEnabled(true)
    }

    private fun setupEditingMarkerMenu(mark: com.mapbox.mapboxsdk.annotations.Marker) {

        val markerInformation = markerHashMap[getMarkerKey(mark.id)]
        val currentUser = fbAuth.currentUser?.uid.toString()


        markerManager.visibility = View.VISIBLE
        showHideMarkerMenuButton.visibility = View.GONE
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
            setupLocationDisplay()
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
            //setCameraPosition(location)
            setupLocationDisplay()
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
