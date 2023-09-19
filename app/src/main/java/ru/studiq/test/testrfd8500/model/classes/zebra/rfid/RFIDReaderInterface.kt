package ru.studiq.test.testrfd8500.model.classes.zebra.rfid

import android.content.Context
import android.util.Log
import com.zebra.rfid.api3.*
import java.util.ArrayList

class RFIDReaderInterface(var listener: IRFIDReaderListener) : RfidEventsListener {

    private val TAG: String = RFIDReaderInterface::class.java.simpleName

    class RFIDReaderInterface {

    }

    private lateinit var readers: Readers
    private var availableRFIDReaderList: ArrayList<ReaderDevice>? = null
    private var readerDevice: ReaderDevice? = null
    private var isDataExists: Boolean = false
    lateinit var reader: RFIDReader
    var multipleReadType: RFIDReaderMultipleReadType = RFIDReaderMultipleReadType.multiple

    fun connect(context: Context): Boolean {
        readers = Readers(context, ENUM_TRANSPORT.ALL)
        try {
            return readers?.let { readers ->
                readers.GetAvailableRFIDReaderList()?.firstOrNull()?.let { readerDevice ->
                    reader = readerDevice!!.rfidReader
                    if (!reader!!.isConnected) {
                        Log.d(TAG, "RFID Reader Connecting...")
                        reader!!.connect()
                        configureReader()
                        Log.d(TAG, "RFID Reader Connected!")
                        listener.onRFIDReaderConnect(this, reader)
                        true
                    } else false
                } ?: false
            } ?: false
        } catch (e: InvalidUsageException) {
            e.printStackTrace()
            listener.onRFIDReaderError(this, e)
        } catch (e: OperationFailureException) {
            e.printStackTrace()
            listener.onRFIDReaderError(this, e)
        } catch (e: OperationFailureException) {
            e.printStackTrace()
            listener.onRFIDReaderError(this, e)
        } catch (e: InvalidUsageException) {
            e.printStackTrace()
            listener.onRFIDReaderError(this, e)
        }
        Log.d(TAG, "RFID Reader connection error!")
        return false
    }

    private fun configureReader() {
        if (reader.isConnected) {
            val triggerInfo = TriggerInfo()
            triggerInfo.StartTrigger.triggerType = START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE
            triggerInfo.StopTrigger.triggerType = STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE
            try {
                reader.Events.addEventsListener(this)
                reader.Events.setHandheldEvent(true)
                reader.Events.setTagReadEvent(true)
                reader.Events.setAttachTagDataWithReadEvent(false)
                reader.Config.startTrigger = triggerInfo.StartTrigger
                reader.Config.stopTrigger = triggerInfo.StopTrigger
                reader.Config.setKeylayoutType(ENUM_KEYLAYOUT_TYPE.UPPER_TRIGGER_FOR_SLED_SCAN)
            } catch (e: InvalidUsageException) {
                e.printStackTrace()
                listener.onRFIDReaderError(this, e)
            } catch (e: OperationFailureException) {
                e.printStackTrace()
                listener.onRFIDReaderError(this, e)
            }
        }
    }
    fun startRead() {
        try {
            isDataExists = false
            val memoryBanksToRead = arrayOf(MEMORY_BANK.MEMORY_BANK_EPC, MEMORY_BANK.MEMORY_BANK_TID, MEMORY_BANK.MEMORY_BANK_USER);
            for (bank in memoryBanksToRead) {
                val ta = TagAccess()
                val sequence = ta.Sequence(ta)
                val op = sequence.Operation()
                op.accessOperationCode = ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ
                op.ReadAccessParams.memoryBank = bank ?: throw IllegalArgumentException("bank must not be null")
                reader.Actions.TagAccess.OperationSequence.add(op)
            }

            reader.Actions.TagAccess.OperationSequence.performSequence()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun stopRead() {
        try {
            reader.Actions.TagAccess.OperationSequence.stopSequence()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun eventStatusNotify(rfidStatusEvents: RfidStatusEvents) {
        Log.d(TAG, "Status Notification: " + rfidStatusEvents.StatusEventData.statusEventType)
        if (rfidStatusEvents.StatusEventData.statusEventType === STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
            if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.handheldEvent === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
                listener.onTriggerStateChange(this, RFIDReaderTriggerState.down)
            } else if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.handheldEvent === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                listener.onTriggerStateChange(this, RFIDReaderTriggerState.up)
            }
        }
    }
    override fun eventReadNotify(e: RfidReadEvents) {
        val readTags = reader.Actions.getReadTags(100)
        if (readTags != null) {
            val readTagsList = readTags.toList()
            val tagReadGroup = readTagsList.groupBy { it.tagID }.toMutableMap()

            var tag = RFIDReaderTag()
            for (tagKey in tagReadGroup.keys) {
                val tagValueList = tagReadGroup[tagKey]

                for (tagData in tagValueList!!) {
                    if (tagData.opCode == ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ) {
                        when (tagData.memoryBank.ordinal) {
                            MEMORY_BANK.MEMORY_BANK_EPC.ordinal -> tag.epc = getMemBankData(tagData.memoryBankData, tagData.opStatus)
                            MEMORY_BANK.MEMORY_BANK_TID.ordinal -> tag.tid = getMemBankData(tagData.memoryBankData, tagData.opStatus)
                            MEMORY_BANK.MEMORY_BANK_USER.ordinal -> tag.usr = getMemBankData(tagData.memoryBankData, tagData.opStatus)
                        }
                    }
                }
                if (multipleReadType != RFIDReaderMultipleReadType.single || !isDataExists)
                    listener.onTagRead(this, tag)
                isDataExists = true
                if (multipleReadType != RFIDReaderMultipleReadType.multiple) {
                    stopRead()
                }
            }
        }
    }

    fun getMemBankData(memoryBankData : String?, opStatus : ACCESS_OPERATION_STATUS) : String {
        return if(opStatus != ACCESS_OPERATION_STATUS.ACCESS_SUCCESS){
            opStatus.toString()
        } else
            memoryBankData!!
    }


    fun onDestroy() {
        try {
            if (reader != null) {
                reader.Events?.removeEventsListener(this)
                reader.disconnect()
                reader.Dispose()
                readers.Dispose()
            }
        } catch (e: InvalidUsageException) {
            e.printStackTrace()
        } catch (e: OperationFailureException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}