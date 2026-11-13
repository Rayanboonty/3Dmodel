package dev.galios

import android.app.Application

class GalIosApp : Application() {
    val controller by lazy { Controller(this) }
}
