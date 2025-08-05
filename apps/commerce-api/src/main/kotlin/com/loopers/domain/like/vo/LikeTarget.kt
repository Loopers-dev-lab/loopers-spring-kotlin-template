package com.loopers.domain.like.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Embeddable
class LikeTarget protected constructor(
    type: Type,
    targetId: Long,
) {
    @Column(name = "target_id", nullable = false)
    var targetId: Long = targetId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: Type = type
        protected set

    enum class Type {
        PRODUCT,
        BRAND,
    }

    companion object {
        fun create(type: Type, targetId: Long): LikeTarget {
            return LikeTarget(type, targetId)
        }
    }
}
