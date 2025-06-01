package org.cheek.wakeitup

import android.app.Application

class WakeItUpApp : Application() {
    val database by lazy { DeviceDatabase.getDatabase(this) }
}