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

หลัง PR #26 ของโชกุน merge เข้า develop หน้าเว็บชำระเงินอยู่ที่ /payments/new ประวัติรอบใช้งานมีลิงก์ /payments/new?type=USAGE_SESSION&id=<sessionId> สำหรับรอบ RESERVED, IN_USE และ COMPLETED เพื่อกรอกประเภทกับเลขรอบให้อัตโนมัติ ไม่แสดงลิงก์ในรอบ CANCELLED ลิงก์ไม่ส่ง amount หรือ userId และยังไม่ยืนยันว่ารอบชำระแล้วหรือยัง เพราะ SessionResponse ไม่มี payment status หากชำระซ้ำ PaymentService จะปฏิเสธตามกฎเดิม ไม่พึ่ง getPayableSummary ที่ลบใน PR #24

## การตรวจ

SelfServiceWebSecurityTest ตรวจ template จริงทั้งบอร์ด/รายละเอียด/จอง/ประวัติ, CSRF, STAFF denial, forged owner/amount, validation, overlap form และ trusted actor รวมทั้งลิงก์ชำระเงินในสามสถานะและการซ่อนลิงก์ของรอบยกเลิก ส่วน SessionPaymentIntegrationTest ตรวจ form booking/lifecycle กับ Service และ PostgreSQL จริง หลังรวม develop f7c82e1 (PR #26) พร้อมเทสต์ลิงก์ใหม่ ผลรวมโปรเจกต์ 357 รายการผ่านเมื่อ LAUNDRY_DB_TESTS=true วันที่ 10 ตุลาคม 2569

หน้าเว็บและ diagrams ชุดหลัก merge ผ่าน PR #25 แล้ว โดย Test และ Docker build ผ่านทั้งสองรายการ งานลิงก์ชำระเงินเพิ่มเติมต้องผ่าน CI และ human review ใน PR ถัดไป ยังไม่ได้ยืนยัน flow จองและชำระบน deployment ผ่านบัญชีจริง
