package com.loopers.domain.integration

import com.loopers.domain.user.UserActivityEvent

interface UserActivityPublisher {
    fun sendUserActivity(event: UserActivityEvent.UserActivity)
}
