package com.loopers.domain.like.dto.command

import com.loopers.domain.like.entity.Like
import com.loopers.domain.like.vo.LikeTarget.Type

class LikeCommand {
    data class AddLike(
        val userId: Long,
        val targetId: Long,
        val type: Type,
    ) {
        fun toEntity(): Like {
            return Like.create(userId, targetId, type)
        }
    }

    data class RemoveLike(
        val userId: Long,
        val targetId: Long,
        val type: Type,
    )
}
