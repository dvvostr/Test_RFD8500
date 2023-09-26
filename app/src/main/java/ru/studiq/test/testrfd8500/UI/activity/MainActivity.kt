package ru.studiq.test.testrfd8500.UI.activity

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.zebra.scannercontrol.RMDAttributes
import ru.studiq.test.testrfd8500.R
import ru.studiq.test.testrfd8500.model.classes.zebra.*
import ru.studiq.test.testrfd8500.model.classes.zebra.barcode.*
import ru.studiq.test.testrfd8500.model.classes.zebra.rfid.*

class MainActivity : AppCompatActivity(), IZebraHandheldDeviceListener {

    private val TAG: String = MainActivity::class.java.simpleName
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 100
    private val ACCESS_FINE_LOCATION_REQUEST_CODE = 99

    private var lastTag: RFIDReaderTag? = null

    private val textCode by lazy { findViewById<EditText?>(R.id.mainactivity_text_lastcode) }
    private val buttonWrite by lazy { findViewById<Button?>(R.id.mainactivity_btn_write) }
    private val buttonTest1 by lazy { findViewById<Button?>(R.id.mainactivity_btn_test1) }
    private val buttonTest2 by lazy { findViewById<Button?>(R.id.mainactivity_btn_test2) }
    private val listView by lazy { findViewById<ListView?>(R.id.mainactivity_list_data) }
    private val progressBar by lazy { findViewById<ProgressBar?>(R.id.mainactivity_progressbar) }
    private var tag: RFIDReaderTag?
        get() = lastTag
        set(value) {
            lastTag = value
            value?.asString()?.let { str ->
                textCode.setText(value?.key)
                dataList.add(0, "RFID TAG: ${str}")
                listView.invalidateViews()
            }
        }
    private var dataList: MutableList<String> = mutableListOf()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, dataList)
        listView.adapter = listAdapter
        device = ZebraHandheldDevice(this).apply {
            connect(object : IZebraHandheldDeviceActionListener{
                override fun onZebraHandheldDeviceActionSuccess(sender: Any?, data: Any?) {
                    (sender as? ZebraHandheldDevice)?.let { sender ->
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Device ${sender.name ?: "[UNKNOWN]"} successfully connect", Toast.LENGTH_LONG).show()
                        }
                        sender.multipleReadType = RFIDReaderMultipleReadType.single
                    }
                }
                override fun onZebraHandheldDeviceActionError(sender: Any?, msg: ZebraHandheldDeviceMessage?) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Device connection error\n${msg?.msg ?: "[UNKNOWN]"}", Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
        buttonWrite.setOnClickListener {
            tag?.key?.let { key ->
                device?.tagWrite(key, RFIDReaderMemoryBank.epc, textCode.text.toString(), "00", 2, object: IZebraHandheldDeviceActionListener {
                    override fun onZebraHandheldDeviceActionSuccess(sender: Any?, data: Any?) {
                        runOnUiThread {
                            device?.beep(RMDAttributes.RMD_ATTR_VALUE_ACTION_HIGH_SHORT_BEEP_1)
                            device?.blinkLED(BarcodeScannerInterface.LEDType.greenOn)
                            Toast.makeText(this@MainActivity, "Success", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onZebraHandheldDeviceActionError(sender: Any?, msg: ZebraHandheldDeviceMessage?) {
                        runOnUiThread {
                            device?.beep(RMDAttributes.RMD_ATTR_VALUE_ACTION_HIGH_SHORT_BEEP_3)
                            device?.blinkLED(BarcodeScannerInterface.LEDType.redOn)
                            Toast.makeText(this@MainActivity, msg?.msg ?: "Error", Toast.LENGTH_SHORT).show()
                        }

                    }
                })

            }
        }
        buttonTest1.setOnClickListener {
            device?.setAntennaLevel(1, 10.0)
        }
        buttonTest2.setOnClickListener {
            dataList.removeAll(dataList)
            listView.invalidateViews()
//            device?.rfidInterface?.reader?.Config?.beeperVolume = BEEPER_VOLUME.HIGH_BEEP
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Companion.device?.configureDevice(this)
            } else {
                Toast.makeText(this, "Bluetooth Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onZebraHandheldDeviceBarcodeScan(sender: ZebraHandheldDevice?, barcode: String?) {
        runOnUiThread {
            barcode?.let { barcode ->
                dataList.add(0, "BARCODE: ${barcode}")
                listView.invalidateViews()
            }
        }
        if (device?.multipleReadType == RFIDReaderMultipleReadType.single)
            device?.triggerType = RFIDReaderTriggerAction.RFID
    }

    override fun onZebraHandheldDeviceTagRead(sender: ZebraHandheldDevice?, tag: RFIDReaderTag?) {
        device?.beep(RMDAttributes.RMD_ATTR_VALUE_ACTION_HIGH_SHORT_BEEP_1)
        device?.blinkLED(BarcodeScannerInterface.LEDType.redOn)
        runOnUiThread {
            device?.beep(RMDAttributes.RMD_ATTR_VALUE_ACTION_HIGH_SHORT_BEEP_1)
            device?.blinkLED(BarcodeScannerInterface.LEDType.greenOn)
            this.tag = tag
        }
        if (device?.multipleReadType == RFIDReaderMultipleReadType.single)
            device?.triggerType = RFIDReaderTriggerAction.barcode
    }

    override fun onZebraHandheldDeviceTriggerStateChange(sender: ZebraHandheldDevice?, state: RFIDReaderTriggerState): Boolean {
        return true
    }

    override fun onZebraHandheldDeviceWait(sender: ZebraHandheldDevice?, isWait: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (isWait) ProgressBar.VISIBLE else ProgressBar.GONE
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    private fun dispose() {
        try {
            device?.let {
                it.destroy()
            }
        } catch (ex: Exception) {
        }
    }

    companion object {
        private var device: ZebraHandheldDevice? = null
    }
}