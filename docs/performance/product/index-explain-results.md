# 등록일 정렬

## 쿼리

```sql
explain
analyze
SELECT p.id,
       p.brand_id,
       p.created_at,
       p.deleted_at,
       p.description,
       p.name,
       p.price,
       p.updated_at
FROM product p
         inner JOIN like_count lc
                    ON lc.target_id = p.id AND lc.type = 'PRODUCT'
WHERE p.deleted_at IS NULL
ORDER BY p.created_at desc, p.id desc LIMIT 20;
```

## 결과

### 실행계획 (인덱스 없음)

```sql
[inner join]
-> Limit: 20 row(s)  (cost=2.98e+6 rows=20) (actual time=12518..12519 rows=20 loops=1)
-> Nested loop inner join  (cost=2.98e+6 rows=9.69e+6) (actual time=12518..12519 rows=20 loops=1)
-> Sort: p.created_at DESC, p.id DESC  (cost=1.04e+6 rows=9.69e+6) (actual time=12517..12517 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.04e+6 rows=9.69e+6) (actual time=0.361..3444 rows=9.9e+6 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=0.355..3045 rows=10e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.134..0.134 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.128..0.128 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=2.98e+6 rows=20) (actual time=8896..8898 rows=20 loops=1)
-> Nested loop left join  (cost=2.98e+6 rows=9.69e+6) (actual time=8896..8898 rows=20 loops=1)
-> Sort: p.created_at DESC, p.id DESC  (cost=1.04e+6 rows=9.69e+6) (actual time=8896..8896 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.04e+6 rows=9.69e+6) (actual time=0.304..3426 rows=9.9e+6 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=0.302..3029 rows=10e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.0885..0.0886 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.0856..0.0856 rows=1 loops=20)
```

### 실행계획 (조건 인덱스만 존재)

```sql
CREATE INDEX idx_product_deleted_at_id
    ON product (deleted_at, id);

[inner join]
-> Limit: 20 row(s)  (cost=6.03e+6 rows=20) (actual time=28333..28343 rows=20 loops=1)
-> Nested loop inner join  (cost=6.03e+6 rows=4.85e+6) (actual time=28333..28343 rows=20 loops=1)
-> Sort: p.created_at DESC, p.id DESC  (cost=702195 rows=4.85e+6) (actual time=28331..28331 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_id (deleted_at=NULL), with index condition: (p.deleted_at is null)  (cost=702195 rows=4.85e+6) (actual time=0.494..21206 rows=9.9e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.565..0.565 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.56..0.56 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=6.03e+6 rows=20) (actual time=29126..29141 rows=20 loops=1)
-> Nested loop left join  (cost=6.03e+6 rows=4.85e+6) (actual time=29126..29141 rows=20 loops=1)
-> Sort: p.created_at DESC, p.id DESC  (cost=702195 rows=4.85e+6) (actual time=29117..29117 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_id (deleted_at=NULL), with index condition: (p.deleted_at is null)  (cost=702195 rows=4.85e+6) (actual time=0.166..21307 rows=9.9e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=1.22..1.22 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=1.22..1.22 rows=1 loops=20)
```

### 실행계획 (정렬 인덱스만 존재)

```sql
CREATE INDEX idx_product_created_at_id
    ON product (created_at, id);

[inner join]
-> Limit: 20 row(s)  (cost=969168 rows=2) (actual time=1.07..2.07 rows=20 loops=1)
-> Nested loop inner join  (cost=969168 rows=2) (actual time=1.04..2.03 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.96 rows=2) (actual time=0.964..1.61 rows=20 loops=1)
-> Index scan on p using idx_product_created_at_id (reverse)  (cost=1.96 rows=20) (actual time=0.962..1.61 rows=21 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.02..0.0202 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.019..0.0191 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=969168 rows=2) (actual time=0.105..0.345 rows=20 loops=1)
-> Nested loop left join  (cost=969168 rows=2) (actual time=0.104..0.342 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.96 rows=2) (actual time=0.0834..0.208 rows=20 loops=1)
-> Index scan on p using idx_product_created_at_id (reverse)  (cost=1.96 rows=20) (actual time=0.0821..0.205 rows=21 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.00638..0.00646 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.00602..0.00605 rows=1 loops=20)
```

### 실행계획 (조건, 정렬 인덱스 존재)

