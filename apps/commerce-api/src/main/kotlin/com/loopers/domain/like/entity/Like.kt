package com.loopers.domain.like.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.like.vo.LikeTarget
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "likes",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_like_user_target",
            columnNames = ["user_id", "target_id", "type"],
        ),
    ],
)
class Like protected constructor(
    userId: Long,
    target: LikeTarget,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Embedded
    var target: LikeTarget = target
        protected set

    companion object {
        fun create(userId: Long, targetId: Long, type: LikeTarget.Type): Like {
            return Like(userId, LikeTarget.create(type, targetId))
        }
    }
}
