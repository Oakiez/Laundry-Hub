# เตรียมตอบอาจารย์ — โอ๊ค (Foundation / Security / Infra / Deploy)

> ใช้เป็นเอกสารเตรียมตัวส่วนตัว อ่านแล้วต้อง **อธิบายด้วยคำพูดของตัวเอง** ได้ (ไม่ต้องท่อง) กฎของวิชาคือ "อธิบายโค้ดตัวเองไม่ได้ = 0 คะแนนส่วนนั้น"
> path ย่อ: `code/src/main/java/com/laundryhub/` = `…/` · เลขบรรทัด ณ วันที่ 10 ต.ค. 2569 (ถ้าแก้โค้ดต้องตรวจเลขใหม่)
> สูตรตอบทุกข้อ: **(1) ปัญหาคืออะไร → (2) เราแก้ยังไง → (3) ถ้าไม่ทำแบบนี้จะเกิดอะไร**

---

## 5 คำถามหลักจาก brief (หัวข้อ 9)

### 1) Spring Security ทำงานยังไง (filter chain, `UserDetailsService`, BCrypt)

**ตอบสั้น (30 วินาที):**
ทุกคำขอ (request) ต้องผ่าน "ด่านตรวจ" ของ Spring Security ก่อนถึง Controller ด่านแรกถามว่า "คุณคือใคร" (ตรวจชื่อกับรหัสผ่าน) ด่านถัดไปถามว่า "คุณมีสิทธิ์เข้าที่นี่ไหม" (ตรวจบทบาท) รหัสผ่านไม่เคยถูกเก็บเป็นข้อความธรรมดา เก็บเป็น BCrypt hash เท่านั้น

**อธิบายแบบเปรียบเทียบ:**
เหมือนเข้าตึกที่มี รปภ. หลายคนเรียงกัน
1. **รปภ. คนที่ 1 (Authentication):** ขอดูบัตร → เอาชื่อไปถามฝ่ายทะเบียน (`AppUserDetailsService`) ว่ามีคนนี้ไหม และรหัสผ่านที่เก็บไว้คืออะไร → เทียบกับที่พิมพ์มา
2. **รปภ. คนที่ 2 (Authorization):** ดูว่าบัตรระดับนี้ขึ้นชั้นนี้ได้ไหม (`/admin/**` ต้องเป็น ADMIN)
3. ผ่านทั้งสองถึงเข้าห้อง (Controller) ได้

**ขั้นตอนตอนล็อกอินหน้าเว็บ:**
1. กรอกฟอร์มที่ `/login` → `UsernamePasswordAuthenticationFilter` รับชื่อ+รหัส
2. เรียก `AppUserDetailsService.loadUserByUsername` (`…/security/AppUserDetailsService.java:19`) ไปหา `User` ใน DB ด้วย `findByUsername` (บรรทัด 20)
3. คืนเป็น `AppUserDetails` (`…/security/AppUserDetails.java:13`) ซึ่งเก็บ hash ของรหัสผ่านกับบทบาท ติดป้าย `ROLE_` นำหน้า (บรรทัด 39)
4. `PasswordEncoder` (BCrypt) เอารหัสที่พิมพ์มาเข้ารหัสเทียบกับ hash ใน DB (`…/config/PasswordEncoderConfig.java:14`)
5. ตรงกัน → จำผู้ใช้ไว้ใน session (cookie `JSESSIONID`) คำขอต่อไปไม่ต้องกรอกใหม่

**กฎสิทธิ์อยู่ที่ไหน:**
- ระดับโซน: `…/config/SecurityConfig.java:24-31` (`/login`, `/register`, Swagger เปิดสาธารณะ · `/staff/**` = STAFF/ADMIN · `/admin/**` = ADMIN · ที่เหลือต้องล็อกอิน)
- ระดับ endpoint: `@PreAuthorize` ที่ Controller (เปิดใช้ด้วย `@EnableMethodSecurity` บรรทัด 15) เช่น เจ้าของหรือแอดมิน `…/controller/api/UserApiController.java:24`

