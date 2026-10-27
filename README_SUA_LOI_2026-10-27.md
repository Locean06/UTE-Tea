# UTE-Tea – Bản rà soát và sửa lỗi ngày 27/10/2026

## Các vấn đề đã khắc phục trực tiếp trong mã nguồn

1. `static/js/common/websocket.js`: khi CDN SockJS/STOMP không tải được, ứng dụng không còn ném `ReferenceError` làm ngắt quá trình nạp dữ liệu thu ngân. Có phương án dự phòng nạp qua API.
2. `static/js/cashier/delivery-inbox.js`: các đợt polling/refresh/WebSocket chồng lên nhau được gom lại, tải lại bản mới nhất thay vì để dữ liệu cũ ghi đè. Khi API lỗi tạm thời vẫn giữ danh sách đã tải trước đó.
3. `static/js/delivery/dashboard.js`: không bỏ qua lần đồng bộ do một yêu cầu khác đang chạy; làm mới khi quay lại tab.
4. `static/js/customer/delivery-checkout.js`: khi server đã tạo đơn thành công nhưng thao tác xóa giỏ hoặc chuyển trang VNPAY thất bại, vẫn hiển thị mã đơn và đường dẫn xem đơn, khóa thao tác tạo đơn trùng. Mã chống gửi lặp chỉ xóa sau khi đồng bộ giỏ thành công; trường hợp chưa xóa được vẫn dùng lại mã cũ nếu khách thử lại sau khi làm mới trang.
5. `config/WebSocketConfig.java`: chặn subscription STOMP tới đường dẫn không hợp lệ, sai phiên bàn và kênh ngoài danh sách; giới hạn origin cho WebSocket theo cấu hình thay vì cho phép mọi website.
6. `controller/CustomerPageController.java`: trang `/cart` vẫn hiển thị khi truy vấn cấu hình cửa hàng không bắt buộc tạm thời gặp lỗi dữ liệu.
7. `application.yml`: nhận cả `ADMIN_DEFAULT_PASSWORD` và `ADMIN_PASSWORD`; nhận cả `WEBSOCKET_ALLOWED_ORIGIN` và `WS_ALLOWED_ORIGIN`; bỏ mật khẩu admin mặc định yếu ở cấu hình chung (demo/test có cấu hình độc lập).

## Kiểm thử đã thực hiện tại môi trường rà soát

- `python tests/static_audit.py` — **PASS: 122 checks, 0 failures** (liên kết asset/template, HTML ID, cú pháp toàn bộ JS, menu).
- `python tests/order_page_contract.py` — **PASS** (4 trang quản lý đơn, phân quyền và 404).
- `node tests/cashier_refresh_regression.cjs` — **PASS** (đơn không biến mất khi refresh đồng thời).
- `node tests/websocket_cdn_fallback.cjs` — **PASS** (không crash khi SockJS/STOMP vắng mặt).
- Đã thêm `WebSocketSubscriptionSecurityTest.java` để kiểm tra phân quyền qua JUnit ở máy có Maven.

**Giới hạn:** chưa chạy được `mvn test` hay chạy ứng dụng thật với SQL Server vì môi trường này không có Maven đã cài, không tải được Maven Central và không kết nối được SQL Server của bạn. Vì vậy **không thể khẳng định 100% tất cả lỗi runtime đã hết**. Đây là bản sửa mã nguồn có kiểm thử tĩnh và kiểm thử frontend độc lập, **không phải bản đã qua kiểm thử tích hợp DB trên máy của bạn**.

## Cách dùng bản sửa

1. Giải nén ZIP, đặt thư mục `UTE-Tea` vào nơi chạy chương trình. Tạo/copy file `.env` riêng trong thư mục gốc theo cấu hình SQL Server của bạn. File `.env` gốc của bạn không được đóng gói lại để tránh phát tán mật khẩu, khóa API và thông tin SMTP.
2. Giữ nguyên cơ sở dữ liệu đang dùng. **Không chạy** `database/sqlserver/0_DropOldDatabase.sql` hoặc script có thao tác DROP/reset nếu muốn giữ đơn và tài khoản cũ.
3. Nếu chưa có cơ sở Thủ Đức, kiểm tra/cân nhắc chạy `database/sqlserver/9_restore_thu_duc_branch.sql` trong SSMS **sau** migration 7 và 8. Script được thiết kế để bổ sung không xóa dữ liệu, nhưng vẫn sao lưu DB trước khi chạy. Không chạy seed demo trên DB thật nếu không cần.
4. Trên máy có Maven và Java 21: chạy `./mvnw test` (Windows: `mvnw.cmd test`), sau đó khởi động ứng dụng và kiểm tra `http://localhost:8080` hoặc địa chỉ đang cấu hình.
5. Chạy `database/sqlserver/12_audit_checkout_delivery_sync.sql` trong đúng database `UTE_Tea` để xem các cơ sở, menu, cấu hình giao hàng nội bộ và các đơn trong bảng `DeliveryOrder`. Script chỉ đọc/kiểm tra; phải xem kết quả thực tế.
6. Kiểm tra hành trình trên môi trường thật: khách thêm món → chọn địa chỉ/cơ sở → báo giá → xác nhận COD → thu ngân nhận đơn → xác nhận → phân công SHIPPER → shipper giao → thu ngân/khách nhìn thấy trạng thái mới. Lặp lại với cơ sở Thủ Đức và xử lý trường hợp mạng mất/refresh trang. Nếu dùng VNPAY cần khóa sandbox hợp lệ.

### Lưu ý triển khai

- **Đơn giao hàng online** ở bảng `DeliveryOrder` và các dòng ở `DeliveryLine`, **đơn tại quầy** ở `DonHang`; hai danh sách không phải cùng một bảng. Thu ngân xem đơn online tại `/cashier/delivery-orders`, POS tại `/cashier` có khung thông báo đơn online mới.
- `APP_BASE_URL` và `WEBSOCKET_ALLOWED_ORIGIN` (hoặc `WS_ALLOWED_ORIGIN`) phải đúng domain/protocol thực tế, ví dụ `https://...`; không nên đặt origin WebSocket thành `*`.
- Nếu chuyển dữ liệu từ máy cũ, kiểm tra chạy đúng DB trong `.env` trước khi kết luận đơn/cơ sở bị mất.
- Nên thay mật khẩu, JWT secret, SMTP/API keys đang dùng nếu từng tải các file cấu hình có bí mật lên môi trường không tin cậy.
