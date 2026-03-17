package com.enduroplus.companion

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import java.io.File
import java.io.FileOutputStream

/**
 * Main screen — shows a live leaderboard of participants, a GPS track map,
 * and buttons to scan for watches, refresh scores, and export race data.
 *
 * Layout: activity_main.xml
 * Architecture: ViewModel (ResultsViewModel) + BleManager + StateFlow → RecyclerView + TrackMapView
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ResultsViewModel by viewModels()
    private lateinit var bleManager: BleManager
    private val adapter = LeaderboardAdapter()

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

        bleManager = BleManager(applicationContext)
        viewModel.bleManager = bleManager

        binding.leaderboardRecycler.layoutManager = LinearLayoutManager(this)
        binding.leaderboardRecycler.adapter = adapter

        binding.scanButton.setOnClickListener { checkAndScan() }
        binding.refreshButton.setOnClickListener {
            viewModel.refreshScores()
            viewModel.refreshTracks()
        }
        binding.exportFitButton.setOnClickListener { exportFirstParticipantFit() }

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

        // Observe GPS track updates → refresh map view
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tracks.collect { tracksMap ->
                    // Map keys are device addresses; rename to device names
                    // using the current leaderboard for display purposes.
                    val namedTracks = tracksMap.entries.associate { (addr, pts) ->
                        val name = viewModel.leaderboard.value
                            .firstOrNull { it.deviceName.isNotEmpty() }
                            ?.deviceName ?: addr
                        name to pts
                    }
                    binding.trackMapView.setTracks(namedTracks)
                }
            }
        }

        // Observe discovered devices and auto-connect
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bleManager.devicesFlow.collect { devices ->
                    binding.deviceCountLabel.text =
                        getString(R.string.watches_found, devices.size)
                    devices.forEach { device ->
                        connectToWatch(device)
                    }
                }
            }
        }

        viewModel.startCollecting()
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.stopScan()
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

    // ---- FIT export ----

    /**
     * Exports the FIT file for the highest-ranked participant and writes it
     * to the app's external files directory (no special permission needed).
     *
     * On Android 10+ the file is also added to the MediaStore Downloads
     * collection so it appears in the Files app.
     */
    private fun exportFirstParticipantFit() {
        val topResult = viewModel.leaderboard.value.firstOrNull()
        if (topResult == null) {
            Toast.makeText(this, getString(R.string.no_data_to_export), Toast.LENGTH_SHORT).show()
            return
        }

        val deviceAddr = bleManager.devicesFlow.value
            .firstOrNull { it.name == topResult.deviceName }?.address
            ?: bleManager.devicesFlow.value.firstOrNull()?.address

        // The watch BLE protocol does not transmit the race start time, so we
        // approximate it with the current system clock.  The FIT timestamps
        // will be accurate in relative terms (lap offsets, elapsed time) even
        // though the absolute start time is an approximation.
        val startTime = System.currentTimeMillis() / 1000L
        val fitBytes = if (deviceAddr != null) {
            viewModel.exportFit(deviceAddr, startTime)
        } else {
            null
        }

        if (fitBytes == null || fitBytes.isEmpty()) {
            Toast.makeText(this, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "enduro_plus_${topResult.deviceName.replace(" ", "_")}.fit"
        saveFitFile(fileName, fitBytes)
    }

    private fun saveFitFile(fileName: String, bytes: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Toast.makeText(this,
                        getString(R.string.export_saved, fileName), Toast.LENGTH_LONG).show()
                    return
                }
            }
            // Fallback: write to app-specific external files dir
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            Toast.makeText(this,
                getString(R.string.export_saved, file.absolutePath), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this,
                getString(R.string.export_error, e.message), Toast.LENGTH_LONG).show()
        }
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
