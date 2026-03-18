package com.enduroplus.companion

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a minimal FIT (Flexible and Interoperable Data Transfer) activity
 * file from race session data received from the Garmin watch over BLE.
 *
 * The generated FIT file contains:
 *   • FILE_ID message   — file type, time created
 *   • RECORD messages   — one per GPS track point (timestamp, lat, lon, speed)
 *   • LAP messages      — one per completed checkpoint (elapsed time)
 *   • SESSION message   — overall race summary (start time, elapsed time, sport)
 *   • File-level CRC    — CRC-16 over all data bytes
 *
 * The file can be imported into Garmin Connect, Strava, or any FIT-compatible
 * application to review or share a race session.
 *
 * References: ANT+ FIT Protocol specification (fitfile.com / garmin.com).
 */
object FitExporter {

    // FIT epoch starts 1989-12-31 00:00:00 UTC; Unix epoch starts 1970-01-01.
    private const val FIT_EPOCH_OFFSET_SEC = 631_065_600L

    // FIT semicircles: 1 degree = 2^31 / 180.
    // The FIT format expresses latitude and longitude as signed 32-bit
    // integer "semicircles", where the full circle (360°) maps to 2^32
    // values, and 180° therefore maps to Int.MAX_VALUE (2^31 - 1 ≈ 2^31).
    private const val SC_PER_DEG = (Int.MAX_VALUE / 180.0)

    // Global message numbers (FIT Profile)
    private const val MESG_FILE_ID  = 0
    private const val MESG_RECORD   = 20
    private const val MESG_LAP      = 19
    private const val MESG_SESSION  = 18

    // Local message numbers (assigned by this encoder, arbitrary 0-based)
    private const val LOCAL_FILE_ID = 0
    private const val LOCAL_RECORD  = 1
    private const val LOCAL_LAP     = 2
    private const val LOCAL_SESSION = 3

    // FIT base types
    private const val T_ENUM   = 0x00.toByte()
    private const val T_UINT8  = 0x02.toByte()
    private const val T_UINT16 = 0x84.toByte()
    private const val T_UINT32 = 0x86.toByte()
    private const val T_SINT32 = 0x85.toByte()

    // ------------------------------ Public API ------------------------------

    /**
     * A single GPS track sample to include in the FIT file.
     *
     * @param offsetSec seconds elapsed from [Session.startTimeUnixSec]
     * @param lat       latitude in degrees WGS-84
     * @param lon       longitude in degrees WGS-84
     * @param speedMs   speed in m/s (0 if unavailable)
     */
    data class TrackPoint(
        val offsetSec: Int,
        val lat: Double,
        val lon: Double,
        val speedMs: Float,
    )

    /**
     * A completed checkpoint (lap) to embed in the FIT file.
     *
     * @param name      checkpoint name shown in the lap summary
     * @param offsetSec elapsed seconds from [Session.startTimeUnixSec] when
     *                  the checkpoint was reached
     * @param score     score awarded at this checkpoint
     */
    data class Lap(
        val name: String,
        val offsetSec: Int,
        val score: Int,
    )

    /**
     * Full race session data used to produce a FIT file.
     *
     * @param startTimeUnixSec  Unix timestamp (seconds) when the race started
     * @param elapsedSec        total race duration in seconds
     * @param totalScore        accumulated score across all checkpoints
     * @param trackPoints       GPS track recorded during the race
     * @param laps              checkpoints reached during the race
     */
    data class Session(
        val startTimeUnixSec: Long,
        val elapsedSec: Int,
        val totalScore: Int,
        val trackPoints: List<TrackPoint>,
        val laps: List<Lap>,
    )

    /**
     * Serialises [session] as a FIT binary file and returns the raw bytes.
     *
     * The returned [ByteArray] can be written directly to a `.fit` file on
     * disk and will be accepted by Garmin Connect and other FIT tools.
     */
    fun export(session: Session): ByteArray {
        val data = ByteArrayOutputStream()

        writeFileIdDef(data)
        writeFileId(data, session.startTimeUnixSec)

        writeRecordDef(data)
        for (pt in session.trackPoints) {
            writeRecord(
                data,
                session.startTimeUnixSec + pt.offsetSec,
                pt.lat, pt.lon, pt.speedMs,
            )
        }

        writeLapDef(data)
        for (lap in session.laps) {
            writeLap(data, session.startTimeUnixSec, lap.offsetSec)
        }

        writeSessionDef(data)
        writeSession(data, session.startTimeUnixSec, session.elapsedSec)

        val dataBytes = data.toByteArray()
        val out = ByteArrayOutputStream()
        writeFileHeader(out, dataBytes.size)
        out.write(dataBytes)
        writeFileCrc(out, dataBytes)
        return out.toByteArray()
    }

    // ----------------------------- File header ------------------------------

    private fun writeFileHeader(out: ByteArrayOutputStream, dataSize: Int) {
        val buf = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(14.toByte())    // header size
        buf.put(0x10.toByte())  // protocol version 1.0
        buf.putShort(2048)      // profile version 20.48
        buf.putInt(dataSize)    // data content size (excludes header + file CRC)
        buf.put(0x2E.toByte())  // '.'
        buf.put(0x46.toByte())  // 'F'
        buf.put(0x49.toByte())  // 'I'
        buf.put(0x54.toByte())  // 'T'
        val headerBytes = buf.array()
        val crc = fitCrc16(headerBytes, 0, 12)
        headerBytes[12] = (crc and 0xFF).toByte()
        headerBytes[13] = ((crc shr 8) and 0xFF).toByte()
        out.write(headerBytes)
    }

