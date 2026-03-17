package com.example.enduro.garmin

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridges communication between the Android app and a paired Garmin device via
 * the Garmin Mobile SDK (ConnectIQ).  The Garmin SDK dependency is declared but
 * kept as a comment in build.gradle.kts because it requires a local AAR; the
 * interface below uses plain JSON so it is easy to swap in the real SDK calls.
 */
class GarminBridge(
    private val context: Context,
    private val onActivityReceived: (JSONObject) -> Unit
) {
    private val tag = "GarminBridge"

    fun connect() {
        Log.d(tag, "Connecting to Garmin device…")
        // TODO: initialize ConnectIQ SDK and open channel
    }

    fun disconnect() {
        Log.d(tag, "Disconnecting from Garmin device…")
        // TODO: close ConnectIQ channel
    }

    /**
     * Push a list of segments to the Garmin app so [SegmentEngine] can detect
     * segment start/end points on-device.
     */
    fun pushSegments(segments: List<Map<String, Any>>) {
        val payload = JSONObject().apply {
            put("type", "segments")
            put("segments", JSONArray(segments.map { seg ->
                JSONObject(seg)
            }))
        }
        Log.d(tag, "Pushing ${segments.size} segments to Garmin: $payload")
        // TODO: transmit payload via ConnectIQ SDK
    }

    /**
     * Called by the SDK when the Garmin app sends an activity track back.
     */
    fun onMessageFromDevice(raw: String) {
        try {
            val json = JSONObject(raw)
            onActivityReceived(json)
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse message from Garmin device", e)
        }
    }
}
