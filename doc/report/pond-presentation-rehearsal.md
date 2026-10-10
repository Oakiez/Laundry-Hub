# บทซ้อมอธิบาย Self-Service ของปอนด์ (ประมาณ 5 นาที)

Path Java ด้านล่างอยู่ใต้ `code/src/main/java/com/laundryhub/` ตรวจเทียบ develop `bf9d636` หลัง PR #37
ใช้เป็นเนื้อหาสไลด์และบทพูด ต้องเปิดโค้ดและลองอธิบายด้วยคำของตัวเองก่อนนำเสนอ

## 0:00–0:40 ขอบเขตและเส้นทางข้อมูล

“ผมทำเครื่องกับรอบใช้งาน ลูกค้าจอง/ดูประวัติ/เริ่ม/จบ/ยกเลิก พนักงานจัดการเครื่อง
REST กับหน้าเว็บเรียก MachineService และ SessionService ร่วมกัน จึงใช้กฎราคาและสถานะชุดเดียวกัน”
เปิด `controller/web/SelfServiceWebController.java:34` อธิบาย constructor injection และ DTO
PaymentService อ่าน payment IDs รวมครั้งเดียวต่อหน้าที่บรรทัด 43 เพื่อซ่อนปุ่มชำระซ้ำ

## 0:40–1:40 การจองและราคา

เปิด `service/impl/SessionServiceImpl.java:56`: ตรวจเจ้าของและลูกค้า ล็อกเครื่อง ตรวจเวลาซ้อน
คำนวณยอดฝั่ง server แล้วบันทึก session ใน transaction เดียว
เปิด `service/pricing/SelfServicePricing.java:9`: Strategy ใช้ BigDecimal คิด basePrice + rate×minutes
ตัวอย่างเดโม 30 + 1×30 = 60 บาท ผู้ใช้ส่งยอดเงินเองไม่ได้
BookingValidator ตรวจเวลาไม่ย้อนหลัง ช่วง 10–180 นาที และ overlap; ตัว validator ไม่ได้ล็อกเอง

## 1:40–2:40 สถานะกับการจองพร้อมกัน

เปิด `service/state/MachineState.java:5` และ `service/impl/SessionServiceImpl.java:83`
อธิบาย canStart/canFinish ของแต่ละ state; booking ทำ session RESERVED แต่เครื่องยัง AVAILABLE
start ทำทั้งคู่ IN_USE; finish ทำ session COMPLETED และเครื่อง AVAILABLE
cancel รับเฉพาะ RESERVED และไม่เปลี่ยนสถานะเครื่อง เพื่อไม่ปล่อยเครื่องของรอบอื่น
เปิด `repository/MachineRepository.java:18`: PESSIMISTIC_WRITE ทำให้ booking ของเครื่องเดียวกันรอ lock
query overlap ใช้ start < end และ end > start ทำให้รอบติดกันที่ขอบเวลาไม่ชนกัน
การมี @Transactional อย่างเดียวไม่ป้องกันสอง request เห็นช่องว่างพร้อมกัน

## 2:40–3:30 จุดเชื่อม Payment/Notification

เปิด `service/SessionPayableProvider.java:23`: adapter คืน UsageSession ผ่าน Payable
CheckoutFacade ของโชกุนตรวจเจ้าของและสร้าง payment โดยใช้ยอดที่บันทึกจาก server
เปิด `service/impl/SessionServiceImpl.java:185` → `event/NotificationEventListener.java:32`
Session event มี publisher และ listener จริง เป็น Observer ผ่าน Spring events แบบ synchronous
listener เข้าร่วม transaction; DB test ที่เคยรันตรวจ rollback ร่วมกันเมื่อ notification insert ล้มเหลว
Machine event ยังไม่มี listener จึงไม่อ้างว่าส่งแจ้งเตือนเครื่องได้

## 3:30–4:30 เดโมและหลักฐาน

ใช้ลำดับใน `pond-deploy-demo.md`: จอง → QR จำลอง → ตรวจปุ่มชำระหาย → start → finish → เครื่องพร้อมใช้/แจ้งเตือน
UI แสดง “จองแล้ว” → “กำลังใช้งาน” → “เสร็จสิ้น”; เครื่องกลับ “ว่าง” หลังจบ
ถ้า deployment รอโหลด ให้ใช้ภาพหลักฐานที่มี Session 5, Payment 4, Machine 2 แทนและระบุว่าเป็นภาพทดสอบก่อนหน้า
ผล Maven รอบล่าสุดที่บันทึก: 376 รายการ ผ่าน 340 ข้าม 36, ไม่มี failure/error;
36 ข้อเป็น opt-in PostgreSQL suite จึงไม่กล่าวว่ารอบล่าสุดทดสอบ DB ครบ
ผล DB 357/0 skipped เป็นหลักฐานรอบก่อน ไม่ใช่จำนวนล่าสุด

## 4:30–5:00 ข้อจำกัด

“start ตรวจสถานะและเจ้าของ แต่ไม่บังคับเวลาเริ่มหรือชำระก่อนใช้งาน QR เป็นการจำลอง
ยังรอทีมยืนยัน lifecycle 400 เทียบ brief 409 และการอ่านเครื่องต้องล็อกอิน
Class Diagram ของพีชอยู่ใน develop แล้ว และ citation ไฟล์รวมให้โอ๊ครวมจาก section ล่าสุด”

## คำถามที่ควรตอบได้

- ทำไม BigDecimal? เงินควรคำนวณเลขฐานสิบโดยไม่ใช้ floating point; ต้องแยกการเทียบมูลค่ากับ scale
- ทำไมใช้ Strategy? SessionService ขึ้นกับ PricingStrategy interface แยกสูตรออกจาก lifecycle
- State ต่างจาก enum อย่างไร? enum ระบุค่า ส่วน implementation ของ State ระบุ capability ของแต่ละสถานะ
- Factory นับ GoF เพิ่มหรือไม่? ไม่ใช่ Factory Method; เป็น registry factory ที่รวบรวม state beans
- ทำไมล็อก Machine ก่อน Session? ทุก operation ใช้ลำดับเดียวกันและ booking ต้องใช้ lock เครื่องเดียวกัน
- ใครตรวจเจ้าของ? controller ใช้ข้อมูล authentication; SessionService ตรวจเจ้าของอีกชั้น และ CheckoutFacade ตรวจ payment
- ทำไมไม่ cascade remove User/Branch/Machine? เป็น shared data ลบรอบไม่ควรลบข้อมูลหลักตาม
- ทำไม LAZY ไม่พังหน้าเว็บ? map entity เป็น DTO ขณะยังอยู่ใน service transaction
- ทำไมไม่ลบ session แบบ hard delete? ใช้ cancel เพื่อรักษาประวัติรอบและการเชื่อม payment
- เดโมพิสูจน์ concurrency หรือไม่? ไม่; หลักฐานนั้นมาจาก PostgreSQL integration tests แยกต่างหาก

สไลด์แก้ไขได้ 6 หน้าอยู่ที่ [pond-self-service.pptx](../slide/pond-self-service.pptx) พร้อม speaker notes ตามบทพูดนี้ ยังไม่ได้ยืนยันว่าปอนด์ซ้อมพูดแล้ว