```sql
CREATE INDEX idx_product_deleted_at_created_at_id
    ON product (deleted_at, created_at, id);

[inner join]
-> Limit: 20 row(s)  (cost=6.05e+6 rows=20) (actual time=0.705..1.02 rows=20 loops=1)
-> Nested loop inner join  (cost=6.05e+6 rows=4.85e+6) (actual time=0.701..1.02 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=715225 rows=4.85e+6) (actual time=0.656..0.683 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_created_at_id (deleted_at=NULL) (reverse)  (cost=715225 rows=4.85e+6) (actual time=0.653..0.675 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.0157..0.0159 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.0143..0.0144 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=6.05e+6 rows=20) (actual time=0.655..0.827 rows=20 loops=1)
-> Nested loop left join  (cost=6.05e+6 rows=4.85e+6) (actual time=0.637..0.808 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=715225 rows=4.85e+6) (actual time=0.581..0.599 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_created_at_id (deleted_at=NULL) (reverse)  (cost=715225 rows=4.85e+6) (actual time=0.58..0.596 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.00952..0.00964 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.00785..0.00788 rows=1 loops=20)
```

---

# 가격 정렬

## 쿼리

```sql
explain
analyze
SELECT p.id,
       p.brand_id,
       p.created_at,
       p.deleted_at,
       p.description,
       p.name,
       p.price,
       p.updated_at
FROM product p
         inner JOIN like_count lc
                    ON lc.target_id = p.id AND lc.type = 'PRODUCT'
WHERE p.deleted_at IS NULL
ORDER BY p.price DESC, p.id DESC LIMIT 20;
```

### 실행계획 (인덱스 없음)

```sql
[inner join]
-> Limit: 20 row(s)  (cost=2.92e+6 rows=20) (actual time=15433..15438 rows=20 loops=1)
-> Nested loop inner join  (cost=2.92e+6 rows=9.69e+6) (actual time=15433..15438 rows=20 loops=1)
-> Sort: p.price DESC, p.id DESC  (cost=1.04e+6 rows=9.69e+6) (actual time=15430..15430 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.04e+6 rows=9.69e+6) (actual time=0.536..3712 rows=9.9e+6 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=0.53..3303 rows=10e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=0.94 rows=1) (actual time=0.395..0.396 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=0.94 rows=1) (actual time=0.38..0.38 rows=1 loops=20)


[left join]
-> Limit: 20 row(s)  (cost=2.92e+6 rows=20) (actual time=11812..11813 rows=20 loops=1)
-> Nested loop left join  (cost=2.92e+6 rows=9.69e+6) (actual time=11812..11813 rows=20 loops=1)
-> Sort: p.price DESC, p.id DESC  (cost=1.04e+6 rows=9.69e+6) (actual time=11811..11811 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.04e+6 rows=9.69e+6) (actual time=0.327..3573 rows=9.9e+6 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=0.326..3167 rows=10e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=0.94 rows=1) (actual time=0.0932..0.0933 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=0.94 rows=1) (actual time=0.0882..0.0882 rows=1 loops=20)
```

### 실행계획 (조건 인덱스만 존재)

```sql
CREATE INDEX idx_product_deleted_at_id
    ON product (deleted_at, id);

[inner join]
-> Limit: 20 row(s)  (cost=6.03e+6 rows=20) (actual time=27890..27898 rows=20 loops=1)
-> Nested loop inner join  (cost=6.03e+6 rows=4.85e+6) (actual time=27890..27898 rows=20 loops=1)
-> Sort: p.price DESC, p.id DESC  (cost=702495 rows=4.85e+6) (actual time=27883..27883 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_id (deleted_at=NULL), with index condition: (p.deleted_at is null)  (cost=702495 rows=4.85e+6) (actual time=0.189..19894 rows=9.9e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.745..0.746 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.742..0.742 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=6.03e+6 rows=20) (actual time=24984..24992 rows=20 loops=1)
-> Nested loop left join  (cost=6.03e+6 rows=4.85e+6) (actual time=24984..24992 rows=20 loops=1)
-> Sort: p.price DESC, p.id DESC  (cost=702493 rows=4.85e+6) (actual time=24975..24975 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_id (deleted_at=NULL), with index condition: (p.deleted_at is null)  (cost=702493 rows=4.85e+6) (actual time=0.191..18769 rows=9.9e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.883..0.883 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.88..0.88 rows=1 loops=20)
```

