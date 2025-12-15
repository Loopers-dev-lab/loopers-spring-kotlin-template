package com.loopers.infrastructure.userAction

import com.loopers.domain.userAction.UserActionModel
import org.springframework.data.jpa.repository.JpaRepository

interface UserActionJpaRepository : JpaRepository<UserActionModel, Long>
