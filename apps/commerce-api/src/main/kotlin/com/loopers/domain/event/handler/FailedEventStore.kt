package com.loopers.domain.event.handler

import java.lang.reflect.Method

/**
 * 비동기 이벤트 처리 실패 시 실패 정보를 저장하는 인터페이스
 *
 * 역할:
 * - 재시도 후에도 실패한 이벤트 정보를 영구 저장
 * - 추후 수동 재처리나 분석을 위한 데이터 보관
 */
interface FailedEventStore {
    /**
     * 실패한 이벤트 정보를 저장
     *
     * @param method 실패한 메서드
     * @param params 메서드 파라미터 (이벤트 객체 포함)
     * @param exception 발생한 예외
     */
    fun store(method: Method, params: Array<Any>, exception: Throwable)
}
