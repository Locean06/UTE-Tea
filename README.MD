# UTE Tea — Website trà sữa và hệ thống quản trị

> Bản cải tiến giao diện cho toàn bộ role của project Spring Boot / Thymeleaf.

## Khởi động nhanh

1. Mở thư mục này bằng VS Code và sử dụng **JDK 21**.
2. Bật SQL Server và đảm bảo database `UTE_Tea` đã tồn tại.
3. Nếu đang dùng DB có thông tin tên cửa hàng cũ, chạy **chỉ** file `database/sqlserver/4_brand_ute_tea.sql` trong SSMS.
4. Mở PowerShell ngay thư mục có `pom.xml` và chạy:

```powershell
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=UTE_Tea;encrypt=true;trustServerCertificate=true"
$env:DB_USERNAME="sa"
$env:DB_PASSWORD=Read-Host 'Mat khau SQL Server'
$env:JPA_DDL_AUTO="none"
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Giữ Terminal chạy, sau đó truy cập [http://localhost:8080](http://localhost:8080).

Thử demo không cần SQL Server (data in-memory):

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
```

## Giao diện theo vai trò

- Khách: `/`, `/menu`, `/cart`, `/orders`, `/profile`.
- Đăng nhập / đăng ký: `/login`.
- Thu ngân: `/cashier`, `/orders`.
- Bếp: `/kitchen`.
- Quản trị: `/admin/reports`, `/admin/catalog`, `/admin/tables`, `/admin/orders`, `/admin/staff`, `/admin/inventory`, `/admin/documents`, `/admin/settings`.

## Công nghệ

**Spring Boot 4.1.1 + Java 21 + Thymeleaf + Bootstrap 5 + JPA + SQL Server + JWT + WebSocket/STOMP + Cloudinary SDK**. Chưa có cấu hình sử dụng MySQL/PostgreSQL; đề bài cho phép chọn **một** trong ba. Cloudinary cần credentials hợp lệ để upload ảnh thật.

**Lưu ý phạm vi:** project gốc vận hành mô hình một cửa hàng. Quản lý chi nhánh độc lập là hạng mục mở rộng nếu giảng viên yêu cầu đúng nghĩa một chuỗi nhiều cơ sở. Chưa có nghiệm thu end-to-end trong môi trường SQL Server ở máy này.

Đọc toàn bộ thay đổi tại [`HUONG_DAN_UTE_TEA.md`](HUONG_DAN_UTE_TEA.md). Tài liệu cũ được lưu trong `docs/README_goc_truoc_redesign.MD` để tham khảo.
