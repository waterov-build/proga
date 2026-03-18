package com.example.enduro.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.enduro.api.ActivityUploadRequest
import com.example.enduro.api.ApiClient
import org.json.JSONObject

class SyncRepository(context: Context) {

    private val tag   = "SyncRepository"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("enduro_prefs", Context.MODE_PRIVATE)

    private val authToken: String
        get() = "Bearer ${prefs.getString("access_token", "") ?: ""}"

    suspend fun fetchSegments(): List<Map<String, Any>> {
        return try {
            val dtos = ApiClient.api.getSegments(authToken)
            dtos.map { dto ->
                mapOf(
                    "id"        to dto.id,
                    "name"      to dto.name,
                    "start_lat" to dto.start_lat,
                    "start_lon" to dto.start_lon,
                    "end_lat"   to dto.end_lat,
                    "end_lon"   to dto.end_lon
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch segments", e)
            emptyList()
        }
    }

    suspend fun uploadActivity(payload: JSONObject): Boolean {
        return try {
            val trackList = mutableListOf<Map<String, Any>>()
            val trackArray = payload.getJSONArray("track")
            for (i in 0 until trackArray.length()) {
                val pt = trackArray.getJSONObject(i)
                trackList.add(
                    mapOf(
                        "lat"  to pt.getDouble("lat"),
                        "lon"  to pt.getDouble("lon"),
                        "time" to pt.getLong("time")
                    )
                )
            }
            val request = ActivityUploadRequest(track = trackList)
            val response = ApiClient.api.uploadActivity(authToken, request)
            Log.d(tag, "Uploaded activity ${response.activity_id}, found ${response.attempts} attempts")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload activity", e)
            false
        }
    }
}
