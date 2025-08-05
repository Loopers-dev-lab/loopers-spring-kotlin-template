package com.loopers.domain.brand

import com.loopers.domain.brand.dto.command.BrandCommand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BrandServiceIntegrationTest @Autowired constructor(
    private val brandService: BrandService,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상세 조회")
    @Nested
    inner class Get {

        @Test
        fun `id로 브랜드를 정상 조회한다`() {
            // given
            val command = BrandCommand.RegisterBrand("구찌", "비싸다")
            val saved = brandService.register(command)

            // when
            val result = brandService.get(saved.id)

            // then
            assertThat(result.id).isEqualTo(saved.id)
            assertThat(result.name.value).isEqualTo("구찌")
            assertThat(result.description.value).isEqualTo("비싸다")
        }

        @Test
        fun `존재하지 않는 id로 조회하면 예외가 발생한다`() {
            // expect
            val exception = assertThrows<CoreException> {
                brandService.get(-1L)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("다건 조회")
    @Nested
    inner class FindAll {

        @Test
        fun `여러 id로 조회하면 해당 브랜드 목록을 조회한다`() {
            // given
            val brand1 = brandService.register(BrandCommand.RegisterBrand("구찌", "비싸다"))
            val brand2 = brandService.register(BrandCommand.RegisterBrand("루이비똥똥", "비싸다"))

            // when
            val result = brandService.findAll(listOf(brand1.id, brand2.id))

            // then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name.value }).containsExactlyInAnyOrder("구찌", "루이비똥똥")
        }

        @Test
        fun `빈 리스트로 조회하면 빈 리스트를 조회한다`() {
            // when
            val result = brandService.findAll(emptyList())

            // then
            assertThat(result).isEmpty()
        }
    }

    @DisplayName("등록")
    @Nested
    inner class Register {

        @Test
        fun `브랜드가 정상적으로 등록된다`() {
            // given
            val command = BrandCommand.RegisterBrand("구찌", "비싸다")

            // when
            val saved = brandService.register(command)

            // then
            assertThat(saved.id).isNotNull()
            assertThat(saved.name.value).isEqualTo("구찌")
            assertThat(saved.description.value).isEqualTo("비싸다")
        }
    }
}
