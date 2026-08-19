package com.arthur.roottools.policy

import android.content.Context
import androidx.core.content.edit
import com.arthur.roottools.model.SystemActionId

class ActionFavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("action_favorites", Context.MODE_PRIVATE)

    fun read(): Set<SystemActionId> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
        .mapNotNull { value -> runCatching { SystemActionId.valueOf(value) }.getOrNull() }
        .toSet()

    fun set(action: SystemActionId, favorite: Boolean): Set<SystemActionId> {
        val updated = read().toMutableSet().apply {
            if (favorite) add(action) else remove(action)
        }
        prefs.edit { putStringSet(KEY_FAVORITES, updated.map { it.name }.toSet()) }
        return updated
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
    }
}
