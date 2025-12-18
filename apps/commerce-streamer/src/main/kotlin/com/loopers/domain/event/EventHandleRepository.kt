package com.loopers.domain.event

interface EventHandleRepository {

    fun findByMessageKeyAndEventType(
        messageKey: String,
        eventType: String,
    ): EventHandleModel?

    /**
     * 이벤트 처리 기록을 저장합니다.
     *
     * @param eventHandle 저장할 이벤트 처리 기록
     * @return 저장된 EventHandleModel
     */
    fun save(eventHandle: EventHandleModel): EventHandleModel
}