**สองทางเข้า (สำหรับคนกับสำหรับโปรแกรม):**
- หน้าเว็บ ใช้ฟอร์มล็อกอิน + session (บรรทัด 32-36)
- REST API / Swagger ใช้ **HTTP Basic** ส่งชื่อ+รหัสมากับทุกคำขอ (บรรทัด 37) จึงไม่ต้องมี endpoint `/auth/login`

**BCrypt คืออะไร (ถ้าถามต่อ):**
- เป็นการเข้ารหัสทางเดียว (hash) แปลงกลับเป็นรหัสเดิมไม่ได้ ระบบแค่ "เอารหัสที่พิมพ์มาเข้ารหัสแล้วเทียบ"
- ใส่ **salt** (ค่าสุ่ม) ให้ทุกครั้ง รหัสเดียวกันจึงได้ hash ไม่เหมือนกัน คนที่รู้ hash จึงใช้ตารางเดา (rainbow table) ไม่ได้
- ตั้งใจ **ช้า** เพื่อให้คนร้ายลองเดารหัสทีละล้านครั้งไม่ไหว
- `$2a$10$...` ใน `V2__seed_data.sql`: `2a` = เวอร์ชัน, `10` = ระดับความช้า
- ใน `RegisterRequest` จำกัดรหัสผ่านไม่เกิน 72 ตัว (`…/dto/request/RegisterRequest.java:11-12`) เพราะ BCrypt ใช้แค่ 72 ไบต์แรก ถ้ายาวกว่านั้นจะถูกตัดเงียบๆ

**ถ้าไม่มี Spring Security:** ต้องเขียนเช็กสิทธิ์เองทุก Controller ลืมที่ไหนก็เป็นช่องโหว่ และต้องเขียนระบบเก็บรหัสเอง (เสี่ยงผิดพลาด)

**ถ้าถามต่อ**
- **401 กับ 403 ต่างกันยังไง?** 401 = "ยังไม่รู้ว่าคุณคือใคร" (ไม่ล็อกอิน/รหัสผิด) · 403 = "รู้ว่าคุณคือใคร แต่ไม่มีสิทธิ์"
- **ทำไมปิด CSRF เฉพาะ `/api/**`?** (`SecurityConfig.java:44`) CSRF คือการหลอกให้เบราว์เซอร์ของเหยื่อส่งคำขอโดยใช้ cookie ที่ล็อกอินค้างอยู่ หน้าเว็บที่ใช้ cookie จึงต้องมี token (Thymeleaf ใส่ให้เอง) ส่วน API ออกแบบให้ใช้ Basic ที่ส่งมากับทุกคำขอ
- **ข้อจำกัดที่รู้:** ถ้าเบราว์เซอร์ล็อกอินผ่านฟอร์มไว้แล้ว cookie ก็ใช้เรียก `/api/**` ได้ด้วย แต่เบราว์เซอร์รุ่นใหม่ไม่ส่ง cookie ข้ามเว็บให้ในคำขอแบบ POST (SameSite=Lax เป็นค่าเริ่มต้น) จึงลดความเสี่ยงลง
- **ทำไม 401 ของ API เป็น JSON?** ปกติคำขอที่ผ่านด่านก่อนถึง Controller จะไม่โดน `GlobalExceptionHandler` เราเลยเขียน `ApiAuthenticationEntryPoint` / `ApiAccessDeniedHandler` ให้ตอบรูปแบบเดียวกัน (`…/security/ApiErrorWriter.java`) และมีเทสต์รับรอง

---

### 2) ทำไมสมัครสมาชิกต้องอยู่ใน transaction เดียว (`@Transactional`)

