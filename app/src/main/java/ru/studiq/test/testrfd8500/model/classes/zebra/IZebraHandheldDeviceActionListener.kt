package ru.studiq.test.testrfd8500.model.classes.zebra

interface IZebraHandheldDeviceActionListener {
    fun onZebraHandheldDeviceActionSuccess(sender: ZebraHandheldDevice?, data: Any? = null) {}
    fun onZebraHandheldDeviceActionError(sender: ZebraHandheldDevice?, msg: ZebraHandheldDeviceMessage? = null) {}
    fun onZebraHandheldDeviceActionComplite(sender: ZebraHandheldDevice?) {}

}