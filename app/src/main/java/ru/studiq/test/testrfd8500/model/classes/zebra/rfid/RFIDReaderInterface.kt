package ru.studiq.test.testrfd8500.model.classes.zebra.rfid

import android.content.Context
import android.util.Log
import com.zebra.rfid.api3.ACCESS_OPERATION_CODE
import com.zebra.rfid.api3.ACCESS_OPERATION_STATUS
import com.zebra.rfid.api3.Antennas.AntennaRfConfig
import com.zebra.rfid.api3.BEEPER_VOLUME
import com.zebra.rfid.api3.ENUM_KEYLAYOUT_TYPE
import com.zebra.rfid.api3.ENUM_TRANSPORT
import com.zebra.rfid.api3.Events
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE
import com.zebra.rfid.api3.HANDHELD_TRIGGER_TYPE
import com.zebra.rfid.api3.InvalidUsageException
import com.zebra.rfid.api3.MEMORY_BANK
import com.zebra.rfid.api3.OperationFailureException
import com.zebra.rfid.api3.RFIDReader
import com.zebra.rfid.api3.ReaderDevice
import com.zebra.rfid.api3.Readers
import com.zebra.rfid.api3.RfidEventsListener
import com.zebra.rfid.api3.RfidReadEvents
import com.zebra.rfid.api3.RfidStatusEvents
import com.zebra.rfid.api3.START_TRIGGER_TYPE
import com.zebra.rfid.api3.STATUS_EVENT_TYPE
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE
import com.zebra.rfid.api3.TagAccess
import com.zebra.rfid.api3.TagData
import com.zebra.rfid.api3.TriggerInfo
import ru.studiq.test.testrfd8500.model.classes.zebra.IZebraHandheldDeviceActionListener
import ru.studiq.test.testrfd8500.model.classes.zebra.ZebraHandheldDeviceMessage
import ru.studiq.test.testrfd8500.model.classes.zebra.ZebraHandheldDeviceMessageType
import java.lang.Integer.min
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


enum class RFIDReaderMemoryBank(var value: String) {
    reserved("RESERVED"),
    epc("EPC"),
    tid("TID"),
    user("USER");
    val memoryBank: MEMORY_BANK get() {
        return when (this) {
            reserved -> MEMORY_BANK.MEMORY_BANK_RESERVED
            epc -> MEMORY_BANK.MEMORY_BANK_EPC
            tid -> MEMORY_BANK.MEMORY_BANK_TID
            user -> MEMORY_BANK.MEMORY_BANK_USER
        }
    }
}
class RFIDReaderInterface(var context: Context, var listener: IRFIDReaderListener?) : RfidEventsListener {
    private val TAG: String = RFIDReaderInterface::class.java.simpleName

    private lateinit var readers: Readers
    private var availableRFIDReaderList: ArrayList<ReaderDevice>? = null
    private var readerDevice: ReaderDevice? = null
    private var isDataExists: Boolean = false
    private var isReadOperation: Boolean = false
    lateinit var reader: RFIDReader

    var isAsciiMode: Boolean = false
    var multipleReadType: RFIDReaderMultipleReadType = RFIDReaderMultipleReadType.multiple

    //********************************************************************************************//
    private var scheduler: ScheduledExecutorService? = null
    private var taskHandle: ScheduledFuture<*>? = null
    enum class RFIDBankType(value: Int) {
        unassigned(-1),
        TID(1),
        EPC(2),
        user(3),
        reserved(4);
    }
    var settings = Settings
    object Settings {
        var SAVECONNECT_DELAY = 3000L
        var BATTERY_LOW_LEVEL = 25
        var BATTERY_EVENT_DELAY = 300000L
    }
    var power = Power
    object Power {
        var level: Int = -1
        var isCharging: Boolean = false
        var desc: String? = null
        var isOnCradle: Boolean = false

        init {
            initialize()
        }
        fun initialize(level: Int = -1, isCharging: Boolean = false, desc: String? = null) {
            this.level = level
            this.isCharging = isCharging
            this.desc = desc
        }
    }

    //********************************************************************************************//
    init {
        initilize()
    }
    //********************************************************************************************//
    fun initilize() {
        power.initialize()
    }

