package com.loopers.domain.event

enum class EventProcessingResult {
    SHOULD_PROCESS,
    ALREADY_HANDLED,
    OUTDATED,
}
