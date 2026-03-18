package com.example.enduro

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.enduro.garmin.GarminBridge
import com.example.enduro.sync.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val tag = "EnduroMain"
    private lateinit var garminBridge: GarminBridge
    private lateinit var syncRepo: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        syncRepo    = SyncRepository(this)
        garminBridge = GarminBridge(this) { payload ->
            Log.d(tag, "Activity received from Garmin: $payload")
            lifecycleScope.launch(Dispatchers.IO) {
                syncRepo.uploadActivity(payload)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        garminBridge.connect()
        lifecycleScope.launch(Dispatchers.IO) {
            val segments = syncRepo.fetchSegments()
            garminBridge.pushSegments(segments)
        }
    }

    override fun onPause() {
        super.onPause()
        garminBridge.disconnect()
    }
}
