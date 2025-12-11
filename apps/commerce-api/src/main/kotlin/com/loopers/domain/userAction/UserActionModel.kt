package com.loopers.domain.userAction

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "user_actions")
class UserActionModel(

    @Column
    val userId: Long,

    @Enumerated(EnumType.STRING)
    val actionType: ActionType,

    @Column
    val targetId: Long,

    @Enumerated(EnumType.STRING)
    @Column
    val targetType: TargetType,

    @Column(columnDefinition = "TEXT")
    val metadata: String? = null,
) : BaseEntity() {

    companion object {
        fun create(
            userId: Long,
            actionType: ActionType,
            targetId: Long,
            targetType: TargetType,
            metadata: String? = null,
        ): UserActionModel =
            UserActionModel(userId, actionType, targetId, targetType, metadata)
    }
}
