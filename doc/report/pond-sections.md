# ส่วนรายงานของปอนด์ — Self-Service Machine

> ผู้รับผิดชอบ: ปฏิภาณ มะนิลทิพย์ รหัส 673380589-2
> สถานะ ณ 10 ตุลาคม 2569: รายงานนี้อ้างอิงโค้ดจริงใน branch Pathiphan_6733805892_03
> API ผ่าน review ใน PR #22 และ merge เข้า main แล้ว แต่ต้องเปิด PR เข้า develop เพิ่ม เพราะ PR #22 ใช้ base ผิด
> หน้าเว็บและ Sequence/State Diagram ของปอนด์ยังไม่ได้ส่งมอบ ห้ามนำไปสรุปว่าเสร็จแล้ว

## บทที่ 2 ทฤษฎีและเทคโนโลยีที่เกี่ยวข้อง

### State Pattern สำหรับสถานะเครื่อง

การตัดสินใจว่าเครื่องสามารถเริ่ม จบ หรือเข้าสถานะหยุดบริการได้ขึ้นกับสถานะปัจจุบัน ระบบแยกกฎนี้เป็น interface MachineState ซึ่งมี status(), canStart(), canFinish() และ canSetOutOfService() แต่ละ implementation คืนค่าความสามารถของตนเอง Service ใช้ผลดังกล่าวเพื่อตัดสินใจอนุญาตหรือปฏิเสธคำสั่ง ไม่ใช้ UnsupportedOperationException ใน state ที่ไม่รองรับการทำงาน จึงมีสัญญาการเรียกที่สม่ำเสมอ

| สถานะเครื่อง | เริ่มได้ | จบได้ | ตั้งหยุดบริการได้ |
|---|---|---|---|
| AVAILABLE | ได้ | ไม่ได้ | ได้ |
| RESERVED | ไม่ได้ | ไม่ได้ | ได้ |
| IN_USE | ไม่ได้ | ได้ | ไม่ได้ |
| OUT_OF_SERVICE | ไม่ได้ | ไม่ได้ | ได้ |

MachineStateFactory รับ List ของ state จาก Spring ผ่าน constructor แล้วเก็บใน EnumMap เพื่อค้นหาตาม MachineStatus เป็น registry สำหรับเลือก state ไม่ใช่ GoF Factory Method ตามตำรา ค่า RESERVED ของเครื่องมีไว้เผื่อขยาย ส่วน flow ปัจจุบันใช้ RESERVED กับ Session และยังคงให้เครื่อง AVAILABLE จนเริ่มใช้งาน

### Strategy สำหรับการคำนวณราคา

SelfServicePricing implements PricingStrategy ของข้อมูล SelfServicePricingInput ทำให้ SessionService ขึ้นกับ interface การคำนวณราคาแทนรายละเอียดสูตรโดยตรง สูตรคือ basePrice + pricePerMinute × durationMinutes ใช้ BigDecimal สำหรับราคา และตรวจว่าราคาไม่เป็นลบและระยะเวลาอยู่ระหว่าง 10 ถึง 180 นาที ตัวอย่าง ค่าเริ่มต้น 20 บาท ราคาต่อนาที 1.50 บาท ใช้ 30 นาที ได้ยอด 65 บาท

### การตรวจช่วงเวลาซ้อนและการจองพร้อมกัน

ช่วงเวลาเป็นแบบรวมจุดเริ่มและไม่รวมจุดจบ จึงถือว่าซ้อนเมื่อ existing.startTime < new.endTime และ existing.endTime > new.startTime สำหรับเครื่องเดียวกันและ Session ที่มีสถานะ RESERVED หรือ IN_USE หากการจองหนึ่งจบตรงกับเวลาเริ่มของอีกการจองจะไม่ถือว่าซ้อน

การตรวจ query ก่อน insert แม้อยู่ใน transaction ยังไม่เพียงพอเมื่อมีสองคำขอพร้อมกัน เพราะทั้งสองอาจเห็นช่วงว่างเดียวกัน ระบบจึงใช้ PESSIMISTIC_WRITE บนแถวเครื่องก่อนตรวจ overlap และถือ lock จน commit คำขอถัดมารอแล้วตรวจข้อมูลที่บันทึกไปแล้ว การใช้งาน PostgreSQL ตามค่า isolation ของระบบจึงทำให้เหลือเพียงหนึ่ง reservation เมื่อคำขอซ้อนกัน

## บทที่ 3 วิธีดำเนินการและการออกแบบระบบ

### โครงสร้างโมดูล Self-Service

โมดูลแยก Controller → Service → Repository โดย Controller รับ DTO ตรวจ validation และสิทธิ์ ส่วน Service ประสานกฎธุรกิจ การคำนวณราคา transaction และ event Repository รับผิดชอบ query และ lock Mapper แปลง Entity เป็น DTO ภายใน transaction เพื่อไม่ส่ง Entity หรือ LAZY association ไปยัง HTTP client

