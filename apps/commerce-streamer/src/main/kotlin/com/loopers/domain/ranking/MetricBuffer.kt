package com.loopers.domain.ranking

import java.math.BigDecimal
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

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }

    private fun getOrCreate(key: AggregationKey): MutableCounts {
        // CAS loop: poll()과의 경합 조건 방지
        // bufferRef.get() 후 poll()이 버퍼를 교체하면 증분이 유실될 수 있으므로,
        // 연산 후 버퍼가 동일한지 확인하고 교체되었으면 새 버퍼에 재시도
        repeat(MAX_RETRY_COUNT) {
            val currentBuffer = bufferRef.get()
            val counts = currentBuffer.computeIfAbsent(key) { MutableCounts() }
            if (bufferRef.get() === currentBuffer) {
                return counts
            }
        }
        throw IllegalStateException("버퍼 경합이 $MAX_RETRY_COUNT 회 연속 발생. poll() 호출 빈도를 확인하세요.")
    }

    fun incrementView(key: AggregationKey) {
        getOrCreate(key).increment(MetricType.VIEW)
    }

    fun incrementLikeCreated(key: AggregationKey) {
        getOrCreate(key).increment(MetricType.LIKE_CREATED)
    }

    fun incrementLikeCanceled(key: AggregationKey) {
        getOrCreate(key).increment(MetricType.LIKE_CANCELED)
    }

    fun incrementOrderPaid(key: AggregationKey, orderAmount: BigDecimal) {
        getOrCreate(key).apply {
            increment(MetricType.ORDER_PAID)
            addOrderAmount(orderAmount)
        }
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
