package com.loopers.infrastructure.event

import com.loopers.support.event.UserActionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserActionEventHandler(
    @Qualifier("eventCoroutineScope")
    private val coroutineScope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    private val allowedMetadataKeys = setOf("likeId", "orderAmount", "couponId", "brandId", "sortType", "page")

    private fun sanitizeMetadata(metadata: Map<String, String>): Map<String, String> =
        metadata
            .filterKeys { it in allowedMetadataKeys }
            .mapValues { (_, v) ->
                if (v.length <= 200) v else v.take(200) + "…"
            }

    @EventListener
    fun handleUserAction(event: UserActionEvent) {
        // 사용자 행동 로그 적재
        coroutineScope.launch {
            logger.info(
                "USER_ACTION: userId={}, action={}, entity={}:{}, metadata={}, at={}",
                event.userId,
                event.actionType,
                event.targetEntityType,
                event.targetEntityId ?: "N/A",
                sanitizeMetadata(event.metadata),
                event.occurredAt
            )
        }
    }
}
