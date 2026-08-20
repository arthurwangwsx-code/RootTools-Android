package com.arthur.roottools.data

import com.arthur.roottools.model.AppInventoryFilter
import com.arthur.roottools.model.AppInventoryItem
import com.arthur.roottools.model.AppInventorySort

/** Android-free filtering/sorting policy so list behavior is deterministic and unit-testable. */
object AppInventoryQuery {
    fun apply(
        source: List<AppInventoryItem>,
        query: String,
        filter: AppInventoryFilter,
        sort: AppInventorySort,
        descending: Boolean = false,
    ): List<AppInventoryItem> {
        val needle = query.trim().lowercase()
        val filtered = source.asSequence()
            .filter { app ->
                needle.isBlank() || app.label.lowercase().contains(needle) || app.packageName.lowercase().contains(needle)
            }
            .filter { app ->
                when (filter) {
                    AppInventoryFilter.ALL -> true
                    AppInventoryFilter.USER -> !app.systemApp
                    AppInventoryFilter.SYSTEM -> app.systemApp
                    AppInventoryFilter.RUNNING -> app.running
                    AppInventoryFilter.FROZEN -> !app.enabled
                    AppInventoryFilter.DEBUGGABLE -> app.debuggable
                }
            }
            .toList()

        val comparator = when (sort) {
            AppInventorySort.LABEL -> compareBy<AppInventoryItem> { it.label.lowercase() }.thenBy { it.packageName }
            AppInventorySort.PACKAGE -> compareBy<AppInventoryItem> { it.packageName }
            AppInventorySort.LAST_UPDATE -> compareByDescending<AppInventoryItem> { it.lastUpdateTimeMs }.thenBy { it.label.lowercase() }
            AppInventorySort.TARGET_SDK -> compareByDescending<AppInventoryItem> { it.targetSdk }.thenBy { it.label.lowercase() }
        }
        return if (descending) filtered.sortedWith(comparator.reversed()) else filtered.sortedWith(comparator)
    }
}
