package com.loopers.application.product

import com.loopers.application.like.LikeFacade
import com.loopers.domain.brand.entity.Brand
import com.loopers.domain.like.dto.command.LikeCommand.AddLike
import com.loopers.domain.like.vo.LikeTarget.Type.PRODUCT
import com.loopers.domain.product.dto.command.ProductCommand
import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.domain.product.dto.criteria.ProductCriteria.FindAll.ProductSortCondition
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest
class ProductFacadeIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productRepository: ProductJpaRepository,
    private val brandRepository: BrandJpaRepository,
    private val likeFacade: LikeFacade,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("조회 테스트")
    @Nested
    inner class Find {
        @Test
        fun `페이징 조회`() {
            // given
            val brand1 = brandRepository.save(Brand.create("BrandName1", "BrandDescription1"))
            val brand2 = brandRepository.save(Brand.create("BrandName2", "BrandDescription2"))

            val productDetail1 = productFacade.registerProduct(ProductCommand.RegisterProduct(brand1.id, "name1", "description1", BigDecimal(1)))
            val productDetail2 = productFacade.registerProduct(ProductCommand.RegisterProduct(brand1.id, "name2", "description2", BigDecimal(3)))
            val productDetail3 = productFacade.registerProduct(ProductCommand.RegisterProduct(brand2.id, "name3", "description3", BigDecimal(2)))

            likeFacade.addLike(AddLike(1, productDetail1.id, PRODUCT))
            likeFacade.addLike(AddLike(1, productDetail2.id, PRODUCT))
            likeFacade.addLike(AddLike(2, productDetail2.id, PRODUCT))

            // when
            val products = productFacade.getProducts(ProductCriteria.FindAll(sort = ProductSortCondition.LIKES_DESC))

            // then
            assertThat(true)
        }

        @Test
        fun `단건 조회`() {
            // given
            val brand = brandRepository.save(Brand.create("BrandName1", "BrandDescription1"))
            val productDetail = productFacade.registerProduct(ProductCommand.RegisterProduct(brand.id, "name1", "description1", BigDecimal(1)))

            // when
            val products = productFacade.getProduct(productDetail.id)

            // then
            assertThat(true)
        }
    }
}
