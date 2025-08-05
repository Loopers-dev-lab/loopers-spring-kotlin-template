package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeCountRepository
import com.loopers.domain.like.entity.LikeCount
import com.loopers.domain.like.vo.LikeTarget.Type
import org.springframework.stereotype.Component

@Component
class LikeCountRepositoryImpl(
    private val userCountJpaRepository: LikeCountJpaRepository,
) : LikeCountRepository {
    override fun findCountByTargetIdAndType(
        targetId: Long,
        type: Type,
    ): LikeCount? {
        return userCountJpaRepository.findByTarget_TargetIdAndTarget_Type(targetId, type)
    }

    override fun findAllCountByTargetIdAndType(
        targetIds: List<Long>,
        type: Type,
    ): List<LikeCount> {
        return userCountJpaRepository.findAllByTarget_TargetIdInAndTarget_Type(targetIds, type)
    }

    override fun save(likeCount: LikeCount): LikeCount {
        return userCountJpaRepository.save(likeCount)
    }
}
