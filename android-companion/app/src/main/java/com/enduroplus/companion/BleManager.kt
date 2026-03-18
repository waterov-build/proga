package com.enduroplus.companion

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.LinkedList
import java.util.UUID

private const val TAG = "BleManager"

/**
 * Manages BLE scanning, connection, and GATT characteristic I/O with one or
 * more Garmin watches running ENDURO PLUS.
 *
 * Usage:
 *   val mgr = BleManager(context)
 *   mgr.startScan()               // starts discovery; found devices → devicesFlow
 *   mgr.connect(device)           // connects and discovers services
 *   mgr.readScore(device)         // reads score characteristic → resultsFlow
 *   mgr.sendCheckpoints(device, list)  // writes checkpoint list to watch
 *   mgr.stopScan()
 *
 * GATT operations are serialised per-device via an operation queue: each new
 * read/write is enqueued and the next operation is dispatched only after the
 * current one completes (or fails).  This prevents "too many requests" errors
 * that occur when GATT calls are made before a previous one has finished.
 */
@SuppressLint("MissingPermission")  // Permissions checked by the calling Activity
class BleManager(private val context: Context) {

    companion object {
        /** How long to scan before auto-stopping (milliseconds). */
        const val SCAN_TIMEOUT_MS = 30_000L

        /**
         * Requested GATT MTU (bytes).  Larger MTU means more data per
         * characteristic read, allowing more track points per request.
         * 512 is the maximum permitted by the Bluetooth spec.
         */
        const val REQUESTED_MTU = 512
    }

    // Discovered (not yet connected) Garmin watch devices
    private val _devicesFlow = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devicesFlow: StateFlow<List<BluetoothDevice>> = _devicesFlow

    // Latest decoded results per device address
    private val _resultsFlow = MutableStateFlow<Map<String, ParticipantResult>>(emptyMap())
    val resultsFlow: StateFlow<Map<String, ParticipantResult>> = _resultsFlow

    // Latest GPS track per device address — list of (lat, lon) polyline points
    private val _tracksFlow = MutableStateFlow<Map<String, List<Pair<Double, Double>>>>(emptyMap())
    val tracksFlow: StateFlow<Map<String, List<Pair<Double, Double>>>> = _tracksFlow

    // Active GATT connections keyed by device address
    private val _gatts = mutableMapOf<String, BluetoothGatt>()

