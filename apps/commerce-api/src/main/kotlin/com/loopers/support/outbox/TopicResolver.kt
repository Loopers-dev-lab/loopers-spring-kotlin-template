package com.loopers.support.outbox

object TopicResolver {
    fun resolve(eventType: String): String {
        val domain = eventType.split(".").getOrNull(1)
            ?: throw IllegalArgumentException("Invalid event type format: $eventType")
        return "$domain-events"
    }
}