**ตอบสั้น:**
การสมัครเขียนข้อมูล 2 ที่ (ตาราง `users` กับ `customer_profiles`) ถ้าอันแรกสำเร็จแต่อันที่สองพัง จะเหลือ "บัญชีที่ไม่มีโปรไฟล์" `@Transactional` ทำให้ 2 ขั้นตอนนี้ "สำเร็จทั้งคู่ หรือยกเลิกทั้งคู่" (all-or-nothing)

**เปรียบเทียบ:** เหมือนโอนเงิน หักเงินบัญชี A แล้วต้องเติมเข้าบัญชี B ถ้าเติมไม่สำเร็จ ต้องคืนเงินให้ A ไม่ใช่ปล่อยให้เงินหาย

**ชี้โค้ด:** `…/service/impl/AuthServiceImpl.java:31` (`@Transactional` บนเมธอด `register`)
- บรรทัด 33-38: เช็กชื่อผู้ใช้/อีเมลซ้ำ (ไม่สนตัวพิมพ์เล็ก-ใหญ่) ถ้าซ้ำโยน `DuplicateResourceException` → 409
- บรรทัด 43: เอารหัสผ่านไปเข้ารหัสด้วย `passwordEncoder.encode` (ไม่เก็บข้อความจริง)
- บรรทัด 44: บังคับ `Role.CUSTOMER` (ไม่รับ role จากผู้ใช้)
- บรรทัด 50-53: `user.attachProfile(profile)` แล้ว `userRepository.save(user)` ครั้งเดียว → Hibernate บันทึก profile ตามให้ เพราะตั้ง `cascade = ALL` (`…/domain/entity/User.java:42`)

**Spring ทำให้ได้ยังไง:** ตอนเรียกเมธอดที่ติด `@Transactional` Spring สร้าง "ตัวห่อ" (proxy) เปิด transaction ก่อนเข้าเมธอด ถ้าเมธอดจบปกติจะ commit ถ้าเจอ `RuntimeException` จะ rollback ให้อัตโนมัติ

**ถ้าไม่ใช้:** แต่ละ `save` จะ commit แยก ถ้าพังกลางทางเหลือ user ลอย ล็อกอินได้แต่เปิดโปรไฟล์ไม่เจอ (404)

**ถ้าถามต่อ**
- **`@Transactional(readOnly = true)` ที่ระดับคลาสในบางไฟล์ทำไม?** (`UserServiceImpl`, `BranchServiceImpl`) บอก Hibernate ว่าเมธอดนี้แค่อ่าน เร็วขึ้นและกันเขียนพลาด เมธอดที่เขียนจริงติด `@Transactional` ทับเป็นของตัวเอง
- **สองคนสมัครชื่อเดียวกันพร้อมกันได้ไหม?** ช่องว่างตรงเช็ก-แล้ว-บันทึกยังมี แต่ DB มี `UNIQUE` (`V1__init_schema.sql:11-12`) คนที่สองจะชน constraint แล้ว `GlobalExceptionHandler` แปลงเป็น 409
- **อัปเดตโปรไฟล์ทำไมไม่เรียก `save()`?** (`UserServiceImpl.updateProfile`) ตอนอยู่ใน transaction Hibernate จับตาดู object ที่ดึงมา ถ้าค่าเปลี่ยนจะเขียนลง DB เองตอน commit (เรียก *dirty checking*)

---

### 3) ตาราง 1:1 ระหว่าง `users` กับ `customer_profiles` เขียนใน JPA ยังไง

**ตอบสั้น:**
ฝั่ง `CustomerProfile` เป็น "เจ้าของกุญแจ" (มีคอลัมน์ `user_id`) ใช้ `@OneToOne` + `@JoinColumn` ส่วนฝั่ง `User` เป็นแค่ "ผู้ชี้กลับ" ใช้ `@OneToOne(mappedBy = "user")` และ DB บังคับให้ 1:1 จริงด้วย `UNIQUE` ที่ `user_id`

