package com.insightfulmechanisms.capture2.btserver

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.content.Context.BATTERY_SERVICE
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import com.insightfulmechanisms.capture2.MainActivity
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothPeripheralManager
import com.welie.blessed.GattStatus
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.random.Random

internal class CaptureServices(peripheralManager: BluetoothPeripheralManager) :
    BaseService(
        peripheralManager,
        BluetoothGattService(CAPTURE_SERVICE_UUID, SERVICE_TYPE_PRIMARY),
        "Capture Services"
    ) {

    private val battery_measurement = BluetoothGattCharacteristic( BATTERY_LEVEL_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0 )
    private val upload_measurement = BluetoothGattCharacteristic( UPLOAD_COUNT_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0 )
    private val post_measurement = BluetoothGattCharacteristic( POST_COUNT_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0 )
    private val recording_time = BluetoothGattCharacteristic( RECORDING_TIME_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0 )
    private val device_state = BluetoothGattCharacteristic( DEVICE_STATE_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0 )

    private val start_inspection = BluetoothGattCharacteristic( START_INSPECTION_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE )
    private val stop_inspection = BluetoothGattCharacteristic( STOP_INSPECTION_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE )
    private val set_waypoint = BluetoothGattCharacteristic( SET_WAYPOINT_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE )
    private val set_checkpoint = BluetoothGattCharacteristic( SET_CHECKPOINT_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE )

    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnableBattery = Runnable { notifyBatteryLevel() }
    private val notifyRunnableUpload = Runnable { notifyUploadCount() }
    private val notifyRunnablePost = Runnable { notifyPostCount() }
    private val notifyRunnableRecordingTime = Runnable { notifyRecordingTime() }
    private val notifyRunnableDeviceState = Runnable { notifyDeviceState() }

    private var elapsedRecordingTime : Long = 0

    init {

        // Notification Services
        service.addCharacteristic(battery_measurement)
        battery_measurement.addDescriptor(cccDescriptor)

        service.addCharacteristic(upload_measurement)
        upload_measurement.addDescriptor(cccDescriptor)

        service.addCharacteristic(post_measurement)
        post_measurement.addDescriptor(cccDescriptor)

        service.addCharacteristic(recording_time)
        recording_time.addDescriptor(cccDescriptor)

        service.addCharacteristic(device_state)
        device_state.addDescriptor(cccDescriptor)

        // Write Characteristics
        service.addCharacteristic(start_inspection)
        start_inspection.addDescriptor(cccDescriptor)

        service.addCharacteristic(stop_inspection)
        stop_inspection.addDescriptor(cccDescriptor)

        service.addCharacteristic(set_waypoint)
        set_waypoint.addDescriptor(cccDescriptor)

        service.addCharacteristic(set_checkpoint)
        set_checkpoint.addDescriptor(cccDescriptor)
    }

    override fun onCentralDisconnected(central: BluetoothCentral) {
        Timber.i("Central Disconnected")
        if (noCentralsConnected()) {
            stopNotifying()
        }
    }

    override fun onCharacteristicWrite(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic, value: ByteArray): GattStatus {
        var returnStatus = GattStatus.ERROR
        if (characteristic.uuid == START_INSPECTION_CHARACTERISTIC_UUID) {
            Timber.i("// -----------------------")
            Timber.i("//     Start Inspection   ")
            Timber.i("// -----------------------")
            returnStatus = GattStatus.SUCCESS

        } else if (characteristic.uuid == STOP_INSPECTION_CHARACTERISTIC_UUID) {
            Timber.i("// -----------------------")
            Timber.i("//  !! Stop Inspection !! ")
            Timber.i("// -----------------------")
            returnStatus = GattStatus.SUCCESS

        } else if (characteristic.uuid == SET_WAYPOINT_CHARACTERISTIC_UUID) {
            Timber.i("// -----------------------")
            Timber.i("//       Set WAYPOINT     ")
            Timber.i("// -----------------------")
            returnStatus = GattStatus.SUCCESS

        } else if (characteristic.uuid == SET_CHECKPOINT_CHARACTERISTIC_UUID) {
            val buffer = ByteBuffer.wrap(value, 0, 8) // 8 bytes for 2 floats
            val float1 = buffer.float
            val float2 = buffer.float
            Timber.i("// -----------------------")
            Timber.i("//      Set CHECKPOINT    ")
            Timber.i("//   X: $float1")
            Timber.i("//   Y: $float2")
            Timber.i("// -----------------------")
            returnStatus = GattStatus.SUCCESS
        }

        return returnStatus
//        return super.onCharacteristicWrite(central, characteristic, value)
    }

    override fun onNotifyingEnabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
            Timber.i("Notify Battery Level")
            notifyBatteryLevel()
        } else if (characteristic.uuid == UPLOAD_COUNT_CHARACTERISTIC_UUID) {
            Timber.i("Notify Upload Count")
            notifyUploadCount()
        } else if (characteristic.uuid == POST_COUNT_CHARACTERISTIC_UUID) {
            Timber.i("Notify Post Count")
            notifyPostCount()
        } else if (characteristic.uuid == RECORDING_TIME_CHARACTERISTIC_UUID) {
            Timber.i("Notify Recording Time")
            notifyRecordingTime()
        } else if (characteristic.uuid == DEVICE_STATE_CHARACTERISTIC_UUID) {
            Timber.i("Notify Device State")
            notifyDeviceState()
        } else {
            Timber.i("UNKNOWN UUID: $characteristic.uuid")
        }
    }

    override fun onNotifyingDisabled(central: BluetoothCentral, characteristic: BluetoothGattCharacteristic) {
        stopNotifying()
    }

    // ----------------------------------------------------
    //                  NOTIFY METHODS
    // ----------------------------------------------------
    private fun notifyBatteryLevel() {
        val bm = MainActivity.applicationContext().getSystemService(BATTERY_SERVICE) as BatteryManager
        val batLevel:Int = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        // simulate changes
        val randomNumber = Random.nextInt(1, 6)
        val result = batLevel - randomNumber
        val value = byteArrayOf(result.toByte())
        notifyCharacteristicChanged(value, battery_measurement)
        handler.postDelayed(notifyRunnableBattery, 5000)        // Every 5 seconds
    }

    private fun notifyUploadCount() {
        val uploadCount = Random.nextInt(1, 5)
        val value = byteArrayOf(uploadCount.toByte())
        notifyCharacteristicChanged(value, upload_measurement)
        handler.postDelayed(notifyRunnableUpload, 1000)
    }

    private fun notifyPostCount() {
        val postCount = Random.nextInt(6, 10)
        val value = byteArrayOf(postCount.toByte())
        notifyCharacteristicChanged(value, post_measurement)
        handler.postDelayed(notifyRunnablePost, 1000)
    }

    private fun notifyRecordingTime() {
        elapsedRecordingTime += 1          // This is in seconds
//        Timber.i("Current Elapsed Time: $elapsedRecordingTime")
        val value = longToByteArray(elapsedRecordingTime)
        notifyCharacteristicChanged(value, recording_time)
        handler.postDelayed(notifyRunnableRecordingTime, 1000)
    }

    private fun notifyDeviceState() {
        val stateList = listOf("Uploading","Needs Verification","Processing","Needs Review","Completed")
        val randomIndex = Random.nextInt(stateList.size);
        val state = stateList[randomIndex]
        notifyCharacteristicChanged(state.toByteArray(), device_state)
        handler.postDelayed(notifyRunnableDeviceState, 5000)
    }

    private fun stopNotifying() {
        handler.removeCallbacks(notifyRunnableBattery)
        handler.removeCallbacks(notifyRunnableUpload)
        handler.removeCallbacks(notifyRunnablePost)
        handler.removeCallbacks(notifyRunnableRecordingTime)
        handler.removeCallbacks(notifyRunnableDeviceState)
    }

    private fun longToByteArray(value : Long) : ByteArray{
        val b = byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte(),
            (value shr 32).toByte(),
            (value shr 40).toByte(),
            (value shr 48).toByte(),
            (value shr 56).toByte()
        )
        return b
    }

    companion object {
        val CAPTURE_SERVICE_UUID: UUID = UUID.fromString("00002222-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001111-0000-1000-8000-00805f9b34fb")
        private val UPLOAD_COUNT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001122-0000-1000-8000-00805f9b34fb")
        private val POST_COUNT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001133-0000-1000-8000-00805f9b34fb")
        private val RECORDING_TIME_CHARACTERISTIC_UUID = UUID.fromString("00001144-0000-1000-8000-00805f9b34fb")
        private val DEVICE_STATE_CHARACTERISTIC_UUID = UUID.fromString("00001155-0000-1000-8000-00805f9b34fb")

        private val START_INSPECTION_CHARACTERISTIC_UUID = UUID.fromString("00001166-0000-1000-8000-00805f9b34fb")
        private val STOP_INSPECTION_CHARACTERISTIC_UUID = UUID.fromString("00001177-0000-1000-8000-00805f9b34fb")
        private val SET_WAYPOINT_CHARACTERISTIC_UUID = UUID.fromString("00001188-0000-1000-8000-00805f9b34fb")
        private val SET_CHECKPOINT_CHARACTERISTIC_UUID = UUID.fromString("00001199-0000-1000-8000-00805f9b34fb")
    }
}