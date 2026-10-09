# Self-Service web pages

หน้าเว็บใช้ layout ของทีมและ Service เดียวกับ REST API ไม่มีสูตรราคา/กฎ overlap แยกอีกชุด

| Route | สิทธิ์ | หน้าที่ |
|---|---|---|
| GET /machines | ล็อกอิน | เครื่องแบบแบ่งหน้า กรองสาขา/ประเภท/สถานะ |
| GET /machines/{id} | ล็อกอิน | รายละเอียด; เฉพาะ STAFF/ADMIN เห็นประวัติของเครื่องและปุ่ม lifecycle |
| GET/POST /machines/{id}/book | CUSTOMER | กรอกเวลา/ระยะเวลา; owner มาจาก login และยอดมาจาก Service |
| GET /sessions/history | CUSTOMER | ประวัติเฉพาะเจ้าของ login แบบแบ่งหน้า |
| POST /sessions/{id}/start, /finish, /cancel | CUSTOMER/STAFF/ADMIN | Service ตรวจเจ้าของหรือสิทธิ์พนักงาน รวมทั้ง lifecycle |
| GET /staff/machines | STAFF/ADMIN | บอร์ดสถานะพร้อม filter/pagination |
| POST /staff/machines/{id}/status | STAFF/ADMIN | เปิดใช้/ปิดซ่อม; ไม่ bypass IN_USE |

ทุก POST มี CSRF จาก Thymeleaf th:action ใช้ @PreAuthorize ไม่แก้ SecurityConfig การกรอกผิดหรือ overlap คืน form พร้อมข้อความและเก็บข้อมูลเดิมไว้ การทำ lifecycle ที่ผิดสถานะใช้ flash message; error สิทธิ์/ไม่พบใช้ shared handler

วันที่และเวลาของ booking เป็น LocalDateTime ตาม timezone แอปเดิม ไม่มีการแปลง UTC ในเว็บชุดนี้ ระยะเวลา 10–180 นาที สถานะเครื่องแสดงสถานะปัจจุบัน การจองอนาคตยังต้องผ่าน overlap check และไม่เปลี่ยน Machine เป็น RESERVED

ไม่มีหน้าชำระเงินใหม่ในงานชุดนี้ Payment ผ่าน API ของโชกุน ซึ่งมี SessionPayableProvider และ integration test อยู่แล้ว ไม่พึ่ง getPayableSummary ซึ่งถูกลบใน PR #24 แล้ว

## การตรวจ

SelfServiceWebSecurityTest ตรวจ template จริงทั้งบอร์ด/รายละเอียด/จอง/ประวัติ, CSRF, STAFF denial, forged owner/amount, validation, overlap form และ trusted actor ส่วน SessionPaymentIntegrationTest ตรวจ form booking/lifecycle กับ Service และ PostgreSQL จริง หลังรวม develop 6a877cd (PR #24) ผลรวมโปรเจกต์ 335 รายการผ่านเมื่อ LAUNDRY_DB_TESTS=true ลดจาก 339 เพราะ PR revert ลบ summary tests 4 ข้อ

ยังไม่ได้ตรวจภาพใน browser หรือยืนยัน deployment ต้องผ่าน Test + Docker build และ human review ของ PR ก่อน merge
