package com.loopers.infrastructure.userAction

import com.loopers.domain.userAction.UserActionModel
import com.loopers.domain.userAction.UserActionRepository
import org.springframework.stereotype.Component

@Component
class UserActionRepositoryImpl(private val userActionJpaRepository: UserActionJpaRepository) : UserActionRepository {

    override fun save(userAction: UserActionModel): UserActionModel = userActionJpaRepository.saveAndFlush(userAction)
}
