package com.enduroplus.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel that exposes a leaderboard derived from live BLE results.
 *
 * The leaderboard is a sorted list of [ParticipantResult] ranked by
 * [ParticipantResult.totalScore] descending.  It updates automatically
 * whenever a new BLE notification arrives from any connected watch.
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

    /** Start collecting results from the BleManager and updating the leaderboard. */
    fun startCollecting() {
        val mgr = bleManager ?: return
        viewModelScope.launch {
            mgr.resultsFlow.collect { resultsMap ->
                _leaderboard.value = resultsMap.values
                    .sortedByDescending { it.totalScore }
            }
        }
    }

    /** Send a checkpoint list to all currently connected watches. */
    fun pushCheckpoints(checkpoints: List<CourseCheckpoint>) {
        val mgr = bleManager ?: return
        mgr.sendCheckpointsAll(checkpoints)
    }

    /** Request an immediate score read from all connected watches. */
    fun refreshScores() {
        val mgr = bleManager ?: return
        mgr.devicesFlow.value.forEach { device ->
            mgr.readScore(device)
        }
    }
}
