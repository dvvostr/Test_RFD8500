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
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ru.studiq.test.testrfd8500.UI.activity.MainActivity
import ru.studiq.test.testrfd8500.model.classes.zebra.barcode.*
import ru.studiq.test.testrfd8500.model.classes.zebra.rfid.*

class ZebraHandheldDevice(var context: Context, var listener: IZebraHandheldDeviceListener?):
    IBarcodeScannedListener,
    IRFIDReaderListener {

    companion object {
        private var rfidInterface: RFIDReaderInterface? = null
        private var barcodeInterface: BarcodeScannerInterface? = null
    }
    private val TAG: String = MainActivity::class.java.simpleName
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 100
    private val ACCESS_FINE_LOCATION_REQUEST_CODE = 99

    private var triggerType: RFIDReaderTriggerAction = RFIDReaderTriggerAction.RFID

    private lateinit var progressBar: ProgressBar
    private lateinit var listViewRFID: ListView
    private lateinit var listViewBarcodes: ListView

    private var isDWRegistered: Boolean = false
    private var barcodeList: MutableList<String> = mutableListOf()
    private var tagsList: MutableList<String> = mutableListOf()

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
    //Scanner Initializations
        (context as? Activity)?.let { activity ->
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION_REQUEST_CODE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_PERMISSION_REQUEST_CODE)
                } else {
                    configureDevice(activity)
                }
            } else {
                configureDevice(activity)
            }
        }
    }
    private fun configureDevice(activity: Activity) {
        if (barcodeInterface == null)
            barcodeInterface = BarcodeScannerInterface(this)

        var availableScannerList = barcodeInterface!!.getAvailableScanners(context.applicationContext)
        if(availableScannerList.size > 1) {
            val items = availableScannerList.map { x -> x.scannerName }.toTypedArray()
            var checkedItem = 0

            val dialog = AlertDialog.Builder(activity)
                .setTitle("Choose Scanner")
                .setSingleChoiceItems(items, checkedItem) { _, which -> checkedItem = which }
                .setPositiveButton("Connect") { _, _ ->
                    configureScanner(availableScannerList[checkedItem].scannerID)
                }

            val alert = dialog.create()
            alert.setCanceledOnTouchOutside(false)
            alert.show()
        }
        else if (availableScannerList.first() != null) {
            configureScanner(availableScannerList.first().scannerID)
        } else {
            listener?.onZebraHandheldDeviceMessage(this, ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.error, "No available scanner"))
        }

    }
    private fun configureScanner(scannerID: Int) {
        Thread {
            progressBar.visibility = ProgressBar.VISIBLE
            if (barcodeInterface?.connectToScanner(scannerID) ?: false)
                listener?.onZebraHandheldDeviceMessage(this, ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.alert, "Scanner connected!"))
            else
                listener?.onZebraHandheldDeviceMessage(this, ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.error, "Scanner connection ERROR!"))
            // RFID
            configureRFID()
        }.start()
    }
    private fun configureRFID() {
        // Configure RFID
        if (rfidInterface == null)
            rfidInterface = RFIDReaderInterface(this)
        rfidInterface?.multipleReadType = RFIDReaderMultipleReadType.single
        if (rfidInterface?.connect(context) ?: false)
            listener?.onZebraHandheldDeviceMessage(this, ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.alert, "RFID Reader connected!"))
        else
            listener?.onZebraHandheldDeviceMessage(this, ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.error, "RFID Reader connection ERROR!"))
    }

    private fun registerReceivers() {
        val filter = IntentFilter()
        filter.addAction("com.symbol.datawedge.api.NOTIFICATION_ACTION") // for notification result
        filter.addAction("com.symbol.datawedge.api.RESULT_ACTION") // for error code result
        filter.addCategory(Intent.CATEGORY_DEFAULT) // needed to get version info

        // register to received broadcasts via DataWedge scanning
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
        when (triggerType) {
            RFIDReaderTriggerAction.RFID -> {
                when (state) {
                    RFIDReaderTriggerState.down -> rfidInterface?.startRead()
                    RFIDReaderTriggerState.up -> rfidInterface?.stopRead()
                    else -> return
                }
            }
            RFIDReaderTriggerAction.barcode -> {
                if (state == RFIDReaderTriggerState.down) {
                    barcodeInterface?.startScan(context, 1)
                }
            }
            else -> return
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