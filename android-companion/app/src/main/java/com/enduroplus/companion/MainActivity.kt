package com.enduroplus.companion

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.enduroplus.companion.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main screen — shows a live leaderboard of participants, a scan button,
 * and a shortcut to the Course Editor.
 *
 * Layout: activity_main.xml
 * Architecture: ViewModel (ResultsViewModel) + BleManager + StateFlow → RecyclerView
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ResultsViewModel by viewModels()
    private val adapter = LeaderboardAdapter()

    // Shared BleManager lives in the Application so CourseEditorActivity can reuse it.
    private val bleManager: BleManager
        get() = EnduroPlusApplication.instance?.bleManager
            ?: error("EnduroPlusApplication not initialised — check AndroidManifest.xml")

    // ---- Permission handling ----

    companion object {
        // Must be slightly longer than BleManager.SCAN_TIMEOUT_MS so the
        // button re-enables after scanning has already stopped.
        private const val SCAN_BUTTON_REENABLE_MS = BleManager.SCAN_TIMEOUT_MS + 1_000L
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) {
                startScan()
            } else {
                Toast.makeText(this,
                    "Bluetooth permissions required to scan for watches",
                    Toast.LENGTH_LONG).show()
            }
        }

    // ---- Lifecycle ----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.bleManager = bleManager

        binding.leaderboardRecycler.layoutManager = LinearLayoutManager(this)
        binding.leaderboardRecycler.adapter = adapter

        binding.scanButton.setOnClickListener { checkAndScan() }
        binding.refreshButton.setOnClickListener { viewModel.refreshScores() }
        binding.manageCourseButton.setOnClickListener {
            startActivity(Intent(this, CourseEditorActivity::class.java))
        }
        binding.openMapButton.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        binding.exportButton.setOnClickListener { exportSession() }

        // Observe leaderboard updates
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.leaderboard.collect { results ->
                    adapter.submitList(results)
                    binding.emptyLabel.visibility =
                        if (results.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Observe discovered devices and auto-connect
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bleManager.devicesFlow.collect { devices ->
                    binding.deviceCountLabel.text =
                        "Watches found: ${devices.size}"
                    devices.forEach { device ->
                        connectToWatch(device)
                    }
                }
            }
        }

        viewModel.startCollecting()
    }

    // ---- BLE scanning ----

    private fun checkAndScan() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startScan()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startScan() {
        binding.scanButton.isEnabled = false
        binding.scanButton.text = getString(R.string.scanning)
        bleManager.startScan()
        // Re-enable after scan timeout
        binding.scanButton.postDelayed({
            binding.scanButton.isEnabled = true
            binding.scanButton.text = getString(R.string.scan)
        }, SCAN_BUTTON_REENABLE_MS)
    }

    private fun connectToWatch(device: BluetoothDevice) {
        bleManager.connect(device)
    }

    // ---- Session export ----

    /**
     * Serialises the current leaderboard and the active course to a JSON string,
     * then opens the system share sheet so the user can send it via any app.
     *
     * JSON structure:
     * ```
     * {
     *   "exportedAt": "2026-03-18T07:10:40Z",
     *   "course": { "name": "...", "checkpoints": [...] },
     *   "leaderboard": [
     *     { "rank": 1, "participant": "...", "totalScore": 350,
     *       "checkpoints": [{ "name": "CP1", "elapsedSec": 115, "score": 105 }, …] },
     *     …
     *   ]
     * }
     * ```
     */
    private fun exportSession() {
        val results = viewModel.leaderboard.value
        if (results.isEmpty()) {
            Toast.makeText(this, R.string.no_results_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        val course = CourseRepository(applicationContext).loadAll().firstOrNull()
        val courseJson = course?.let { c ->
            JSONObject().apply {
                put("name", c.name)
                put("checkpoints", JSONArray(c.checkpoints.map { cp ->
                    JSONObject().apply {
                        put("name",   cp.name)
                        put("lat",    cp.lat)
                        put("lon",    cp.lon)
                        put("parSec", cp.parSec)
                    }
                }))
            }
        }

        val leaderboardJson = JSONArray(results.mapIndexed { index, r ->
            JSONObject().apply {
                put("rank",        index + 1)
                put("participant", r.deviceName)
                put("totalScore",  r.totalScore)
                put("checkpoints", JSONArray(r.checkpoints.map { cp ->
                    JSONObject().apply {
                        put("name",       cp.name)
                        put("elapsedSec", cp.elapsedSec)
                        put("score",      cp.score)
                    }
                }))
            }
        })

        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .format(Date())
        val root = JSONObject().apply {
            put("exportedAt", timestamp)
            if (courseJson != null) put("course", courseJson)
            put("leaderboard", leaderboardJson)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_session_subject))
            putExtra(Intent.EXTRA_TEXT, root.toString(2))
        }
        startActivity(Intent.createChooser(shareIntent,
            getString(R.string.export_session_subject)))
    }
}

// ---- RecyclerView adapter ----

private class LeaderboardAdapter :
    ListAdapter<ParticipantResult, LeaderboardAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val rank:  TextView = view.findViewById(R.id.rankText)
        private val name:  TextView = view.findViewById(R.id.nameText)
        private val score: TextView = view.findViewById(R.id.scoreText)
        private val cps:   TextView = view.findViewById(R.id.checkpointsText)

        fun bind(result: ParticipantResult, rankPos: Int) {
            rank.text  = "#$rankPos"
            name.text  = result.deviceName
            score.text = "${result.totalScore} pts"
            cps.text   = result.checkpoints.joinToString("  ") { cp ->
                "${cp.name}: ${cp.elapsedSec}s (+${cp.score})"
            }.ifEmpty { "No checkpoints yet" }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ParticipantResult>() {
            override fun areItemsTheSame(a: ParticipantResult, b: ParticipantResult) =
                a.deviceName == b.deviceName
            override fun areContentsTheSame(a: ParticipantResult, b: ParticipantResult) =
                a == b
        }
    }
}
