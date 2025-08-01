package com.loopers.infrastructure.like

import com.loopers.domain.like.entity.Like
import com.loopers.domain.like.vo.LikeTarget.Type
import org.springframework.data.jpa.repository.JpaRepository

interface LikeJpaRepository : JpaRepository<Like, Long> {
    @Suppress("FunctionName")
    fun findByUserIdAndTarget_TargetIdAndTarget_Type(userId: Long, targetTargetId: Long, type: Type): Like?
}
