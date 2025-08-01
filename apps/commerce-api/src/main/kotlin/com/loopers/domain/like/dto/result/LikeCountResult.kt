package com.loopers.domain.like.dto.result

import com.loopers.domain.common.PageResult
import com.loopers.domain.like.entity.LikeCount
import com.loopers.domain.like.vo.LikeTarget.Type
import org.springframework.data.domain.Page
import java.time.ZonedDateTime

class LikeCountResult {
    data class LikeCountDetail(
        val id: Long,
        val targetId: Long,
        val type: Type,
        val count: Long,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(likeCount: LikeCount): LikeCountDetail {
                return LikeCountDetail(
                    likeCount.id,
                    likeCount.target.targetId,
                    likeCount.target.type,
                    likeCount.count,
                    likeCount.createdAt,
                    likeCount.updatedAt,
                )
            }
        }
    }

    data class LikeCountDetails(
        val likeCounts: List<LikeCountDetail>,
    ) {
        companion object {
            fun from(likeCounts: List<LikeCount>): LikeCountDetails {
                return LikeCountDetails(
                    likeCounts.map { LikeCountDetail.from(it) },
                )
            }
        }
    }

    data class LikeCountPageDetails(
        val likeCounts: PageResult<LikeCountDetail>,
    ) {
        companion object {
            fun from(likeCounts: Page<LikeCount>): LikeCountPageDetails {
                return LikeCountPageDetails(
                    PageResult.from(likeCounts) { LikeCountDetail.from(it) },
                )
            }
        }
    }
}
