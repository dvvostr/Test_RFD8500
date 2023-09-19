package ru.studiq.test.testrfd8500.model.classes.zebra.barcode

interface IBarcodeScannedListener {
    fun onBarcodeScan(barcode : String?)
}