package com.arthur.roottools.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticReportStore(private val context: Context) {
    fun write(text: String): File {
        val root = rootDirectory()
        root.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(root, "roottools-diagnostic-$stamp.txt").apply { writeText(text) }
    }

    fun latest(): File? = rootDirectory()
        .listFiles { file -> file.isFile && file.name.startsWith("roottools-diagnostic-") }
        ?.maxByOrNull(File::lastModified)

    private fun rootDirectory(): File =
        context.getExternalFilesDir("diagnostics") ?: File(context.filesDir, "diagnostics")
}
