package ru.studiq.test.testrfd8500.model.classes.zebra

import ru.studiq.test.testrfd8500.model.classes.zebra.rfid.RFIDReaderInterface
import ru.studiq.test.testrfd8500.model.classes.zebra.rfid.RFIDReaderTag

interface IZebraHandheldDeviceListener {
    open fun onZebraHandheldDeviceMessage(sender: ZebraHandheldDevice?, error: ZebraHandheldDeviceMessage?) {}
    open fun onZebraHandheldDeviceException(sender: ZebraHandheldDevice?, exception: Exception) {}
    open fun onZebraHandheldDeviceTagRead(sender: ZebraHandheldDevice?, tag: RFIDReaderTag?) {}
    open fun onZebraHandheldDeviceBarcodeScan(sender: ZebraHandheldDevice?, barcode : String?) {}
}