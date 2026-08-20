package com.arthur.roottools.core.ui.token

import androidx.compose.ui.unit.dp

/** Shared layout tokens. Keep feature screens from inventing near-duplicate spacing values. */
object RootToolsSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object RootToolsRadius {
    val chip = 10.dp
    val card = 18.dp
    val dialog = 24.dp
}

enum class RootToolsStatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Danger,
    Privileged,
}

enum class RootToolsRiskLevel {
    Safe,
    Caution,
    Dangerous,
    Destructive,
}
