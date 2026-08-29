package com.aibox.backgroundserver.runtime

import com.aibox.backgroundserver.engine.BackgroundEngine

class EngineSupervisor(engines: List<BackgroundEngine>) {
    private val engines = engines.associateBy { it.engineId }

    fun start(engineId: String): Result<Unit> =
        engines[engineId]?.start() ?: Result.failure(IllegalArgumentException("Unknown engine: $engineId"))

    fun stop(engineId: String): Result<Unit> =
        engines[engineId]?.stop() ?: Result.failure(IllegalArgumentException("Unknown engine: $engineId"))

    fun isRunning(engineId: String): Boolean = engines[engineId]?.isRunning() == true

    fun runningEngineIds(): Set<String> = engines.values.filter { it.isRunning() }.mapTo(linkedSetOf()) { it.engineId }
}
