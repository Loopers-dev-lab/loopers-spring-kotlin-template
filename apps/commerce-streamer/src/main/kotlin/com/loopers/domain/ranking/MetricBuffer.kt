package com.loopers.domain.ranking

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * 메트릭 축적을 위한 스레드 안전 버퍼
 *
 * - ConcurrentHashMap으로 동시 쓰기 지원
 * - AtomicReference로 원자적 버퍼 스왑 (lock-free drain)
 * - poll() 시 새 버퍼로 교체하여 쓰기 유실 방지
 */
class MetricBuffer {
    private val bufferRef = AtomicReference(ConcurrentHashMap<AggregationKey, MutableCounts>())

    /**
     * 메트릭을 버퍼에 축적
     */
    fun accumulate(key: AggregationKey, action: MutableCounts.() -> Unit) {
        bufferRef.get()
            .computeIfAbsent(key) { MutableCounts() }
            .apply(action)
    }

    /**
     * 버퍼를 원자적으로 비우고 내용물 반환
     * Queue.poll()과 유사한 시맨틱 - 가져가면서 비움
     */
    fun poll(): Map<AggregationKey, MutableCounts> {
        return bufferRef.getAndSet(ConcurrentHashMap())
    }

    /**
     * 현재 버퍼 항목 수
     */
    fun size(): Int = bufferRef.get().size

    /**
     * 버퍼가 비어있는지 확인
     */
    fun isEmpty(): Boolean = bufferRef.get().isEmpty()

    /**
     * 현재 상태의 읽기 전용 스냅샷 (버퍼 비우지 않음)
     * 디버깅/테스트용
     */
    fun snapshot(): Map<AggregationKey, MutableCounts> {
        return bufferRef.get().toMap()
    }
}
