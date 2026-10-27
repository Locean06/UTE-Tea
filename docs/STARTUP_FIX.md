# Sửa lỗi khởi động và SMTP — 27/10/2026

Log đính kèm có ba sự kiện riêng:

1. `NoResourceFoundException` tại `/api/v1/public/checkout/quote`: lỗi của tiến trình cũ PID 10209 chưa nạp API mới. Source đã có endpoint và handler 404 từ lần sửa checkout.
2. `Authentication failed`: máy chủ SMTP từ chối xác thực. Đây là lỗi thông tin đăng nhập SMTP; chưa có bằng chứng App Password thực tế hợp lệ. Không thể sửa bằng cách tạo thông tin Gmail giả.
3. Lần chạy mới PID 97857 dừng vì **Port 8080 was already in use**. Kiểm tra thực tế xác định PID 92242 là tiến trình demo do Codex khởi chạy ở lượt trước, còn chiếm cổng. Exit code 143 của PID cũ là kết quả dừng bằng SIGTERM, không phải lỗi biên dịch Java.

## Đã thực hiện

- Sao lưu H2 đang chạy vào `/private/tmp/ute-port-fix-backup/before-update.sql` trước khi dừng.
- Phục hồi sang `data/ute-tea-demo.mv.db`, không ghi đè file database có sẵn.
- Đổi **riêng profile demo** sang H2 file và `ddl-auto=update` để giữ tài khoản/giỏ/đơn sau khi chạy lại. Local/prod SQL Server và profile test không đổi.
- Loại `data/` khỏi Git, lưu database trong thư mục riêng có quyền truy cập giới hạn.
- Dừng đúng PID 92242, không dừng Java/process khác. Cổng 8080 đã trống.
- Bổ sung xử lý riêng `MailAuthenticationException`, trả lỗi SMTP xác thực rõ ràng, không trả/log mật khẩu hay mã OTP.

## Kiểm chứng

- `bash mvnw -q package`: 126 test, 0 failure, 0 error.
- Khởi động bằng Maven với profile demo trên cổng kiểm tra 18080: thành công.
- `/cart`: HTTP 200. POST quote: HTTP 200, tổng tiền được tính trên backend.
- Sau khởi động và dừng ứng dụng, database vẫn đủ 62 sản phẩm, 9 tài khoản, 0 đơn hàng như bản sao trước cập nhật. Initializer ghi nhận dữ liệu đã tồn tại và không seed lại.
- Đã dừng tiến trình kiểm tra. Cả cổng 8080 và 18080 đều trống, không để lại server nền gây xung đột IDE.
- Thông tin SMTP thực tế chưa được xác minh; unit test chỉ mô phỏng xác thực thất bại và kiểm tra không lộ secret.

## Chạy lại

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Demo mặc định dùng `./data/ute-tea-demo.mv.db`. Biến `DEMO_DB_URL` cho phép chọn database demo tách biệt khi cần. Không xóa file data nếu muốn giữ dữ liệu.

SMTP cần `MAIL_USERNAME`, `MAIL_PASSWORD` là Google App Password hợp lệ và địa chỉ gửi `MAIL_FROM` phù hợp. Khóa `OTP_SECRET` cần được cấu hình riêng như `.env.example`. Điền trên máy trong `.env`/environment; không gửi mật khẩu qua chat hoặc commit vào Git.

Logs kiểm tra: `/tmp/ute-port-fix-tests.log`, `/tmp/ute-port-fix-run.log`.
