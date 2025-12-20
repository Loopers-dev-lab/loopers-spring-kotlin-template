package com.loopers.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 랭킹 점수 가중치 설정
 *
 * application.yml에서 설정 가능하며,
 * Spring Cloud Config 등을 사용하면 런타임에 동적으로 변경 가능
 */
@ConfigurationProperties(prefix = "ranking.weight")
data class RankingWeightProperties(
    /**
     * 조회 이벤트 가중치
     */
    var view: Double = 0.1,

    /**
     * 좋아요 이벤트 가중치
     */
    var like: Double = 0.2,

    /**
     * 주문 이벤트 가중치
     */
    var order: Double = 0.7,
) {
    init {
        require(view >= 0) { "view 가중치는 0 이상이어야 합니다: $view" }
        require(like >= 0) { "like 가중치는 0 이상이어야 합니다: $like" }
        require(order >= 0) { "order 가중치는 0 이상이어야 합니다: $order" }
    }
}