| ส่วน | คลาสหลัก | หน้าที่ |
|---|---|---|
| Entity | Machine, UsageSession | เครื่องและรอบใช้งาน โดย UsageSession implements Payable |
| Repository | MachineRepository, UsageSessionRepository | ค้นหา แบ่งหน้า ตรวจ overlap และล็อกแถว |
| Service | MachineServiceImpl, SessionServiceImpl | จัดการเครื่องและ flow จอง/เริ่ม/จบ/ยกเลิก |
| กฎแยก | BookingValidator, SelfServicePricing, MachineState | ตรวจเวลา คิดราคา และความสามารถตามสถานะ |
| Controller | MachineApiController, SessionApiController | REST API และสิทธิ์ระดับ endpoint |
| DTO/Mapper | MachineRequest, BookSessionRequest, SessionResponse, MachineMapper, SessionMapper | สัญญาข้อมูลเข้า/ออก |
| เชื่อมชำระเงิน | SessionPayableProvider | ค้น Session ผ่าน PayableProvider กลาง |

Machine อ้าง Branch และ UsageSession อ้าง Machine/User ด้วย ManyToOne แบบ LAZY โดยไม่กำหนด cascade remove ไปยังข้อมูลร่วม Foreign key ของ migration ตรวจความถูกต้องของรหัสอ้างอิง และ unique(branch_id, name) ป้องกันชื่อเครื่องซ้ำในสาขา ฐานข้อมูลเดิมมี index สำหรับ machine_id, start_time, end_time ซึ่งสนับสนุนการค้นช่วงเวลา แต่ไม่ได้แทนที่กฎป้องกัน overlap หรือ lock

### ขั้นตอนการจองและการเปลี่ยนสถานะ

Service ตรวจเจ้าของคำขอและผู้ใช้ที่เป็น CUSTOMER และ enabled จากนั้นล็อก Machine เรียก BookingValidator คำนวณ endTime เรียก PricingStrategy คำนวณ amount และบันทึก Session สถานะ RESERVED ก่อนประกาศ SessionStatusChangedEvent การจองยังไม่เปลี่ยนสถานะเครื่องเป็น RESERVED

Start ตรวจ Session ว่าเป็น RESERVED และ Machine ว่าเริ่มได้ แล้วเปลี่ยนทั้งสองเป็น IN_USE Finish ตรวจ Session ว่า IN_USE และ Machine ว่าจบได้ แล้วเปลี่ยน Session เป็น COMPLETED และ Machine เป็น AVAILABLE Cancel อนุญาตเฉพาะ RESERVED และเปลี่ยนเฉพาะ Session เป็น CANCELLED เพื่อไม่ปล่อยเครื่องที่อาจกำลังใช้งานโดย Session อื่น

Lifecycle อ่าน machineId แบบ scalar ก่อนล็อก Machine แล้วจึงล็อก Session เพื่อใช้ลำดับล็อกเดียวกันทุก operation และลดการล็อกสลับลำดับ การเปลี่ยนสถานะประกาศ event หลัง flush ภายใน transaction เดียวกัน Listener ของ Session ซึ่งทีมชำระเงินจัดทำเป็น synchronous จึง rollback งานหลักด้วยเมื่อบันทึกแจ้งเตือนไม่สำเร็จ MachineStatusChangedEvent มี publisher แล้วแต่ยังไม่มี listener

### REST API และการตรวจสิทธิ์

| Endpoint | การทำงานและสิทธิ์ |
|---|---|
| GET /api/v1/machines และ /{id} | ค้นหา/อ่านสำหรับผู้ล็อกอิน |
| POST, PUT, DELETE /api/v1/machines และ /{id} | CRUD สำหรับ ADMIN |
| PATCH /api/v1/machines/{id}/status | ตั้ง AVAILABLE/OUT_OF_SERVICE สำหรับ STAFF/ADMIN |
| POST /api/v1/machines/{machineId}/sessions | จองโดยเจ้าของหรือ STAFF/ADMIN ที่ทำแทนลูกค้า |
| GET /api/v1/machines/{machineId}/sessions | ประวัติเครื่องสำหรับ STAFF/ADMIN |
| GET /api/v1/users/{userId}/sessions | ประวัติของผู้ล็อกอินเอง |
| GET /api/v1/sessions/{id} | อ่านสำหรับเจ้าของหรือ STAFF/ADMIN |
| PATCH /api/v1/sessions/{id}/start, /finish, /cancel | เปลี่ยนสถานะสำหรับเจ้าของหรือ STAFF/ADMIN |

