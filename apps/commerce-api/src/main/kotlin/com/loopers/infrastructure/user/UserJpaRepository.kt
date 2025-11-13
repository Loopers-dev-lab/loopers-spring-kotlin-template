package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserId
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<User, Long> {
    fun findByUserId(userId: UserId): User?
}
