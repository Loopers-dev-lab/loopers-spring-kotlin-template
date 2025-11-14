package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

@DisplayName("PageQuery")
class PageQueryTest {
    @Nested
    inner class Of {

        @Test
        @DisplayName("기본값으로 PageQuery를 생성한다")
        fun `creates PageQuery with default values`() {
            // when
            val pageQuery = PageQuery.of()

            // then
            assertEquals(0, pageQuery.page)
            assertEquals(20, pageQuery.size)
            assertEquals(ProductSortType.LATEST, pageQuery.sort)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 1, 10, 100, 1000])
        @DisplayName("유효한 page로 PageQuery를 생성한다")
        fun `creates PageQuery when valid page is given`(validPage: Int) {
            // when
            val pageQuery = PageQuery.of(page = validPage)

            // then
            assertEquals(validPage, pageQuery.page)
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 10, 20, 50, 100])
        @DisplayName("유효한 size로 PageQuery를 생성한다")
        fun `creates PageQuery when valid size is given`(validSize: Int) {
            // when
            val pageQuery = PageQuery.of(size = validSize)

            // then
            assertEquals(validSize, pageQuery.size)
        }

        @ParameterizedTest
        @ValueSource(ints = [-1, -10, -100])
        @DisplayName("page가 음수이면 CoreException을 던진다")
        fun `throws CoreException when page is negative`(invalidPage: Int) {
            // when
            val exception = assertThrows<CoreException> {
                PageQuery.of(page = invalidPage)
            }

            // then
            assertEquals(ErrorType.BAD_REQUEST, exception.errorType)
            assertEquals("page는 0 이상이어야 합니다.", exception.message)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, -10])
        @DisplayName("size가 0 이하이면 CoreException을 던진다")
        fun `throws CoreException when size is zero or negative`(invalidSize: Int) {
            // when
            val exception = assertThrows<CoreException> {
                PageQuery.of(size = invalidSize)
            }

            // then
            assertEquals(ErrorType.BAD_REQUEST, exception.errorType)
            assertEquals("size는 1 이상이어야 합니다.", exception.message)
        }

        @Test
        @DisplayName("모든 ProductSortType을 sort로 사용할 수 있다")
        fun `creates PageQuery with all ProductSortType values`() {
            // when and then
            ProductSortType.entries.forEach { sortType ->
                val pageQuery = PageQuery.of(page = 0, size = 20, sort = sortType)
                assertEquals(sortType, pageQuery.sort)
            }
        }
    }
}
