# 시퀀스 다이어그램 (Sequence Diagrams)

본 문서는 감성 이커머스의 핵심 유스케이스에 대한 객체 간 상호작용을 시각화합니다.

---

## 1. 상품 목록 조회

사용자가 상품 목록을 조회하는 시나리오입니다. 각 상품의 좋아요 수를 함께 조회하여 인기도를 표시합니다.

![img](https://private-user-images.githubusercontent.com/170931381/510891444-0297db81-3ee3-42cf-9156-9bff1c832b65.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjI0NDgyMzIsIm5iZiI6MTc2MjQ0NzkzMiwicGF0aCI6Ii8xNzA5MzEzODEvNTEwODkxNDQ0LTAyOTdkYjgxLTNlZTMtNDJjZi05MTU2LTliZmYxYzgzMmI2NS5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTA2JTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwNlQxNjUyMTJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT1iYTIwY2MxYjU0ZTgzM2U4YWE4NTQwZGUyZjMxYTIyMGI5MzhjYjczZThkY2QwOTMyYWQ4OTBmODA3NjJlYWY4JlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.ukAa0c9P--FHMLj2_ddNh4dTPSgVibJPO8IvAepcuEI)

**주요 흐름:**

1. 사용자가 페이징, 정렬 기준과 함께 상품 목록 조회를 요청
2. Facade가 정렬 기준에 따라 상품 목록을 조회 (Slice 반환)
3. 조회된 상품 ID 목록으로 좋아요 수를 일괄 조회
4. Facade에서 상품 정보와 좋아요 수를 조합하여 ProductInfo 리스트 생성

**고려사항:**

- ProductLikeCount를 일괄 조회하여 N+1 문제 방지
- 정렬 기준: latest(최신순), price_asc(가격낮은순), likes_desc(인기순)

---

## 2. 상품 좋아요 등록

사용자가 상품에 좋아요를 등록하는 시나리오입니다. 멱등성을 보장하여 중복 등록 시에도 오류 없이 처리됩니다.

![img_1.png](https://private-user-images.githubusercontent.com/170931381/510891239-674305da-6e52-49f2-9f4d-c53425da04b9.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjI0NDgyMzIsIm5iZiI6MTc2MjQ0NzkzMiwicGF0aCI6Ii8xNzA5MzEzODEvNTEwODkxMjM5LTY3NDMwNWRhLTZlNTItNDlmMi05ZjRkLWM1MzQyNWRhMDRiOS5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTA2JTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwNlQxNjUyMTJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT00NjY5ZGU4YjY4ODRjYzg1NTM4NWYwMmY0MjQxMGUyZTljMDJiZjg3NzNmMTA2MWM2ZDkzNjEyN2VkNzBkMGEzJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.JPvItEAM5YPwW9hP_E-g3Q3JUwgw0MA1QTiefdZesco)

**주요 흐름:**

1. 사용자가 특정 상품에 좋아요 등록 요청
2. Facade가 기존 좋아요 존재 여부 확인
3. 이미 존재하면 아무 작업 없이 성공 응답 (멱등성 보장)
4. 존재하지 않으면 ProductLike 생성 및 저장
5. ProductLikeCountRepository의 increment()로 좋아요 수 증가
6. 성공 응답

**고려사항:**

- 멱등성 보장: 중복 등록 시도에도 오류 없이 성공 응답
- 좋아요 수 증가는 Repository 레벨에서 원자적으로 처리

---

## 3. 주문 생성 및 결제

사용자가 상품을 주문하고 결제하는 시나리오입니다. 전액 포인트 결제 여부에 따라 외부 PG 호출 여부가 결정됩니다.

![img_2.png](https://private-user-images.githubusercontent.com/170931381/510891297-8fbf47e9-4716-4924-bdf0-cb15d76fd81b.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjI0NDgyMzIsIm5iZiI6MTc2MjQ0NzkzMiwicGF0aCI6Ii8xNzA5MzEzODEvNTEwODkxMjk3LThmYmY0N2U5LTQ3MTYtNDkyNC1iZGYwLWNiMTVkNzZmZDgxYi5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTA2JTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwNlQxNjUyMTJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT1lMzA1NGRiMTEwZTFhMzA4NDgxOTljMjJiZjNjZDNmYjRmMTgyNDJkMDI5MjM3YzNkYWYxZjg5OWE2NzZkMDQzJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.jq3q5NqcxW1UTFa0YxqHrKrHq1vGPwmMEn6ADfA1soY)

**주요 흐름:**

1. 재고를 비관적 락으로 조회하여 차감 (StockManager)
2. 주문을 접수 상태(PLACED)로 생성 (Order.place)
3. 포인트를 차감하고 이력 기록 (PointAccountManager)
4. **결제 처리 (PaymentManager.pay)**
    - Payment 생성 (READY 상태)
    - 결제 처리 필요 여부 판단 (Payment.needsExternalPayment())
    - 외부 결제 필요: PG 시스템에 결제 요청 전송
    - 전액 포인트: 즉시 결제 완료 처리 (PAID 상태)
5. 성공 시 트랜잭션 커밋, 실패 시 자동 롤백

**고려사항:**

- 재고 차감은 비관적 락을 통한 동시성 제어
- 전액 포인트 결제 여부는 Payment 도메인 객체가 판단 (actualPaymentAmount == 0)
- 재고 부족, 포인트 부족, PG 실패 시 트랜잭션 롤백으로 일관성 보장
