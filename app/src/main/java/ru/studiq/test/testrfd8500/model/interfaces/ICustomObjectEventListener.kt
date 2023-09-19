package ru.studiq.test.testrfd8500.model.interfaces

import android.content.Context

interface ICustomObjectEventListener {
    open class EventMessage(var code: Int, var msg: String?, var data: Any?) {}
    abstract fun onSuccess(sender: Context?, data: Any?, msg: EventMessage? = null)
    open fun onComplite(sender: Context?, result: Any? = null) { }
    open fun onEmpty(sender: Context?) { }
    open fun onError(sender: Context?, msg: EventMessage?) { }
}
