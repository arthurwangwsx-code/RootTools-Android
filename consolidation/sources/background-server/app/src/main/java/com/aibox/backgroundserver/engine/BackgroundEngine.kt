package com.aibox.backgroundserver.engine

interface BackgroundEngine {
    val engineId: String
    fun isRunning(): Boolean
    fun start(): Result<Unit>
    fun stop(): Result<Unit>
}
