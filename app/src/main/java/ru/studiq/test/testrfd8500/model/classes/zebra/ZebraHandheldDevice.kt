package ru.studiq.test.testrfd8500.model.classes.zebra

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ru.studiq.test.testrfd8500.model.classes.zebra.barcode.*
import ru.studiq.test.testrfd8500.model.classes.zebra.rfid.*

class ZebraHandheldDevice(var context: Context):
    IBarcodeScannedListener,
    IRFIDReaderListener {

    companion object {
        private val STRING_SCANNER_NOT_AVAILABLE = "No available scanner"
        private val STRING_DEVICE_CONNECTION_ERROR = "Device connection ERROR!"
        private val STRING_SCANNER_CONNECTION_SUCCESS = "Scanner connected!"
        private val STRING_SCANNER_CONNECTION_ERROR = "Scanner connection ERROR!"
        private val STRING_RFID_CONNECTION_SUCCESS = "RFID Reader connected!"
        private val STRING_RFID_CONNECTION_ERROR = "RFID Reader connection ERROR!"


    }
    private val TAG: String = ZebraHandheldDevice::class.java.simpleName

    private var rfidInterface: RFIDReaderInterface? = null
    private var barcodeInterface: BarcodeScannerInterface? = null

    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 100
    private val ACCESS_FINE_LOCATION_REQUEST_CODE = 99

    private var listener: IZebraHandheldDeviceListener? = null

    public var triggerType: RFIDReaderTriggerAction = RFIDReaderTriggerAction.RFID
    public val name : String?
        get() = rfidInterface?.reader?.hostName
    public var multipleReadType: RFIDReaderMultipleReadType
        get() = rfidInterface?.multipleReadType ?: RFIDReaderMultipleReadType.unassigned
        set(value) {
            rfidInterface?.multipleReadType = value
        }

    private var isDWRegistered: Boolean = false

    private val dataWedgeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (action == "ru.studiq.test.testrfd8500.ACTION") {
                val decodedData: String? = intent.getStringExtra("com.symbol.datawedge.data_string")
                this@ZebraHandheldDevice.onBarcodeScan(decodedData)
            }
        }
    }
