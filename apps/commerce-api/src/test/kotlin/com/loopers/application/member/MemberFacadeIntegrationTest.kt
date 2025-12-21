package com.loopers.application.member

import com.loopers.domain.member.BirthDate
import com.loopers.domain.member.DuplicateMemberIdException
import com.loopers.domain.member.Gender
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import com.loopers.domain.shared.Email
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

@SpringBootTest
@TestPropertySource(properties = ["spring.task.scheduling.enabled=false"])
class MemberFacadeIntegrationTest @Autowired constructor(
    private val memberJpaRepository: MemberJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoSpyBean
   private lateinit var memberFacade: MemberFacade

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

   @AfterEach
   fun tearDown() {
       databaseCleanUp.truncateAllTables()
   }

    @DisplayName("회원 가입 시 Member 저장이 수행된다")
    @Test
    fun saveMemberOnJoin() {
        val command = JoinMemberCommand("user1", "test@email.com", "1990-05-15", "MALE")

        memberFacade.joinMember(command)

        verify(memberFacade).joinMember(any())
    }

    @DisplayName("이미 가입된 ID로 회원가입 시도 시 실패한다")
    @Test
    fun failToJoinWithDuplicateMemberId() {
        val memberId = "testUser1"
        val member = Member(MemberId(memberId), Email("test@gmail.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        memberJpaRepository.save(member)

        val command2 = JoinMemberCommand(memberId, "test2@example.com", "1995-08-11", "FEMALE")

        assertThrows<DuplicateMemberIdException> {
            memberFacade.joinMember(command2)
        }
    }

    @DisplayName("해당 ID의 회원이 존재할 경우, 회원 정보가 반환된다")
    @Test
    fun getMemberInfoWhenExists() {
        val memberId = "testUser1"
        val member = Member(MemberId(memberId), Email("test@gmail.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        memberJpaRepository.save(member)

        val memberInfo = memberFacade.getMemberByMemberId(memberId)
        assertThat(memberInfo).isNotNull
        assertThat(memberInfo?.memberId).isEqualTo(memberId)
    }

    @DisplayName("회원 조회 시 해당 ID의 회원이 존재하지 않을 경우, null이 반환된다")
    @Test
    fun returnNullWhenMemberNotExists() {
        val memberInfo = memberFacade.getMemberByMemberId("noUser")

        assertThat(memberInfo).isNull()
    }

    @DisplayName("해당 ID의 회원이 존재할 경우, 보유 포인트가 반환된다")
    @Test
    fun getPointWhenMemberExists() {
        val memberId = "testUser1"
        val member = Member(MemberId(memberId), Email("test@gmail.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        member.chargePoint(1000L)
        memberJpaRepository.save(member)

        val point = memberFacade.getPoint(memberId)

        assertThat(point).isNotNull
        assertThat(point).isEqualTo(1000L)
    }

    @DisplayName("포인트 조회 시 해당 ID의 회원이 존재하지 않을 경우, null이 반환된다")
    @Test
    fun returnNullPointWhenMemberNotExists() {
        val memberId = "testUser1"
        val point = memberFacade.getPoint(memberId)

        assertThat(point).isNull()
    }

    @DisplayName("포인트 충전 시 해당 ID의 회원이 존재하지 않을 경우, 실패한다")
    @Test
    fun failToChargePointWhenMemberNotExists() {
        assertThrows<CoreException> {
            memberFacade.chargePoint("noUser1", 1000L)
        }
    }

}
