package com.arthur.nfclab.hce

import android.content.Context
import android.util.Base64
import androidx.core.content.edit

/**
 * Stores only synthetic lab keys. Real access-control credential keys must not be persisted here.
 */
class TestKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("iso_dep_lab_keys", Context.MODE_PRIVATE)

    fun put(alias: String, key: ByteArray) {
        require(alias.matches(Regex("[A-Za-z0-9_.-]{1,48}")))
        require(key.size in setOf(16, 24, 32))
        prefs.edit { putString(alias, Base64.encodeToString(key, Base64.NO_WRAP)) }
    }

    fun get(alias: String): ByteArray? = prefs.getString(alias, null)?.let {
        runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
    }

    fun remove(alias: String) {
        prefs.edit { remove(alias) }
    }
}