    fun finalize() {
        stopTimer()
        power.initialize()
        try {
            reader.Events?.removeEventsListener(this)
            reader.disconnect();
            readers.Dispose();
        } catch (ex: Exception) {
            listener?.onRFIDReaderError(this, ex)
        }
    }
    private fun startTimer() {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(1)
            val task = Runnable {
                try {
                    reader?.let {
                        queryStatus()
                    } ?: run {
                        stopTimer()
                    }
                } catch (e: InvalidUsageException) {
                    Log.d(this.TAG, "Returned SDK Exception")
                } catch (e: OperationFailureException) {
                    Log.d(this.TAG, "Returned SDK Exception")
                }
            }
            taskHandle = scheduler?.scheduleAtFixedRate(task, 0, 30, TimeUnit.SECONDS)
        }
    }

    private fun stopTimer() {
        if (taskHandle != null) {
            taskHandle!!.cancel(true)
            scheduler!!.shutdown()
        }
        taskHandle = null
        scheduler = null
    }

    fun connect(): Boolean {
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
                        handleReaderConnect(reader)
                        true
                    } else false
                } ?: false
            } ?: false
        } catch (e: InvalidUsageException) {
            e.printStackTrace()
            listener?.onRFIDReaderError(this, e)
        } catch (e: OperationFailureException) {
            e.printStackTrace()
            listener?.onRFIDReaderError(this, e)
        } catch (e: OperationFailureException) {
            e.printStackTrace()
            listener?.onRFIDReaderError(this, e)
        } catch (e: InvalidUsageException) {
            e.printStackTrace()
            listener?.onRFIDReaderError(this, e)
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
                reader.Events.setTagReadEvent(true)
                reader.Events.setAttachTagDataWithReadEvent(false)
                reader.Config.startTrigger = triggerInfo.StartTrigger
                reader.Config.stopTrigger = triggerInfo.StopTrigger
                reader.Config.setKeylayoutType(ENUM_KEYLAYOUT_TYPE.UPPER_TRIGGER_FOR_SLED_SCAN)
            } catch (e: InvalidUsageException) {
                e.printStackTrace()
                listener?.onRFIDReaderError(this, e)
            } catch (e: OperationFailureException) {
                e.printStackTrace()
                listener?.onRFIDReaderError(this, e)
            }
        }
    }
    fun setAccessProfile(value: Boolean) {

        if (reader.isConnected() && reader.isCapabilitiesReceived() && !isReadOperation) {
            val config: AntennaRfConfig
            try {
//                if (value && RFIDController.antennaRfConfig.getrfModeTableIndex() !== 0) {
//                    config = RFIDController.antennaRfConfig
//                    config.setrfModeTableIndex(0)
//                    reader.Config.Antennas.setAntennaRfConfig(1, config)
//                    RFIDController.antennaRfConfig = antennaRfConfigLocal
//                } else if (!value && RFIDController.antennaRfConfig.getrfModeTableIndex() !== LinkProfileUtil.getInstance()
//                        .getSimpleProfileModeIndex(RFIDController.ActiveProfile.LinkProfileIndex)
//                ) {
//                    config = RFIDController.antennaRfConfig
//                    config.setrfModeTableIndex(
//                        LinkProfileUtil.getInstance()
//                            .getSimpleProfileModeIndex(RFIDController.ActiveProfile.LinkProfileIndex)
//                    )
//                    reader.Config.Antennas.setAntennaRfConfig(1, config)
//                    RFIDController.antennaRfConfig = config
//                }
            } catch (ex: InvalidUsageException) {
                if (ex.stackTrace.size > 0) {
                    Log.e(TAG, ex.stackTrace[0].toString()
                    )
                }
            } catch (ex: OperationFailureException) {
                if (ex.stackTrace.size > 0) {
                    Log.e(TAG, ex.stackTrace[0].toString())
                }
            }
        }
    }
    fun queryStatus() {
        this.reader.Config.getDeviceStatus(true, true, false)
    }
    fun startInventory(resultListener: IZebraHandheldDeviceActionListener? = null) {
        isReadOperation = true
        isDataExists = false
        reader.Events.setTagReadEvent(true)
        reader.Events.setAttachTagDataWithReadEvent(false)
        val triggerInfo = TriggerInfo()
        triggerInfo.StartTrigger.triggerType = START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE
        triggerInfo.StopTrigger.triggerType = STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE
        reader.Config.startTrigger = triggerInfo.StartTrigger
        reader.Config.stopTrigger = triggerInfo.StopTrigger
        try {
            reader.Actions.Inventory.perform()
            resultListener?.onZebraHandheldDeviceActionSuccess(this)
        } catch (ex: Exception) {
            resultListener?.onZebraHandheldDeviceActionError(
                this,
                ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.exception, ex.message)
            )
        }
    }
    fun stopInventory(resultListener: IZebraHandheldDeviceActionListener? = null) {
        try {
            isReadOperation = false
            reader.Actions.Inventory.stop()
            resultListener?.onZebraHandheldDeviceActionSuccess(this)
        } catch (ex: Exception) {
            resultListener?.onZebraHandheldDeviceActionError(
                this,
                ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.exception, ex.message)
            )
        }
    }
    fun startRead() {
        try {
            isReadOperation = true
            isDataExists = false
            //           configureReader()
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
            isReadOperation = false
            reader.Actions.TagAccess.OperationSequence.stopSequence()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun startRead(tagId: String, memoryBank: RFIDReaderMemoryBank, password: String, length: Int, offset: Int, resultListener: IZebraHandheldDeviceActionListener?) {
        val tagAccess = TagAccess()
        val params = tagAccess.ReadAccessParams()
        try {
            params.accessPassword = java.lang.Long.decode("0X$password")
        } catch (ex: java.lang.NumberFormatException) {
            resultListener?.onZebraHandheldDeviceActionError(
                this,
                ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.exception, ex.message)
            )
        }
        params.count = length
        params.memoryBank = memoryBank.memoryBank
        params.offset = offset
        Thread {
            try {
                setAccessProfile(true)
                val flag = tagId.length <= 24
                val obj = reader.Actions.TagAccess.readWait(tagId, params, null, flag)
                resultListener?.onZebraHandheldDeviceActionSuccess(this, obj)
            } catch (ex: Exception) {
                resultListener?.onZebraHandheldDeviceActionError(
                    this,
                    ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.exception, ex.message)
                )
            }
        }.start()
    }
    fun startWrite(tag: RFIDReaderTag, memoryBank: RFIDReaderMemoryBank, writeData: String, offset: Int = 0, resultListener: IZebraHandheldDeviceActionListener? = null) {
        val tagData: TagData? = null
        val tagAccess = TagAccess()
        val params = tagAccess.WriteAccessParams()
        try {
            params.accessPassword = 0
            params.memoryBank = memoryBank.memoryBank
            params.offset = offset

            params.setWriteData(writeData)
            params.writeDataLength = writeData.length / 4
            this.reader.Actions.TagAccess.writeWait(tag.tid, params, null, tagData)
            resultListener?.onZebraHandheldDeviceActionSuccess(this, null)
        } catch (ex: Exception) {
            if (ex.stackTrace.size > 0) {
                resultListener?.onZebraHandheldDeviceActionError(
                    this,
                    ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.exception, ex.message)
                )
            }
        }
    }
    fun startWrite(tagId: String, memoryBank: RFIDReaderMemoryBank, data: String, password: String, offset: Int, resultListener: IZebraHandheldDeviceActionListener?) {
        var strData = data
        val tagAccess = TagAccess()
        val params = tagAccess.WriteAccessParams()
        try {
            params.accessPassword = java.lang.Long.decode("0X$password")
        } catch (ex: NumberFormatException) {
            resultListener?.onZebraHandheldDeviceActionError(
                this,
                ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.exception, ex.message)
            )
        }
        params.memoryBank = memoryBank.memoryBank
        params.offset = offset
        if (isAsciiMode === true) {
            params.setWriteData(AsciiToHEX.convert(data) ?: "")
            params.writeDataLength = data.length / 4
        } else {
            params.setWriteData(data)
            params.writeDataLength = data.length / 4
        }
        Thread {
            try {
                setAccessProfile(true)
                val flag = tagId.length <= 24
                val tagData = reader.Actions.TagAccess.writeWait(tagId, params, null, null, flag, false)
                resultListener?.onZebraHandheldDeviceActionSuccess(this, tagData)
            } catch (ex: Exception) {
                if (ex.stackTrace.size > 0) {
                    resultListener?.onZebraHandheldDeviceActionError(
                        this,
                        ZebraHandheldDeviceMessage(ZebraHandheldDeviceMessageType.exception, ex.message)
                    )
                }
            }
        }.start()
    }
    fun setAntennaLevel(antennaIndex: Int, level: Double): Boolean {
        try {
            val MAX_POWER = reader.ReaderCapabilities.getTransmitPowerLevelValues().max()
            val config = reader.Config.Antennas.getAntennaRfConfig(antennaIndex)
            config.transmitPowerIndex = min(((level / 100.0) * MAX_POWER).toInt(), MAX_POWER)
            reader.Config.Antennas.setAntennaRfConfig(antennaIndex, config)
            return true
        } catch (ex: OperationFailureException) {
            return false
        }
    }
    override fun eventStatusNotify(events: RfidStatusEvents) {
//        Log.d(TAG, "Status Notification: ${rfidStatusEvents.StatusEventData.statusEventType}")
//        if (rfidStatusEvents.StatusEventData.statusEventType === STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
//            if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.handheldEvent === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
//                listener?.onTriggerStateChange(this, RFIDReaderTriggerState.down)
//            } else if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.handheldEvent === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
//                listener?.onTriggerStateChange(this, RFIDReaderTriggerState.up)
//            }
//        }
//    }
//    override fun eventStatusNotify(events: RfidStatusEvents?) {
        // https://techdocs.zebra.com/dcs/rfid/android/2-0-2-82/apis/reference/com/zebra/rfid/api3/status_event_type
        when (events?.StatusEventData?.statusEventType) {
            // A GPI event (state change from high to low, or low to high) has occurred on a GPI port. When this event is signaled, StatusEventData contains GPI Port and GPI Event that has occurred.
            STATUS_EVENT_TYPE.GPI_EVENT -> handleEventGPI(events?.StatusEventData?.GPIEventData)
            // When the internal buffers are 90% full, this event is signaled
            STATUS_EVENT_TYPE.BUFFER_FULL_WARNING_EVENT -> return // TODO
            // A particular Antenna has been Connected/Disconnected. When this event is signaled, StatusEventData contains Antenna and the Connection Status that has occurred.
            STATUS_EVENT_TYPE.ANTENNA_EVENT -> return // TODO
            // Inventory Operation started. In case of periodic trigger, this event is triggered for each period
            STATUS_EVENT_TYPE.INVENTORY_START_EVENT -> handleEventInventoryStart(events?.StatusEventData?.InventoryStartEventData)
            // Inventory Operation has stopped. In case of periodic trigger this event is triggered for each period
            STATUS_EVENT_TYPE.INVENTORY_STOP_EVENT -> handleEventInventoryStop(events?.StatusEventData?.InventoryStopEventData)
            // Access Operation has started.
            STATUS_EVENT_TYPE.ACCESS_START_EVENT -> return // TODO
            // Access Operation has stopped.
            STATUS_EVENT_TYPE.ACCESS_STOP_EVENT -> return // TODO
            // Event notifying disconnection from the Reader. The Application can call reconnect method periodically to attempt reconnection or call disconnect method to cleanup and exit.
            STATUS_EVENT_TYPE.DISCONNECTION_EVENT -> handleEventDisconnection(events?.StatusEventData?.DisconnectionEventData)
            // When the internal buffers are 100% full, this event is signaled and tags are discarded in FIFO manner
            STATUS_EVENT_TYPE.BUFFER_FULL_EVENT -> handleEventBufferFullWarning(events?.StatusEventData?.BufferFullWarningEventData)
            // Event Notifying the presence of NXP tag with EAS bit set
            STATUS_EVENT_TYPE.NXP_EAS_ALARM_EVENT -> return // TODO
            // Event notifying that an exception has occurred in the Reader.
            STATUS_EVENT_TYPE.READER_EXCEPTION_EVENT -> handleEventException(events?.StatusEventData?.ReaderExceptionEventData)
            //  hand-held Gun/Button event Pull/Release has occurred.
            STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT -> handleEventTrigger(events?.StatusEventData?.HandheldTriggerEventData)
            STATUS_EVENT_TYPE.DEBUG_INFO_EVENT -> return // TODO
            // When Temperature reaches Threshold level, this event is generated. The event data contains source name (PA/Ambient),current Temperature and alarm Level (Low, High or Critical)
            STATUS_EVENT_TYPE.TEMPERATURE_ALARM_EVENT -> handleEventTemperatureAlarm(events?.StatusEventData?.TemperatureAlarmData)
            // Event generated when operation end summary has been generated. The data associated with the event contains total rounds, total number of tags and total time in micro secs
            STATUS_EVENT_TYPE.OPERATION_END_SUMMARY_EVENT -> handleEventOperationEndSummary(events?.StatusEventData?.OperationEndSummaryData)
            // This is a batch mode Event
            STATUS_EVENT_TYPE.BATCH_MODE_EVENT -> handleEventBatchMode(events?.StatusEventData?.BatchModeEventData)
            // This is an Alarm Event generated when reader reaches temperature threshold level
            STATUS_EVENT_TYPE.INFO_EVENT -> this.handleEventInfo(events?.StatusEventData?.InfoData)
            // Events which notify the different power states of the reader device. The event data contains cause, voltage, current and power
            STATUS_EVENT_TYPE.POWER_EVENT -> this.handleEventPower(events?.StatusEventData?.PowerData)
            // Events notifying different levels of battery, state of the battery, if charging or discharging.
            STATUS_EVENT_TYPE.BATTERY_EVENT -> this.handleEventBattery(events?.StatusEventData?.BatteryData)
            STATUS_EVENT_TYPE.CRADLE_EVENT -> this.handleEventCradle(events?.StatusEventData?.cradleData)
            STATUS_EVENT_TYPE.WPA_EVENT -> this.handleEventWPA(events?.StatusEventData?.WPAEventData)
            else -> return
        }
    }
    override fun eventReadNotify(e: RfidReadEvents) {
        val readTags = reader.Actions.getReadTags(100)
        if (readTags != null) {
            val readTagsList = readTags.toList()
            val tagReadGroup = readTagsList.groupBy { it.tagID }.toMutableMap()

            var tag = RFIDReaderTag()
            for (tagKey in tagReadGroup.keys) {
                tag.key = tagKey
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
                    listener?.onTagRead(this, tag)
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
        } catch (ex: Exception) {
            listener?.onRFIDReaderError(this, ex)
        }
    }
    //********************************************************************************************//
    private fun handleReaderConnect(reader: RFIDReader?) {
        startTimer()
        listener?.onRFIDReaderConnect(this, reader)
    }

    private fun handleReaderDisconnect(reader: RFIDReader?) {
        stopTimer()
        listener?.onRFIDReaderDisconnect(this, reader)
    }

    //********************************************************************************************//
    private fun handleEventGPI(data: Events.GPIEventData?) {
        data?.let { data ->
            Log.d("GPIEventData", "${data.gpiEventState} ${data.gpiPort}")
        }
    }

    private fun handleEventInventoryStart(data: Events.InventoryStartEventData?) {
        data?.let { data ->
            Log.d("InventoryStartEventData", "${data.toString()}")
        }
    }

    private fun handleEventInventoryStop(data: Events.InventoryStopEventData?) {
        data?.let { data ->
            Log.d("InventoryStopEventData", "${data.toString()}")
        }
    }

    private fun handleEventDisconnection(data: Events.DisconnectionEventData?) {
        data?.let { data ->
            handleReaderDisconnect(reader)
            Log.d("DisconnectionEventData", "${data.toString()}")
        }
    }

    private fun handleEventBufferFullWarning(data: Events.BufferFullWarningEventData?) {
        data?.let { data ->
            Log.d("BufferFullWarningEventData", "${data.toString()}")
        }
    }

    private fun handleEventException(data: Events.ReaderExceptionEventData?) {
        data?.let { data ->
            Log.d("ReaderExceptionEventData", "${data.readerExceptionEventInfo}")
        }
    }

    private fun handleEventTrigger(data: Events.HandheldTriggerEventData?) {
        data?.let { data ->
            if (data.handheldTriggerType == HANDHELD_TRIGGER_TYPE.HANDHELD_TRIGGER_RFID)
                if (data.handheldEvent === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
                    listener?.onTriggerStateChange(this, RFIDReaderTriggerState.down)
                } else if (data.handheldEvent === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                    listener?.onTriggerStateChange(this, RFIDReaderTriggerState.up)
                }
        }
    }

    private fun handleEventTemperatureAlarm(data: Events.TemperatureAlarmData?) {
        data?.let { data ->
            Log.d(
                "TemperatureAlarmData",
                "${data.alarmLevel} ${data.currentTemperature} ${data.cause}"
            )
        }
    }

    private fun handleEventOperationEndSummary(data: Events.OperationEndSummaryData?) {
        data?.let { data ->
            Log.d("OperationEndSummaryData", "${data.totalRounds} ${data.totalTags}")
        }
    }

    private fun handleEventBatchMode(data: Events.BatchModeEventData?) {
        data?.let { data ->
            Log.d("BatchModeEventData", "${data._RepeatTrigger}")
        }
    }

    private fun handleEventInfo(data: Events.InfoData?) {
        data?.let { data ->
            Log.d("InfoData", "${data.cause}")
        }
    }

    private fun handleEventPower(data: Events.PowerData?) {
        data?.let { data ->
            Log.d("PowerData", "${data.power} ${data.current} ${data.voltage} ${data.cause}")
        }
    }

    private fun handleEventBattery(data: Events.BatteryData?) {
        data?.let { data ->
            if (data.charging != this.power.isCharging)
                listener?.onRFIDReaderCharging(context, !data.charging)
            this.power.initialize(data.level, data.charging, data.cause)
            listener?.onRFIDReaderBattery(context, data.level, data.charging, data.cause)
        }
    }

    private fun handleEventCradle(data: Events.CradleData?) {
        data?.let { data ->
            this.power.isOnCradle = data.isOnCradle
            listener?.onRFIDCradleChange(context, data.isOnCradle, data.cause)
        }
    }

    private fun handleEventWPA(data: Events.WPAEventData?) {
        data?.let { data ->
            data.m_ssid
            Log.d("WPAEventData", "${data.m_ssid} ${data.m_ssid} ${data.type}")
        }
    }
    /**********************************************************************************************/
    object HexToAscii {
        val EXTENDED_ASCII_CHAR = charArrayOf(
            0x00C7.toChar(), 0x00FC.toChar(), 0x00E9.toChar(), 0x00E2.toChar(), 0x00E4.toChar(), 0x00E0.toChar(), 0x00E5.toChar(), 0x00E7.toChar(),
            0x00EA.toChar(), 0x00EB.toChar(), 0x00E8.toChar(), 0x00EF.toChar(), 0x00EE.toChar(), 0x00EC.toChar(), 0x00C4.toChar(), 0x00C5.toChar(),
            0x00C9.toChar(), 0x00E6.toChar(), 0x00C6.toChar(), 0x00F4.toChar(), 0x00F6.toChar(), 0x00F2.toChar(), 0x00FB.toChar(), 0x00F9.toChar(),
            0x00FF.toChar(), 0x00D6.toChar(), 0x00DC.toChar(), 0x00A2.toChar(), 0x00A3.toChar(), 0x00A5.toChar(), 0x20A7.toChar(), 0x0192.toChar(),
            0x00E1.toChar(), 0x00ED.toChar(), 0x00F3.toChar(), 0x00FA.toChar(), 0x00F1.toChar(), 0x00D1.toChar(), 0x00AA.toChar(), 0x00BA.toChar(),
            0x00BF.toChar(), 0x2310.toChar(), 0x00AC.toChar(), 0x00BD.toChar(), 0x00BC.toChar(), 0x00A1.toChar(), 0x00AB.toChar(), 0x00BB.toChar(),
            0x2591.toChar(), 0x2592.toChar(), 0x2593.toChar(), 0x2502.toChar(), 0x2524.toChar(), 0x2561.toChar(), 0x2562.toChar(), 0x2556.toChar(),
            0x2555.toChar(), 0x2563.toChar(), 0x2551.toChar(), 0x2557.toChar(), 0x255D.toChar(), 0x255C.toChar(), 0x255B.toChar(), 0x2510.toChar(),
            0x2514.toChar(), 0x2534.toChar(), 0x252C.toChar(), 0x251C.toChar(), 0x2500.toChar(), 0x253C.toChar(), 0x255E.toChar(), 0x255F.toChar(),
            0x255A.toChar(), 0x2554.toChar(), 0x2569.toChar(), 0x2566.toChar(), 0x2560.toChar(), 0x2550.toChar(), 0x256C.toChar(), 0x2567.toChar(),
            0x2568.toChar(), 0x2564.toChar(), 0x2565.toChar(), 0x2559.toChar(), 0x2558.toChar(), 0x2552.toChar(), 0x2553.toChar(), 0x256B.toChar(),
            0x256A.toChar(), 0x2518.toChar(), 0x250C.toChar(), 0x2588.toChar(), 0x2584.toChar(), 0x258C.toChar(), 0x2590.toChar(), 0x2580.toChar(),
            0x03B1.toChar(), 0x00DF.toChar(), 0x0393.toChar(), 0x03C0.toChar(), 0x03A3.toChar(), 0x03C3.toChar(), 0x00B5.toChar(), 0x03C4.toChar(),
            0x03A6.toChar(), 0x0398.toChar(), 0x03A9.toChar(), 0x03B4.toChar(), 0x221E.toChar(), 0x03C6.toChar(), 0x03B5.toChar(), 0x2229.toChar(),
            0x2261.toChar(), 0x00B1.toChar(), 0x2265.toChar(), 0x2264.toChar(), 0x2320.toChar(), 0x2321.toChar(), 0x00F7.toChar(), 0x2248.toChar(),
            0x00B0.toChar(), 0x2219.toChar(), 0x00B7.toChar(), 0x221A.toChar(), 0x207F.toChar(), 0x00B2.toChar(), 0x25A0.toChar(), 0x00A0.toChar()
        )
        fun convert(tag: String?): String? {
            return hex2Ascii(tag)
        }

        fun isDataInHex(tagID: String): Boolean {
            val hex = tagID
            return if (tagID.startsWith("'") && tagID.endsWith("'")) false else true
        }

        private fun hex2Ascii(tagID: String?): String? {
            return if (tagID != null && tagID != "") {
                val hex: String = tagID
                val n = hex.length
                if (n % 2 > 0) {
                    return tagID
                }
                var sb = java.lang.StringBuilder(n / 2)
                try {
                    sb = java.lang.StringBuilder(n / 2 + 2)
                    //prefexing the ascii representation with a single quote
                    sb.append("'")
                    var i = 0
                    while (i < n) {
                        val a = hex[i]
                        val b = hex[i + 1]
                        val c = (hexToInt(a) shl 4 or hexToInt(b)).toChar()
                        if (hexToInt(a) <= 7 && hexToInt(b) <= 0xf && c.code > 0x20 && c.code <= 0x7f) {
                            sb.append(c)
                        } else if (hexToInt(a) >= 8 && hexToInt(b) <= 0xf && c.code >= 0x80 && c.code <= 0xff) {
                            sb.append(EXTENDED_ASCII_CHAR[c.code - 0x7F])
                        } else {
                            sb.append(' ')
                        }
                        i += 2
                    }
                } catch (ex: java.lang.IllegalArgumentException) {
                    return tagID
                } catch (ex: ArrayIndexOutOfBoundsException) {
                    return tagID
                }
                sb.append("'")
                sb.toString()
            } else tagID
        }

        private fun hexToInt(ch: Char): Int {
            if ('a' <= ch && ch <= 'f') {
                return ch.code - 'a'.code + 10
            }
            if ('A' <= ch && ch <= 'F') {
                return ch.code - 'A'.code + 10
            }
            if ('0' <= ch && ch <= '9') {
                return ch.code - '0'.code
            }
            throw java.lang.IllegalArgumentException(ch.toString())
        }
    }
    object AsciiToHEX {
        fun convert(data: String?): String? {
            return data?.let { data ->
                if (HexToAscii.isDataInHex(data))
                    data
                var data = data.substring(1, data.length - 1)
                val ch = data.toByteArray()
                val builder = StringBuilder()
                for (c in ch) {
                    builder.append(Integer.toHexString(c.toInt()))
                }
                return builder.toString()
            }
        }
    }
    /**********************************************************************************************/
}