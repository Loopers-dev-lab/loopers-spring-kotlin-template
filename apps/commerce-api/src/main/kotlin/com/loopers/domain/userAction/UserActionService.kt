package com.loopers.domain.userAction

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserActionService(private val userActionRepository: UserActionRepository) {

    @Transactional
    fun save(userId: Long, actionType: ActionType, targetType: TargetType, targetId: Long) {
        val userAction =
                UserActionModel.create(
                        userId = userId,
                        actionType = actionType,
                        targetType = targetType,
                        targetId = targetId,
                )
        userActionRepository.save(userAction)
    }
}
