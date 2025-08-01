package com.loopers.domain.like.dto.criteria

import com.loopers.domain.like.vo.LikeTarget
import org.springframework.data.domain.PageRequest

class LikeCriteria {
    data class FindAll(
        val userId: Long,
        val type: LikeTarget.Type = LikeTarget.Type.PRODUCT,
        val page: Int = 0,
        val size: Int = 20,
    ) {
        fun toPageRequest(): PageRequest {
            return PageRequest.of(page, size)
        }
    }
}
