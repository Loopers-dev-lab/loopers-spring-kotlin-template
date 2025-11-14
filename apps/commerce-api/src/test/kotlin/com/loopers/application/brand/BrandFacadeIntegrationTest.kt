package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest

@SpringBootTest
class BrandFacadeIntegrationTest @Autowired constructor(
    private val brandFacade: BrandFacade,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드 ID로 브랜드를 조회할 수 있다")
    @Test
    fun getBrand() {
        val brand = Brand("테스트브랜드", "브랜드 설명")
        brandJpaRepository.save(brand)

        val result = brandFacade.getBrand(brand.id!!)

        assertThat(result).isNotNull
        assertThat(result.name).isEqualTo("테스트브랜드")
        assertThat(result.description).isEqualTo("브랜드 설명")
    }

    @DisplayName("존재하지 않는 브랜드 ID로 조회 시 예외가 발생한다")
    @Test
    fun failToGetBrandWhenNotExists() {
        val exception = assertThrows<CoreException> {
            brandFacade.getBrand(999L)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.BRAND_NOT_FOUND)
    }

    @DisplayName("브랜드 목록을 페이징하여 조회할 수 있다")
    @Test
    fun getBrands() {
        val brand1 = Brand("브랜드1", "설명1")
        val brand2 = Brand("브랜드2", "설명2")
        val brand3 = Brand("브랜드3", "설명3")
        brandJpaRepository.saveAll(listOf(brand1, brand2, brand3))

        val pageable = PageRequest.of(0, 10)
        val result = brandFacade.getBrands(pageable)

        assertThat(result.content).hasSize(3)
        assertThat(result.totalElements).isEqualTo(3)
    }

    @DisplayName("브랜드 목록 조회 시 페이지 크기에 맞게 조회된다")
    @Test
    fun getBrandsWithPageSize() {
        val brand1 = Brand("브랜드1", "설명1")
        val brand2 = Brand("브랜드2", "설명2")
        val brand3 = Brand("브랜드3", "설명3")
        brandJpaRepository.saveAll(listOf(brand1, brand2, brand3))

        val pageable = PageRequest.of(0, 2)
        val result = brandFacade.getBrands(pageable)

        assertThat(result.content).hasSize(2)
        assertThat(result.totalElements).isEqualTo(3)
        assertThat(result.totalPages).isEqualTo(2)
    }
}