**ชี้โค้ด:**
| ฝั่ง | ไฟล์:บรรทัด | ความหมาย |
|---|---|---|
| เจ้าของ FK | `…/domain/entity/CustomerProfile.java:20-21` | `@OneToOne(fetch = LAZY, optional = false)` + `@JoinColumn(name = "user_id", nullable = false, unique = true)` |
| ผู้ชี้กลับ | `…/domain/entity/User.java:42` | `@OneToOne(mappedBy = "user", cascade = ALL, fetch = LAZY)` ไม่มีคอลัมน์ในตาราง `users` |
| ใน DB | `V1__init_schema.sql:23, 27` | `user_id BIGINT NOT NULL UNIQUE` + `FOREIGN KEY ... ON DELETE CASCADE` |

**ทำไม `UNIQUE` ถึงทำให้เป็น 1:1:** ถ้าไม่มี UNIQUE หนึ่ง user จะมีหลาย profile ได้ (กลายเป็น 1:N) การมี UNIQUE ทำให้ `user_id` หนึ่งค่าซ้ำไม่ได้

**ตัวเลือกที่เลือกและเหตุผล:**
| เลือก | ทำไม |
|---|---|
| `mappedBy` ฝั่ง User | บอก JPA ว่า "กุญแจอยู่ฝั่งโน้น อย่าสร้างคอลัมน์ซ้ำ" |
| `cascade = ALL` จาก User | บันทึก/ลบ user แล้ว profile ตามไปเอง (ใช้ตอนสมัคร: `save(user)` ครั้งเดียว) |
| `fetch = LAZY` | ไม่ดึง profile มาถ้าไม่ได้ใช้ ลด query (ตามกติกาทีมว่า LAZY ทุกความสัมพันธ์) |
| `attachProfile()` (`User.java:45`) | ตั้งความสัมพันธ์ **ทั้งสองทิศ** ให้ตรงกัน ถ้าตั้งทางเดียว `profile.getUser()` จะเป็น null |
| `ON DELETE CASCADE` ใน DB | ลบ user แล้ว profile ถูกลบตาม ไม่ค้างเป็นขยะ |

**ถ้าถามต่อ**
- **LAZY ทำให้ error ไหม?** ถ้าอ่านค่า LAZY นอก transaction จะ error (`LazyInitializationException`) เราตั้ง `open-in-view: false` (`application.yml:9`) จึงต้องอ่านข้อมูลใน Service ที่ `@Transactional` เท่านั้น ซึ่งเป็นแนวปฏิบัติที่ดี
- **ทำไมสร้างโปรไฟล์เฉพาะลูกค้า?** บัญชีพนักงาน/แอดมินไม่ต้องมีเบอร์/ที่อยู่ลูกค้า จึงขอโปรไฟล์ของ staff ได้ 404 (ทดสอบแล้ว)

---

### 4) Flyway ทำอะไร ทำไมไม่ใช้ `ddl-auto: update`

**ตอบสั้น:**
Flyway คือ "สมุดบันทึกเวอร์ชันของฐานข้อมูล" เก็บคำสั่งสร้าง/แก้ตารางเป็นไฟล์เรียงลำดับ (`V1`, `V2`, …) และรันให้ครั้งเดียวต่อไฟล์ ทุกเครื่องและเซิร์ฟเวอร์ได้โครงสร้างเหมือนกันเป๊ะ ส่วน `ddl-auto: update` คือให้ Hibernate "เดา" แล้วแก้ตารางเอง ซึ่งคาดเดาไม่ได้และไม่มีประวัติ

**ชี้โค้ด:**
- `…/resources/db/migration/V1__init_schema.sql` สร้าง 10 ตาราง + constraint + index
- `V2__seed_data.sql` ข้อมูลตัวอย่าง (admin/staff1/customer1)
- `application.yml:8` `ddl-auto: validate` และ `flyway` เปิดไว้ (บรรทัด 10)

