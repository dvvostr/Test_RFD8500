package ru.studiq.test.testrfd8500.model.classes.zebra.barcode

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.zebra.rfid.api3.*
import com.zebra.scannercontrol.*
import ru.studiq.test.testrfd8500.model.interfaces.ICustomObjectEventListener
import java.util.ArrayList
import java.util.concurrent.Executors

class BarcodeScannerInterface (val listener : IBarcodeScannedListener): IDcsSdkApiDelegate {
    private var sdkHandler: SDKHandler? = null
    private var scannerInfoList : ArrayList<DCSScannerInfo> = ArrayList()

    enum class LEDType(var value: Int) {
        redOn(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_RED_ON),
        redOff(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_RED_OFF),
        greenOff(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_GREEN_OFF),
        greenOn(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_GREEN_ON),
        otherOn(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_OTHER_ON),
        otherOff(RMDAttributes.RMD_ATTR_VALUE_ACTION_LED_OTHER_OFF);
    }

    fun getAvailableScanners(context : Context) : ArrayList<DCSScannerInfo> {
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
            if(scanner.isActive)
                return true
            return sdkHandler!!.dcssdkEstablishCommunicationSession(scanner.scannerID) == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS
        } catch (e: Exception) {
            return false
        }
    }
    fun onDestroy() {
        try {
            if (sdkHandler != null) {
                sdkHandler = null
            }
        } catch (e: InvalidUsageException) {
            e.printStackTrace()
        } catch (e: OperationFailureException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun startScan(context: Context?, scannerID: Int): Boolean {
        try {
            setLED(context, scannerID, LEDType.redOn)
            var xml = "<inArgs><scannerID>${scannerID}</scannerID></inArgs>"
            val outXML = StringBuilder()
            return (sdkHandler?.dcssdkExecuteCommandOpCodeInXMLForScanner(DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_DEVICE_PULL_TRIGGER, xml, outXML) == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS) ?: false
        } finally {
            setLED(context, scannerID, LEDType.redOff)
        }
    }
    fun stopScan(){
        sdkHandler?.dcssdkStopScanningDevices()
    }
    fun setLED(context: Context?, scannerID: Int, ledType: LEDType): Boolean {
        val xml = "<inArgs><scannerID>${scannerID}</scannerID><cmdArgs><arg-int>${ledType.value}</arg-int></cmdArgs></inArgs>"
        val outXML = StringBuilder()
        executeCommandAsync(context, DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_SET_ACTION, xml, outXML)
        return true
    }
    fun executeCommand(opCode: DCSSDKDefs.DCSSDK_COMMAND_OPCODE?, inXML: String?, outXML: StringBuilder?): Boolean {
        var outXML = outXML ?: StringBuilder()
        return sdkHandler?.let { handler ->
            val result: DCSSDKDefs.DCSSDK_RESULT = handler.dcssdkExecuteCommandOpCodeInXMLForScanner(opCode, inXML, outXML)
            return (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS)
        } ?: false

    }
    fun executeCommandAsync(context: Context?, opCode: DCSSDKDefs.DCSSDK_COMMAND_OPCODE?, inXML: String?, outXML: StringBuilder?, listener: ICustomObjectEventListener? = null){
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
            listener.onBarcodeScan(String(barcode))
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