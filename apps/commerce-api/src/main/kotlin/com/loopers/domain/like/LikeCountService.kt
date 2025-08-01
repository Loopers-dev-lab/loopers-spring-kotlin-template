package com.loopers.domain.like

import com.loopers.domain.like.dto.command.LikeCountCommand
import com.loopers.domain.like.entity.LikeCount
import com.loopers.domain.like.vo.LikeTarget.Type
import org.springframework.stereotype.Component

@Component
class LikeCountService(
    private val likeCountRepository: LikeCountRepository,
) {
    fun getLikeCount(targetId: Long, type: Type): LikeCount {
        return likeCountRepository.findCountByTargetIdAndType(targetId, type)
    }

    fun getLikeCounts(targetIds: List<Long>, type: Type): List<LikeCount> {
        return likeCountRepository.findAllCountByTargetIdAndType(targetIds, type)
    }

    fun register(command: LikeCountCommand.Register): LikeCount {
        return likeCountRepository.save(command.toEntity())
    }
}
