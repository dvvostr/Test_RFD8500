package ru.studiq.test.testrfd8500.model.classes.zebra.barcode

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.zebra.rfid.api3.*
import com.zebra.scannercontrol.*
import ru.studiq.test.testrfd8500.model.interfaces.ICustomObjectEventListener
import java.util.ArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BarcodeScannerInterface (val context: Context?, var listener: IBarcodeScannedListener?): IDcsSdkApiDelegate {
    private var sdkHandler: SDKHandler? = null
    private var scannerID: Int = -1
    private var scannerInfoList : ArrayList<DCSScannerInfo> = ArrayList()

    enum class LEDType(var value: Int) {
        redOn(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_RED_ON),
        redOff(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_RED_OFF),
        greenOff(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_GREEN_OFF),
        greenOn(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_GREEN_ON),
        otherOn(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_OTHER_ON),
        otherOff(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_OTHER_OFF);
        val negative: LEDType
            get() {
                return when (this) {
                    redOn -> redOff
                    redOff -> redOn
                    greenOff -> greenOn
                    greenOn -> greenOff
                    otherOn -> otherOff
                    otherOff -> otherOn
                }
            }
    }

    fun getAvailableScanners() : ArrayList<DCSScannerInfo> {
        if(sdkHandler == null)
            sdkHandler = SDKHandler(context)

        sdkHandler!!.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL)
        sdkHandler!!.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_USB_CDC)

        sdkHandler!!.dcssdkSetDelegate(this);
        var notifications_mask = 0
        notifications_mask = notifications_mask or
                (DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value or
                DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value)
        notifications_mask = notifications_mask or
                (DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value or
                DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value)
        notifications_mask = notifications_mask or DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value

        // subscribe to events set in notification mask
        sdkHandler!!.dcssdkSubsribeForEvents(notifications_mask)
        sdkHandler!!.dcssdkEnableAvailableScannersDetection(true)

        sdkHandler!!.dcssdkGetAvailableScannersList(scannerInfoList)
        return scannerInfoList
    }
    fun connectToScanner(scannerID : Int) : Boolean{
        try {
            val scanner = scannerInfoList.first { x -> x.scannerID == scannerID }
            var  resilt = if  (scanner.isActive)
                true
            else
                sdkHandler!!.dcssdkEstablishCommunicationSession(scanner.scannerID) == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS
            this.scannerID = if (resilt) scanner.scannerID else -1
            return resilt
        } catch (e: Exception) {
            return false
        }
    }
    fun onDestroy() {
        try {
            if (sdkHandler != null) {
                sdkHandler = null
            }
            this.scannerID = -1
        } catch (e: InvalidUsageException) {
            e.printStackTrace()
        } catch (e: OperationFailureException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun startScan(): Boolean {
        try {
            setLED(LEDType.redOn)
            var xml = "<inArgs><scannerID>${scannerID}</scannerID></inArgs>"
            val outXML = StringBuilder()
            return (sdkHandler?.dcssdkExecuteCommandOpCodeInXMLForScanner(DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_DEVICE_PULL_TRIGGER, xml, outXML) == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS) ?: false
        } finally {
            setLED(LEDType.redOff)
        }
    }
    fun stopScan(): Boolean {
        val scannerID = scannerID?.let { it } ?: this.scannerID
        try {
            setLED(LEDType.redOn)
            var xml = "<inArgs><scannerID>${scannerID}</scannerID></inArgs>"
            val outXML = StringBuilder()
            return (sdkHandler?.dcssdkExecuteCommandOpCodeInXMLForScanner(DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_DEVICE_RELEASE_TRIGGER, xml, outXML) == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS) ?: false
        } finally {
            setLED(LEDType.redOff)
        }
        sdkHandler?.dcssdkStopScanningDevices()
    }
    fun beep(beepType: Int): Boolean {
        val xml = "<inArgs><scannerID>${scannerID}</scannerID><cmdArgs><arg-int>${beepType}</arg-int></cmdArgs></inArgs>"
        val outXML = StringBuilder()
        executeCommandAsync(DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_SET_ACTION, xml, outXML)
        return true
    }
    fun blinkLED(ledType: LEDType, delay: Long = 300): Boolean {
        setLED(ledType)
        Executors.newSingleThreadScheduledExecutor().schedule({
            setLED(ledType.negative)
        }, delay, TimeUnit.MILLISECONDS)
        return true
    }
    fun setLED(ledType: LEDType): Boolean {
        val xml = "<inArgs><scannerID>${scannerID}</scannerID><cmdArgs><arg-int>${ledType.value}</arg-int></cmdArgs></inArgs>"
        val outXML = StringBuilder()
        executeCommandAsync(DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_SET_ACTION, xml, outXML)
        return true
    }
    fun executeCommand(opCode: DCSSDKDefs.DCSSDK_COMMAND_OPCODE?, inXML: String?, outXML: StringBuilder?): Boolean {
        var outXML = outXML ?: StringBuilder()
        return sdkHandler?.let { handler ->
            val result: DCSSDKDefs.DCSSDK_RESULT = handler.dcssdkExecuteCommandOpCodeInXMLForScanner(opCode, inXML, outXML)
            return (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS)
        } ?: false

    }
    fun executeCommandAsync(opCode: DCSSDKDefs.DCSSDK_COMMAND_OPCODE?, inXML: String?, outXML: StringBuilder?, listener: ICustomObjectEventListener? = null){
        Executors.newSingleThreadExecutor().execute {
            val hnd = Handler(Looper.getMainLooper())
            if (executeCommand(opCode, inXML, outXML))
                listener?.onSuccess(context, outXML)
            else
                listener?.onError(context, ICustomObjectEventListener.EventMessage(-1, "Error execute command", null))
            hnd.post {}
        }
    }
    override fun dcssdkEventScannerAppeared(scanner: DCSScannerInfo?) {
    }
    override fun dcssdkEventScannerDisappeared(scannerID: Int) {
    }
    override fun dcssdkEventCommunicationSessionEstablished(scanner: DCSScannerInfo?) {
    }
    override fun dcssdkEventCommunicationSessionTerminated(scannerID: Int) {
    }
    override fun dcssdkEventBarcode(barcodeData: ByteArray?, barcodeType: Int, scannerID: Int) {
        barcodeData?.let { barcode ->
            listener?.onBarcodeScan(String(barcode))
        }
    }
    override fun dcssdkEventImage(imageData: ByteArray?, scannerID: Int) {
    }
    override fun dcssdkEventVideo(videoFrame: ByteArray?, scannerID: Int) {
    }

    override fun dcssdkEventBinaryData(binaryData: ByteArray?, scannerID: Int) {
    }

    override fun dcssdkEventFirmwareUpdate(firmwareUpdateEvent: FirmwareUpdateEvent?) {
    }

    override fun dcssdkEventAuxScannerAppeared(newTopology: DCSScannerInfo?, auxScanner: DCSScannerInfo?) {
    }
}