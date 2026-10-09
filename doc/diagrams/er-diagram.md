# ER Diagram — LaundryHub

ที่มา: `code/src/main/resources/db/migration/V1__init_schema.sql` (PostgreSQL 16, 10 ตาราง)

## ความสัมพันธ์

```mermaid
erDiagram
    users ||--o| customer_profiles : "has profile (1:1)"
    users ||--o{ laundry_orders : "places (1:N)"
    users ||--o{ usage_sessions : "books (1:N)"
    users ||--o{ notifications : "receives (1:N)"
    branches ||--o{ machines : "has (1:N)"
    branches ||--o{ laundry_orders : "receives (1:N)"
    service_types ||--o{ laundry_order_items : "prices (1:N)"
    laundry_orders ||--|{ laundry_order_items : "contains (1:N)"
    machines ||--o{ usage_sessions : "used in (1:N)"
    laundry_orders ||--o| payments : "paid by (1:1)"
    usage_sessions ||--o| payments : "paid by (1:1)"

    users {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password
        varchar role "CUSTOMER|STAFF|ADMIN"
        boolean enabled
        timestamp created_at
    }
    customer_profiles {
        bigint id PK
        bigint user_id FK, UK
        varchar full_name
        varchar phone
        varchar address
    }
    branches {
        bigint id PK
        varchar name
        varchar address
        varchar phone
    }
    service_types {
        bigint id PK
        varchar name UK
        numeric price_per_kg
        numeric express_surcharge
        boolean active
    }
    laundry_orders {
        bigint id PK
        bigint user_id FK
        bigint branch_id FK
        varchar status
        boolean express
        numeric total_weight_kg
        numeric total_amount
        varchar note
        timestamp created_at
        timestamp updated_at
    }
    laundry_order_items {
        bigint id PK
        bigint order_id FK
        bigint service_type_id FK
        varchar item_name
        numeric weight_kg
        numeric subtotal
    }
    machines {
        bigint id PK
        bigint branch_id FK
        varchar name
        varchar machine_type "WASHER|DRYER"
        varchar status
        numeric base_price
        numeric price_per_minute
    }
    usage_sessions {
        bigint id PK
        bigint machine_id FK
        bigint user_id FK
        varchar status
        timestamp start_time
        timestamp end_time
        int duration_minutes
        numeric amount
        timestamp created_at
    }
    payments {
        bigint id PK
        bigint order_id FK, UK "nullable"
        bigint session_id FK, UK "nullable"
        numeric amount
        varchar method "CASH|QR_MOCK|COIN"
        varchar status "PENDING|PAID|FAILED"
        timestamp paid_at
        timestamp created_at
    }
    notifications {
        bigint id PK
        bigint user_id FK
        varchar message
        boolean is_read
        timestamp created_at
    }
```

## ความสัมพันธ์ตามข้อกำหนด

| ชนิด | ความสัมพันธ์ | บังคับด้วย |
|---|---|---|
| One-to-One | `users` ↔ `customer_profiles` | `customer_profiles.user_id` เป็น `UNIQUE` |
| One-to-One | `laundry_orders` ↔ `payments` | `payments.order_id` เป็น `UNIQUE` |
| One-to-One | `usage_sessions` ↔ `payments` | `payments.session_id` เป็น `UNIQUE` |
| One-to-Many | `branches` → `machines` | FK `machines.branch_id` |
| One-to-Many | `users` → `laundry_orders` → `laundry_order_items` | FK `laundry_orders.user_id`, `laundry_order_items.order_id` |
| One-to-Many | `machines` → `usage_sessions` | FK `usage_sessions.machine_id` |
| One-to-Many | `users` → `notifications` | FK `notifications.user_id` |

## ทำไม `payments` มี FK 2 ตัวพร้อม CHECK

การชำระเงินหนึ่งรายการจ่ายให้ "ออเดอร์ฝากซัก" **หรือ** "รอบใช้เครื่อง" อย่างใดอย่างหนึ่งเท่านั้น จึงมี `order_id` และ `session_id` ที่เป็น nullable ทั้งคู่ แล้วใช้ constraint คุม 2 ชั้น

1. **`chk_payment_target`**: บังคับให้มีค่า **เพียงตัวเดียว** (`order_id IS NOT NULL AND session_id IS NULL` หรือกลับกัน) ป้องกันแถวที่ผูกทั้งสองหรือไม่ผูกอะไรเลย
2. **`UNIQUE` บน `order_id` และ `session_id`**: ป้องกันการชำระซ้ำ — 1 ออเดอร์หรือ 1 รอบใช้เครื่องมีใบชำระเงินได้ใบเดียว (PostgreSQL อนุญาตให้ `NULL` ซ้ำได้ ค่า `NULL` ของอีกฝั่งจึงไม่ชนกัน)

ทางเลือกอื่นที่ไม่ใช้: ตาราง `payments` แยกสองตาราง (ซ้ำโค้ด) หรือใช้ `payable_type + payable_id` แบบ polymorphic (ตั้ง FK จริงไม่ได้ เสียความสมบูรณ์เชิงอ้างอิง)