Controller ใช้ PreAuthorize และอ่าน currentUserId/staff จาก authenticated principal ผ่าน SecurityUtils ไม่รับสิทธิ์เหล่านี้จาก JSON หรือ query parameter SessionService ตรวจเจ้าของอีกชั้นเมื่อเข้าถึง Session รายตัว Request ใช้ Bean Validation และผลแบ่งหน้าใช้ PageResponse ที่มี content, page, size, totalElements และ totalPages มี allowlist ของ sort fields ก่อนส่ง query

สถานะสำเร็จใช้ 201 สำหรับสร้าง 200 สำหรับอ่าน/แก้ไข และ 204 สำหรับลบ การจองซ้อนและชื่อซ้ำตอบ 409 ไม่พบตอบ 404 ไม่มีสิทธิ์ตอบ 403 และยังไม่ล็อกอินตอบ 401 Validation และผิดสถานะ lifecycle ตอบ 400 ตาม BusinessRuleException handler กลาง ซึ่งต่างจาก 409 ในตาราง API ของ brief แต่ตรงกับชนิด exception ที่ brief ระบุในเทสต์ ข้อแตกต่างนี้บันทึกไว้ให้ทีมตรวจรับ ส่วนการอ่านเครื่องต้องล็อกอินตาม Security กลางโดยไม่ได้แก้ requestMatchers

### หลัก SOLID และการเชื่อมโมดูล

SRP แยก BookingValidator สำหรับกฎเวลา PricingStrategy สำหรับราคา และ Mapper สำหรับแปลงข้อมูล DIP ใช้ constructor injection และ interface ของ Service/PricingStrategy ISP ใช้ Payable ที่ให้ข้อมูลเฉพาะรหัส ยอด ประเภท และเจ้าของแก่ระบบชำระเงิน LSP ของ MachineState ใช้ผล boolean แทน exception สำหรับความสามารถที่ไม่รองรับ OCP ของระบบชำระเงินรองรับ SessionPayableProvider เพิ่มผ่าน registry เดิมโดยไม่แก้ CheckoutFacade เพื่อรู้จัก UsageSession โดยตรง

SessionService.findPayable คืนข้อมูลตาม contract แล้ว CheckoutFacade ตรวจเจ้าของใน payment transaction รอบนอก ยอด payment มาจาก amount ที่บันทึกฝั่ง server การเพิ่ม provider ไม่ได้เพิ่มการตรวจสิทธิ์ลงใน getter ของ Entity

## บทที่ 4 ผลการดำเนินงาน

### ผลการทดสอบโมดูล Self-Service

ผลทดสอบล่าสุดวันที่ 10 ตุลาคม 2569 บนฐาน develop 6a877cd พร้อมหน้าเว็บใน branch ปอนด์ มี 335 รายการ Failures 0 Errors 0 Skipped 0 และ BUILD SUCCESS โดยเปิด LAUNDRY_DB_TESTS=true รอบก่อน revert ผ่าน 339 และลด 4 ข้อเพราะ PR #24 ลบเทสต์ getPayableSummary ตัวเลขเป็นจำนวนทั้งโปรเจกต์ ไม่ใช่จำนวนเทสต์ที่ปอนด์เขียนทั้งหมด Log รอบล่าสุดอยู่ใน code/target/pond-revert-sync-tests.log ซึ่งเป็น build output ไม่ได้ commit เข้ารายงาน

| ประเภท | หลักฐานที่ตรวจ |
|---|---|
| Pricing | สูตรราคา ค่าขอบเขต 10/180 นาที และปฏิเสธค่าที่ผิด |
| BookingValidator/Repository | เวลาซ้อน ติดขอบ เวลาอดีต เครื่องเสีย และ association กับฐานข้อมูล |
| State/Service | ความสามารถแต่ละ state เจ้าของ และการเปลี่ยนทั้ง Session/Machine |
| MVC Security | ADMIN CRUD, STAFF status, owner history, validation และตัวตนที่ client ปลอมไม่ได้ |
| PostgreSQL concurrency | ตรวจว่าคำขอทั้งสองรอ lock จริง แล้วเกิดการจอง/เริ่มสำเร็จเพียงหนึ่งรายการ |
| Transaction rollback | บังคับ notification insert ล้มเหลวแล้วสถานะ Session/Machine ไม่เปลี่ยน |
| HTTP integration | จองราคา 65 บาท → overlap 409 → non-owner 403 → COIN PAID → start → finish → AVAILABLE |

เทสต์ฐานข้อมูลสร้าง schema ชื่อสุ่มแยกจาก public รัน Flyway ใน schema นั้น และลบเฉพาะ schema ของเทสต์เมื่อจบ ไม่แก้ข้อมูลใช้งานจริง เทสต์ concurrency ใช้ transaction แยกใน worker thread เพื่อให้ตรวจการแข่งขันจริง ส่วนเทสต์ HTTP เชื่อม Security, Controller, Service, Repository, Payment และ Notification จริงโดยไม่ mock service

