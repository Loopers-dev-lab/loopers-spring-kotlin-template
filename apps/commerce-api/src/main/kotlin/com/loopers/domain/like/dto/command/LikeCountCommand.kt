package com.loopers.domain.like.dto.command

import com.loopers.domain.like.entity.LikeCount
import com.loopers.domain.like.vo.LikeTarget.Type

class LikeCountCommand {
    data class Register(
        val targetId: Long,
        val type: Type,
        val likeCount: Long,
    ) {
        fun toEntity(): LikeCount {
            return LikeCount.create(targetId, type, likeCount)
        }
    }
}
