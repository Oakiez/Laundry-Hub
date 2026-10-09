# SOLID Analysis — ส่วนของโอ๊ค (Foundation / Security / Auth / Branch)

> ส่วนนี้ให้โอ๊ครวมเข้า `doc/solid-analysis.md`
> เลขบรรทัดอ้างอิงโค้ด ณ วันที่ 10 ต.ค. 2569 ถ้าแก้ไฟล์ต้องอัปเดตเลขบรรทัดด้วย
> path ย่อ: `code/src/main/java/com/laundryhub/` = `…/`

## S — Single Responsibility

| ไฟล์ : บรรทัด | หน้าที่เดียว | เหตุผล |
|---|---|---|
| `…/service/impl/AuthServiceImpl.java:17` | สมัครสมาชิก (เช็กซ้ำ, เข้ารหัสผ่าน, สร้าง `User` + `CustomerProfile`) | ไม่ดูแลโปรไฟล์หลังสมัคร (เป็นของ `UserServiceImpl`) และไม่คิดวิธีเข้ารหัสเอง (ให้ `PasswordEncoder`) |
| `…/service/impl/UserServiceImpl.java:17` | อ่านข้อมูลผู้ใช้และอ่าน/แก้โปรไฟล์ | แยกจากเรื่องบัญชี/รหัสผ่าน แก้เรื่องหนึ่งไม่กระทบอีกเรื่อง |
| `…/service/impl/BranchServiceImpl.java:17` | กฎการจัดการสาขา (CRUD) | ไม่รู้เรื่องหน้าเว็บ/JSON ไม่แปลง DTO เอง (ส่งให้ `BranchMapper`) |
| `…/config/PasswordEncoderConfig.java:10` แยกจาก `…/config/SecurityConfig.java:16` | เลือกอัลกอริทึมเข้ารหัสรหัสผ่าน (BCrypt) | เปลี่ยนด้วยเหตุผลต่างจากกฎสิทธิ์ URL (SecurityConfig) และกัน dependency วนกัน (Service ฉีด encoder ได้โดยไม่ผูกกับ SecurityConfig) |
| `…/security/ApiErrorWriter.java:21` | เขียน `ApiErrorResponse` เป็น JSON ให้ error ที่เกิดในชั้น Security | ตัวจับ 401/403 (`ApiAuthenticationEntryPoint.java:14`, `ApiAccessDeniedHandler.java:14`) แค่ตัดสินใจว่าจะตอบอะไร ไม่ต้องรู้วิธีเขียน JSON |
| `…/mapper/UserMapper.java:10`, `…/mapper/BranchMapper.java:9` | แปลง Entity ↔ DTO | แยกจาก Service เพื่อไม่ให้ Entity หลุดเป็น API contract (เช่น `password`) |
| `…/common/SecurityUtils.java:9` | อ่านผู้ใช้ปัจจุบันจาก SecurityContext (`currentUserId`, `currentRole`) | จุดเดียวที่โมดูลอื่นเรียกใช้ ไม่ต้องกระจายโค้ดอ่าน SecurityContext |
| `…/controller/api/BranchApiController.java:27` | รับ HTTP, ตรวจสิทธิ์ (`@PreAuthorize`), `@Valid` แล้วส่งต่อ | ไม่มีกฎธุรกิจและไม่ import Repository เลย |

## O — Open/Closed

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/config/SecurityConfig.java:28` | กฎสิทธิ์ระดับ endpoint ย้ายไปอยู่ที่ `@PreAuthorize` ใน Controller ของแต่ละโมดูล (เช่น `…/controller/api/BranchApiController.java`, `UserApiController.java:24`) โมดูลใหม่ (Order, Machine, Payment) **เพิ่มกฎของตัวเองได้โดยไม่ต้องแก้ `SecurityConfig`** ซึ่งเป็นไฟล์กลาง เหลือแค่กฎระดับโซน (`/staff/**`, `/admin/**`) |
| `…/security/ApiErrorWriter.java:21` | เพิ่มชนิด error ใหม่จากชั้น Security = เรียก `write(...)` ด้วยสถานะ/ข้อความใหม่ ไม่ต้องแก้ตัวเขียน JSON |

## L — Liskov Substitution

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/security/AppUserDetails.java:13` | implement `UserDetails` ครบสัญญาที่ Spring Security ต้องการ (`getAuthorities`, `getPassword`, `getUsername`, `isEnabled`) ใช้แทน `UserDetails` ได้ทุกที่ที่ framework เรียก ไม่มีเมธอดที่ throw `UnsupportedOperationException` |
| `…/security/AppUserDetailsService.java:10` | แทน `UserDetailsService` ได้ตามสัญญา: ไม่พบผู้ใช้ → `UsernameNotFoundException` ตามที่ Spring Security คาดหวัง |
| `…/security/ApiAuthenticationEntryPoint.java:14`, `ApiAccessDeniedHandler.java:14` | implement `AuthenticationEntryPoint` / `AccessDeniedHandler` และใช้แทนตัวเดิมของ Spring ได้โดยพฤติกรรมที่ framework ต้องการยังครบ (ตอบ 401/403) |

## I — Interface Segregation

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/repository/UserRepository.java:8`, `BranchRepository.java:6`, `CustomerProfileRepository.java:8` | แยก Repository ตามตาราง และเพิ่มเฉพาะเมธอดที่ใช้จริง (เช่น `existsByUsernameIgnoreCase`, `findByUserId`) ไม่มี Repository เดียวรวมทุกตาราง |
| `…/service/AuthService.java:6`, `UserService.java:7`, `BranchService.java:8` | แบ่ง Service interface ตามหน้าที่ (สมัคร / ผู้ใช้-โปรไฟล์ / สาขา) Controller แต่ละตัวพึ่งเฉพาะ interface ที่ใช้ ไม่ต้องพึ่งเมธอดที่ไม่เกี่ยว |

## D — Dependency Inversion

| ไฟล์ : บรรทัด | หลักฐาน |
|---|---|
| `…/service/impl/AuthServiceImpl.java:23` | รับ `UserRepository`, `PasswordEncoder`, `UserMapper` ผ่าน **constructor** (ไม่มี `@Autowired` บน field) เป็น interface/ตัวแปลงที่เปลี่ยนได้ เทสต์ส่ง mock เข้าไปตรงๆ (`test/java/com/laundryhub/service/AuthServiceTest.java:41`) |
| `…/service/impl/UserServiceImpl.java:23`, `BranchServiceImpl.java:22` | รูปแบบเดียวกัน: ขึ้นกับ Repository interface ผ่าน constructor |
| `…/controller/api/AuthApiController.java:23`, `UserApiController.java:28`, `BranchApiController.java:31` | Controller ขึ้นกับ Service **interface** (`AuthService`, `UserService`, `BranchService`) ไม่รู้จัก `*Impl` และไม่ import Repository |
| `…/controller/web/AuthWebController.java:20`, `ProfileWebController.java:23`, `BranchWebController.java:29` | ฝั่งหน้าเว็บทำแบบเดียวกัน ใช้ Service interface เดียวกับฝั่ง API จึงไม่มีตรรกะซ้ำ |
| `…/security/AppUserDetailsService.java:10` | ขึ้นกับ `UserRepository` (interface) ไม่ผูกกับ JPA โดยตรง |
