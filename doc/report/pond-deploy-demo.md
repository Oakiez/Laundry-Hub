# หลักฐานเดโม Self-Service บน Render

วันที่ 10 ตุลาคม 2569 ทดสอบผ่าน browser จริงที่
https://laundry-hub-1ltc.onrender.com ใช้บัญชีตัวอย่าง customer1 ตาม README
จองใหม่หนึ่งรอบและใช้ **QR จำลอง** ไม่มีการชำระเงินจริง ไม่ลบข้อมูลของผู้ใช้อื่น
GitHub develop ที่ตรวจล่าสุดคือ `9a11daa` แต่หน้าเว็บไม่ได้แสดง deployment SHA จึงไม่ยืนยันว่า Render ใช้ revision นี้ตรงตัว

| ขั้นตอน | ผลที่เห็นจริง |
|---|---|
| จอง Washer 2 (machine id 2) | session id 1, owner user id 3, RESERVED |
| เวลา/ราคา | 11 ต.ค. 2569 12:47–13:17, 30 นาที; 30 + 1×30 = 60.00 บาท |
| ชำระผ่านลิงก์จากประวัติ | type USAGE_SESSION และ id 1 ถูกเติมใน form; Payment id 1, QR จำลอง, ชำระแล้ว 60.00 บาท |
| เริ่มใช้งาน | session 1 → IN_USE และ machine 2 → IN_USE |
| จบรอบ | session 1 → COMPLETED และ machine 2 → AVAILABLE |
| แจ้งเตือน | RESERVED, ชำระสำเร็จ, IN_USE, COMPLETED ครบ 4 ข้อความ |
| ตรวจ API หลังจบรอบ | GET /api/v1/sessions/1 คืน COMPLETED, amount 60.00, machineId 2 |
| กด finish ซ้ำผ่าน API | 400 JSON มาตรฐาน, message Session must be IN_USE; session ยัง COMPLETED |
| อ่านเครื่องแบบ anonymous | GET /api/v1/machines ได้ 401 |

หลักฐานภาพ:

1. [จองสำเร็จ](../../img/pond-demo-01-booked.png)
2. [ชำระ QR จำลอง](../../img/pond-demo-02-paid.png)
3. [รอบ IN_USE](../../img/pond-demo-03-session-in-use.png)
4. [เครื่อง IN_USE](../../img/pond-demo-04-machine-in-use.png)
5. [รอบ COMPLETED](../../img/pond-demo-05-completed.png)
6. [เครื่อง AVAILABLE](../../img/pond-demo-06-machine-available.png)
7. [แจ้งเตือนครบ](../../img/pond-demo-07-notifications.png)

ทดสอบนี้เป็น happy path หนึ่งรอบ ไม่ใช่ load/concurrency test และไม่ได้พิสูจน์ทุก role/ทุก endpoint บน Render
เวลาในหน้าเว็บแสดงโดยไม่มี timezone จึงไม่อ้างว่าเป็นเวลาไทย; ข้อมูลจองข้างต้นเป็นค่าที่กรอกและเห็นใน UI
Start ได้ก่อนเวลาเริ่มที่จองไว้ เป็นข้อจำกัดที่พบจริง ลิงก์ชำระเงินยังปรากฏหลังชำระเพราะ SessionResponse ไม่มี payment status
ไม่ได้จ่ายซ้ำ และไม่ได้ทดสอบข้อห้าม start ก่อนชำระ ดูข้อแตกต่างที่รอตกลงใน pond-contract-check.md

การสาธิตซ้ำ: login → จองเครื่อง → เลือกเวลาข้างหน้า/30 นาที → ประวัติ → ชำระ QR จำลอง
→ กลับประวัติ → เริ่มใช้ → จบรอบ → เปิดรายละเอียดเครื่องและแจ้งเตือน
ใช้เลขรอบที่เพิ่งสร้างเท่านั้น; ถ้าเครื่องไม่ AVAILABLE ให้เลือกเครื่องพร้อมใช้อีกเครื่อง
