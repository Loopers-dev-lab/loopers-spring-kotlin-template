package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrandQueryServiceTest {
    private val brandRepository = mockk<BrandRepository>()
    private val brandQueryService = BrandQueryService(brandRepository)

    @DisplayName("브랜드를 조회할 때,")
    @Nested
    inner class GetBrand {
        @DisplayName("브랜드가 존재하면 브랜드를 반환한다")
        @Test
        fun getBrand_whenBrandExists_thenReturnsBrand() {
            // given
            val brand = Brand(
                name = "나이키",
                description = "스포츠 브랜드",
            )
            every { brandRepository.findById(1L) } returns brand

            // when
            val result = brandQueryService.getBrand(1L)

            // then
            assertThat(result).isEqualTo(brand)
            verify(exactly = 1) { brandRepository.findById(1L) }
        }

        @DisplayName("브랜드가 존재하지 않으면 예외가 발생한다")
        @Test
        fun getBrand_whenBrandNotFound_thenThrowsException() {
            // given
            every { brandRepository.findById(999L) } returns null

            // when & then
            val exception = assertThrows<CoreException> {
                brandQueryService.getBrand(999L)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("브랜드를 찾을 수 없습니다")
            assertThat(exception.message).contains("999")
        }
    }
}
