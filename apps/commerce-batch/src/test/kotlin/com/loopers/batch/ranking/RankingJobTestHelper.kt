package com.loopers.batch.ranking

import com.loopers.domain.ranking.ProductRankDaily
import com.loopers.domain.ranking.ProductRankDailyRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.repository.explore.JobExplorer
import java.time.LocalDate

/**
 * 랭킹 배치 Job 테스트를 위한 공통 헬퍼 클래스
 *
 * 여러 랭킹 집계 Job 테스트에서 공통으로 사용하는 헬퍼 메서드를 제공합니다.
 */
class RankingJobTestHelper(
    private val jobExplorer: JobExplorer,
    private val productRankDailyRepository: ProductRankDailyRepository,
) {
    /**
     * JobOperator로 실행한 Job의 실행 결과 조회
     *
     * Job이 완료될 때까지 대기하며, 최대 30초까지 기다립니다.
     *
     * @param executionId Job 실행 ID
     * @return Job 실행 결과
     * @throws IllegalStateException Job 실행이 30초 이내에 완료되지 않으면 예외 발생
     */
    fun getJobExecution(executionId: Long): JobExecution {
        // Job 완료까지 대기 (최대 30초)
        var waitCount = 0
        while (waitCount < 300) {
            val execution = jobExplorer.getJobExecution(executionId)
            if (execution != null && execution.status != BatchStatus.STARTED && execution.status != BatchStatus.STARTING) {
                return execution
            }
            Thread.sleep(100)
            waitCount++
        }
        throw IllegalStateException("Job execution timeout: executionId=$executionId")
    }

    /**
     * 동일한 점수로 여러 날의 일간 랭킹 데이터를 생성하는 헬퍼 메서드
     *
     * @param startDate 시작 날짜
     * @param daysCount 생성할 일수
     * @param productId 상품 ID
     * @param score 점수
     * @param rank 순위
     */
    fun createDailyRankings(
        startDate: LocalDate,
        daysCount: Int,
        productId: Long,
        score: Double,
        rank: Int,
    ) {
        for (day in 0 until daysCount) {
            productRankDailyRepository.save(
                ProductRankDaily(
                    rankingDate = startDate.plusDays(day.toLong()),
                    productId = productId,
                    score = score,
                    rank = rank,
                ),
            )
        }
    }

    /**
     * 날짜별로 다른 점수로 일간 랭킹 데이터를 생성하는 헬퍼 메서드
     *
     * @param startDate 시작 날짜
     * @param productId 상품 ID
     * @param scoresPerDay 날짜별 점수 리스트 (인덱스 0 = 첫째 날, 인덱스 1 = 둘째 날, ...)
     * @param rank 순위
     */
    fun createDailyRankings(
        startDate: LocalDate,
        productId: Long,
        scoresPerDay: List<Double>,
        rank: Int,
    ) {
        scoresPerDay.forEachIndexed { day, score ->
            productRankDailyRepository.save(
                ProductRankDaily(
                    rankingDate = startDate.plusDays(day.toLong()),
                    productId = productId,
                    score = score,
                    rank = rank,
                ),
            )
        }
    }
}
