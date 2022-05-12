package com.example.weatherappv2.network

import com.example.weatherappv2.models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {

    @GET(value = "2.5/weather")
    fun getWeather (
        @Query("lat") lat: Double,
        @Query("lon") long: Double,
        @Query("units") units: String?,
        @Query("appid") appid: String?,
    ) : Call<WeatherResponse>
}