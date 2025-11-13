package com.loopers.domain.user

interface UserRepository {
    fun save(user: User): User
    fun findBy(id: Long): User?
    fun findBy(userId: String): User?
}