**`validate` ทำอะไร:** Hibernate เทียบว่า Entity ตรงกับตารางจริงไหม ถ้าไม่ตรงจะ **ไม่ยอมสตาร์ทแอป** (เราเจอจริงตอนทำงาน: ใช้ตรวจว่า Entity ถูก) แต่ไม่แก้อะไรให้

**เปรียบเทียบ `ddl-auto` แต่ละแบบ:**
| ค่า | ทำอะไร | ปัญหา |
|---|---|---|
| `update` | เติมคอลัมน์/ตารางที่ขาดให้เอง | ไม่ลบ/ไม่เปลี่ยนชื่อ, ผลต่างกันแต่ละเครื่อง, ไม่มีประวัติ, อันตรายกับข้อมูลจริง |
| `create` | ลบแล้วสร้างใหม่ทุกครั้ง | **ข้อมูลหายหมด** |
| `validate` (เราใช้) | แค่ตรวจว่าตรงกัน | ปลอดภัย ไม่แตะข้อมูล |

**ข้อดีของ Flyway สำหรับทีม 4 คน:**
- ทุกคนได้ schema เดียวกัน (รัน `docker compose up` แล้วเหมือนกัน)
- มีตาราง `flyway_schema_history` เก็บว่ารันไฟล์ไหนไปแล้ว พร้อม checksum
- **กฎ: ห้ามแก้ `V1` หลังล็อกแล้ว** ถ้าแก้ Flyway จะตรวจ checksum แล้วไม่ยอมรัน ต้องเพิ่มเป็น `V3` แทน
- บนเว็บจริง (Neon) แอปสตาร์ทครั้งแรก Flyway สร้างตารางและข้อมูลตัวอย่างให้เองโดยไม่ต้องรัน SQL มือ

**ถ้าถามต่อ**
- **ถ้าอยากเพิ่มคอลัมน์ทำยังไง?** เขียนไฟล์ `V3__add_xxx.sql` ใหม่ เช่น `ALTER TABLE ... ADD COLUMN ...`
- **แล้ว rollback ได้ไหม?** Flyway ฟรีไม่มี rollback อัตโนมัติ ใช้วิธีเขียน migration ใหม่แก้ย้อน

---

### 5) Deploy flow: Docker multi-stage → Render ← Neon

**ตอบสั้น:**
โค้ดอยู่ GitHub → GitHub Actions ทดสอบ → Render ดึงโค้ดมา build เป็น Docker image แล้วรัน → แอปต่อฐานข้อมูล PostgreSQL ที่อยู่ Neon (แยกจากแอป) ผ่านตัวแปร environment

```
git push → GitHub ──► Actions: mvn test + docker build (ผ่านก่อน merge)
              └────► Render: build image จาก code/Dockerfile → รัน container
                                      │  DB_URL, DB_USER, DB_PASSWORD (ตั้งบน Render ไม่อยู่ในโค้ด)
                                      ▼
                              Neon PostgreSQL (Singapore)  ← Flyway สร้างตารางตอนสตาร์ท
```

**Docker multi-stage คืออะไร:** (`code/Dockerfile`)
- **ชั้นที่ 1 (บรรทัด 2):** `maven:...-temurin-17` มีเครื่องมือ build ครบ ใช้ build เป็นไฟล์ `.jar`
- **ชั้นที่ 2 (บรรทัด 15):** `eclipse-temurin:17-jre` มีแค่ตัวรันโปรแกรม ก๊อปเฉพาะ `.jar` จากชั้นแรกมา (บรรทัด 19)
- **ผล:** image สุดท้ายไม่แบก Maven/ซอร์สโค้ดไปด้วย เล็กและปลอดภัยกว่า
- เหมือนทำอาหารในครัวใหญ่ (มีอุปกรณ์เต็ม) แล้วยกแต่จานอาหารไปเสิร์ฟ ไม่ยกครัวไปด้วย

