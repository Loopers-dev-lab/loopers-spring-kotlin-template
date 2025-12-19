package com.loopers.domain.like.event

import com.loopers.application.like.LikeFacade
import com.loopers.domain.member.BirthDate
import com.loopers.domain.member.Gender
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import com.loopers.domain.shared.Email
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["spring.task.scheduling.enabled=false"])
class ProductLikeEventHandlerRollbackTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val memberJpaRepository: MemberJpaRepository,
    private val likeJpaRepository: LikeJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요 집계 실패 시 Like도 저장되지 않는다 (전체 롤백)")
    @Test
    fun rollbackLikeWhenCountUpdateFails() {
        // given
        val member = memberJpaRepository.save(
            Member(
                memberId = MemberId("testUser1"),
                email = Email("test@example.com"),
                birthDate = BirthDate.from("1990-05-15"),
                gender = Gender.MALE
            )
        )
        val invalidProductId = 9999999L // 존재하지 않는 상품

        // when & then
        assertThrows<CoreException> {
            likeFacade.addLike(member.memberId.value, invalidProductId)
        }

        // then: Like도 저장되지 않음 (전체 롤백 확인)
        val likes = likeJpaRepository.findAll()
        assertThat(likes).isEmpty()
    }
}
