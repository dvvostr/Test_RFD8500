package ru.studiq.test.testrfd8500.model.classes.zebra

enum class ZebraHandheldDeviceMessageType(var value: Int) {
    unassigned(-1),
    alert(0),
    warning(1),
    confirm(2),
    error(3),
    exception(4)
}
class ZebraHandheldDeviceMessage(var type: ZebraHandheldDeviceMessageType, var msg: String?, var data: Any? = null) {
}