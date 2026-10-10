# ตรวจ citation ส่วนปอนด์ — 10 ตุลาคม 2569

ตรวจเทียบโค้ด develop `9a11daa` หลัง PR #34 merge แล้ว ไม่มีการแก้ไฟล์รวมของโอ๊คโดยตรง
ไฟล์ต้นทาง `doc/sections/solid-analysis-pond.md` และ `design-patterns-pond.md` ใช้เลขบรรทัดปัจจุบัน
Path Java ในตารางต่อไปนี้อยู่ใต้ `code/src/main/java/com/laundryhub/`

| ไฟล์รวมและบรรทัดที่พบ | Citation เดิม | Citation ที่ถูก | หลักฐาน |
|---|---|---|---|
| doc/solid-analysis.md:47 | service/impl/MachineServiceImpl.java:32 | service/impl/MachineServiceImpl.java:34 | ประกาศ class |
| doc/solid-analysis.md:48 | mapper/SessionMapper.java:7 | mapper/SessionMapper.java:6 | ประกาศ class |
| doc/solid-analysis.md:83 | service/impl/SessionServiceImpl.java:42 | service/impl/SessionServiceImpl.java:41 | constructor |
| doc/solid-analysis.md:184 | service/impl/MachineServiceImpl.java:40 | service/impl/MachineServiceImpl.java:42 | constructor |
| doc/design-patterns.md:208 | service/impl/SessionServiceImpl.java:42 | service/impl/SessionServiceImpl.java:41 | constructor |

เพิ่มหลักฐาน Observer ที่ใช้งานจริงใน section ของปอนด์: publisher ที่
`service/impl/SessionServiceImpl.java:185` และ consumer ที่
`event/NotificationEventListener.java:32` ส่วน MachineStatusChangedEvent ยังไม่มี listener

ข้อความส่งโอ๊ค:

> ตรวจ citation ส่วนผมกับ develop 9a11daa แล้วครับ รบกวนรวม solid-analysis-pond.md และ design-patterns-pond.md ใหม่ มี 5 จุดในไฟล์รวมที่เลขเก่า: MachineService class 32→34, constructor 40→42, SessionMapper 7→6 และ SessionService constructor 42→41 ทั้ง SOLID กับ Patterns เพิ่ม publisher/consumer ของ Session Observer พร้อมหลักฐานเดโมแล้ว ผมไม่ได้แก้ไฟล์รวมโดยตรงครับ หลังรันรวมรบกวนตรวจเลขอีกรอบถ้าโค้ดเปลี่ยน

ข้อจำกัด: เลขบรรทัดถูก ณ revision ที่ระบุ การแก้โค้ดหรือเพิ่ม import ภายหลังต้องตรวจใหม่

## ตรวจเพิ่มหลัง PR #37

รวม develop bf9d636 แล้ว SelfServiceWebController constructor เลื่อน 30→34 และเพิ่ม PaymentService; addPaidSessionIds อยู่บรรทัด 43 อ่าน payment IDs รวมต่อหน้า ไฟล์ section/rehearsal และ notes สไลด์ปรับตามแล้ว โอ๊คควรรวม section ใหม่และตรวจ citation ไฟล์รวมอีกครั้ง ตารางด้านบนเป็น audit ที่ revision 9a11daa ไม่ใช่การยืนยันไฟล์รวมปัจจุบัน
