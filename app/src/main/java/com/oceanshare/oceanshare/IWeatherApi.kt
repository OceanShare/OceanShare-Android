package com.oceanshare.oceanshare

import android.os.StrictMode
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import java.util.*

class UV {
    var value: Double? = null
}

class WeatherData {
    var coord: Coord? = null
    var weather: ArrayList<WeatherData2>? = null
    var main: Main? = null
    var wind: Wind? = null
    var clouds: Clouds? = null
    var sys: Sys? = null
    var visibility: Int? = null
    var id: Int? = null
    var name: String? = null
}

class WeatherData2 {
    var id: Int? = null
    var main: String? = null
    var description: String? = null
}

class Coord {
    var lon: Double? = null
    var lat: Double? = null
}

class Main {
    var temp: Double? = null
    var humidity: Int? = null
}

class Wind {
    var speed: Double? = null
    var deg: Double? = null
}

class Clouds {
    var all: Int? = null
}

class FullWeather {
    var uv: UV? = null
    var weather: WeatherData? = null
}

class Sys {
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
    suspend fun getWeather(@Query("lat") lat: String?, @Query("lng") lng: String?): FullWeather

    companion object {

        operator fun invoke(): IWeatherApi {
            val url = "https://oceanshare.cleverapps.io"
            val retrofit = Retrofit.Builder()
                    .baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

            val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)

            return retrofit.create(IWeatherApi::class.java)
        }
    }
}