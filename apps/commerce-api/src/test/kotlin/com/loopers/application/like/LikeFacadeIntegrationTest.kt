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
import org.springframework.data.domain.PageRequest

@SpringBootTest
class LikeFacadeIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val likeJpaRepository: LikeJpaRepository,
    private val memberJpaRepository: MemberJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

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
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), brand)
        )

        val result = likeFacade.addLike(member.memberId.value, product.id!!)

        assertThat(result).isNotNull
        assertThat(result.memberId).isEqualTo(member.memberId.value)
        assertThat(result.product.id).isEqualTo(product.id)

        // 좋아요 수 증가 확인
        val updatedProduct = productJpaRepository.findById(product.id!!).get()
        assertThat(updatedProduct.likesCount).isEqualTo(1)
    }

    @DisplayName("이미 좋아요가 있을 경우 중복 추가되지 않는다")
    @Test
    fun addLikeWhenAlreadyExists() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), brand)
        )

        // 첫 번째 좋아요
        likeFacade.addLike(member.memberId.value, product.id!!)

        // 두 번째 좋아요 시도
        val result = likeFacade.addLike(member.memberId.value, product.id!!)

        assertThat(result).isNotNull
        // 좋아요가 중복 생성되지 않았는지 확인
        val likes = likeJpaRepository.findAll()
        assertThat(likes).hasSize(1)

        // 좋아요 수가 중복 증가하지 않았는지 확인
        val updatedProduct = productJpaRepository.findById(product.id!!).get()
        assertThat(updatedProduct.likesCount).isEqualTo(1)
    }

    @DisplayName("좋아요를 취소할 수 있다")
    @Test
    fun cancelLike() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), brand)
        )

        // 좋아요 추가
        likeFacade.addLike(member.memberId.value, product.id!!)

        // 좋아요 취소
        likeFacade.cancelLike(member.memberId.value, product.id!!)

        // 좋아요가 삭제되었는지 확인
        val likes = likeJpaRepository.findAll()
        assertThat(likes).isEmpty()

        // 좋아요 수 감소 확인
        val updatedProduct = productJpaRepository.findById(product.id!!).get()
        assertThat(updatedProduct.likesCount).isEqualTo(0)
    }

    @DisplayName("좋아요가 없을 경우 취소해도 에러가 발생하지 않는다")
    @Test
    fun cancelLikeWhenNotExists() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), brand)
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
        val product1 = productJpaRepository.save(Product("상품1", "설명1", Money.of(10000L), Stock.of(100), brand))
        val product2 = productJpaRepository.save(Product("상품2", "설명2", Money.of(20000L), Stock.of(50), brand))
        val product3 = productJpaRepository.save(Product("상품3", "설명3", Money.of(15000L), Stock.of(30), brand))

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
        val product1 = productJpaRepository.save(Product("상품1", "설명1", Money.of(10000L), Stock.of(100), brand))
        val product2 = productJpaRepository.save(Product("상품2", "설명2", Money.of(20000L), Stock.of(50), brand))
        val product3 = productJpaRepository.save(Product("상품3", "설명3", Money.of(15000L), Stock.of(30), brand))

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
