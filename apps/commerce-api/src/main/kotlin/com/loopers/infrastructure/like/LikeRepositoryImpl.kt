package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeRepository
import com.loopers.domain.like.dto.criteria.LikeCriteria
import com.loopers.domain.like.entity.Like
import com.loopers.domain.like.entity.QLike
import com.loopers.domain.like.vo.LikeTarget.Type
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val userJpaRepository: LikeJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : LikeRepository {
    override fun find(userId: Long, targetId: Long, type: Type): Like? {
        return userJpaRepository.findByUserIdAndTarget_TargetIdAndTarget_Type(userId, targetId, type)
    }

    override fun findAll(
        criteria: LikeCriteria.FindAll,
    ): Page<Like> {
        val like = QLike.like

        val pageable = criteria.toPageRequest()
        val offset = pageable.offset
        val limit = pageable.pageSize.toLong()

        val results = queryFactory
            .select(like)
            .from(like)
            .where(
                like.userId.eq(criteria.userId)
                .and(like.target.type.eq(criteria.type)),
            )
            .orderBy(like.createdAt.desc())
            .offset(offset)
            .limit(limit)
            .fetch()

        val totalCount = queryFactory
            .select(like.count())
            .from(like)
            .where(
                like.userId.eq(criteria.userId)
                    .and(like.target.type.eq(criteria.type)),
            )
            .fetchOne() ?: 0L
        return PageImpl(results, pageable, totalCount)
    }

    override fun save(like: Like): Like {
        return userJpaRepository.save(like)
    }

    override fun delete(like: Like) {
        return userJpaRepository.delete(like)
    }
}
