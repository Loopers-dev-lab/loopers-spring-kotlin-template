package com.loopers.domain.user

interface UserRepository {
    fun save(user: User): User
    fun exist(id: Long): Boolean
    fun findBy(id: Long): User?
    fun findBy(userId: String): User?
}
