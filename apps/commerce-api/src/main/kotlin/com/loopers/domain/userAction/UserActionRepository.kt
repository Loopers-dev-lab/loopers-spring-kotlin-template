package com.loopers.domain.userAction

interface UserActionRepository {
    fun save(userAction: UserActionModel): UserActionModel
}
