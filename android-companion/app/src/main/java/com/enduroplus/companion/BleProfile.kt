package com.enduroplus.companion

/**
 * BLE GATT profile constants shared between the Android companion and the
 * Garmin watch app.  UUID strings must match those in EnduroPlusApp.mc.
 */
object BleProfile {
    /** Primary ENDURO PLUS service */
    const val SERVICE_UUID    = "12340000-1234-1234-1234-123456789abc"
    /** Read/Notify: serialised race results (score + checkpoint list) */
    const val CHAR_SCORE      = "12340001-1234-1234-1234-123456789abc"
    /** Read: GPS track polyline snapshot */
    const val CHAR_TRACK      = "12340002-1234-1234-1234-123456789abc"
    /** Write: push a new checkpoint list to the watch */
    const val CHAR_CP_LIST    = "12340003-1234-1234-1234-123456789abc"
    /** Read/Notify: live status string */
    const val CHAR_STATUS     = "12340004-1234-1234-1234-123456789abc"
    /** Standard CCCD (Client Characteristic Configuration Descriptor) */
    const val CCCD_UUID       = "00002902-0000-1000-8000-00805f9b34fb"
}

/**
 * Decoded result from a single watch.
 *
 * @param deviceName  BLE device name (e.g. "ENDURO PLUS – Alice")
 * @param totalScore  Total points accumulated
 * @param checkpoints List of [CheckpointEntry] in order of completion
 */
data class ParticipantResult(
    val deviceName: String,
    val totalScore: Int,
    val checkpoints: List<CheckpointEntry>,
)

/**
 * A single completed checkpoint record decoded from the BLE payload.
 */
data class CheckpointEntry(
    val name: String,
    val elapsedSec: Int,
    val score: Int,
)

/**
 * A checkpoint in a course definition, including the par time for scoring.
 *
 * @param name    Human-readable label (e.g. "CP1-Start")
 * @param lat     Latitude in degrees WGS-84
 * @param lon     Longitude in degrees WGS-84
 * @param parSec  Par time for this segment in seconds (used for watch scoring)
 */
data class CourseCheckpoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val parSec: Int,
)

/**
 * Parses the text payload produced by EnduroPlusModel.serializeResults().
 * Format: "score=NNN;cp1=name,elapsed,score;cp2=…"
 */
fun parseResults(deviceName: String, payload: String): ParticipantResult {
    val parts = payload.split(";")
    val totalScore = parts.firstOrNull { it.startsWith("score=") }
        ?.removePrefix("score=")?.toIntOrNull() ?: 0
    val checkpoints = parts
        .filter { it.matches(Regex("cp\\d+=.*")) }
        .mapNotNull { entry ->
            val fields = entry.substringAfter("=").split(",")
            if (fields.size >= 3) {
                CheckpointEntry(
                    name       = fields[0],
                    elapsedSec = fields[1].toIntOrNull() ?: 0,
                    score      = fields[2].toIntOrNull() ?: 0,
                )
            } else null
        }
    return ParticipantResult(deviceName, totalScore, checkpoints)
}

/**
 * Encodes a list of [CourseCheckpoint] to the format expected by the watch.
 * Format (one line per checkpoint): "name,lat,lon,parSec\n…"
 * The parSec field enables the watch to use real per-segment par times.
 */
fun encodeCheckpointList(checkpoints: List<CourseCheckpoint>): String =
    checkpoints.joinToString("\n") { cp ->
        "${cp.name},%.6f,%.6f,${cp.parSec}".format(cp.lat, cp.lon)
    }

/**
 * A single GPS sample from the watch's [TrackRecorder] ring-buffer.
 *
 * @param lat       Latitude in degrees WGS-84
 * @param lon       Longitude in degrees WGS-84
 * @param speedMs   Speed in m/s
 * @param elapsedSec Seconds since race start
 */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val speedMs: Double,
    val elapsedSec: Int,
)

/**
 * Parses the CHAR_TRACK payload produced by [TrackRecorder.serialize].
 *
 * Format: `"track:<count>|<lat>,<lon>,<spd>,<t>|…"`
 * - lat/lon are decimal degrees (6 d.p.)
 * - spd is m/s (1 d.p.)
 * - t is elapsed seconds since race start
 *
 * Returns an empty list if the payload is malformed.
 */
fun parseTrack(payload: String): List<TrackPoint> {
    if (!payload.startsWith("track:")) return emptyList()
    return payload.split("|")
        .drop(1) // skip "track:<count>" header
        .mapNotNull { seg ->
            val f = seg.split(",")
            if (f.size >= 4) {
                TrackPoint(
                    lat        = f[0].toDoubleOrNull() ?: return@mapNotNull null,
                    lon        = f[1].toDoubleOrNull() ?: return@mapNotNull null,
                    speedMs    = f[2].toDoubleOrNull() ?: return@mapNotNull null,
                    elapsedSec = f[3].toIntOrNull()    ?: return@mapNotNull null,
                )
            } else null
        }
}
