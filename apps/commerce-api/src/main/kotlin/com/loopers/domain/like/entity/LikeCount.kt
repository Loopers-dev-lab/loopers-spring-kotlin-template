package com.loopers.domain.like.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.like.vo.LikeTarget
import com.loopers.domain.like.vo.LikeTarget.Type
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "like_count",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_like_count_target",
            columnNames = ["target_id", "type"],
        ),
    ],
)
class LikeCount protected constructor(
    target: LikeTarget,
    count: Long,
) : BaseEntity() {
    @Embedded
    var target: LikeTarget = target
        protected set

    @Column(name = "count", nullable = false)
    var count: Long = count
        protected set

    fun increase() {
        count++
    }

    fun decrease() {
        count--
    }

    companion object {
        fun create(targetId: Long, type: Type, count: Long): LikeCount {
            return LikeCount(LikeTarget.create(type, targetId), count)
        }
    }
}
