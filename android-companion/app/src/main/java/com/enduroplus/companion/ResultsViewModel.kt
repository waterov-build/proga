package com.enduroplus.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel that exposes a leaderboard and track map derived from live BLE
 * results, and provides a helper to export session data as a FIT file.
 *
 * The leaderboard is a sorted list of [ParticipantResult] ranked by
 * [ParticipantResult.totalScore] descending.  Both the leaderboard and the
 * tracks map update automatically whenever a BLE notification arrives.
 */
class ResultsViewModel : ViewModel() {

    // Injected or replaced in tests; not constructed here to keep the VM
    // framework-agnostic (no Context dependency).
    var bleManager: BleManager? = null

    /**
     * Leaderboard: participants sorted by total score (highest first).
     * Exposed as a hot StateFlow so the UI only redraws on changes.
     */
    val leaderboard: StateFlow<List<ParticipantResult>> =
        MutableStateFlow(emptyList<ParticipantResult>())

    // Backing field so we can update it from collectResults()
    private val _leaderboard = leaderboard as MutableStateFlow

    /**
     * Tracks: map of device address → GPS polyline (lat, lon pairs).
     * Updated whenever a TRACK characteristic notification arrives.
     */
    val tracks: StateFlow<Map<String, List<Pair<Double, Double>>>> =
        MutableStateFlow(emptyMap())

    private val _tracks = tracks as MutableStateFlow

    /** Start collecting results and tracks from the BleManager. */
    fun startCollecting() {
        val mgr = bleManager ?: return
        viewModelScope.launch {
            mgr.resultsFlow.collect { resultsMap ->
                _leaderboard.value = resultsMap.values
                    .sortedByDescending { it.totalScore }
            }
        }
        viewModelScope.launch {
            mgr.tracksFlow.collect { tracksMap ->
                _tracks.value = tracksMap
            }
        }
    }

    /** Send a checkpoint list to all currently connected watches. */
    fun pushCheckpoints(checkpoints: List<Triple<String, Double, Double>>) {
        val mgr = bleManager ?: return
        viewModelScope.launch {
            mgr.devicesFlow.value.forEach { device ->
                mgr.sendCheckpoints(device, checkpoints)
            }
        }
    }

    /** Request an immediate score read from all connected watches. */
    fun refreshScores() {
        val mgr = bleManager ?: return
        mgr.devicesFlow.value.forEach { device ->
            mgr.readScore(device)
        }
    }

    /**
     * Request GPS track reads from all connected watches so the map can be
     * refreshed.  Typically called after [refreshScores] or on a timer.
     */
    fun refreshTracks() {
        val mgr = bleManager ?: return
        mgr.devicesFlow.value.forEach { device ->
            mgr.readTrack(device)
        }
    }

    /**
     * Build a [FitExporter.Session] for [deviceAddress] using the latest
     * leaderboard entry and track data, then serialise it as a FIT file.
     *
     * @param deviceAddress BLE address of the watch to export
     * @param startTimeUnixSec  Unix timestamp when the race began (provided
     *                          by the caller since the watch doesn't send it
     *                          over BLE in the current protocol version)
     * @return raw FIT bytes, or null if no data is available for the device
     */
    fun exportFit(deviceAddress: String, startTimeUnixSec: Long): ByteArray? {
        val result = bleManager?.resultsFlow?.value?.get(deviceAddress) ?: return null
        val trackPts = bleManager?.tracksFlow?.value?.get(deviceAddress) ?: emptyList()

        val laps = result.checkpoints.map { cp ->
            FitExporter.Lap(
                name      = cp.name,
                offsetSec = cp.elapsedSec,
                score     = cp.score,
            )
        }

        // Convert track (lat, lon) pairs to FitExporter.TrackPoint.
        // Without per-point timestamps, distribute evenly across elapsed time.
        val elapsedSec = if (laps.isNotEmpty()) laps.last().offsetSec else 0
        val fitTrackPoints = trackPts.mapIndexed { i, (lat, lon) ->
            val tSec = if (trackPts.size > 1 && elapsedSec > 0)
                (i.toLong() * elapsedSec / (trackPts.size - 1)).toInt()
            else 0
            FitExporter.TrackPoint(
                offsetSec = tSec,
                lat       = lat,
                lon       = lon,
                speedMs   = 0f,
            )
        }

        val session = FitExporter.Session(
            startTimeUnixSec = startTimeUnixSec,
            elapsedSec       = elapsedSec,
            totalScore       = result.totalScore,
            trackPoints      = fitTrackPoints,
            laps             = laps,
        )
        return FitExporter.export(session)
    }
}
