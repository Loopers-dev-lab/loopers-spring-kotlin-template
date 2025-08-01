package com.loopers.domain.like

import com.loopers.domain.like.dto.command.LikeCommand
import com.loopers.domain.like.dto.criteria.LikeCriteria
import com.loopers.domain.like.dto.info.LikeInfo.Add
import com.loopers.domain.like.entity.Like
import com.loopers.domain.like.vo.LikeTarget.Type
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class LikeService(
    private val likeRepository: LikeRepository,
) {
    fun find(userId: Long, targetId: Long, targetType: Type): Like? {
        return likeRepository.find(userId, targetId, targetType)
    }

    fun findAll(criteria: LikeCriteria.FindAll): Page<Like> {
        return likeRepository.findAll(criteria)
    }

    fun add(command: LikeCommand.AddLike): Add {
        return find(command.userId, command.targetId, command.type)
            ?.let { Add.of(it, false) }
            ?: Add.of(likeRepository.save(command.toEntity()), true)
    }

    fun remove(command: LikeCommand.RemoveLike) {
        find(command.userId, command.targetId, command.type)
            ?.let { likeRepository.delete(it) }
    }
}
