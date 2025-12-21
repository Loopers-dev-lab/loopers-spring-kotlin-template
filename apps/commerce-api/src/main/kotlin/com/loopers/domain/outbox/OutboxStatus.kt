package com.loopers.domain.outbox

enum class OutboxStatus(val description: String) {
    PENDING("발행 대기"),
    PUBLISHED("발행 완료"),
    FAILED("발행 실패"),
}
