package com.loopers.event

enum class EventType(val topic: String) {
    CATALOG_EVENT(Topic.CATALOG_EVENT),
    ORDER_EVENT(Topic.ORDER_EVENT),
    LIKE_EVENT(Topic.LIKE_EVENT),
    ;

    object Topic {
        const val CATALOG_EVENT = "CATALOG_EVENT"
        const val ORDER_EVENT = "ORDER_EVENT"
        const val LIKE_EVENT = "LIKE_EVENT"
    }
}