    /**
     * Per-device GATT operation queue.
     *
     * GATT is single-threaded per connection: only one operation (read, write,
     * descriptor write, MTU request) may be in-flight at a time.  Enqueueing
     * them here lets callers issue back-to-back requests without risk of
     * silently dropping operations or triggering a GATT_ERROR 133.
     */
    private val _queues = mutableMapOf<String, LinkedList<() -> Unit>>()
    private val _operationInProgress = mutableMapOf<String, Boolean>()

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())

    // ---------- Scanning ----------

    /** Start scanning for ENDURO PLUS watches (filtered by service UUID). */
    fun startScan() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid.fromString(BleProfile.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
        // Auto-stop after timeout to preserve battery
        handler.postDelayed(::stopScan, SCAN_TIMEOUT_MS)
        Log.d(TAG, "Scan started")
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        Log.d(TAG, "Scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val current = _devicesFlow.value
            if (current.none { it.address == device.address }) {
                _devicesFlow.value = current + device
                Log.d(TAG, "Found device: ${device.address} ${device.name}")
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
        }
    }

    // ---------- Connection ----------

    /** Connect to the given watch and discover services. */
    fun connect(device: BluetoothDevice) {
        val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        _gatts[device.address] = gatt
        _queues[device.address] = LinkedList()
        _operationInProgress[device.address] = false
    }

    fun disconnect(device: BluetoothDevice) {
        _gatts[device.address]?.disconnect()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to ${gatt.device.address}; requesting MTU $REQUESTED_MTU…")
                    // Request a larger MTU first; service discovery follows in onMtuChanged.
                    enqueue(gatt.device.address) { gatt.requestMtu(REQUESTED_MTU) }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from ${gatt.device.address}")
                    _gatts.remove(gatt.device.address)
                    _queues.remove(gatt.device.address)
                    _operationInProgress.remove(gatt.device.address)
                    gatt.close()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU for ${gatt.device.address} → $mtu bytes (status=$status)")
            operationCompleted(gatt.device.address)
            // Discover services regardless of whether the requested MTU was granted
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered for ${gatt.device.address}")
                enableNotifications(gatt, BleProfile.CHAR_SCORE)
                enableNotifications(gatt, BleProfile.CHAR_STATUS)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            operationCompleted(gatt.device.address)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicData(gatt.device, characteristic.uuid, value)
            }
        }

        @Suppress("DEPRECATION") // Legacy callback needed for API < 33
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleCharacteristicData(gatt.device, characteristic.uuid,
                characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicData(gatt.device, characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            operationCompleted(gatt.device.address)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Write failed for ${characteristic.uuid}: status=$status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            operationCompleted(gatt.device.address)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Descriptor write failed: status=$status")
            }
        }
    }

    // ---------- Read / write ----------

    /** Read the score characteristic from a connected watch. */
    fun readScore(device: BluetoothDevice) {
        val gatt = _gatts[device.address] ?: return
        val char = gatt.getService(UUID.fromString(BleProfile.SERVICE_UUID))
            ?.getCharacteristic(UUID.fromString(BleProfile.CHAR_SCORE)) ?: return
        enqueue(device.address) { gatt.readCharacteristic(char) }
    }

    /** Read the GPS track characteristic from a connected watch. */
    fun readTrack(device: BluetoothDevice) {
        val gatt = _gatts[device.address] ?: return
        val char = gatt.getService(UUID.fromString(BleProfile.SERVICE_UUID))
            ?.getCharacteristic(UUID.fromString(BleProfile.CHAR_TRACK)) ?: return
        enqueue(device.address) { gatt.readCharacteristic(char) }
    }

    /**
     * Write a checkpoint list to the watch.
     * [checkpoints] — list of Triple(name, lat, lon)
     */
    fun sendCheckpoints(device: BluetoothDevice,
                        checkpoints: List<Triple<String, Double, Double>>) {
        val gatt = _gatts[device.address] ?: return
        val char = gatt.getService(UUID.fromString(BleProfile.SERVICE_UUID))
            ?.getCharacteristic(UUID.fromString(BleProfile.CHAR_CP_LIST)) ?: return
        val payload = encodeCheckpointList(checkpoints).toByteArray(Charsets.UTF_8)
        enqueue(device.address) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(char, payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.value = payload
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        }
    }

    // ---------- GATT operation queue ----------

    /**
     * Enqueue a GATT operation for the given device.
     *
     * If no operation is currently in progress for this device, the operation
     * is dispatched immediately on the main thread.  Otherwise it is placed in
     * the queue and will be dispatched once the current operation completes.
     */
    private fun enqueue(address: String, op: () -> Unit) {
        val queue = _queues[address] ?: return
        queue.add(op)
        if (_operationInProgress[address] != true) {
            dispatchNext(address)
        }
    }

    /** Dispatch the next queued operation for [address], if any. */
    private fun dispatchNext(address: String) {
        val queue = _queues[address] ?: return
        val next = queue.poll()
        if (next != null) {
            _operationInProgress[address] = true
            handler.post(next)
        } else {
            _operationInProgress[address] = false
        }
    }

    /**
     * Called from GATT callbacks when an operation finishes (success or error).
     * Dispatches the next pending operation for the same device.
     */
    private fun operationCompleted(address: String) {
        _operationInProgress[address] = false
        dispatchNext(address)
    }

    // ---------- Helpers ----------

    private fun enableNotifications(gatt: BluetoothGatt, charUuid: String) {
        val char = gatt.getService(UUID.fromString(BleProfile.SERVICE_UUID))
            ?.getCharacteristic(UUID.fromString(charUuid)) ?: return
        gatt.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(UUID.fromString(BleProfile.CCCD_UUID)) ?: return
        enqueue(gatt.device.address) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(cccd,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }
    }

    private fun handleCharacteristicData(device: BluetoothDevice,
                                         uuid: UUID, value: ByteArray) {
        val text = value.toString(Charsets.UTF_8)
        when (uuid) {
            UUID.fromString(BleProfile.CHAR_SCORE) -> {
                val result = parseResults(device.name ?: device.address, text)
                val updated = _resultsFlow.value.toMutableMap()
                updated[device.address] = result
                _resultsFlow.value = updated
                Log.d(TAG, "Score from ${device.address}: ${result.totalScore} pts")
            }
            UUID.fromString(BleProfile.CHAR_STATUS) -> {
                Log.d(TAG, "Status from ${device.address}: $text")
            }
            UUID.fromString(BleProfile.CHAR_TRACK) -> {
                val track = parseTrack(text)
                val updated = _tracksFlow.value.toMutableMap()
                updated[device.address] = track
                _tracksFlow.value = updated
                Log.d(TAG, "Track from ${device.address}: ${track.size} points")
            }
        }
    }
}