//    constructor(context: Context?, params: Any?) : this(context){}
    init {
        listener = context as? IZebraHandheldDeviceListener
        (context as? Activity)?.let { activity ->
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION_REQUEST_CODE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_PERMISSION_REQUEST_CODE)
                } else {
                    listener?.onZebraHandheldDeviceInit(this)
                }
            } else {
                listener?.onZebraHandheldDeviceInit(this)
            }
        }
    }
    public fun configureDevice(activity: Activity) {
        if (barcodeInterface == null)
            barcodeInterface = BarcodeScannerInterface(this.context, this)

        var availableScannerList = barcodeInterface!!.getAvailableScanners()
        if(availableScannerList.size > 1) {
            val items = availableScannerList.map { x -> x.scannerName }.toTypedArray()
            var checkedItem = 0

            val dialog = AlertDialog.Builder(activity)
                .setTitle("Choose Scanner")
                .setSingleChoiceItems(items, checkedItem) { _, which -> checkedItem = which }
                .setPositiveButton("Connect") { _, _ ->
                    connectScanner(availableScannerList[checkedItem].scannerID)
                }

            val alert = dialog.create()
            alert.setCanceledOnTouchOutside(false)
            alert.show()
        }
        else if (availableScannerList.first() != null) {
            connectScanner(availableScannerList.first().scannerID)
        } else {
            listener?.onZebraHandheldDeviceMessage(this, ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.error, STRING_SCANNER_NOT_AVAILABLE))
        }
    }
    fun  connect(callback: IZebraHandheldDeviceActionListener) {
        Thread {
            try {
                listener?.onZebraHandheldDeviceWait(this, true)
                if (rfidInterface == null)
                    rfidInterface = RFIDReaderInterface(context, this)
                var result = rfidInterface?.connect() ?: false
                if (result) {
                    if (barcodeInterface == null)
                        barcodeInterface = BarcodeScannerInterface(this.context, this)
                    result = barcodeInterface?.getAvailableScanners()?.firstOrNull {
                        it.scannerName == rfidInterface?.reader?.hostName
                    }?.let {scanner ->
                        barcodeInterface?.connectToScanner(scanner.scannerID) ?: false
                    } ?: false
                }
                if (result) {
                    callback?.onZebraHandheldDeviceActionSuccess(this)
                } else {
                    callback?.onZebraHandheldDeviceActionError(
                        this,
                        ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.error, STRING_DEVICE_CONNECTION_ERROR)
                    )
                }
            } finally {
                listener?.onZebraHandheldDeviceWait(this, false)
            }
        }.start()
    }
    private fun connectScanner(scannerID: Int) {
        Thread {
            try {
                listener?.onZebraHandheldDeviceWait(this, true)
                val msg = if (barcodeInterface?.connectToScanner(scannerID) ?: false)
                    ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.alert, STRING_SCANNER_CONNECTION_SUCCESS)
                else
                    ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.error, STRING_SCANNER_CONNECTION_ERROR)
                listener?.onZebraHandheldDeviceMessage(this, msg)
                // RFID
            } finally {
                listener?.onZebraHandheldDeviceWait(this, false)
            }
        }.start()
    }
    private fun connectRFID() {
        Thread {
            try {
                listener?.onZebraHandheldDeviceWait(this, true)
                if (rfidInterface == null)
                    rfidInterface = RFIDReaderInterface(context, this)
                rfidInterface?.multipleReadType = RFIDReaderMultipleReadType.single
                val msg = if (rfidInterface?.connect() ?: false)
                    ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.alert, STRING_RFID_CONNECTION_SUCCESS)
                else
                    ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.error, STRING_RFID_CONNECTION_ERROR)
                listener?.onZebraHandheldDeviceMessage(this, msg)
            } finally {
                listener?.onZebraHandheldDeviceWait(this, false)
            }
        }.start()
    }

    private fun registerReceivers() {
        val filter = IntentFilter()
        filter.addAction("com.symbol.datawedge.api.NOTIFICATION_ACTION")
        filter.addAction("com.symbol.datawedge.api.RESULT_ACTION")
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        filter.addAction("${context.packageName}.ACTION")
        filter.addAction("${context.packageName}.service.ACTION")
        context.registerReceiver(dataWedgeReceiver, filter)
        isDWRegistered = true
    }
    override fun onBarcodeScan(barcode: String?) {
        listener?.onZebraHandheldDeviceBarcodeScan(this, barcode)
    }

    override fun onTagRead(sender: RFIDReaderInterface?, tag: RFIDReaderTag?) {
        listener?.onZebraHandheldDeviceTagRead(this, tag)
    }

    override fun onTriggerStateChange(sender: RFIDReaderInterface?, state: RFIDReaderTriggerState) {
        if (listener?.onZebraHandheldDeviceTriggerStateChange(this, state) ?: true) {
            when (triggerType) {
                RFIDReaderTriggerAction.RFID -> {
                    when (state) {
                        RFIDReaderTriggerState.down -> rfidInterface?.startRead()
                        RFIDReaderTriggerState.up -> rfidInterface?.stopRead()
                        else -> return
                    }
                }
                RFIDReaderTriggerAction.barcode -> {
                    when (state) {
                        RFIDReaderTriggerState.down -> barcodeInterface?.startScan()
                        RFIDReaderTriggerState.up -> barcodeInterface?.stopScan()
                        else -> return
                    }
                }
                else -> return
            }
        }
    }
    public fun destroy() {
        try {
            if (isDWRegistered)
                context.unregisterReceiver(dataWedgeReceiver)
            isDWRegistered = false
            rfidInterface?.let { obj ->
                obj.onDestroy()
            }
            barcodeInterface?.let { obj ->
                obj.onDestroy()
            }
        } catch (ex: Exception) {
            listener?.onZebraHandheldDeviceMessage(this, ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.exception, ex.message))
        }
    }
}