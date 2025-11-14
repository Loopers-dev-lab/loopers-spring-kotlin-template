package com.loopers.support.error

import org.springframework.http.HttpStatus

enum class ErrorType(val status: HttpStatus, val code: String, val message: String) {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase, "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.reasonPhrase, "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.reasonPhrase, "이미 존재하는 리소스입니다."),

    /** 도메인별 에러 */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "상품을 찾을 수 없습니다."),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_001", "브랜드를 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_001", "주문을 찾을 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "회원을 찾을 수 없습니다."),

    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "STOCK_001", "재고가 부족합니다."),
    INVALID_STOCK(HttpStatus.BAD_REQUEST, "STOCK_002", "유효하지 않은 재고 수량입니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "QUANTITY_001", "유효하지 않은 주문 수량입니다."),

    INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, "POINT_001", "포인트가 부족합니다."),
    INVALID_POINT_AMOUNT(HttpStatus.BAD_REQUEST, "POINT_002", "유효하지 않은 포인트 금액입니다."),

    ORDER_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_002", "주문 처리 중 오류가 발생했습니다."),
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "ORDER_003", "유효하지 않은 주문 상태입니다."),
}
