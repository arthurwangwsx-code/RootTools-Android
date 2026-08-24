package com.arthur.roottools.feature.adgovernance.ui

import com.arthur.roottools.feature.adgovernance.model.AdGovernanceSnapshot

data class AdGovernanceUiState(
    val loading: Boolean = true,
    val snapshot: AdGovernanceSnapshot = AdGovernanceSnapshot(),
    val error: String? = null,
)
