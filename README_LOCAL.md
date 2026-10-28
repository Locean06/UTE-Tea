# UTE Tea - Chay local tu source moi nhat (dong bo deploy -> main)

Code Java, Thymeleaf, CSS/JS, controllers, services, database entities tren main duoc dong bo tu deploy. **Khong can chay Netlify/Docker/Render** de truy cap local.

## Yeu cau
- Windows 10/11; Java **JDK 21** (kiem tra `java -version`).
- Maven (hoac dung Maven Wrapper `mvnw.cmd`; can internet lan dau).
- PowerShell, port 8080 con trong.
- Khong commit file `.env`; **khong copy mat khau len GitHub**.

## 1. Tai source main moi nhat
```powershell
git clone -b main https://github.com/Locean06/UTE-Tea.git
cd UTE-Tea
```
Neu da clone: `git fetch origin; git switch main; git pull --ff-only origin main`.
Sao chep `.env.example` thanh `.env` khi muon dung Neon hoac SQL Server:
```powershell
Copy-Item .env.example .env
notepad .env
```
File nay co gia tri mau `CHANGE_ME` / `YOUR_NEON_ENDPOINT`; bat buoc thay bang thong tin ca nhan.

## 2. Chay H2 Demo (nhanh nhat; khong can Neon/SQL Server)
```powershell
powershell -ExecutionPolicy Bypass -File .\run_ute_tea_windows.ps1 -Demo
```
Truy cap `http://localhost:8080`.
Demo seed tai khoan `admin@utetea.vn` / `Password@123` (CHI dung H2 demo). Demo la database RAM nen reset khi dung.

## 3. Chay localhost voi Neon PostgreSQL hien tai
1. Neon Console > Connect > lay JDBC hostname, database name, user, password. Uu tien **dev branch** cua Neon de tranh thay doi du lieu production.
2. Dien thong tin vao `.env`:
```dotenv
DB_URL=jdbc:postgresql://YOUR_NEON_ENDPOINT/utetea?sslmode=require
DB_USERNAME=neondb_owner
DB_PASSWORD=CHANGE_ME
```
3. Chay:
```powershell
powershell -ExecutionPolicy Bypass -File .\run_ute_tea_windows.ps1 -Neon
```
Launcher tu bat profiles `local,local-neon`, driver PostgreSQL, `JPA_DDL_AUTO=validate`, tắt demo seed, cookie HTTP localhost, websocket origin localhost. **Khong tu chinh schema, khong nạp menu mau**.

**CANH BAO:** Web local van **ghi that** vao Neon database da cau hinh khi ban dat hang / tao mon / xoa du lieu. `validate` chi ngan Hibernate tu chinh schema, khong ngan cac API ghi du lieu.

Neu `Schema-validation: missing table`, ban dang ket noi database rong/sai; dung `-Demo` hoac khoi tao schema co chu dich tren mot DB dev, khong doi `ddl-auto` tu dong tren production.

## 4. SQL Server local (giữ hỗ trợ cũ)
Doi `DB_URL` trong `.env` sang `jdbc:sqlserver://localhost:1433;databaseName=UTE_Tea;encrypt=true;trustServerCertificate=true`; thay `DB_USERNAME`/ `DB_PASSWORD`. Khoi tao schema SQL Server theo cac script trong `database/sqlserver/`, sau do:
```powershell
powershell -ExecutionPolicy Bypass -File .\run_ute_tea_windows.ps1
```
Che do nay bat `local` va `JPA_DDL_AUTO=none`.

## 5. Build / Kiem tra
```powershell
.\mvnw.cmd -DskipTests clean package
```
Khi ung dung dang chay, kiem tra `http://localhost:8080/actuator/health` (trang thai UP).
Cloudinary chi upload duoc khi dien 3 bien `CLOUDINARY_*` trong `.env`. Email OTP chi gui duoc khi SMTP duoc cau hinh.

**Quan trong:** `deploy` giu cau hinh Render. Tren `main`, source duoc dong bo day du va them cau hinh local. Khi muon deploy tiep, can dong bo nhung thay doi mong muon sang `deploy` theo Git workflow; main va deploy khong tu dong dong bo nguoc.
