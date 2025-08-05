package com.loopers.domain.like

import com.loopers.domain.like.entity.LikeCount
import com.loopers.domain.like.vo.LikeTarget.Type.PRODUCT
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class LikeCountTest {
    @Test
    fun `좋아요 카운트가 정상적으로 생성된다`() {
        // when
        val likeCount = LikeCount.create(1L, PRODUCT, 1L)

        // then
        assertThat(likeCount.target.type).isEqualTo(PRODUCT)
        assertThat(likeCount.target.targetId).isEqualTo(1L)
    }

    @Test
    fun `좋아요 카운트 생성 시 카운트가 0보다 작으면 예외가 발생한다`() {
        // then
        assertThrows<CoreException> {
            LikeCount.create(1L, PRODUCT, -1L)
        }
    }

    @Test
    fun `좋아요 수가 증가한다`() {
        // given
        val likeCount = LikeCount.create(1L, PRODUCT, 10L)

        // when
        val increaseLikeCount = likeCount.count.increase()

        // then
        assertThat(increaseLikeCount.value).isEqualTo(11L)
    }

    @Test
    fun `좋아요 수가 감소한다`() {
        // given
        val likeCount = LikeCount.create(1L, PRODUCT, 10L)

        // when
        val decreaseLikeCount = likeCount.count.decrease()

        // then
        assertThat(decreaseLikeCount.value).isEqualTo(9L)
    }

    @Test
    fun `좋아요 수가 0일 때 감소 시 예외가 발생한다`() {
        // given
        val likeCount = LikeCount.create(1L, PRODUCT, 0L)

        // when & then
        assertThrows<CoreException> {
            likeCount.count.decrease()
        }
    }
}
