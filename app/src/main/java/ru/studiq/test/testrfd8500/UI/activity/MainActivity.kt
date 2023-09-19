package ru.studiq.test.testrfd8500.UI.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ru.studiq.test.testrfd8500.R

import ru.studiq.test.testrfd8500.model.classes.zebra.barcode.*
import ru.studiq.test.testrfd8500.model.classes.zebra.rfid.*

class MainActivity : AppCompatActivity(), IBarcodeScannedListener, IRFIDReaderListener {

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
                this@MainActivity.onBarcodeScan(decodedData)
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        progressBar = findViewById(R.id.progressBar)
        listViewRFID = findViewById(R.id.listViewRFID)
        listViewBarcodes = findViewById(R.id.listViewBarcodes)
        val tagsLIstAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tagsList)
        listViewRFID.adapter = tagsLIstAdapter
        val barcodeListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, barcodeList)
        listViewBarcodes.adapter = barcodeListAdapter

        //Scanner Initializations
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION_REQUEST_CODE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_PERMISSION_REQUEST_CODE)
            } else {
                configureDevice()
            }
        } else {
            configureDevice()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                configureDevice()
            } else {
                Toast.makeText(this, "Bluetooth Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    private fun configureDevice() {

        if (barcodeInterface == null)
            barcodeInterface = BarcodeScannerInterface(this)

        var availableScannerList = barcodeInterface!!.getAvailableScanners(applicationContext)
        if(availableScannerList.size > 1) {
            val items = availableScannerList.map { x -> x.scannerName }.toTypedArray()
            var checkedItem = 0

            val dialog = AlertDialog.Builder(this)
                .setTitle("Choose Scanner")
                .setSingleChoiceItems(items, checkedItem) { _, which -> checkedItem = which }
                .setPositiveButton("Connect") { _, _ ->
                    configureScanner(availableScannerList[checkedItem].scannerID)
                }

            val alert = dialog.create()
            alert.setCanceledOnTouchOutside(false)
            alert.show()
        }
        else if(availableScannerList.first() != null){
            configureScanner(availableScannerList.first().scannerID)
        }
        else{
            Toast.makeText(
                applicationContext,
                "No available scanner",
                Toast.LENGTH_LONG
            ).show()
        }

    }

    private fun configureScanner(scannerID: Int) {
        Thread {
            progressBar.visibility = ProgressBar.VISIBLE
            val connectScannerResult =
                barcodeInterface!!.connectToScanner(scannerID)

            runOnUiThread {
                Toast.makeText(
                    applicationContext,
                    if (connectScannerResult) "Scanner connected!" else "Scanner connection ERROR!",
                    Toast.LENGTH_LONG
                ).show()
            }

            // RFID
            configureRFID()
        }.start()
    }
    private fun configureRFID() {
        // Configure RFID
        if (rfidInterface == null)
            rfidInterface = RFIDReaderInterface(this)
        rfidInterface?.multipleReadType = RFIDReaderMultipleReadType.single

        var connectRFIDResult = rfidInterface!!.connect(applicationContext)

        runOnUiThread {
            progressBar.visibility = ProgressBar.GONE
            Toast.makeText(
                applicationContext,
                if (connectRFIDResult) "RFID Reader connected!" else "RFID Reader connection ERROR!",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    private fun registerReceivers() {
        val filter = IntentFilter()
        filter.addAction("com.symbol.datawedge.api.NOTIFICATION_ACTION") // for notification result
        filter.addAction("com.symbol.datawedge.api.RESULT_ACTION") // for error code result
        filter.addCategory(Intent.CATEGORY_DEFAULT) // needed to get version info

        // register to received broadcasts via DataWedge scanning
        filter.addAction("$packageName.ACTION")
        filter.addAction("$packageName.service.ACTION")
        registerReceiver(dataWedgeReceiver, filter)
        isDWRegistered = true
    }

    override fun onBarcodeScan(barcode: String?) {
        runOnUiThread {
            barcodeList.add(0, barcode!!)
            listViewBarcodes.invalidateViews()
        }
        triggerType = RFIDReaderTriggerAction.RFID
    }

    override fun onTagRead(sender: RFIDReaderInterface?, tag: RFIDReaderTag?) {
        runOnUiThread {
            tagsList.add(0, tag?.asString() ?: "")
            listViewRFID.invalidateViews()
        }
        triggerType = RFIDReaderTriggerAction.barcode
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
                    barcodeInterface?.startScan(this, 1)
                }
            }
            else -> return
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    private fun dispose() {
        try {
            if (isDWRegistered)
                unregisterReceiver(dataWedgeReceiver)

            isDWRegistered = false

            if (rfidInterface != null) {
                rfidInterface!!.onDestroy()
            }
            if (barcodeInterface != null) {
                barcodeInterface!!.onDestroy()
            }
        } catch (ex: Exception) {
        }
    }

    companion object {
        private var rfidInterface: RFIDReaderInterface? = null
        private var barcodeInterface: BarcodeScannerInterface? = null
    }
}