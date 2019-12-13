package com.oceanshare.oceanshare

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.oceanshare.oceanshare.utils.isConnectedToNetwork
import kotlinx.android.synthetic.main.fragment_meteo.*

class MeteoFragment : Fragment() {
    private var apiService = IWeatherApi()
    private var weatherConverter = WeatherConverter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_meteo, container, false)
    }

    @SuppressLint("DefaultLocale")
    suspend fun fetchMeteo(location: Location) {
        if (context == null || !context!!.isConnectedToNetwork()) {
            return
        }
        val meteo = apiService.getWeather(location.latitude.toString(), location.longitude.toString())
        meteo.weather?.weather?.get(0)?.let { analyseDescription(it) }?.let { meteoImage.setImageResource(it) }
        val sharedPref = activity?.getSharedPreferences("OCEANSHARE_SHARED_PREFERENCES", Context.MODE_PRIVATE)
                ?: return
        if (sharedPref.getInt("temperature_preferences", 0) == 0) {
            meteoTemp.text = weatherConverter.getTemperature(meteo.weather?.main?.temp)
        } else {
            meteoTemp.text = weatherConverter.getFTemperature(meteo.weather?.main?.temp)
        }
        meteo.weather?.weather?.let {
            meteoDescription.text = it[0].description?.capitalize()
        }
        meteoLongitute.text = location.longitude.toString()
        meteoLatitude.text = location.latitude.toString()
        meteoSunrise.text = weatherConverter.getTime(meteo.weather?.sys?.sunrise)
        meteoSunset.text = weatherConverter.getTime(meteo.weather?.sys?.sunset)
        meteoRainRisk.text = weatherConverter.getCloudyValue(meteo.weather?.clouds?.all)
        meteoWaterTemp.text = "--"
        meteoWind.text = weatherConverter.getWindData(meteo.weather?.wind?.deg, meteo.weather?.wind?.speed)
        meteoHumidity.text = weatherConverter.getHumidity(meteo.weather?.main?.humidity)
        meteoVisibility.text = weatherConverter.getVisibility(meteo.weather?.visibility)
        meteoUVIndice.text = weatherConverter.getUv(meteo.uv?.value)
    }

    private fun analyseDescription(weather: WeatherData2): Int {
        val choosenOne: Int
        val id = weather.id

        if (id in 0..232) {
            choosenOne = R.drawable.storm
        } else if (id in 300..321 || id in 520..531) {
            choosenOne = R.drawable.light_rain
        } else if (id in 500..504) {
            choosenOne = R.drawable.rain
        } else if (id == 511 || id in 600..601 || id in 615..622) {
            choosenOne = R.drawable.snowflake
        } else if (id in 611..613) {
            choosenOne = R.drawable.hail
        } else if (id in 701..771) {
            choosenOne = R.drawable.cloud
        } else if (id == 781) {
            choosenOne = R.drawable.tornado
        } else if (id == 800) {
            choosenOne = R.drawable.sun
        } else if (id == 801 || id == 802) {
            choosenOne = R.drawable.cloudy
        } else if (id == 803 || id == 804) {
            choosenOne = R.drawable.clouds
        } else {
            choosenOne = R.drawable.thermometer
        }
        return choosenOne

    }
}
