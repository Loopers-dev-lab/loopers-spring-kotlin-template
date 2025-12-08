package com.loopers.domain.integration

import com.loopers.application.user.event.UserActivityEvent

interface UserActivityPublisher {
    fun sendUserActivity(event: UserActivityEvent.UserActivity)
}
