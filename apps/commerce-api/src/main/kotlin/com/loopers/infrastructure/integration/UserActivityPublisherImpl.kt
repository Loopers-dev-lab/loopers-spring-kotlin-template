package com.loopers.infrastructure.integration

import com.loopers.application.user.event.UserActivityEvent
import com.loopers.domain.integration.UserActivityPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class UserActivityPublisherImpl : UserActivityPublisher {
    private val logger = LoggerFactory.getLogger(UserActivityPublisherImpl::class.java)

    override fun sendUserActivity(event: UserActivityEvent.UserActivity) {
        logger.info(
            "🔍 [UserActivity] type=${event.activityType}, userId=${event.userId}, " +
                    "targetId=${event.targetId}, timestamp=${event.timestamp}, metadata=${event.metadata}",
        )
    }
}
