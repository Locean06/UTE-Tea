# UTE-Tea FULL – hướng dẫn chạy và luồng giao nội bộ

## Thay đổi mới
- Gộp source đầy đủ từ RAR đã khôi phục, gồm .mvn, api, docs, skills, tests và source đã sửa trước.
- Giao hàng: thu ngân phân công ở CONFIRMED, không cần chờ bếp.
- Shipper nội bộ chỉ xác nhận “Đã lấy hàng · Bắt đầu giao” khi preparationStatus=READY và COD hoặc đã thanh toán VNPAY. Nút gọi trạng thái SHIPPING **không tạo cột PICKED_UP mới** để tương thích database hiện có.
- Backend từ chối chuyển SHIPPING khi chưa phân công shipper (kể cả thao tác admin).
- Khi không có shipper đang hoạt động, giao diện thu ngân thông báo lý do.
- Chèn các SQL vá dữ liệu demo vào database/sqlserver/patches. Script backfill chỉ chạy trên DB demo, xem điều kiện trong file.

## Cách chạy Windows
1. Giải nén và mở thư mục chứa pom.xml trong VS Code.
2. Tạo/cập nhật .env theo .env.example; chỉ định đúng DB SQL Server. Không chia sẻ .env.
3. Thiết lập JDK 21 cho Maven, kiểm tra bằng `.\mvnw.cmd -version`.
4. Chạy `.\mvnw.cmd clean test`; sau đó dùng `powershell -ExecutionPolicy Bypass -File .\run_ute_tea_windows.ps1`.
5. Để tạo shipper demo, trong .env đặt `SHIPPER_BOOTSTRAP_ENABLED=true`, `SHIPPER_BOOTSTRAP_EMAIL=shipper@utetea.local`, `SHIPPER_BOOTSTRAP_PASSWORD=<mật khẩu riêng ít nhất 12 ký tự>`, `SHIPPER_BOOTSTRAP_NAME=Shipper nội bộ UTE Tea`; chạy app 1 lần rồi tắt bootstrap.
6. Chạy SQL demo snapshot nếu cần (sao lưu DB trước); **không** chạy lại toàn bộ SQL khởi tạo trên dữ liệu đang dùng thật.

## Kiểm thử chính
- CASHIER xác nhận đơn giao → phân công shipper đang hoạt động ngay khi bếp chưa READY.
- SHIPPER mở /delivery: trước READY không có nút lấy hàng.
- KITCHEN chuyển READY → SHIPPER thấy nút Đã lấy hàng · Bắt đầu giao.
- SHIPPER chuyển SHIPPING rồi DELIVERED hoặc DELIVERY_FAILED.
- Thử API SHIPPING trước READY hoặc chưa phân công: backend phải từ chối.

## Giới hạn
- Chưa thêm trạng thái PICKED_UP riêng, vì cần migration + DTO + kiểm thử tác động. Hành động lấy hàng hiện tương ứng chuyển sang SHIPPING.
- SQL backfill phục hồi demo theo công thức hiện tại, không áp dụng cho đơn thật sau khi công thức thay đổi.
- Chưa kiểm thử luồng thanh toán VNPAY thực tế hoặc kiểm thử tích hợp cùng database của bạn.
