package ru.studiq.test.testrfd8500.model.classes.zebra

interface IZebraHandheldDeviceActionListener {
    fun onZebraHandheldDeviceActionSuccess(sender: Any?, data: Any? = null) {}
    fun onZebraHandheldDeviceActionError(sender: Any?, msg: ZebraHandheldDeviceMessage? = null) {}
    fun onZebraHandheldDeviceActionComplite(sender: Any?) {}

}