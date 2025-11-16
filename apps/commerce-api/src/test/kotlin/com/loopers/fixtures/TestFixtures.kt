package com.loopers.fixtures

import com.loopers.domain.BaseEntity
import java.time.ZonedDateTime

/**
 * 테스트용 엔티티를 생성할 때 BaseEntity의 필드를 설정하는 헬퍼 함수
 *
 * 이 함수는 리플렉션을 사용하지만, 모든 리플렉션 로직이 한 곳에 집중되어 있어
 * 유지보수가 용이하고 테스트 코드의 가독성을 향상시킵니다.
 *
 * @param id 설정할 ID 값
 * @param createdAt 생성 시점 (기본값: 현재 시간)
 * @param updatedAt 수정 시점 (기본값: 현재 시간)
 * @return 필드가 설정된 엔티티
 */
fun <T : BaseEntity> T.withId(
    id: Long,
    createdAt: ZonedDateTime = ZonedDateTime.now(),
    updatedAt: ZonedDateTime = ZonedDateTime.now(),
): T {
    val superclass = this::class.java.superclass

    val idField = superclass.getDeclaredField("id")
    idField.isAccessible = true
    idField.set(this, id)

    val createdAtField = superclass.getDeclaredField("createdAt")
    createdAtField.isAccessible = true
    createdAtField.set(this, createdAt)

    val updatedAtField = superclass.getDeclaredField("updatedAt")
    updatedAtField.isAccessible = true
    updatedAtField.set(this, updatedAt)

    return this
}
