package com.loopers.domain.user

interface UserRepository {
    fun save(user: User): User
    fun exist(userId: String): Boolean
    fun findBy(userId: String): User?
}