### 실행계획 (정렬 인덱스만 존재)

```sql
CREATE INDEX idx_product_price_id
    ON product (price, id);

[inner join]
-> Limit: 20 row(s)  (cost=969168 rows=2) (actual time=0.09..0.264 rows=20 loops=1)
-> Nested loop inner join  (cost=969168 rows=2) (actual time=0.0775..0.25 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.96 rows=2) (actual time=0.0585..0.135 rows=20 loops=1)
-> Index scan on p using idx_product_price_id (reverse)  (cost=1.96 rows=20) (actual time=0.0575..0.133 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.00544..0.00552 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.00506..0.00509 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=969168 rows=2) (actual time=0.143..0.327 rows=20 loops=1)
-> Nested loop left join  (cost=969168 rows=2) (actual time=0.139..0.321 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.96 rows=2) (actual time=0.121..0.21 rows=20 loops=1)
-> Index scan on p using idx_product_price_id (reverse)  (cost=1.96 rows=20) (actual time=0.119..0.207 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.00522..0.00531 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.00486..0.00489 rows=1 loops=20)
```

### 실행계획 (조건, 정렬 인덱스 존재)

```sql
CREATE INDEX idx_product_deleted_at_price_id
    ON product (deleted_at, price, id);

[inner join]
-> Limit: 20 row(s)  (cost=6.05e+6 rows=20) (actual time=0.705..0.869 rows=20 loops=1)
-> Nested loop inner join  (cost=6.05e+6 rows=4.85e+6) (actual time=0.696..0.859 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=715225 rows=4.85e+6) (actual time=0.654..0.673 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_price_id (deleted_at=NULL) (reverse)  (cost=715225 rows=4.85e+6) (actual time=0.652..0.669 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.00854..0.00863 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.00759..0.00762 rows=1 loops=20)


[left join]
-> Limit: 20 row(s)  (cost=6.05e+6 rows=20) (actual time=0.659..0.789 rows=20 loops=1)
-> Nested loop left join  (cost=6.05e+6 rows=4.85e+6) (actual time=0.641..0.769 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=715225 rows=4.85e+6) (actual time=0.617..0.63 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_price_id (deleted_at=NULL) (reverse)  (cost=715225 rows=4.85e+6) (actual time=0.615..0.626 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.0065..0.00661 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.00603..0.00607 rows=1 loops=20)
```

---

# 좋아요 정렬

## 쿼리

```sql
explain
analyze
SELECT p.id,
       p.brand_id,
       p.created_at,
       p.deleted_at,
       p.description,
       p.name,
       p.price,
       p.updated_at
FROM product p
         left JOIN like_count lc
                   ON lc.target_id = p.id AND lc.type = 'PRODUCT'
WHERE p.deleted_at IS NULL
ORDER BY lc.count DESC, lc.target_id DESC LIMIT 20;
```

### 실행계획 (인덱스 없음 - like_count uk 인덱스 존재(type,target_id))

```sql
[inner join]
-> Limit: 20 row(s)  (actual time=81209..81209 rows=20 loops=1)
-> Sort: lc.count DESC, lc.target_id DESC, limit input to 20 row(s) per chunk  (actual time=81209..81209 rows=20 loops=1)
-> Stream results  (cost=2.07e+6 rows=969166) (actual time=31.5..80142 rows=9.9e+6 loops=1)
-> Nested loop inner join  (cost=2.07e+6 rows=969166) (actual time=31..77167 rows=9.9e+6 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.04e+6 rows=969166) (actual time=18.8..15386 rows=9.9e+6 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=18.8..14945 rows=10e+6 loops=1)
-> Single-row index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT'), with index condition: (lc.`type` = 'PRODUCT')  (cost=0.963 rows=1) (actual time=0.00611..0.00613 rows=1 loops=9.9e+6)

[left join]
-> Limit: 20 row(s)  (actual time=58905..58905 rows=20 loops=1)
-> Sort: lc.count DESC, lc.target_id DESC, limit input to 20 row(s) per chunk  (actual time=58905..58905 rows=20 loops=1)
-> Stream results  (cost=2.08e+6 rows=969166) (actual time=6.19..57851 rows=9.9e+6 loops=1)
-> Nested loop left join  (cost=2.08e+6 rows=969166) (actual time=6.17..54990 rows=9.9e+6 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.04e+6 rows=969166) (actual time=3.66..13206 rows=9.9e+6 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=3.65..12767 rows=10e+6 loops=1)
-> Single-row index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT'), with index condition: <if>(is_not_null_compl(lc), (lc.`type` = 'PRODUCT'), true)  (cost=0.967 rows=1) (actual time=0.0041..0.00412 rows=1 loops=9.9e+6)
```