    private fun writeFileCrc(out: ByteArrayOutputStream, data: ByteArray) {
        val crc = fitCrc16(data, 0, data.size)
        out.write(crc and 0xFF)
        out.write((crc shr 8) and 0xFF)
    }

    // ------------------------ Definition messages ---------------------------

    private fun writeFileIdDef(out: ByteArrayOutputStream) {
        // field 0: type (1 byte ENUM), field 4: time_created (4 bytes UINT32)
        writeDef(out, LOCAL_FILE_ID, MESG_FILE_ID,
            listOf(Triple(0, 1, T_ENUM), Triple(4, 4, T_UINT32)))
    }

    private fun writeRecordDef(out: ByteArrayOutputStream) {
        // 253: timestamp (UINT32), 0: lat (SINT32 sc), 1: lon (SINT32 sc),
        // 6: speed (UINT16 mm/s)
        writeDef(out, LOCAL_RECORD, MESG_RECORD, listOf(
            Triple(253, 4, T_UINT32),
            Triple(0,   4, T_SINT32),
            Triple(1,   4, T_SINT32),
            Triple(6,   2, T_UINT16),
        ))
    }

    private fun writeLapDef(out: ByteArrayOutputStream) {
        // 253: timestamp (UINT32), 2: start_time (UINT32),
        // 7: total_elapsed_time (UINT32, units = ms × 1000)
        writeDef(out, LOCAL_LAP, MESG_LAP, listOf(
            Triple(253, 4, T_UINT32),
            Triple(2,   4, T_UINT32),
            Triple(7,   4, T_UINT32),
        ))
    }

    private fun writeSessionDef(out: ByteArrayOutputStream) {
        // 253: timestamp, 2: start_time, 7: total_elapsed_time (ms×1000),
        // 5: sport (ENUM), 6: sub_sport (ENUM)
        writeDef(out, LOCAL_SESSION, MESG_SESSION, listOf(
            Triple(253, 4, T_UINT32),
            Triple(2,   4, T_UINT32),
            Triple(7,   4, T_UINT32),
            Triple(5,   1, T_ENUM),
            Triple(6,   1, T_ENUM),
        ))
    }

    private fun writeDef(
        out: ByteArrayOutputStream,
        local: Int,
        global: Int,
        fields: List<Triple<Int, Int, Byte>>,
    ) {
        out.write(0x40 or local)  // definition record header
        out.write(0)              // reserved
        out.write(0)              // architecture: little-endian
        out.write(global and 0xFF)
        out.write((global shr 8) and 0xFF)
        out.write(fields.size)
        for ((num, size, type) in fields) {
            out.write(num)
            out.write(size)
            out.write(type.toInt())
        }
    }

    // -------------------------- Data messages -------------------------------

    private fun writeFileId(out: ByteArrayOutputStream, startUnixSec: Long) {
        out.write(LOCAL_FILE_ID)
        out.write(4)                        // type = activity (4)
        writeU32(out, toFitTime(startUnixSec))
    }

    private fun writeRecord(
        out: ByteArrayOutputStream,
        unixSec: Long,
        lat: Double,
        lon: Double,
        speedMs: Float,
    ) {
        out.write(LOCAL_RECORD)
        writeU32(out, toFitTime(unixSec))
        writeS32(out, degToSc(lat))
        writeS32(out, degToSc(lon))
        writeU16(out, (speedMs * 1000).toInt().coerceAtLeast(0))
    }

    private fun writeLap(
        out: ByteArrayOutputStream,
        startUnixSec: Long,
        elapsedSec: Int,
    ) {
        val lapEndTime = startUnixSec + elapsedSec
        out.write(LOCAL_LAP)
        writeU32(out, toFitTime(lapEndTime))            // timestamp = lap end
        writeU32(out, toFitTime(startUnixSec))          // start_time = race start
        writeU32(out, elapsedSec.toLong() * 1000L)      // total_elapsed_time in ms
    }

    private fun writeSession(
        out: ByteArrayOutputStream,
        startUnixSec: Long,
        elapsedSec: Int,
    ) {
        val endTime = startUnixSec + elapsedSec
        out.write(LOCAL_SESSION)
        writeU32(out, toFitTime(endTime))               // timestamp = session end
        writeU32(out, toFitTime(startUnixSec))          // start_time
        writeU32(out, elapsedSec.toLong() * 1000L)      // total_elapsed_time in ms
        out.write(0)   // sport: Generic (0)
        out.write(58)  // sub_sport: Enduro (58)
    }

    // ----------------------------- Primitives -------------------------------

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Long) {
        out.write((value and 0xFFL).toInt())
        out.write(((value shr 8) and 0xFFL).toInt())
        out.write(((value shr 16) and 0xFFL).toInt())
        out.write(((value shr 24) and 0xFFL).toInt())
    }

    private fun writeS32(out: ByteArrayOutputStream, value: Int) =
        writeU32(out, value.toLong() and 0xFFFFFFFFL)

    private fun toFitTime(unixSec: Long): Long = unixSec - FIT_EPOCH_OFFSET_SEC

    /** Convert a decimal degree to a FIT semicircle integer. */
    private fun degToSc(deg: Double): Int = (deg * SC_PER_DEG).toLong().toInt()

    // -------------------- FIT CRC-16 (CRC-CCITT variant) -------------------

    private fun fitCrc16(data: ByteArray, start: Int, end: Int): Int {
        val table = intArrayOf(
            0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
            0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
        )
        var crc = 0
        for (i in start until end) {
            val b = data[i].toInt() and 0xFF
            var tmp = table[crc and 0x0F]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor table[b and 0x0F]
            tmp = table[crc and 0x0F]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor table[(b shr 4) and 0x0F]
        }
        return crc
    }
}
