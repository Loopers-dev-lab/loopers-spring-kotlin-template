package com.loopers.infrastructure.like

import com.loopers.domain.like.entity.LikeCount
import com.loopers.domain.like.vo.LikeTarget.Type
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface LikeCountJpaRepository : JpaRepository<LikeCount, Long> {
    @Suppress("FunctionName")
    fun findByTarget_TargetIdAndTarget_Type(targetTargetId: Long, type: Type): LikeCount?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lc FROM LikeCount lc WHERE lc.target.targetId = :targetTargetId AND lc.target.type = :type")
    fun findCountWithLockByTargetIdAndType(targetTargetId: Long, type: Type): LikeCount?

    @Suppress("FunctionName")
    fun findAllByTarget_TargetIdInAndTarget_Type(targetTargetIds: List<Long>, type: Type): List<LikeCount>
}
