package com.loopers.support.error

import org.springframework.http.HttpStatus

enum class ErrorType(val status: HttpStatus, val code: String, val message: String) {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase, "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.reasonPhrase, "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.reasonPhrase, "이미 존재하는 리소스입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.reasonPhrase, "접근 권한이 없습니다."),

    /** 재고 에러 */
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "재고가 부족합니다."),

    /** 포인트 에러 */
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "포인트 잔액이 부족합니다."),

    /** 주문 에러 */
    ORDER_NOT_COMPLETABLE(HttpStatus.CONFLICT, "ORDER_NOT_COMPLETABLE", "대기 중인 주문만 완료 처리할 수 있습니다."),
    ORDER_NOT_PAYMENT_FAILED(HttpStatus.CONFLICT, "ORDER_NOT_COMPLETABLE", "대기 중인 주문만 결제 실패 처리할 수 있습니다."),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "대기 중인 주문만 취소할 수 있습니다."),

    /** 쿠폰 에러 */
    COUPON_ALREADY_USED(HttpStatus.CONFLICT, "COUPON_IS_USED", "이미 사용된 쿠폰입니다."),
}
