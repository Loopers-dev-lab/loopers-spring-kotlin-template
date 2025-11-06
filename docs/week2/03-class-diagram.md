# 클래스 다이어그램 (Class Diagram)

본 문서는 감성 이커머스의 도메인 모델 구조를 시각화합니다.

---

## 1. Products 도메인

![img_3.png](https://private-user-images.githubusercontent.com/170931381/510891365-113ecae4-3379-4e8b-8faa-84a36b1cc6b0.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjI0NDgyMzIsIm5iZiI6MTc2MjQ0NzkzMiwicGF0aCI6Ii8xNzA5MzEzODEvNTEwODkxMzY1LTExM2VjYWU0LTMzNzktNGU4Yi04ZmFhLTg0YTM2YjFjYzZiMC5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTA2JTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwNlQxNjUyMTJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT1kNWQxNDg2NjJjNTBmYTI2MGI1NDkxNzUyMGQxZDI5MzBiYzkxOTg2ZmE3YWFiMDQ3OWRlNGNiYjdiMDE4NTU3JlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.rhMJf1I_nYk6QWc4tgXIvy3I4_zUVeQt8l2B81sFIGw)

**주요 구조:**

- **Brand**: 브랜드 정보 엔티티
- **Product**: 상품 엔티티. 상태 관리 행위(품절/재판매/단종) 포함
- **Stock**: 재고 엔티티. 차감/증가/소진 확인 행위 포함
- **StockManager**: 재고 차감 시 Stock과 Product를 함께 조율하는 도메인 서비스
- **Money**: 금액 값 객체. 금액 계산 로직 캡슐화
- **ProductStatus**: 상품 상태 열거형

**설계 의도:**

- Product와 Stock은 각각 독립적인 애그리게이트 루트
- StockManager가 두 애그리게이트를 조율하여 비즈니스 로직 수행

---

## 2. Likes 도메인

![img_6.png](https://private-user-images.githubusercontent.com/170931381/510891429-c945b66b-a17d-4353-91da-dc5bdd96ccdc.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjI0NDgyMzIsIm5iZiI6MTc2MjQ0NzkzMiwicGF0aCI6Ii8xNzA5MzEzODEvNTEwODkxNDI5LWM5NDViNjZiLWExN2QtNDM1My05MWRhLWRjNWJkZDk2Y2NkYy5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTA2JTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwNlQxNjUyMTJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT0wOTQ1ODFjMjIxMzE4YjRjNWEwNDE5ODVhZTQ1MTRkNjE5MGNlYzI5YWI3NjZiMDY2MmNiNGU0ZmQ0ZTA0ZGNjJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.1Is5yGTO4rmP9D03z5yDbnF5RCJB7-_AjBS7VqaH0eE)

**주요 구조:**

- **ProductLike**: 사용자의 상품 좋아요 기록 엔티티

**설계 의도:**

- 단순한 관계 엔티티로 별도의 비즈니스 로직 없음

---

## 3. Orders 도메인

![img_5.png](https://private-user-images.githubusercontent.com/170931381/510891406-a8e34055-4d62-494d-a564-575a8367c4d9.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjI0NDgyMzIsIm5iZiI6MTc2MjQ0NzkzMiwicGF0aCI6Ii8xNzA5MzEzODEvNTEwODkxNDA2LWE4ZTM0MDU1LTRkNjItNDk0ZC1hNTY0LTU3NWE4MzY3YzRkOS5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTA2JTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwNlQxNjUyMTJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT05MmQwMGEyMDgyZjI5YzRhNjBhZWFlNjNjODAyMjRiNjk4YTNmYWU5NGZiMGEyOWIwM2ViYjU2Y2RmZWZhYjI5JlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.s6VZmA1qRUmFviUhAuvaKFYU4uJCYPhuH-0lbMCn31E)

**주요 구조:**

- **Order**: 주문 엔티티. 주문 생성(place) 및 결제 완료(pay) 행위 포함
- **OrderItem**: 주문 항목 엔티티. 주문 시점의 상품 정보 스냅샷
- **Payment**: 결제 엔티티. 결제 생성, 외부 결제 필요 여부 판단, 완료 처리 행위 포함
- **PaymentManager**: 결제 생성 및 PG 호출 조율을 담당하는 도메인 서비스
- **OrderStatus**: 주문 상태 열거형
- **PaymentStatus**: 결제 상태 열거형

**설계 의도:**

- Order와 OrderItem은 하나의 애그리게이트 (강한 일관성)
- Payment는 독립적인 애그리게이트
- PaymentManager가 결제 프로세스 전체를 조율
