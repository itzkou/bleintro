package com.example.bleintro

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
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
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    /*private val isGpsEnabled: Boolean by lazy {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.isLocationEnabled

    }*/


    private val mDevices = LinkedHashMap<String, BluetoothDevice>()
    private val mDeviceAdapter = DeviceAdapter()
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            mDevices[device.address] = device

            mDeviceAdapter.updateDevices(mDevices.values.toList())

        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
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

    }

    private fun checkLocationPermission() {
        //todo add android sdk < M permissions and handle multiple permissions ( add location )
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
        //mDeviceAdapter.updateDevices(listOf())
        bleScanner.startScan(null, scanSettings, scanCallback)
        Toast.makeText(this, "Scanning ...", Toast.LENGTH_SHORT).show()
    }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
    }

    private fun goToSettings() {
        val myAppSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")
        )
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(myAppSettings)
    }
}