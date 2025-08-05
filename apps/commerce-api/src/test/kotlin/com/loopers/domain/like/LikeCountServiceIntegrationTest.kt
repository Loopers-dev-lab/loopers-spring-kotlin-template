package com.loopers.domain.like

import com.loopers.domain.like.dto.command.LikeCountCommand
import com.loopers.domain.like.entity.LikeCount
import com.loopers.domain.like.vo.LikeTarget
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
class LikeCountServiceIntegrationTest @Autowired constructor(
    private val likeCountService: LikeCountService,
    private val likeCountRepository: LikeCountRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("상세 조회")
    inner class GetLikeCount {

        @Test
        fun `좋아요 수를 정상적으로 조회한다`() {
            // given
            val saved = likeCountRepository.save(LikeCount.create(1L, LikeTarget.Type.PRODUCT, 1L))

            // when
            val result = likeCountService.getLikeCount(saved.target.targetId, saved.target.type)

            // then
            assertThat(result.count.value).isEqualTo(1L)
        }

        @Test
        fun `좋아요 수가 없으면 예외가 발생한다`() {
            // when & then
            assertThrows<CoreException> {
                likeCountService.getLikeCount(1L, LikeTarget.Type.PRODUCT)
            }
        }
    }

    @Nested
    @DisplayName("목록 조회")
    inner class GetLikeCounts {

        @Test
        fun `좋아요 수 목록를 조회한다`() {
            likeCountRepository.save(LikeCount.create(1L, LikeTarget.Type.PRODUCT, 1L))
            likeCountRepository.save(LikeCount.create(2L, LikeTarget.Type.PRODUCT, 2L))

            // when
            val results = likeCountService.getLikeCounts(listOf(1L, 2L), LikeTarget.Type.PRODUCT)

            // then
            assertThat(results).hasSize(2)
            assertThat(results.map { it.count.value }).containsExactlyInAnyOrder(1L, 2L)
        }

        @Test
        fun `해당하는 좋아요 수가 없으면 빈 리스트를 반환한다`() {
            // when
            val results = likeCountService.getLikeCounts(listOf(-1L, -2L), LikeTarget.Type.PRODUCT)

            // then
            assertThat(results).isEmpty()
        }
    }

    @Nested
    @DisplayName("좋아요 수 등록")
    inner class Register {

        @Test
        fun `좋아요 수를 정상적으로 등록한다`() {
            // given
            val command = LikeCountCommand.Register(1L, LikeTarget.Type.PRODUCT, 1L)

            // when
            val saved = likeCountService.register(command)

            // then
            assertThat(saved.count.value).isEqualTo(1L)
            assertThat(saved.target.targetId).isEqualTo(command.targetId)
        }
    }
}
