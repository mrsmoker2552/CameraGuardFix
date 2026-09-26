package com.boss.cameraguard

import android.app.Application
import org.maplibre.android.MapLibre
import com.boss.cameraguard.data.DriveDiagnosticStore

class CameraGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        MapLibre.getInstance(this)
        DriveDiagnosticStore.initialize(this)
    }
}