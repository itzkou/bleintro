package com.example.bleintro

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bleintro.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.util.*
import kotlin.collections.LinkedHashMap

private const val GATT_MAX_MTU_SIZE = 517

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")

    private val CCC_Heartrate_Descriptor = "00002902-0000-1000-8000-00805f9b34fb"


    /** ActivityContracts **/
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startBleScan()
            } else {
                Toast.makeText(this, "Location permission denied...", Toast.LENGTH_SHORT).show()
            }
        }
    private val enableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            if (activityResult.resultCode != Activity.RESULT_OK)
                promptEnableBluetooth()
        }

    /** Bluetooth components **/
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    /** Devices **/
    private val mDevices = LinkedHashMap<String, BluetoothDevice>()
    private val mDeviceAdapter = DeviceAdapter()
    private var mBluetoothGatt: BluetoothGatt? = null

    /** Scan  **/
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            mDevices[device.address] = device
            val namedDevices = mDevices.values.toList().filter {
                it.name != null
            }
            mDeviceAdapter.updateDevices(namedDevices)


        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
        }


    }

    /** Gatt Callbacks **/
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            gatt?.device?.let { btDevice ->

                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {

                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                mBluetoothGatt = gatt

                                /***  we highly recommend calling discoverServices() from the main/UI thread to prevent a rare threading
                                 * issue from causing a deadlock situation where the app can be left waiting for the onServicesDiscovered()
                                 * callback that somehow got dropped. ***/
                                runOnUiThread {
                                    mBluetoothGatt?.discoverServices()
                                    Log.w(
                                        "BluetoothProfileConnected",
                                        "Successfully connected to ${btDevice.address}"
                                    )
                                }
                                Log.w(
                                    "BluetoothGattCallback",
                                    "Successfully connected to ${btDevice.address}"
                                )
                                mBluetoothGatt?.requestMtu(GATT_MAX_MTU_SIZE)


                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Log.w(
                                    "BluetoothGattCallback",
                                    "Successfully disconnected from ${btDevice.address}"
                                )
                                gatt.close()
                            }
                            else -> Log.i(
                                "BluetoothProfile",
                                " This else concerns the rest of states"
                            )
                        }
                        //stopBleScan()
                    }
                    BluetoothGatt.GATT_FAILURE -> {
                        gatt.close()

                        Log.e(
                            "onConnectionStateChange",
                            "Error $status encountered for ${btDevice.address}! Disconnecting..."
                        )
                    }
                    else -> {
                        Log.i(
                            "onConnectionStateChange",
                            " Error 133 shows up here"
                        )
                    }
                }

            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            mBluetoothGatt?.let {
                Log.w(
                    "BluetoothGattCallback",
                    "Discovered ${it.services.size} services for ${it.device.address}"
                )
                it.printGattTable() // See implementation just above this section
                //readBatteryLevel()
                //writeHeartControlPoint()
                //enableHeartRateMeasurementNotifications()
                //disableHeartRateMeasurementNotifications()

            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.w(
                "onMtuChanged",
                "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}"
            )
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            characteristic?.let {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        val readBytes: ByteArray = it.value
                        val batteryLevel = readBytes.first().toInt()
                        Log.i(
                            "BluetoothGattCallback",
                            "Read characteristic HEX ${it.uuid} :${it.value.toHexString()}"
                        )
                        Log.i(
                            "BluetoothGattCallback",
                            "Read characteristic DEC ${it.uuid} :$batteryLevel"
                        )
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for ${it.uuid}!")
                    }
                    else -> {
                        Log.e(
                            "BluetoothGattCallback",
                            "Characteristic read failed for ${it.uuid}, error: $status"
                        )
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            characteristic?.let {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(
                            "BluetoothGattCallback",
                            "Wrote to characteristic ${it.uuid} | value: ${it.value.toHexString()}"
                        )
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for ${it.uuid}!")
                    }
                    else -> {
                        Log.e(
                            "BluetoothGattCallback",
                            "Characteristic write failed for ${it.uuid}, error: $status"
                        )
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setupUi()


    }


    override fun onResume() {
        super.onResume()
        promptEnableBluetooth()


    }


    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetooth.launch(enableBtIntent)
        }

    }


    private fun setupUi() {
        /** checking permissions **/
        binding.btnScan.setOnClickListener {
            checkLocationPermission()
        }
        binding.stop.setOnClickListener {
            stopBleScan()
        }

        binding.rvDevices.apply {
            layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            adapter = mDeviceAdapter

        }
        mDeviceAdapter.selectDevice { macAddress ->
            mDevices[macAddress]!!.connectGatt(this, false, gattCallback)
            stopBleScan()
        }

    }

    private fun checkLocationPermission() {
        // android sdk < M permissions doesn't support rationale
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the permission.
                startBleScan()

            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // explain why this permission is needed using UI
                Snackbar.make(
                    this,
                    binding.root,
                    "Allow location and turn it on in order to use our services",
                    Snackbar.LENGTH_SHORT
                ).setAction("Settings") {
                    goToSettings()
                }.show()

            }
            else -> {
                // directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                permissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }

    }


    private fun startBleScan() {
        mDevices.clear()
        bleScanner.startScan(null, scanSettings, scanCallback)
        Toast.makeText(this, "Make sure your location services are on...", Toast.LENGTH_SHORT)
            .show()
    }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        Log.w("stopBleScan", "stopBleScan")
    }

    private fun goToSettings() {
        val myAppSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")
        )
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(myAppSettings)
    }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i(
                "printGattTable",
                "No service and characteristic available, call discoverServices() first?"
            )
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i(
                "printGattTable",
                "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }

    private fun readBatteryLevel() {
        val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val batteryLevelCharUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val batteryLevelChar: BluetoothGattCharacteristic? = mBluetoothGatt!!
            .getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)
        if (batteryLevelChar?.isReadable() == true) {    //batteryLevelChar is an optional, we use == true to both check for the fact that it is nonnull and that it is indeed a readable characteristic.
            mBluetoothGatt!!.readCharacteristic(batteryLevelChar)
        }
    }

    private fun writeHeartControlPoint() {
        val heartServiceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val controlPointCharUuid = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")
        val controlPointChar = mBluetoothGatt!!
            .getService(heartServiceUuid)?.getCharacteristic(controlPointCharUuid)
        controlPointChar?.let {
            val byteArray = byteArrayOf(0x1)
            writeCharacteristic(it, byteArray)
        }

    }

    private fun enableHeartRateMeasurementNotifications() {
        val heartServiceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val heartRateMeasurementUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val heartRateMeasurementChar =
            mBluetoothGatt!!.getService(heartServiceUuid)
                ?.getCharacteristic(heartRateMeasurementUuid)

        heartRateMeasurementChar?.let {
            enableNotifications(it)

        }

    }
    private fun disableHeartRateMeasurementNotifications() {
        val heartServiceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val heartRateMeasurementUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val heartRateMeasurementChar =
            mBluetoothGatt!!.getService(heartServiceUuid)
                ?.getCharacteristic(heartRateMeasurementUuid)

        heartRateMeasurementChar?.let {
           disableNotifications(it)

        }

    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }
        Log.i("writotypo", writeType.toString())
        mBluetoothGatt?.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not connected to a BLE device!")
    }

    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCC_Heartrate_Descriptor)
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e(
                    "ConnectionManager",
                    "${characteristic.uuid} doesn't support notifications/indications"
                )
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (mBluetoothGatt?.setCharacteristicNotification(characteristic, true) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e(
            "ConnectionManager",
            "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    fun disableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e(
                "ConnectionManager",
                "${characteristic.uuid} doesn't support indications/notifications"
            )
            return
        }

        val cccdUuid = UUID.fromString(CCC_Heartrate_Descriptor)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (mBluetoothGatt?.setCharacteristicNotification(characteristic, false) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e(
            "ConnectionManager",
            "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        mBluetoothGatt?.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!")
    }

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }
}