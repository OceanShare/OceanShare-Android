package com.oceanshare.oceanshare

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.StrictMode
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import kotlinx.android.synthetic.main.dialog_not_implemented.view.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class UV {
    var lat: Double? = null
    var lon: Double? = null
    var date: Int? = null
    var date_iso: String? = null
    var value: Double? = null
}

class WeatherData {
    var coord: Coord? = null
    var weather: ArrayList<WeatherData2>? = null
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

class WeatherData2 {
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
    var message: String? = null
    var status: String? = null
    var uv: UV? = null
    var weather: WeatherData? = null
}

class Sys {
    var message: Double? = null
    var sunrise: Long? = null
    var sunset: Long? = null
}

class Weather {

}

interface IWeatherApi {
    @Headers(
            "Accept: application/json",
            "Content-type: application/json",
            "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJVc2VySWQiOjF9.Vcp2grZ53t_OG3jwSXsRwfc_UUjboNgZarkAGiX0jgM"
    )
    @GET("api/weather")
    suspend fun getWeather(@Query("lat") lat: String?, @Query("lng") lng: String?): FullWeather

    companion object{

        operator fun invoke() : IWeatherApi {

            val url = "https://oceanshare.cleverapps.io"
            val retrofit = Retrofit.Builder()
                    .baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

            val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)

            val service = retrofit.create(IWeatherApi::class.java)

            return  service
        }
    }
}