### 실행계획 (조건 인덱스만 존재 - like_count uk 인덱스 존재(type,target_id))

```sql
CREATE INDEX idx_product_deleted_at_id
    ON product (deleted_at, id);

[inner join]
-> Limit: 20 row(s)  (cost=6.83e+6 rows=20) (actual time=4780..4780 rows=20 loops=1)
-> Nested loop inner join  (cost=6.83e+6 rows=4.86e+6) (actual time=4780..4780 rows=20 loops=1)
-> Sort: lc.count DESC, lc.target_id DESC  (cost=1.01e+6 rows=9.71e+6) (actual time=4780..4780 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1.01e+6 rows=9.71e+6) (actual time=0.937..2397 rows=10e+6 loops=1)
-> Table scan on lc  (cost=1.01e+6 rows=9.71e+6) (actual time=0.922..1615 rows=10e+6 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1 rows=0.5) (actual time=0.0108..0.0108 rows=1 loops=20)
-> Single-row index lookup on p using PRIMARY (id=lc.target_id)  (cost=1 rows=1) (actual time=0.0106..0.0106 rows=1 loops=20)


[left join]
-> Limit: 20 row(s)  (actual time=114396..114396 rows=20 loops=1)
-> Sort: lc.count DESC, lc.target_id DESC, limit input to 20 row(s) per chunk  (actual time=114396..114396 rows=20 loops=1)
-> Stream results  (cost=5.65e+6 rows=4.85e+6) (actual time=1.58..113345 rows=9.9e+6 loops=1)
-> Nested loop left join  (cost=5.65e+6 rows=4.85e+6) (actual time=1.56..110406 rows=9.9e+6 loops=1)
-> Index lookup on p using idx_product_deleted_at_id (deleted_at=NULL), with index condition: (p.deleted_at is null)  (cost=712561 rows=4.85e+6) (actual time=0.92..35221 rows=9.9e+6 loops=1)
-> Single-row index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT'), with index condition: <if>(is_not_null_compl(lc), (lc.`type` = 'PRODUCT'), true)  (cost=0.919 rows=1) (actual time=0.00747..0.00749 rows=1 loops=9.9e+6)
```

### 실행계획 (좋아요 인덱스 존재 - 커버링)

```sql
CREATE INDEX idx_like_count_type_count_target_id
    ON like_count (type, count, target_id);

[inner join]
-> Limit: 20 row(s)  (actual time=66576..66576 rows=20 loops=1)
-> Sort: lc.count DESC, lc.target_id DESC, limit input to 20 row(s) per chunk  (actual time=66576..66576 rows=20 loops=1)
-> Stream results  (cost=2.08e+6 rows=969166) (actual time=18.3..65569 rows=9.9e+6 loops=1)
-> Nested loop inner join  (cost=2.08e+6 rows=969166) (actual time=18.2..62690 rows=9.9e+6 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.04e+6 rows=969166) (actual time=12.9..14880 rows=9.9e+6 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=12.9..14442 rows=10e+6 loops=1)
-> Single-row index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT'), with index condition: (lc.`type` = 'PRODUCT')  (cost=0.966 rows=1) (actual time=0.0047..0.00472 rows=1 loops=9.9e+6)

[left join]
-> Limit: 20 row(s)  (actual time=77565..77565 rows=20 loops=1)
-> Sort: lc.count DESC, lc.target_id DESC, limit input to 20 row(s) per chunk  (actual time=77565..77565 rows=20 loops=1)
-> Stream results  (cost=2.08e+6 rows=969166) (actual time=1.21..76500 rows=9.9e+6 loops=1)
-> Nested loop left join  (cost=2.08e+6 rows=969166) (actual time=1.08..73502 rows=9.9e+6 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1.04e+6 rows=969166) (actual time=0.611..16675 rows=9.9e+6 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=0.602..16212 rows=10e+6 loops=1)
-> Single-row index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT'), with index condition: <if>(is_not_null_compl(lc), (lc.`type` = 'PRODUCT'), true)  (cost=0.965 rows=1) (actual time=0.00561..0.00563 rows=1 loops=9.9e+6)
```

