package com.loopers.domain.like

import com.loopers.domain.brand.Brand
import com.loopers.domain.member.BirthDate
import com.loopers.domain.member.Gender
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Email
import com.loopers.domain.shared.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LikeTest {

    @DisplayName("좋아요를 생성할 수 있다")
    @Test
    fun createLike() {
        val member = Member(
            MemberId("testUser1"),
            Email("test@example.com"),
            BirthDate.from("1990-01-01"),
            Gender.MALE
        )
        val product = Product("상품1", "상품 설명", Money.of(10000L), Stock.of(100), 1L)

        val like = Like.of(member, product)

        assertThat(like.member).isEqualTo(member)
        assertThat(like.product).isEqualTo(product)
    }
}
