#!/bin/bash

# Round 10 테스트용 일간 랭킹 데이터 초기화 스크립트
# Redis에 7일치 (주간) + 30일치 (월간) 랭킹 데이터를 생성합니다.

echo "========================================="
echo "Redis 일간 랭킹 데이터 초기화 시작"
echo "========================================="

# Redis 접속 정보
REDIS_HOST="localhost"
REDIS_PORT="6379"

# 기준 날짜 (오늘)
BASE_DATE=$(date +%Y%m%d)
echo "기준 날짜: $BASE_DATE"

# 30일치 데이터 생성 (월간 랭킹용)
echo ""
echo "30일치 일간 랭킹 데이터 생성 중..."

for i in {0..29}; do
    # i일 전 날짜 계산
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        DATE=$(date -v-${i}d +%Y%m%d)
    else
        # Linux
        DATE=$(date -d "$i days ago" +%Y%m%d)
    fi

    KEY="ranking:all:$DATE"

    echo "[$((30-i))/30] 생성 중: $KEY"

    # 상품 100개에 대해 랜덤 점수 생성
    for PRODUCT_ID in {1..100}; do
        # 일자가 최근일수록 점수를 높게 설정 (최근 데이터 가중치)
        BASE_SCORE=$((100 - i))
        RANDOM_SCORE=$((RANDOM % 50))
        SCORE=$((BASE_SCORE + RANDOM_SCORE))

        # Redis ZADD
        redis-cli -h $REDIS_HOST -p $REDIS_PORT ZADD $KEY $SCORE "product:$PRODUCT_ID" > /dev/null
    done
done

echo ""
echo "========================================="
echo "✅ 데이터 생성 완료!"
echo "========================================="

# 생성된 키 확인
echo ""
echo "생성된 Redis 키 목록:"
redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS "ranking:all:*" | head -10
echo "..."

# 샘플 데이터 확인
echo ""
echo "샘플 데이터 확인 (가장 최근 날짜):"
LATEST_KEY="ranking:all:$BASE_DATE"
echo "Key: $LATEST_KEY"
redis-cli -h $REDIS_HOST -p $REDIS_PORT ZREVRANGE $LATEST_KEY 0 9 WITHSCORES

echo ""
echo "========================================="
echo "다음 명령어로 주간/월간 배치를 실행하세요:"
echo ""
echo "# 주간 랭킹 (최근 7일)"
if [[ "$OSTYPE" == "darwin"* ]]; then
    YEAR_WEEK=$(date -v-1d +%G-W%V)
else
    YEAR_WEEK=$(date -d "1 day ago" +%G-W%V)
fi
echo "curl -X POST \"http://localhost:8085/batch/weekly?yearWeek=$YEAR_WEEK\""
echo ""
echo "# 월간 랭킹 (이번 달)"
YEAR_MONTH=$(date +%Y-%m)
echo "curl -X POST \"http://localhost:8085/batch/monthly?yearMonth=$YEAR_MONTH\""
echo "========================================="
