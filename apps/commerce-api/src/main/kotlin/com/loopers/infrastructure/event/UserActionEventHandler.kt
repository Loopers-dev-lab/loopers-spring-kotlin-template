package com.loopers.infrastructure.event

import com.loopers.support.event.UserActionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserActionEventHandler(
    @Qualifier("eventCoroutineScope")
    private val coroutineScope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUserAction(event: UserActionEvent) {
        // 사용자 행동 로그 적재
        coroutineScope.launch {
            logger.info(
                "USER_ACTION: userId={}, action={}, entity={}:{}, metadata={}, at={}",
                event.userId,
                event.actionType,
                event.targetEntityType,
                event.targetEntityId ?: "N/A",
                event.metadata,
                event.occurredAt
            )
        }
    }
}
