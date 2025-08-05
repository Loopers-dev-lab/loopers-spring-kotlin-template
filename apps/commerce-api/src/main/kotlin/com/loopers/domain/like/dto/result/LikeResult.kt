package com.loopers.domain.like.dto.result

import com.loopers.domain.common.PageResult
import com.loopers.domain.like.entity.Like
import com.loopers.domain.like.vo.LikeTarget
import org.springframework.data.domain.Page
import java.time.ZonedDateTime

class LikeResult {
    data class LikeDetail(
        val id: Long,
        val userId: Long,
        val targetId: Long,
        val type: LikeTarget.Type,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(like: Like): LikeDetail {
                return LikeDetail(like.id, like.userId, like.target.targetId, like.target.type, like.createdAt, like.updatedAt)
            }
        }
    }

    data class LikeDetails(
        val likes: List<LikeDetail>,
    ) {
        companion object {
            fun from(likes: List<Like>): LikeDetails {
                return LikeDetails(
                    likes.map { LikeDetail.from(it) },
                )
            }
        }
    }

    data class LikePageDetails(
        val likes: PageResult<LikeDetail>,
    ) {
        companion object {
            fun from(likes: Page<Like>): LikePageDetails {
                return LikePageDetails(
                    PageResult.from(likes) { LikeDetail.from(it) },
                )
            }
        }
    }
}
