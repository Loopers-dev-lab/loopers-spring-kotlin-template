package com.loopers.application.ranking

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.ranking.ProductRankWeeklyRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.task.scheduling.enabled=false"])
@DisplayName("RankingFacade 통합 테스트")
class RankingFacadeIntegrationTest @Autowired constructor(
    private val rankingFacade: RankingFacade,
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp
) {

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private lateinit var brand: Brand
    private lateinit var products: List<Product>

    @BeforeEach
    fun setUp() {
        brand = brandJpaRepository.save(Brand(name = "테스트브랜드", description = "설명"))
        products = (1..10).map { i ->
            productJpaRepository.save(
                Product(
                    name = "상품$i",
                    description = "설명$i",
                    price = Money.of(10000L),
                    stock = Stock.of(100),
                    brandId = brand.id
                )
            )
        }

        // MV Weekly 테이블에 데이터 저장
        val weeklyData = products.mapIndexed { index, product ->
            ProductRankWeekly(
                productId = product.id,
                yearWeek = "2025-W52",
                score = (100 - index * 10).toDouble(),
                rankPosition = (index + 1).toLong()
            )
        }
        productRankWeeklyRepository.saveAll(weeklyData)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주간 랭킹 1페이지 조회 시 상품 정보가 포함되어 반환된다")
    @Test
    fun getWeeklyRankingsWithProductInfo() {
        // Given
        val yearWeek = "2025-W52"
        val pageable = PageRequest.of(0, 5)

        // When
        val result = rankingFacade.getWeeklyRankings(yearWeek, pageable)

        // Then
        assertThat(result.content).hasSize(5)
        assertThat(result.totalElements).isEqualTo(10)
        assertThat(result.totalPages).isEqualTo(2)

        val firstRanking = result.content[0]
        assertThat(firstRanking.rank).isEqualTo(1)
        assertThat(firstRanking.score).isEqualTo(100.0)
        assertThat(firstRanking.product.name).isEqualTo("상품1")
    }

    @DisplayName("주간 랭킹 2페이지 조회가 정상 동작한다")
    @Test
    fun getWeeklyRankingsSecondPage() {
        // Given
        val yearWeek = "2025-W52"
        val pageable = PageRequest.of(1, 5)

        // When
        val result = rankingFacade.getWeeklyRankings(yearWeek, pageable)

        // Then
        assertThat(result.content).hasSize(5)
        assertThat(result.number).isEqualTo(1) // 페이지 번호

        val firstRankingInPage = result.content[0]
        assertThat(firstRankingInPage.rank).isEqualTo(6) // 6~10위
    }
}
