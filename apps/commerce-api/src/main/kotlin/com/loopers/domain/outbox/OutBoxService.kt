package com.loopers.domain.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutBoxService(
        private val outBoxRepository: OutBoxRepository,
        private val objectMapper: ObjectMapper
) {
    @Transactional
    fun enqueue(
            eventId: String,
            topic: String,
            payload: Any,
            eventType: OutBoxEventType
    ): OutBoxModel {
        val outbox =
                OutBoxModel.create(
                        eventId,
                        topic,
                        objectMapper.writeValueAsString(payload),
                        eventType
                )
        return outBoxRepository.save(outbox)
    }

    @Transactional
    fun markAsPublished(eventId: String) {
        val outbox =
                outBoxRepository.findByEventId(eventId)
                        ?: throw CoreException(ErrorType.NOT_FOUND, "이벤트가 존재하지 않습니다.")
        outbox.markAsPublished()
        outBoxRepository.save(outbox)
    }

    @Transactional
    fun markAsFailed(eventId: String) {
        val outbox =
                outBoxRepository.findByEventId(eventId)
                        ?: throw CoreException(ErrorType.NOT_FOUND, "이벤트가 존재하지 않습니다.")
        outbox.markAsFailed()
        outBoxRepository.save(outbox)
    }
}
