package ru.studiq.test.testrfd8500.model.classes.zebra

import ru.studiq.test.testrfd8500.model.classes.zebra.rfid.RFIDReaderTag
import ru.studiq.test.testrfd8500.model.classes.zebra.rfid.RFIDReaderTriggerState

interface IZebraHandheldDeviceListener {
    fun onZebraHandheldDeviceInit(sender: ZebraHandheldDevice?) {}
    fun onZebraHandheldDeviceConnect(sender: ZebraHandheldDevice?) {}
    fun onZebraHandheldDeviceMessage(sender: ZebraHandheldDevice?, error: ZebraHandheldDeviceMessage?) {}
    fun onZebraHandheldDeviceException(sender: ZebraHandheldDevice?, exception: Exception) {}
    fun onZebraHandheldDeviceWait(sender: ZebraHandheldDevice?, isWait: Boolean = false) {}
    fun onZebraHandheldDeviceTriggerStateChange(sender: ZebraHandheldDevice?, state: RFIDReaderTriggerState): Boolean { return true}
    fun onZebraHandheldDeviceTagRead(sender: ZebraHandheldDevice?, tag: RFIDReaderTag?) {}
    fun onZebraHandheldDeviceBarcodeScan(sender: ZebraHandheldDevice?, barcode : String?) {}
}