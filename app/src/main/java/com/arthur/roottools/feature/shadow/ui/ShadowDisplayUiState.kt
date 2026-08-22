package com.arthur.roottools.feature.shadow.ui

import androidx.annotation.StringRes
import com.arthur.roottools.model.PrivilegeRouteBackend
import com.arthur.roottools.model.ShadowDisplayStatus

data class ShadowDisplayUiState(
    val status: ShadowDisplayStatus = ShadowDisplayStatus(),
    val loading: Boolean = false,
    val actionRunning: Boolean = false,
    val lastBackend: PrivilegeRouteBackend = PrivilegeRouteBackend.NONE,
    val previewJpeg: ByteArray? = null,
    @StringRes val messageRes: Int? = null,
    @StringRes val errorRes: Int? = null,
    val errorDetail: String? = null,
)
