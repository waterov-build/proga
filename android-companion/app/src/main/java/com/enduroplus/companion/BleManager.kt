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
 */
@SuppressLint("MissingPermission")  // Permissions checked by the calling Activity
class BleManager(private val context: Context) {

    companion object {
        /** How long to scan before auto-stopping (milliseconds). */
        const val SCAN_TIMEOUT_MS = 30_000L
    }

    // Discovered (not yet connected) Garmin watch devices
    private val _devicesFlow = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devicesFlow: StateFlow<List<BluetoothDevice>> = _devicesFlow

    // Latest decoded results per device address
    private val _resultsFlow = MutableStateFlow<Map<String, ParticipantResult>>(emptyMap())
    val resultsFlow: StateFlow<Map<String, ParticipantResult>> = _resultsFlow

    // Active GATT connections keyed by device address
    private val _gatts = mutableMapOf<String, BluetoothGatt>()

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
    }

    fun disconnect(device: BluetoothDevice) {
        _gatts[device.address]?.disconnect()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to ${gatt.device.address}; discovering services…")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from ${gatt.device.address}")
                    _gatts.remove(gatt.device.address)
                    gatt.close()
                }
            }
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
    }

    // ---------- Read / write ----------

    /** Read the score characteristic from a connected watch. */
    fun readScore(device: BluetoothDevice) {
        val gatt = _gatts[device.address] ?: return
        val char = gatt.getService(UUID.fromString(BleProfile.SERVICE_UUID))
            ?.getCharacteristic(UUID.fromString(BleProfile.CHAR_SCORE)) ?: return
        gatt.readCharacteristic(char)
    }

    /** Read the GPS track characteristic from a connected watch. */
    fun readTrack(device: BluetoothDevice) {
        val gatt = _gatts[device.address] ?: return
        val char = gatt.getService(UUID.fromString(BleProfile.SERVICE_UUID))
            ?.getCharacteristic(UUID.fromString(BleProfile.CHAR_TRACK)) ?: return
        gatt.readCharacteristic(char)
    }

    /**
     * Write a checkpoint list to the watch.
     * [checkpoints] — list of [CourseCheckpoint] with name, coordinates and par time.
     */
    fun sendCheckpoints(device: BluetoothDevice,
                        checkpoints: List<CourseCheckpoint>) {
        val gatt = _gatts[device.address] ?: return
        val char = gatt.getService(UUID.fromString(BleProfile.SERVICE_UUID))
            ?.getCharacteristic(UUID.fromString(BleProfile.CHAR_CP_LIST)) ?: return
        val payload = encodeCheckpointList(checkpoints).toByteArray(Charsets.UTF_8)
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

    /**
     * Broadcast a checkpoint list to every currently connected watch.
     */
    fun sendCheckpointsAll(checkpoints: List<CourseCheckpoint>) {
        _gatts.keys.toList().forEach { address ->
            val device = _gatts[address]?.device ?: return@forEach
            sendCheckpoints(device, checkpoints)
        }
        Log.d(TAG, "Sent course (${checkpoints.size} checkpoints) to ${_gatts.size} watch(es)")
    }

    // ---------- Helpers ----------

    private fun enableNotifications(gatt: BluetoothGatt, charUuid: String) {
        val char = gatt.getService(UUID.fromString(BleProfile.SERVICE_UUID))
            ?.getCharacteristic(UUID.fromString(charUuid)) ?: return
        gatt.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(UUID.fromString(BleProfile.CCCD_UUID)) ?: return
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
                Log.d(TAG, "Track from ${device.address}: ${text.length} chars")
            }
        }
    }
}