**รายละเอียดที่ตั้งใจ (ชี้ได้):**
| บรรทัด | ทำอะไร | ทำไม |
|---|---|---|
| 6-7 | copy `pom.xml` แล้วโหลด dependency ก่อน copy โค้ด | Docker จำ (cache) ชั้นนี้ไว้ แก้โค้ดแล้ว build ใหม่ไม่ต้องโหลดไลบรารีซ้ำ |
| 12 | `-Dmaven.test.skip=true` | โฟลเดอร์ `test/` อยู่นอก `code/` (ตามข้อกำหนดวิชา) ใน image จึงมองไม่เห็น เทสต์รันที่ CI แทน |
| 18, 20 | สร้างและใช้ user `laundry` ไม่ใช่ root | ถ้าแอปโดนเจาะ ผู้โจมตีไม่ได้สิทธิ์สูงสุดในเครื่อง |
| 24 | `-XX:MaxRAMPercentage=75` | Render ฟรีมี RAM 512 MB บอก Java ให้ใช้ไม่เกิน 75% จะได้ไม่ถูกระบบฆ่า |

**ทำไมใช้ตัวแปร environment:** รหัสผ่านฐานข้อมูลห้ามอยู่ในโค้ด (repo เป็น Public) `application.yml` อ่านจาก `${DB_URL}` (บรรทัด 3) ถ้าไม่ตั้งใช้ค่าเริ่มต้นสำหรับเครื่อง dev, ส่วน Render ตั้งค่าจริงที่หน้า Environment

**ทำไมแยก Neon ออกจากแอป:** ถ้าเก็บข้อมูลในเครื่องที่รันแอป ข้อมูลจะหายทุกครั้งที่ deploy ใหม่หรือเครื่องรีสตาร์ท (Render ฟรีไม่มี disk ถาวร) การแยก DB ทำให้ข้อมูลอยู่รอด และต้องใช้ `sslmode=require` เพราะ Neon บังคับต่อแบบเข้ารหัส

**CI/CD ที่เรามี (คะแนนพิเศษ):** `.github/workflows/ci.yml` รัน `mvn test` และ `docker build` ทุก PR เข้า `develop`/`main` และตั้งเป็น **required check** ถ้าไม่ผ่านจะ merge ไม่ได้ (พีชเป็นคนเสนอ) Render deploy อัตโนมัติเมื่อมี push เข้า branch ที่ผูกไว้

**ถ้าถามต่อ**
- **ทำไมเว็บเปิดครั้งแรกช้า?** Render แผนฟรีหยุดแอปเมื่อไม่มีผู้ใช้ และ CPU น้อย (0.1) สตาร์ท Spring Boot ใช้ 2 นาทีขึ้นไป (เครื่องเรา 5 วินาที) ก่อนพรีเซนต์จึงต้องเปิดปลุกไว้
- **ทำไมไม่ใช้ `docker-compose` บน Render?** Render รัน container เดี่ยวจาก Dockerfile ส่วน compose ใช้ในเครื่อง dev/ตรวจก่อน deploy (รัน app + db พร้อมกัน)
- **พอร์ต DB ในเครื่องทำไม 5433?** เครื่องหลายคนมี PostgreSQL ติดตั้งอยู่แล้วที่ 5432 ชนกัน

---

## คำถามอื่นที่มีโอกาสโดนถาม (ส่วนของโอ๊ค)

### A) DTO + Mapper ทำไมไม่ส่ง Entity ตรงๆ
- ถ้าส่ง `User` ตรงๆ `password` (hash) จะหลุดออก API, `profile` ที่เป็น LAZY อาจทำให้ JSON error/วนลูป, และ client ส่ง `role: "ADMIN"` มาตอนสมัครแล้วถูกบันทึกตาม (mass assignment)
- เราแก้ด้วย `RegisterRequest` **ไม่มีช่อง `role`** (`…/dto/request/RegisterRequest.java:7`) และ `UserResponse` **ไม่มี `password`** (`…/dto/response/UserResponse.java`) ทดสอบจริงแล้ว: ส่ง `"role":"ADMIN"` มา ผลคือได้ CUSTOMER
- Mapper (`…/mapper/UserMapper.java`) รวมการแปลงไว้ที่เดียว (SRP) ถ้า Entity เปลี่ยนแก้ที่ Mapper ที่เดียว