### 실행계획 (조건, 정렬 인덱스 존재 - 커버링)

```sql
CREATE INDEX idx_product_deleted_at_id
    ON product (deleted_at, id);

CREATE INDEX idx_like_count_type_count_target_id
    ON like_count (type, count, target_id);

[inner join]
-> Limit: 20 row(s)  (cost=5.84e+6 rows=20) (actual time=1.53..1.7 rows=20 loops=1)
-> Nested loop inner join  (cost=5.84e+6 rows=2.43e+6) (actual time=1.52..1.68 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=500377 rows=4.86e+6) (actual time=1.4..1.41 rows=20 loops=1)
-> Covering index lookup on lc using idx_like_count_type_count_target_id (type='PRODUCT') (reverse)  (cost=500377 rows=4.86e+6) (actual time=1.39..1.4 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=1 rows=0.5) (actual time=0.0127..0.0128 rows=1 loops=20)
-> Single-row index lookup on p using PRIMARY (id=lc.target_id)  (cost=1 rows=1) (actual time=0.0117..0.0118 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (actual time=85180..85180 rows=20 loops=1)
-> Sort: lc.count DESC, lc.target_id DESC, limit input to 20 row(s) per chunk  (actual time=85180..85180 rows=20 loops=1)
-> Stream results  (cost=5.9e+6 rows=4.85e+6) (actual time=0.291..84085 rows=9.9e+6 loops=1)
-> Nested loop left join  (cost=5.9e+6 rows=4.85e+6) (actual time=0.284..81063 rows=9.9e+6 loops=1)
-> Index lookup on p using idx_product_deleted_at_id (deleted_at=NULL), with index condition: (p.deleted_at is null)  (cost=708508 rows=4.85e+6) (actual time=0.211..31339 rows=9.9e+6 loops=1)
-> Single-row index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT'), with index condition: <if>(is_not_null_compl(lc), (lc.`type` = 'PRODUCT'), true)  (cost=0.971 rows=1) (actual time=0.00489..0.00491 rows=1 loops=9.9e+6)
```

---

# 브랜드 아이디 조건 추가

## 쿼리

```sql
explain
analyze
SELECT p.id,
       p.brand_id,
       p.created_at,
       p.deleted_at,
       p.description,
       p.name,
       p.price,
       p.updated_at
FROM product p
         inner jOIN like_count lc
                    ON lc.target_id = p.id AND lc.type = 'PRODUCT'
WHERE p.deleted_at IS NULL
  AND p.brand_id = 100
ORDER BY p.created_at desc, p.id desc LIMIT 20;
```

### 실행계획 (인덱스 없음)

```sql
[inner join]
-> Limit: 20 row(s)  (cost=2.11e+6 rows=20) (actual time=3517..3518 rows=20 loops=1)
-> Nested loop inner join  (cost=2.11e+6 rows=9.69e+6) (actual time=3517..3518 rows=20 loops=1)
-> Sort: p.created_at DESC, p.id DESC  (cost=1.04e+6 rows=9.69e+6) (actual time=3517..3517 rows=20 loops=1)
-> Filter: ((p.brand_id = 100) and (p.deleted_at is null))  (cost=1.04e+6 rows=9.69e+6) (actual time=0.25..3508 rows=10010 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=0.172..3177 rows=10e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=0.982 rows=1) (actual time=0.0397..0.0398 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=0.982 rows=1) (actual time=0.038..0.038 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=2.11e+6 rows=20) (actual time=3014..3014 rows=20 loops=1)
-> Nested loop left join  (cost=2.11e+6 rows=9.69e+6) (actual time=3014..3014 rows=20 loops=1)
-> Sort: p.created_at DESC, p.id DESC  (cost=1.04e+6 rows=9.69e+6) (actual time=3014..3014 rows=20 loops=1)
-> Filter: ((p.brand_id = 100) and (p.deleted_at is null))  (cost=1.04e+6 rows=9.69e+6) (actual time=0.485..3009 rows=10010 loops=1)
-> Table scan on p  (cost=1.04e+6 rows=9.69e+6) (actual time=0.338..2690 rows=10e+6 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.00323..0.00328 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.00296..0.00298 rows=1 loops=20)

```

### 실행계획 (조건 인덱스만 존재)

