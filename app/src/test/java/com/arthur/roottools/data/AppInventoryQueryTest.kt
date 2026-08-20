package com.arthur.roottools.data

import com.arthur.roottools.model.AppInventoryFilter
import com.arthur.roottools.model.AppInventoryItem
import com.arthur.roottools.model.AppInventorySort
import org.junit.Assert.assertEquals
import org.junit.Test

class AppInventoryQueryTest {
    private val apps = listOf(
        AppInventoryItem(
            packageName = "com.example.alpha",
            label = "Alpha Player",
            targetSdk = 35,
            lastUpdateTimeMs = 200,
            running = true,
        ),
        AppInventoryItem(
            packageName = "com.vendor.system",
            label = "Vendor Agent",
            targetSdk = 34,
            lastUpdateTimeMs = 300,
            systemApp = true,
        ),
        AppInventoryItem(
            packageName = "com.example.beta",
            label = "Beta Notes",
            targetSdk = 36,
            lastUpdateTimeMs = 100,
            enabled = false,
            debuggable = true,
        ),
    )

    @Test
    fun `search matches label and package`() {
        assertEquals(
            listOf("com.example.alpha"),
            AppInventoryQuery.apply(apps, "player", AppInventoryFilter.ALL, AppInventorySort.LABEL).map { it.packageName },
        )
        assertEquals(
            listOf("com.example.beta", "com.example.alpha"),
            AppInventoryQuery.apply(apps, "example", AppInventoryFilter.ALL, AppInventorySort.TARGET_SDK).map { it.packageName },
        )
    }

    @Test
    fun `filters expose core governance states`() {
        assertEquals(2, AppInventoryQuery.apply(apps, "", AppInventoryFilter.USER, AppInventorySort.LABEL).size)
        assertEquals("com.vendor.system", AppInventoryQuery.apply(apps, "", AppInventoryFilter.SYSTEM, AppInventorySort.LABEL).single().packageName)
        assertEquals("com.example.alpha", AppInventoryQuery.apply(apps, "", AppInventoryFilter.RUNNING, AppInventorySort.LABEL).single().packageName)
        assertEquals("com.example.beta", AppInventoryQuery.apply(apps, "", AppInventoryFilter.FROZEN, AppInventorySort.LABEL).single().packageName)
        assertEquals("com.example.beta", AppInventoryQuery.apply(apps, "", AppInventoryFilter.DEBUGGABLE, AppInventorySort.LABEL).single().packageName)
    }

    @Test
    fun `sort policies are deterministic`() {
        assertEquals(
            listOf("com.vendor.system", "com.example.alpha", "com.example.beta"),
            AppInventoryQuery.apply(apps, "", AppInventoryFilter.ALL, AppInventorySort.LAST_UPDATE).map { it.packageName },
        )
        assertEquals(
            listOf("com.example.beta", "com.example.alpha", "com.vendor.system"),
            AppInventoryQuery.apply(apps, "", AppInventoryFilter.ALL, AppInventorySort.TARGET_SDK).map { it.packageName },
        )
    }
}
