package com.loopers.support.fixtures

import com.loopers.domain.BaseEntity
import java.time.ZonedDateTime

/**
 * 테스트 코드 전용 확장 함수.
 *
 * JPA에서는 엔티티의 ID(`@Id`) 필드는 보통 Persistence Context에 의해 자동으로 설정되므로
 * 직접 변경이 불가능하다. 하지만 테스트에서는 특정 ID가 설정된 상태로 테스트하고 싶을 수 있으므로
 * 리플렉션을 사용해 강제로 ID 값을 주입할 수 있도록 한다.
 *
 * **주의: 본 함수는 오직 테스트 코드에서만 사용되어야 하며, 실제 애플리케이션 로직에서는 절대 사용하지 말 것.**
 *
 * @param id 테스트용으로 강제 세팅할 엔티티 ID 값
 * @return ID가 설정된 원래 엔티티 객체
 */
fun <T : BaseEntity> T.withId(id: Long): T {
    val field = BaseEntity::class.java.getDeclaredField("id")
    field.isAccessible = true
    field.set(this, id)
    return this
}

/**
 * 테스트 코드 전용 확장 함수.
 *
 * JPA의 `@CreatedDate` 애노테이션이 적용된 필드는 보통 자동으로 설정되므로
 * 직접 변경이 불가능하다. 하지만 테스트에서는 특정 생성 시간이 설정된 상태로
 * 테스트하고 싶을 수 있으므로 리플렉션을 사용해 강제로 createdAt 값을 주입할 수 있도록 한다.
 *
 * **주의: 본 함수는 오직 테스트 코드에서만 사용되어야 하며, 실제 애플리케이션 로직에서는 절대 사용하지 말 것.**
 *
 * @param createdAt 테스트용으로 강제 세팅할 생성 시간
 * @return createdAt이 설정된 원래 엔티티 객체
 */
fun <T : BaseEntity> T.withCreatedAt(createdAt: ZonedDateTime): T {
    val field = BaseEntity::class.java.getDeclaredField("createdAt")
    field.isAccessible = true
    field.set(this, createdAt)
    return this
}
