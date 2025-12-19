package com.loopers.support.outbox

import java.time.Instant

data class CloudEventEnvelope(
    val id: String,
    val type: String,
    val source: String,
    val aggregateType: String,
    val aggregateId: String,
    val time: Instant,
    val payload: String,
)
