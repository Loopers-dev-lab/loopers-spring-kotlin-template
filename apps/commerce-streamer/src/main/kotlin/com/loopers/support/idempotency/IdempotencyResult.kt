package com.loopers.support.idempotency

/**
 * IdempotencyResult - 멱등성 키 저장 결과를 나타내는 sealed interface
 *
 * EventHandledRepository.save()의 반환 타입으로 사용됨.
 * 저장 실패는 예외를 발생시키지 않고 RecordFailed를 반환하여
 * 멱등성 기록 실패가 비즈니스 로직에 영향을 주지 않도록 함.
 */
sealed interface IdempotencyResult {
    /**
     * 멱등성 키가 성공적으로 저장됨
     */
    data object Recorded : IdempotencyResult

    /**
     * 저장 실패 (중복 키 또는 기타 예외)
     * 에러는 내부적으로 로깅되며, 호출자는 결과를 무시할 수 있음
     */
    data object RecordFailed : IdempotencyResult
}
