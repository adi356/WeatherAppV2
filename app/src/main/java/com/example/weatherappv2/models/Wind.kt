package com.example.weatherappv2.models

import java.io.Serializable

data class Wind(
    val speed: Double,
    val degree: Int
) : Serializable
