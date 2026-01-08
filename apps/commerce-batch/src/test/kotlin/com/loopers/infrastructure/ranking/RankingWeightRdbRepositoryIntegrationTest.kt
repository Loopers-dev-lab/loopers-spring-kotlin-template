package com.loopers.infrastructure.ranking

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
@DisplayName("RankingWeightRdbRepository")
class RankingWeightRdbRepositoryIntegrationTest @Autowired constructor(
    private val repository: RankingWeightRdbRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @BeforeEach
    fun setUp() {
        databaseCleanUp.afterPropertiesSet()
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("findLatest 메서드는")
    inner class FindLatestTest {

        @DisplayName("가중치가 존재하면 최신 가중치를 반환한다")
        @Test
        fun shouldReturnLatestWeight_whenWeightExists() {
            // arrange
            jdbcTemplate.update(
                """
                INSERT INTO ranking_weight (view_weight, like_weight, order_weight, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW())
                """.trimIndent(),
                BigDecimal("0.10"),
                BigDecimal("0.20"),
                BigDecimal("0.70"),
            )
            jdbcTemplate.update(
                """
                INSERT INTO ranking_weight (view_weight, like_weight, order_weight, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW())
                """.trimIndent(),
                BigDecimal("0.15"),
                BigDecimal("0.25"),
                BigDecimal("0.60"),
            )

            // act
            val result = repository.findLatest()

            // assert
            assertThat(result).isNotNull
            assertThat(result!!.viewWeight).isEqualByComparingTo(BigDecimal("0.15"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.25"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.60"))
        }

        @DisplayName("가중치가 없으면 null을 반환한다")
        @Test
        fun shouldReturnNull_whenNoWeightExists() {
            // arrange - no data

            // act
            val result = repository.findLatest()

            // assert
            assertThat(result).isNull()
        }

        @DisplayName("삭제된 가중치는 제외하고 최신 가중치를 반환한다")
        @Test
        fun shouldExcludeDeletedWeight_whenFindingLatest() {
            // arrange
            jdbcTemplate.update(
                """
                INSERT INTO ranking_weight (view_weight, like_weight, order_weight, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW())
                """.trimIndent(),
                BigDecimal("0.10"),
                BigDecimal("0.20"),
                BigDecimal("0.70"),
            )
            jdbcTemplate.update(
                """
                INSERT INTO ranking_weight (view_weight, like_weight, order_weight, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, NOW(), NOW(), NOW())
                """.trimIndent(),
                BigDecimal("0.15"),
                BigDecimal("0.25"),
                BigDecimal("0.60"),
            )

            // act
            val result = repository.findLatest()

            // assert
            assertThat(result).isNotNull
            assertThat(result!!.viewWeight).isEqualByComparingTo(BigDecimal("0.10"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.20"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.70"))
        }
    }
}
