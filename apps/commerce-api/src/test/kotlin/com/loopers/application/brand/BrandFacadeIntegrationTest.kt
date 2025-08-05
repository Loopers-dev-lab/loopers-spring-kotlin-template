package com.loopers.application.brand

import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.brand.dto.command.BrandCommand
import com.loopers.support.error.CoreException
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
class BrandFacadeIntegrationTest @Autowired constructor(
    private val brandFacade: BrandFacade,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("브랜드 등록")
    inner class RegisterBrand {

        @Test
        fun `브랜드 등록에 성공하고 BrandDetail 을 반환한다`() {
            // given
            val command = BrandCommand.RegisterBrand("구찌", "비싸다")

            // when
            val result = brandFacade.registerBrand(command)

            // then
            assertThat(result).isNotNull
            assertThat(result.name).isEqualTo("구찌")
            assertThat(result.description).isEqualTo("비싸다")
        }

        @Test
        fun `브랜드 이름이 비어있으면 등록에 실패한다`() {
            // given
            val command = BrandCommand.RegisterBrand("", "LOOPERS")

            // expect
            assertThrows<CoreException> {
                brandFacade.registerBrand(command)
            }
        }
    }
}
