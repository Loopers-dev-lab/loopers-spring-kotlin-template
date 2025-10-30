package com.loopers.infrastructure.user

import com.loopers.domain.user.UserModel
import com.loopers.domain.user.vo.LoginId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserJpaRepository : JpaRepository<UserModel, Long> {
    fun findByLoginId(loginId: LoginId): UserModel?

    fun existsByLoginId(loginId: LoginId): Boolean

    @Query("SELECT u FROM UserModel u WHERE u.loginId.value = :loginId")
    fun findByLoginIdValue(loginId: String): UserModel?
}
