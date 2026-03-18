package com.enduroplus.companion

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.enduroplus.companion.databinding.ActivityMapBinding
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Displays the active course checkpoints and each participant's GPS track on an
 * interactive OSMDroid map.
 *
 * Checkpoints are drawn as markers with their name and par time.
 * Each participant's track is drawn as a coloured polyline; colours are
 * assigned from [trackColors] in order of the address map key.
 *
 * Layout: activity_map.xml
 */
class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding

    private val bleManager: BleManager
        get() = EnduroPlusApplication.instance?.bleManager
            ?: error("EnduroPlusApplication not initialised — check AndroidManifest.xml")

    /** Distinct colours for up to 8 simultaneous participants. */
    private val trackColors = listOf(
        Color.parseColor("#F44336"), // red
        Color.parseColor("#2196F3"), // blue
        Color.parseColor("#4CAF50"), // green
        Color.parseColor("#FF9800"), // orange
        Color.parseColor("#9C27B0"), // purple
        Color.parseColor("#00BCD4"), // cyan
        Color.parseColor("#FFEB3B"), // yellow
        Color.parseColor("#795548"), // brown
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OSMDroid requires the app package name as the HTTP user-agent.
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.map_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(15.0)

        // Load the first saved course and draw its checkpoints.
        val course = CourseRepository(applicationContext).loadAll().firstOrNull()
        if (course != null) {
            drawCheckpoints(course.checkpoints)
            val first = course.checkpoints.first()
            binding.map.controller.setCenter(GeoPoint(first.lat, first.lon))
        } else {
            Toast.makeText(this, R.string.no_course_for_map, Toast.LENGTH_LONG).show()
            binding.map.controller.setCenter(GeoPoint(0.0, 0.0))
        }

        // Observe GPS track updates from connected watches.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bleManager.tracksFlow.collect { tracksMap ->
                    drawParticipantTracks(tracksMap)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ---- Drawing helpers ----

    /**
     * Adds a [Marker] for each checkpoint.  Existing checkpoint markers are
     * removed first so this method is safe to call multiple times.
     */
    private fun drawCheckpoints(checkpoints: List<CourseCheckpoint>) {
        binding.map.overlays.removeAll { it is Marker }
        checkpoints.forEach { cp ->
            val marker = Marker(binding.map)
            marker.position = GeoPoint(cp.lat, cp.lon)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = cp.name
            marker.snippet = "Par: ${cp.parSec}s"
            binding.map.overlays.add(marker)
        }
        binding.map.invalidate()
    }

    /**
     * Replaces all participant track polylines on the map.
     * Each entry in [tracksMap] (keyed by BLE device address) gets its own
     * colour from [trackColors].
     */
    private fun drawParticipantTracks(tracksMap: Map<String, List<TrackPoint>>) {
        binding.map.overlays.removeAll { it is Polyline }
        tracksMap.entries.forEachIndexed { index, (_, points) ->
            if (points.size < 2) return@forEachIndexed
            val line = Polyline()
            line.color = trackColors[index % trackColors.size]
            line.width = 6f
            line.setPoints(points.map { GeoPoint(it.lat, it.lon) })
            binding.map.overlays.add(line)
        }
        binding.map.invalidate()
    }
}
