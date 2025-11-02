package com.loopers.infrastructure.user

import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.vo.LoginId
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(private val userJpaRepository: UserJpaRepository) : UserRepository {

    override fun save(user: UserModel): UserModel = userJpaRepository.save(user)

    override fun existsByLoginId(loginId: LoginId): Boolean = userJpaRepository.existsByLoginId(loginId)

    override fun findByLoginIdValue(loginId: String): UserModel? = userJpaRepository.findByLoginIdValue(loginId)
}