### B) `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")` อ่านยังไง
อ่านว่า "เป็นแอดมิน หรือ `id` ใน URL ตรงกับ id ของคนที่ล็อกอินอยู่" (`UserApiController.java:24`) `#id` คือตัวแปร path ส่วน `principal.id` คือ `AppUserDetails.getId()` เหมือนห้องพักในหอ เจ้าของเข้าห้องตัวเองได้ เจ้าของหอ (แอดมิน) เข้าได้ทุกห้อง คนอื่นเข้าห้องเพื่อนไม่ได้ → 403 และเราตอบ 403 ก่อนเช็กว่ามีผู้ใช้นี้ไหม เพื่อไม่ให้คนไล่เดา id

### C) ทำไมใช้ Constructor Injection (ไม่ใช้ `@Autowired` บน field)
- dependency ชัดเจนและ `final` เปลี่ยนไม่ได้หลังสร้าง
- **ทดสอบง่าย:** ใน `AuthServiceTest` เรา `new AuthServiceImpl(mockRepo, mockEncoder, ...)` ได้ตรงๆ ไม่ต้องเปิด Spring ซึ่งทำกับ field injection ไม่ได้
- ถ้าลืมใส่ dependency จะ compile ไม่ผ่านทันที ไม่ใช่รู้ตอนรันแล้ว NullPointerException

### D) SOLID ที่อยู่ในส่วนของโอ๊ค
| หลัก | ตัวอย่าง |
|---|---|
| **S** | `AuthServiceImpl` (สมัคร) แยกจาก `UserServiceImpl` (โปรไฟล์) · `PasswordEncoderConfig` แยกจาก `SecurityConfig` · `ApiErrorWriter` แยกจาก entry point |
| **D** | Service ขึ้นกับ interface (`UserRepository`, `PasswordEncoder`) รับผ่าน constructor · Controller รู้จักแค่ `XxxService` (interface) |
| **L** | `AppUserDetails` ใช้แทน `UserDetails` ได้ในทุกที่ที่ Spring Security ใช้ |
| **I** | Repository แยกตามตาราง มีแค่เมธอดที่ใช้จริง |

### E) ทำไมไม่มี endpoint `/auth/login`
เราใช้ Spring Security จัดการให้: หน้าเว็บใช้ฟอร์ม `/login`, API ใช้ HTTP Basic (Swagger กดปุ่ม **Authorize**) การเขียน endpoint login เองซ้ำซ้อนและเสี่ยงผิดพลาดกว่า ระบุไว้ใน README หัวข้อ API Documentation

### F) ชั้น (Layer) ในโปรเจกต์ และกฎที่ห้ามข้าม
`Controller → Service (interface) → Repository → Entity` ห้าม Controller เรียก Repository ตรง ตรวจได้: ไม่มี `import ...repository` ในโฟลเดอร์ `controller/` (เช็กด้วย grep ได้ ซึ่งผลคือ 0)

### G) ทดสอบอะไรไว้บ้างในส่วนของโอ๊ค
- **Unit test (Mockito):** `AuthServiceTest`, `BranchServiceTest`, `UserServiceTest` ไม่ต้องใช้ DB
- **Security test (`SecurityRulesTest`):** ใช้ `@WebMvcTest` เช็กว่าใครเข้า endpoint ไหนได้ (ไม่ล็อกอิน 401, ลูกค้าเขียนสาขา 403, เจ้าของ/แอดมินเข้าได้ ฯลฯ) และรูปแบบ error ถูกต้อง
- **พิสูจน์ว่าเทสต์ไม่ปลอม:** เคยถอดการแก้ 401 ออกชั่วคราว เทสต์ตัวที่เกี่ยวต้องล้มจริง ใส่กลับแล้วผ่าน
- **ทดสอบกับของจริง:** รันแอปกับ PostgreSQL ใน Docker แล้วยิงด้วย curl ครบ 201/200/204/400/401/403/404/409

