package com.loopers.domain.like

import com.loopers.domain.like.dto.command.LikeCommand
import com.loopers.domain.like.entity.Like
import com.loopers.domain.like.vo.LikeTarget.Type.PRODUCT
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class LikeServiceIntegrationTest @Autowired constructor(
    private val likeService: LikeService,
    private val likeRepository: LikeRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("좋아요 추가")
    inner class AddLike {

        @Test
        fun `좋아요를 정상적으로 추가한다`() {
            // given
            val command = LikeCommand.AddLike(1L, 1L, PRODUCT)

            // when
            val result = likeService.add(command)

            // then
            assertThat(result.isNew).isTrue()
            val like = likeRepository.find(command.userId, command.targetId, command.type)
            assertThat(like).isNotNull
        }

        @Test
        fun `userId, targetId, type이 동일 한 좋아요가 이미 존재하는 경우 추가하지 않는다`() {
            // given
            val command = LikeCommand.AddLike(1L, 1L, PRODUCT)
            likeRepository.save(command.toEntity())

            // when
            val result = likeService.add(command)

            // then
            assertThat(result.isNew).isFalse()
        }
    }

    @Nested
    @DisplayName("좋아요 제거")
    inner class RemoveLike {

        @Test
        fun `좋아요가 존재하면 제거한다`() {
            // given
            val command = LikeCommand.RemoveLike(1L, 1L, PRODUCT)
            likeRepository.save(Like.create(command.userId, command.targetId, command.type))

            // when
            likeService.remove(command)

            // then
            val found = likeRepository.find(command.userId, command.targetId, command.type)
            assertThat(found).isNull()
        }

        @Test
        fun `좋아요가 존재하지 않아도 예외 없이 통과한다`() {
            // given
            val command = LikeCommand.RemoveLike(1L, 1L, PRODUCT)

            // when & then
            assertThatCode { likeService.remove(command) }.doesNotThrowAnyException()
        }
    }

    @Nested
    @DisplayName("상세 조회")
    inner class FindLike {

        @Test
        fun `좋아요가 존재하면 반환한다`() {
            // given
            likeRepository.save(Like.create(1L, 1L, PRODUCT))

            // when
            val like = likeService.get(1L, 1L, PRODUCT)

            // then
            assertThat(like.id).isEqualTo(1L)
        }

        @Test
        fun `좋아요가 존재하지 않으면 null 반환한다`() {
            // when
            val result = likeService.find(1L, 1L, PRODUCT)

            // then
            assertThat(result).isNull()
        }
    }
}
