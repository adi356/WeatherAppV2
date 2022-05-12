@file:Suppress("DEPRECATION")

package com.example.weatherappv2

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherappv2.models.Weather
import com.example.weatherappv2.models.WeatherResponse
import com.example.weatherappv2.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()
        //check if location provider is turned on in emulator
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow for app to work.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
            //END
        }
    }

    //Checks if user has given access to location services
    private fun isLocationEnabled(): Boolean {
        //This provides access to the system location services
        val locationManager: LocationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }
    //END

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It looks like permissions required for this feature have been turned off, they can be re-enabled under Application Settings.")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        //TODO come back to this and potentially change, something not working correctly with looper
        Looper.myLooper()?.let {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                it
            )
        }
    }
    //END

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }
    // END

    //function for getting the weather using retrofit
    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {

            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    //need to fix, check if response is succesful then if it is create a weatherList variable which will be weather response
                    //may not need to be denullified(?)
                    if (response!!.isSuccessful) {

                        hideProgressDialog()

                        val weatherList = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()

                        Log.i("Response Result", "$weatherList")
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generec Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error", t!!.message.toString())
                    hideProgressDialog()
                }

            })

        } else {
            Toast.makeText(
                this@MainActivity,
                "No internet connection available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    //END

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progres)

        //start the dialog and display
        mProgressDialog!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }


    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI() {

        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList.weather.indices) {
                Log.i("Weather Name", weatherList.weather.toString())

                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                //TODO - need to figure out whats wrong with using locales here, and how to safely replace it
                tv_temp.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                tv_sunset_time.text = unixTime(weatherList.sys.sunset)

                tv_humidity.text = weatherList.main.humidity.toString() + " % humidity"
                tv_min.text = weatherList.main.temp_min.toString() + "° min"
                tv_max.text = weatherList.main.temp_max.toString() + "° max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country

                when (weatherList.weather[i].icon) {
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)

                }
            }
        }
    }

        private fun getUnit(value: String): String? {
            var value = "°C"
            if ("US" == value || "LR" == value || "MM" == value) {
                value = "°F"
            }
            return value
        }

        //get the time for sunrise and sunset
        //TODO - not showing correct location, manually enter lon and lat?
        private fun unixTime(timex: Long): String? {
            val date = Date(timex * 1000L)
            val sdf = SimpleDateFormat("HH:mm", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            return sdf.format(date)
        }
}
