package com.loopers.domain.event.handler

import com.loopers.domain.event.StockDepletedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 재고 이벤트 핸들러
 * 트랜잭션 커밋 후 이벤트를 수신하여 StockEventProcessor로 위임
 *
 * 역할 분리:
 * - StockEventHandler: @TransactionalEventListener로 이벤트 수신
 * - StockEventProcessor: @Async + @Retryable로 실제 비동기 처리
 *
 * 이렇게 분리하는 이유:
 * @Async와 @Retryable을 같은 메서드에 선언하면 @Async 프록시가 먼저 실행되어
 * 새 스레드에서 메서드를 실행하므로 @Retryable 프록시가 적용되지 않음.
 * 별도 Spring Bean의 public 메서드로 분리하여 두 프록시가 모두 정상 작동하도록 함.
 */
@Component
class StockEventHandler(private val stockEventProcessor: StockEventProcessor) {
    /**
     * 재고 소진 이벤트 수신 후 비동기 처리기로 위임
     * 트랜잭션 커밋 후 실행되어 메인 트랜잭션과 분리됨
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleStockDepleted(event: StockDepletedEvent) {
        stockEventProcessor.processStockDepleted(event)
    }
}
