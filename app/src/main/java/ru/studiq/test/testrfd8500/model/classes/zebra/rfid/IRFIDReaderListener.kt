package ru.studiq.test.testrfd8500.model.classes.zebra.rfid

import android.content.Context
import com.zebra.rfid.api3.RFIDReader

interface IRFIDReaderListener {
    abstract fun onTagRead(sender: RFIDReaderInterface?, tag: RFIDReaderTag?)
    open fun onRFIDReaderConnect(sender: RFIDReaderInterface?, reader: RFIDReader?) {}
    open fun onRFIDReaderDisconnect(sender: RFIDReaderInterface?, reader: RFIDReader?) {}
    open fun onRFIDReaderSuccess(sender: RFIDReaderInterface?, data: Any?) {}
    open fun onRFIDReaderError(sender: RFIDReaderInterface?, exception: Exception) {}
    open fun onTriggerStateChange(sender: RFIDReaderInterface?, state: RFIDReaderTriggerState) {}
    open fun onRFIDReaderCharging(sender: Context?, isCharging: Boolean) {}
    open fun onRFIDReaderBattery(sender: Context?, level: Int, isCharging: Boolean, desc: String?) {}
    open fun onRFIDCradleChange(sender: Context?, isOnCradle: Boolean, desc: String?) {}

}