```sql
CREATE INDEX idx_product_deleted_at_brand_id_id
    ON product (deleted_at, brand_id, id);

[inner join]
-> Limit: 20 row(s)  (cost=39403 rows=20) (actual time=110..110 rows=20 loops=1)
-> Nested loop inner join  (cost=39403 rows=18514) (actual time=110..110 rows=20 loops=1)
-> Sort: p.created_at DESC, p.id DESC  (cost=19037 rows=18514) (actual time=109..109 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_brand_id_id (deleted_at=NULL, brand_id=100), with index condition: (p.deleted_at is null)  (cost=19037 rows=18514) (actual time=0.698..105 rows=10010 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.0277..0.0277 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.0271..0.0271 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=39486 rows=20) (actual time=186..187 rows=20 loops=1)
-> Nested loop left join  (cost=39486 rows=18514) (actual time=186..187 rows=20 loops=1)
-> Sort: p.created_at DESC, p.id DESC  (cost=19121 rows=18514) (actual time=186..186 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_brand_id_id (deleted_at=NULL, brand_id=100), with index condition: (p.deleted_at is null)  (cost=19121 rows=18514) (actual time=1.99..181 rows=10010 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.0346..0.0346 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.0342..0.0342 rows=1 loops=20)
```

### 실행계획 (정렬 인덱스만 존재)

```sql
CREATE INDEX idx_product_created_at_id
    ON product (created_at, id);

[inner join]
-> Limit: 20 row(s)  (cost=96919 rows=0.2) (actual time=96.7..100 rows=20 loops=1)
-> Nested loop inner join  (cost=96919 rows=0.2) (actual time=96.7..100 rows=20 loops=1)
-> Filter: ((p.brand_id = 100) and (p.deleted_at is null))  (cost=2.13 rows=0.2) (actual time=96.6..99.7 rows=20 loops=1)
-> Index scan on p using idx_product_created_at_id (reverse)  (cost=2.13 rows=20) (actual time=1.91..98.8 rows=8160 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.0246..0.0247 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.0237..0.0238 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=96919 rows=0.2) (actual time=53.5..56.4 rows=20 loops=1)
-> Nested loop left join  (cost=96919 rows=0.2) (actual time=53.5..56.4 rows=20 loops=1)
-> Filter: ((p.brand_id = 100) and (p.deleted_at is null))  (cost=2.13 rows=0.2) (actual time=53.4..56 rows=20 loops=1)
-> Index scan on p using idx_product_created_at_id (reverse)  (cost=2.13 rows=20) (actual time=0.466..55.4 rows=8160 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.0192..0.0193 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.0186..0.0187 rows=1 loops=20)
```

### 실행계획 (조건, 정렬 인덱스 존재)

```sql
CREATE INDEX idx_product_deleted_at_brand_id_created_at_id
    ON product (deleted_at, brand_id, created_at, id);
[inner join]
-> Limit: 20 row(s)  (cost=41335 rows=20) (actual time=0.242..0.302 rows=20 loops=1)
-> Nested loop inner join  (cost=41335 rows=18794) (actual time=0.235..0.294 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=20662 rows=18794) (actual time=0.212..0.216 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_brand_id_created_at_id (deleted_at=NULL, brand_id=100) (reverse)  (cost=20662 rows=18794) (actual time=0.211..0.214 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.00347..0.00353 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.00301..0.00303 rows=1 loops=20)

[left join]
-> Limit: 20 row(s)  (cost=41335 rows=20) (actual time=0.78..0.91 rows=20 loops=1)
-> Nested loop left join  (cost=41335 rows=18794) (actual time=0.747..0.875 rows=20 loops=1)
-> Filter: (p.deleted_at is null)  (cost=20662 rows=18794) (actual time=0.566..0.577 rows=20 loops=1)
-> Index lookup on p using idx_product_deleted_at_brand_id_created_at_id (deleted_at=NULL, brand_id=100) (reverse)  (cost=20662 rows=18794) (actual time=0.564..0.575 rows=20 loops=1)
-> Filter: (lc.`type` = 'PRODUCT')  (cost=1 rows=1) (actual time=0.0126..0.0126 rows=1 loops=20)
-> Single-row covering index lookup on lc using uk_like_count_target (target_id=p.id, type='PRODUCT')  (cost=1 rows=1) (actual time=0.00617..0.00619 rows=1 loops=20)
```