### ผลการรีวิวและข้อจำกัดของหลักฐาน

PR #22 มี review และ approval จากโชกุนก่อน merge เข้า main แต่ใช้ base ผิดจาก workflow ของทีม จากนั้น PR #23 ส่งงานเข้า develop แล้วที่ a21cb2a หน้าเว็บและ diagrams เป็นงานเพิ่มเติมใน branch ปอนด์ที่ต้องเปิด PR ไป develop และให้โชกุนตรวจอีกครั้ง การผ่านเทสต์ในเครื่องซึ่งเปิดฐานข้อมูลไม่เท่ากับ CI ทดสอบ PostgreSQL เพราะ workflow ปัจจุบันไม่ได้เปิด opt-in database suite รายละเอียดอยู่ใน doc/self-service-api.md และ doc/test-report/session-lifecycle-pond.md

## บทที่ 5 สรุปผลและข้อเสนอแนะ

### สรุปโมดูลที่ทำแล้ว

ส่งมอบ Entity/Repository, Strategy, State, BookingValidator, MachineService/SessionService, DTO/Mapper, REST API เครื่องและรอบใช้งาน, SessionPayableProvider และเทสต์กฎธุรกิจ/สิทธิ์/ฐานข้อมูล การจองคิดราคาและเวลาใน server ใช้ lock ป้องกันคำขอพร้อมกัน และเปลี่ยนสถานะเครื่องกับรอบใช้งานใน transaction เดียวกัน เพิ่มหน้าเว็บเครื่อง จอง ประวัติ และพนักงานเปลี่ยนสถานะ โดยใช้ layout ของทีมและ Service เดียวกับ API พร้อม Sequence Diagram สาม scenario และ State Diagram ของ Machine ใน branch ปอนด์

### งานที่ยังไม่ส่งมอบและแนวทางพัฒนาต่อ

หน้าเว็บและ diagrams ผ่านการตรวจใน branch แต่ยังต้อง push เปิด PR ให้โชกุนรีวิวและผ่าน CI ก่อน merge เข้า develop ยังไม่ได้ยืนยัน deployment ของงานชุดนี้ Start ปัจจุบันตรวจสถานะ แต่ไม่บังคับช่วงเวลาเริ่มหรือการชำระเงินก่อนใช้งาน Machine event ยังไม่มี listener และ API อ่านเครื่องใช้ข้อกำหนดล็อกอินของทีม หน้าชำระเงินเว็บอยู่นอกงานเว็บชุดนี้ ระบบชำระผ่าน API ของโชกุนได้ตามเทสต์ integration

ก่อนปิดงานควรตกลง HTTP status ของ lifecycle ให้ตรง brief ทุกส่วน ตรวจ flow ผ่านหน้าเว็บจริง และปรับ diagrams/เอกสารรวมตาม branch ที่ส่งมอบล่าสุด แนวทางขยายคือกำหนดนโยบายเริ่มก่อน/หลังเวลาจองและ session เกินเวลา รวมทั้งเลือกผู้รับแจ้งเตือนเครื่องหยุดบริการก่อนเพิ่ม listener

## ภาคผนวก

### หลักฐานส่วนของปอนด์

- โค้ดหลัก: code/src/main/java/com/laundryhub/service/impl/SessionServiceImpl.java และ controller/api/MachineApiController.java, SessionApiController.java
- เอกสาร API: doc/self-service-api.md
- รายงานทดสอบ: doc/test-report/session-lifecycle-pond.md
- วิเคราะห์ส่วนบุคคล: doc/sections/solid-analysis-pond.md และ design-patterns-pond.md
- เทสต์ HTTP: test/java/com/laundryhub/service/SessionPaymentIntegrationTest.java
- เทสต์การแข่งขัน: test/java/com/laundryhub/service/SessionLifecycleIntegrationTest.java
- หน้าเว็บ: code/src/main/java/com/laundryhub/controller/web/SelfServiceWebController.java และ templates/machines/, templates/sessions/
- เทสต์เว็บ: test/java/com/laundryhub/security/SelfServiceWebSecurityTest.java และกรณี realWebFormBooksForAuthenticatedOwnerAndLifecyclePersists ใน SessionPaymentIntegrationTest
- Sequence Diagram: doc/diagrams/sequence-order-create-pond.md, sequence-session-booking-pond.md, sequence-checkout-pond.md
- State Diagram: doc/diagrams/state-machine-pond.md
- ผลตรวจความสอดคล้องกับทีม: doc/report/pond-integration-check.md
