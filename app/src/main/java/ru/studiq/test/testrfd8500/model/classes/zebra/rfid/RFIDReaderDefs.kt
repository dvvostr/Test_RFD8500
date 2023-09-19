package ru.studiq.test.testrfd8500.model.classes.zebra.rfid

enum class  RFIDReaderTriggerAction(var value: Int) {
    unassigned(-1),
    barcode(1),
    RFID(2)
}
enum class  RFIDReaderTriggerState(var value: Int) {
    unassigned(-1),
    up(0),
    down(1)
}

enum class  RFIDReaderMultipleReadType(var value: Int) {
    unassigned(-1),
    single(0),
    multiple(1)
}

open class RFIDReaderTag(var epc: String? = "", var tid: String? = "", var usr: String = "") {
    fun asString(): String {
        return "EPC ${epc}\nTID ${tid}\nUSER ${usr}\n"
    }
}
