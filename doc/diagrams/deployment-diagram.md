# Deployment Diagram

แสดงว่าระบบรันที่ไหน และไหลจากโค้ดไปสู่ผู้ใช้อย่างไร (Docker → Render ← Neon)

```mermaid
flowchart LR
    subgraph USER["อุปกรณ์ผู้ใช้"]
        BR["เบราว์เซอร์"]
    end

    subgraph CDN["CDN (jsDelivr)"]
        BS["Bootstrap 5<br/>CSS / JS"]
    end

    subgraph DEV["เครื่องนักพัฒนา (แต่ละคน)"]
        IDE["IntelliJ + Maven + JDK 17"]
        DC["Docker Desktop<br/>docker compose<br/>db (Postgres 16 :5433)<br/>app (:8080)"]
    end

    subgraph GH["GitHub (Oakiez/Laundry-Hub)"]
        REPO["Git repository<br/>main · develop · branch ส่วนตัว"]
        CI["GitHub Actions (ubuntu-latest)<br/>1) mvn test<br/>2) docker build"]
    end

    subgraph RENDER["Render — Web Service (Singapore)"]
        direction TB
        BUILD["Docker build<br/>Root Directory: code/<br/>multi-stage: Maven → JRE"]
        subgraph CNT["Container (non-root user)"]
            APP["eclipse-temurin:17-jre<br/>app.jar (Spring Boot)<br/>พอร์ต $PORT"]
        end
        ENVV["Environment Variables<br/>DB_URL · DB_USER · DB_PASSWORD"]
        BUILD --> CNT
        ENVV -.-> APP
    end

    subgraph NEON["Neon — PostgreSQL 16 (AWS ap-southeast-1, Singapore)"]
        PG[("ฐานข้อมูล neondb<br/>10 ตาราง + flyway_schema_history")]
    end

    IDE -->|git push| REPO
    DC -.->|ทดสอบก่อน push| IDE
    REPO -->|เปิด PR / push| CI
    REPO -->|auto-deploy เมื่อ push branch ที่ผูกไว้| BUILD
    BR -->|HTTPS| APP
    BR -->|โหลดไฟล์ static| BS
    APP -->|JDBC + SSL<br/>sslmode=require| PG
    APP -->|Flyway migrate ตอนสตาร์ท| PG
```

## รายละเอียดแต่ละโหนด

| โหนด | เทคโนโลยี | หน้าที่ |
|---|---|---|
| **เบราว์เซอร์ผู้ใช้** | Chrome/Edge ฯลฯ | เปิดหน้าเว็บและ Swagger UI ผ่าน HTTPS |
| **Render Web Service** | Docker, Free plan, region Singapore | build จาก `code/Dockerfile` แล้วรัน container ตัวเดียว (non-root) รับพอร์ตจากตัวแปร `PORT` |
| **Neon PostgreSQL** | Postgres 16 แบบ serverless, region Singapore | เก็บข้อมูลถาวร แยกจากแอป จึง **ข้อมูลไม่หายเมื่อ redeploy** |
| **GitHub** | Git + Pull Request | เก็บโค้ด ทุกการรวมงานผ่าน PR + รีวิวอย่างน้อย 1 คน |
| **GitHub Actions** | `.github/workflows/ci.yml` | ทุก PR/push เข้า `develop`/`main` รัน `mvn test` และทดสอบ build Docker image กันโค้ดพังก่อน deploy |
| **เครื่องนักพัฒนา** | JDK 17, Maven, Docker Desktop | พัฒนาและรันทั้งระบบด้วย `docker compose up --build` |

## ขั้นตอน deploy (สรุป)
1. push โค้ดเข้า branch ที่ Render ผูกไว้ → Render รับสัญญาณจาก GitHub อัตโนมัติ
2. Render build image จาก `code/Dockerfile` (ขั้น build ข้ามเทสต์ เพราะเทสต์อยู่นอก `code/` และรันใน CI แทน)
3. สตาร์ท container พร้อมตัวแปร `DB_URL`, `DB_USER`, `DB_PASSWORD` (ตั้งในหน้า Render **ไม่เก็บในโค้ด**)
4. แอปเชื่อม Neon (ต้องใช้ `sslmode=require`) และ Flyway สร้าง/อัปเดตตารางให้อัตโนมัติ
5. เมื่อ health check ผ่าน Render เปิดรับทราฟฟิกที่ URL สาธารณะ

## ข้อจำกัดของ Free plan
- Web Service **หยุดทำงานเมื่อไม่มีผู้ใช้** การเปิดครั้งแรกหลังหยุดใช้เวลาสตาร์ท 2 นาทีขึ้นไป (CPU 0.1) จึงควรเปิดปลุกก่อนนำเสนอ
- RAM 512 MB จึงตั้ง JVM ด้วย `-XX:MaxRAMPercentage=75`

URL ที่ deploy: https://laundry-hub-1ltc.onrender.com

---

**ตรวจก่อนส่ง (โอ๊ค):** Render ต้องผูกกับ branch `main` (ช่วงพัฒนาใช้ branch ส่วนตัวชั่วคราว) และ URL ด้านบนยังเปิดได้