### H) ความต่างของ `PATCH`/`PUT`/`POST`/`DELETE` และ status code ที่ใช้
POST สร้าง → **201** · GET อ่าน → **200** · PUT แก้ → **200** · DELETE ลบ → **204** (ไม่มีเนื้อหา) · ข้อมูลผิด → **400** · ไม่ล็อกอิน → **401** · ไม่มีสิทธิ์ → **403** · ไม่พบ → **404** · ซ้ำ/ชน constraint → **409**

---

## สาธิตสด (Demo) ประมาณ 3 นาที

**ก่อนเริ่ม 10–15 นาที:** เปิด https://laundry-hub-1ltc.onrender.com/login ทิ้งไว้ให้แอปตื่น (แผนฟรีใช้เวลาปลุก 2 นาที+)

1. เปิด `/swagger-ui.html` → กด **Authorize** ใส่ `admin` / `password123` → ลอง `GET /api/v1/branches` (ได้ 200)
2. ยิง `GET /api/v1/users/3` ด้วยแอดมิน (ได้) แล้วเปลี่ยนไป Authorize เป็น `customer1` ลอง `POST /api/v1/branches` → **403 พร้อม JSON มาตรฐาน**
3. หน้าเว็บ: `/register` สมัครผู้ใช้ใหม่ (ลองกรอกผิดให้เห็น error) → ล็อกอิน → `/profile` แก้ข้อมูล
4. ล็อกอินเป็น `admin` → เมนู **จัดการสาขา** (เพิ่ม/แก้ สาขา) แล้วลองลบสาขาที่มีเครื่องอยู่ให้เห็นว่าลบไม่ได้
5. เปิดแท็บ **Actions** บน GitHub ให้เห็น CI เขียว และ branch protection ที่บังคับผ่าน CI + รีวิว

**แผนสำรองถ้าเว็บล่ม/ช้า:** รัน `docker compose up --build` ในเครื่อง (http://localhost:8080) หรือเปิดภาพหน้าจอที่เตรียมไว้

---

## ข้อจำกัดที่รู้ (ตอบตรงๆ ถ้าถูกถาม ไม่ต้องกลัว)

| เรื่อง | ความจริง |
|---|---|
| รหัสผ่านตัวอย่าง | `admin/staff1/customer1` ใช้ `password123` (เป็นรหัสตัวอย่างสำหรับทดสอบ เก็บเป็น BCrypt hash) เว็บจริงเปิดสาธารณะ ถ้าใช้งานจริงต้องเปลี่ยน |
| Free plan | แอปหยุดเมื่อไม่มีผู้ใช้ เปิดครั้งแรกช้า ไม่เหมาะกับงานจริงแต่พอสำหรับส่งงาน |
| จัดการผู้ใช้โดยแอดมิน | ไม่ได้ทำหน้านี้ (ตัดสโคป) ดูข้อมูลผู้ใช้ได้ผ่าน API รายคน |
| เทสต์ที่ใช้ DB | บางไฟล์ของเพื่อนต้องมี `LAUNDRY_DB_TESTS=true` ถึงจะรัน จึงถูกข้ามใน CI (แสดงเป็น Skipped) |
| ช่องว่างเช็ก-แล้ว-บันทึก | สมัครชื่อซ้ำพร้อมกันได้ แต่ DB `UNIQUE` กันไว้ → 409 (ไม่ใช่ข้อความที่เจาะจง) |
