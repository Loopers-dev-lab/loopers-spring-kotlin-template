package com.loopers.domain.user

interface UserRepository {
    fun find(id: Long): User?

    fun findByUserName(userName: String): User?

    fun save(user: User): User
}
