# PR หน้าเว็บ Self-Service — ข้อความเตรียมส่ง

base: develop; compare: Pathiphan_6733805892_03; reviewer: PEEMDECH009

## Title

feat: add self-service web pages, security tests and diagrams

## Description

เพิ่มหน้าเว็บดูเครื่องและกรองสาขา/ประเภท/สถานะ, จอง, ประวัติของลูกค้า และหน้าพนักงานเปิดใช้/ปิดซ่อมเครื่อง ใช้ layout และ Service เดียวกับ REST API ของทีม พร้อม Sequence Diagram 3 scenario, Machine State Diagram และอัปเดตรายงาน/SOLID/Pattern ส่วนปอนด์

- form รับเฉพาะเวลาและระยะเวลา เจ้าของมาจาก login และราคาคำนวณใน server
- ใช้ @PreAuthorize และ CSRF เดิม ไม่แก้ SecurityConfig requestMatchers
- รวม develop 6a877cd หลัง PR revert #24 แล้ว ไม่ใช้ getPayableSummary
- ผลทดสอบ: 335 tests, 0 failures, 0 errors, 0 skipped เมื่อ LAUNDRY_DB_TESTS=true
- ขอให้ตรวจสิทธิ์ owner/staff, lifecycle/maintenance, form validation และ diagrams เทียบโค้ดจริง
- ยังไม่ได้ยืนยัน deployment หรือภาพหน้าเว็บใน browser; ไม่มีหน้าชำระเงินใหม่ใน PR นี้ Payment API เดิมยังใช้งานได้
- มีข้อแตกต่างจาก brief ที่บันทึกไว้: lifecycle ผิดสถานะตอบ 400 ตาม shared handler, อ่านเครื่องต้อง login, start ไม่บังคับ payment/clock window และ Machine event ยังไม่มี listener

## ตัวอย่างคอมเมนต์ให้โชกุนอ่านก่อนเลือกใช้

### SelfServiceWebController.java บรรทัด 87–94

form นี้รับเฉพาะเวลาเริ่มและระยะเวลา ส่วน userId มาจาก SecurityUtils และ amount คำนวณใน SessionService ทำให้การส่ง userId หรือ amount ปลอมใน form ไม่เปลี่ยนเจ้าของหรือราคา เว็บยังใช้ Service เดียวกับ API จึงได้ overlap validation และ transaction/lock ชุดเดียวกันครับ

### SelfServiceWebSecurityTest.java บรรทัด 83–98

เทสต์สองส่วนนี้ตรวจการปลอม userId/amount และคำสั่ง POST ที่ไม่มี CSRF โดยตรวจว่าคำขอที่ไม่มี CSRF ไม่ถึง Service ช่วยยืนยันว่าฟอร์มไม่ได้เปิดทางข้ามกฎสิทธิ์และการคำนวณฝั่ง server ครับ

### SelfServiceWebController.java บรรทัด 111–115

หน้าประวัติใช้ userId จาก login ทั้งใน argument เจ้าของและ actor ไม่รับ userId จาก query ส่วนรายละเอียดเครื่องจะโหลดประวัติของเครื่องเฉพาะ STAFF/ADMIN จึงไม่เปิดเผยรอบของลูกค้าคนอื่นให้ผู้ใช้ทั่วไปครับ

### SessionPaymentIntegrationTest.java บรรทัด 100–125

กรณีนี้ส่ง form ผ่าน Security/Controller/Service/PostgreSQL จริง ตรวจว่าเจ้าของมาจาก login และยอด 65 บาทคำนวณจาก 20 + 1.50 × 30 จากนั้นยืนยันว่าผู้ใช้อื่นเริ่มรอบไม่ได้ และเมื่อเจ้าของเริ่ม/จบรอบ สถานะ Session เป็น COMPLETED และ Machine กลับ AVAILABLE ไม่ได้ mock Service ครับ

## ตัวอย่างสรุป Review changes

อ่าน diff หน้าเว็บและเทสต์แล้ว Controller ใช้ interface ของ Service เดิมและ shared layout มีการตรวจ role/CSRF และไม่รับ owner/amount จาก form เทสต์ครอบคลุม template render, validation, forged input และการจอง/lifecycle กับ PostgreSQL จริง มี Sequence 3 scenario และ Machine State Diagram ที่แยก Machine RESERVED ออกจากการจองจริงอย่างชัดเจน ผู้เปิด PR แนบผลทดสอบรวม 335 รายการผ่านหลังรวม PR revert #24 ส่วน deployment, payment web และข้อแตกต่างจาก brief ระบุไว้แล้ว ขอให้ผู้รีวิวตรวจ diff และผล CI ก่อนเลือก Approve, Comment หรือ Request changes ตามความเห็นของตนเอง
