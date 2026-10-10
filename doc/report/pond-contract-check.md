# ข้อแตกต่างของ contract ที่ต้องตกลงกับทีม

ตรวจ 10 ตุลาคม 2569 เทียบโค้ด develop `9a11daa` และ HTTP บน Render
สถานะ: **รอโอ๊ค/โชกุนยืนยัน ยังไม่ถือว่า requirement ทั้งหมดตรงกัน**
BRIEF สี่ไฟล์ต้นฉบับไม่อยู่ใน checkout นี้ จึงอ้างข้อแตกต่างที่บันทึกไว้ในรายงานเดิมของปอนด์
และยังต้องให้ทีมเทียบ BRIEF ต้นฉบับก่อนสรุปการยอมรับ

| ประเด็น | Requirement ที่รายงานเดิมระบุ | ผลจริง/หลักฐาน | สิ่งที่ต้องตัดสินใจ |
|---|---|---|---|
| lifecycle ผิดสถานะ | API table ระบุ 409 | PATCH /api/v1/sessions/1/finish หลัง COMPLETED ได้ 400, message Session must be IN_USE; GlobalExceptionHandler.java:94 map BusinessRuleException เป็น BAD_REQUEST | ยืนยัน 400 เป็น contract กลาง หรือแก้เฉพาะ lifecycle conflict เป็น 409 พร้อม docs/tests หลังตกลง |
| อ่านเครื่องโดยไม่ล็อกอิน | brief ระบุอ่านได้ทุกคน | GET /api/v1/machines โดยไม่ส่ง credentials ได้ 401; SecurityConfig.java:31 anyRequest().authenticated() | ยืนยันต้องล็อกอิน หรือให้โอ๊คกำหนด public GET อย่างจำกัด พร้อม security tests |

Path Java อยู่ใต้ `code/src/main/java/com/laundryhub/` การจองซ้อนใช้ BookingConflictException
แยกจาก BusinessRuleException จึงไม่ควรเปลี่ยน mapping ของ BusinessRuleException ทั้งระบบเพื่อแก้ lifecycle ข้อเดียว
ไม่แก้ requestMatchers ของทีมเอง และไม่ลดสิทธิ์เพื่อให้ตรงตารางโดยไม่มีข้อตกลง

ข้อจำกัดที่พบจริง: start อนุญาตก่อนเวลาเริ่มที่จองไว้ โค้ดตรวจสถานะ/เจ้าของแต่ไม่ได้ตรวจ clock window
หรือบังคับชำระก่อน start (SessionServiceImpl.java:83) เดโมนี้ชำระก่อน start ตามลำดับที่นำเสนอ
จึงไม่ได้พิสูจน์ว่าระบบห้าม start ก่อนชำระ Machine event ไม่มี consumer

ข้อความส่งโอ๊คและโชกุน:

> เดโม Self-Service บน Render ผ่านแล้วครับ เหลือ contract 2 ข้อให้ยืนยัน: ผิดสถานะ lifecycle ตอบ 400 ตาม handler กลาง (ตาราง brief เดิมระบุ 409) และ GET machines ไม่ล็อกอินตอบ 401 (brief เดิมระบุอ่านได้ทุกคน) ต้องการคง contract กลางและระบุข้อแตกต่างในรายงาน หรือแก้ให้ตรง brief ครับ ผมยังไม่ได้เปลี่ยน SecurityConfig/handler กลาง มีหลักฐานใน doc/report/pond-contract-check.md และ start ยังไม่บังคับเวลา/การชำระก่อนเริ่มตามข้อจำกัดที่แจ้งไว้
