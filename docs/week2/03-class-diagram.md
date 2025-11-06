# 클래스 다이어그램 (Class Diagram)

본 문서는 감성 이커머스의 도메인 모델 구조를 시각화합니다.

---

## 1. Products 도메인

![img_3.png](https://github.com/user-attachments/assets/7aaa6268-7eeb-42e5-a30f-b324ba849468)

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

![img_6.png](https://github.com/user-attachments/assets/ed4682ae-1f41-4d4b-8819-f412a08d5fb8)

**주요 구조:**

- **ProductLike**: 사용자의 상품 좋아요 기록 엔티티

**설계 의도:**

- 단순한 관계 엔티티로 별도의 비즈니스 로직 없음

---

## 3. Orders 도메인

![img_5.png](https://github.com/user-attachments/assets/d8074471-d055-4d55-8b21-2c8daf2e1ae0)

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
