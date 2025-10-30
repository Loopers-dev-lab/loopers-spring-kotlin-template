package com.loopers.domain.user

import com.loopers.domain.user.vo.LoginId

interface UserRepository {
    fun save(user: UserModel): UserModel

    fun existsByLoginId(loginId: LoginId): Boolean

    fun findByLoginIdValue(loginId: String): UserModel?
}
