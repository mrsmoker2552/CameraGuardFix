package com.boss.cameraguard.alerts

import java.util.concurrent.CopyOnWriteArraySet

/**
 * In-process source of truth for the currently selected camera target.
 *
 * The DrivingService owns the warning engine. The Activity only observes the
 * service result so UI state and audio-warning state can never diverge because
 * of two independent warning-engine instances.
 */
object CameraWarningRuntime {

    private val listeners =
        CopyOnWriteArraySet<(RealCameraTarget?) -> Unit>()

    @Volatile
    var latestTarget: RealCameraTarget? = null
        private set

    fun publish(target: RealCameraTarget?) {
        latestTarget = target
        listeners.forEach { listener ->
            runCatching {
                listener(target)
            }
        }
    }

    fun addListener(
        listener: (RealCameraTarget?) -> Unit
    ) {
        listeners.add(listener)
        listener(latestTarget)
    }

    fun removeListener(
        listener: (RealCameraTarget?) -> Unit
    ) {
        listeners.remove(listener)
    }

    fun clear() {
        latestTarget = null
        listeners.forEach { listener ->
            runCatching {
                listener(null)
            }
        }
    }
}
