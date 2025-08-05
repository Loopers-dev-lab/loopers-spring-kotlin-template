package com.loopers.domain.like

import com.loopers.domain.like.entity.Like
import com.loopers.domain.like.vo.LikeTarget.Type.PRODUCT
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class LikeTest {
    @Test
    fun `좋아요가 정상적으로 생성된다`() {
        // when
        val like = Like.create(1L, 1L, PRODUCT)

        // then
        assertThat(like.userId).isEqualTo(1L)
    }
}
