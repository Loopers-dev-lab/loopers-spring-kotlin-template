package com.loopers.domain.like.dto.info

import com.loopers.domain.like.entity.Like

class LikeInfo {
    data class Add(
        val like: Like,
        val isNew: Boolean,
    ) {
        companion object {
            fun of(like: Like, isNew: Boolean): Add {
                return Add(like, isNew)
            }
        }
    }
}
