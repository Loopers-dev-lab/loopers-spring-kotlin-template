package com.loopers.application.like

import com.loopers.domain.brand.Brand
import com.loopers.domain.member.BirthDate
import com.loopers.domain.member.Gender
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Email
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.event.EventOutboxJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["spring.task.scheduling.enabled=false"])
class LikeFacadeIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val likeJpaRepository: LikeJpaRepository,
    private val memberJpaRepository: MemberJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val eventOutboxRepository: EventOutboxJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요를 추가할 수 있다")
    @Test
    fun addLike() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), 1L)
        )

        val result = likeFacade.addLike(member.memberId.value, product.id!!)

        assertThat(result).isNotNull
        assertThat(result.memberId).isEqualTo(member.memberId.value)
        assertThat(result.product.id).isEqualTo(product.id)

        // EventOutbox에 저장되었는지 확인 (Kafka 발행은 별도 프로세스)
        var retryCount = 0
        var outboxEvents = eventOutboxRepository.findAll()
        while (outboxEvents.isEmpty() && retryCount < 30) {
            Thread.sleep(100)
            outboxEvents = eventOutboxRepository.findAll()
            retryCount++
        }

        // EventOutbox에 ProductLikedEvent가 저장되었는지 확인
        assertThat(outboxEvents).hasSize(1)
        assertThat(outboxEvents[0].eventType).isEqualTo("PRODUCT_LIKED")
        assertThat(outboxEvents[0].aggregateId).isEqualTo(product.id)
    }

    @DisplayName("이미 좋아요가 있을 경우 중복 추가되지 않는다")
    @Test
    fun addLikeWhenAlreadyExists() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), 1L)
        )

        // 첫 번째 좋아요
        likeFacade.addLike(member.memberId.value, product.id!!)

        // 두 번째 좋아요 시도
        val result = likeFacade.addLike(member.memberId.value, product.id!!)

        assertThat(result).isNotNull
        // 좋아요가 중복 생성되지 않았는지 확인
        val likes = likeJpaRepository.findAll()
        assertThat(likes).hasSize(1)

        // EventOutbox에 저장되었는지 확인 (중복 이벤트는 무시되어야 함)
        var retryCount = 0
        var outboxEvents = eventOutboxRepository.findAll()
        while (outboxEvents.size < 1 && retryCount < 30) {
            Thread.sleep(100)
            outboxEvents = eventOutboxRepository.findAll()
            retryCount++
        }

        // EventOutbox에 1개만 저장되었는지 확인 (중복 이벤트는 무시됨)
        assertThat(outboxEvents).hasSize(1)
        assertThat(outboxEvents[0].eventType).isEqualTo("PRODUCT_LIKED")
    }

    @DisplayName("좋아요를 취소할 수 있다")
    @Test
    fun cancelLike() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), 1L)
        )

        // 좋아요 추가
        likeFacade.addLike(member.memberId.value, product.id!!)

        // EventOutbox에 ProductLikedEvent 저장 확인
        var retryCount = 0
        var outboxEvents = eventOutboxRepository.findAll()
        while (outboxEvents.isEmpty() && retryCount < 30) {
            Thread.sleep(100)
            outboxEvents = eventOutboxRepository.findAll()
            retryCount++
        }
        assertThat(outboxEvents).hasSize(1)
        assertThat(outboxEvents[0].eventType).isEqualTo("PRODUCT_LIKED")

        // 좋아요 취소
        likeFacade.cancelLike(member.memberId.value, product.id!!)

        // 좋아요가 삭제되었는지 확인
        val likes = likeJpaRepository.findAll()
        assertThat(likes).isEmpty()

        // EventOutbox에 ProductUnlikedEvent도 저장되었는지 확인
        retryCount = 0
        outboxEvents = eventOutboxRepository.findAll()
        while (outboxEvents.size < 2 && retryCount < 30) {
            Thread.sleep(100)
            outboxEvents = eventOutboxRepository.findAll()
            retryCount++
        }

        // EventOutbox에 두 이벤트가 모두 저장되었는지 확인
        assertThat(outboxEvents).hasSize(2)
        val eventTypes = outboxEvents.map { it.eventType }
        assertThat(eventTypes).containsExactlyInAnyOrder("PRODUCT_LIKED", "PRODUCT_UNLIKED")
    }

    @DisplayName("좋아요가 없을 경우 취소해도 에러가 발생하지 않는다")
    @Test
    fun cancelLikeWhenNotExists() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), 1L)
        )

        // 좋아요 없이 취소 시도
        likeFacade.cancelLike(member.memberId.value, product.id!!)

        // 에러가 발생하지 않고 정상 처리됨
        val likes = likeJpaRepository.findAll()
        assertThat(likes).isEmpty()
    }

    @DisplayName("내 좋아요 목록을 조회할 수 있다")
    @Test
    fun getMyLikes() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product1 = productJpaRepository.save(Product("상품1", "설명1", Money.of(10000L), Stock.of(100), 1L))
        val product2 = productJpaRepository.save(Product("상품2", "설명2", Money.of(20000L), Stock.of(50), 1L))
        val product3 = productJpaRepository.save(Product("상품3", "설명3", Money.of(15000L), Stock.of(30), 1L))

        // 좋아요 추가
        likeFacade.addLike(member.memberId.value, product1.id!!)
        likeFacade.addLike(member.memberId.value, product2.id!!)
        likeFacade.addLike(member.memberId.value, product3.id!!)

        val pageable = PageRequest.of(0, 10)
        val result = likeFacade.getMyLikes(member.memberId.value, pageable)

        assertThat(result.content).hasSize(3)
        assertThat(result.totalElements).isEqualTo(3)
    }

    @DisplayName("좋아요 목록 조회 시 페이지 크기에 맞게 조회된다")
    @Test
    fun getMyLikesWithPageSize() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product1 = productJpaRepository.save(Product("상품1", "설명1", Money.of(10000L), Stock.of(100), 1L))
        val product2 = productJpaRepository.save(Product("상품2", "설명2", Money.of(20000L), Stock.of(50), 1L))
        val product3 = productJpaRepository.save(Product("상품3", "설명3", Money.of(15000L), Stock.of(30), 1L))

        likeFacade.addLike(member.memberId.value, product1.id!!)
        likeFacade.addLike(member.memberId.value, product2.id!!)
        likeFacade.addLike(member.memberId.value, product3.id!!)

        val pageable = PageRequest.of(0, 2)
        val result = likeFacade.getMyLikes(member.memberId.value, pageable)

        assertThat(result.content).hasSize(2)
        assertThat(result.totalElements).isEqualTo(3)
        assertThat(result.totalPages).isEqualTo(2)
    }
}
