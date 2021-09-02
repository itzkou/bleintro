package com.example.bleintro

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bleintro.databinding.ItemDeviceBinding

class DeviceAdapter() : RecyclerView.Adapter<DeviceAdapter.ScanViewHolder>() {

    private var devices = listOf<BluetoothDevice>()

    class ScanViewHolder(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanViewHolder {
        return ScanViewHolder(
            ItemDeviceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ScanViewHolder, position: Int) {
        val device = devices[position]
        with(holder.binding) {
            txAdress.text = device.address
            txName.text = device.name
        }
    }


    override fun getItemCount(): Int {
        return devices.size
    }

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        val diffResult =
            DiffUtil.calculateDiff(SimpleCallback(this.devices, newDevices) { it.address})
        this.devices = newDevices
        diffResult.dispatchUpdatesTo(this)
    }

    /*fun add(chatMsg: BluetoothDevice) {
        devices.add(chatMsg)
        notifyItemInserted(devices.size)
    }*/


}

