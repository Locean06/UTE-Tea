# UTE TEA – Sửa đồng bộ Thu ngân / giao hàng / chi nhánh Thủ Đức

## Nội dung

1. `templates/cashier/index.html`: bỏ thanh nhãn "Vận hành cửa hàng" trùng lặp, đưa nút POS vào banner xanh và giữ **một** nút Đơn hàng; thêm tóm tắt đơn giao hàng mới.
2. `templates/cashier/delivery-orders.html`: trang quản lý đơn giao hàng mới với xác nhận, phân công SHIPPER, tìm kiếm/lọc.
3. `static/js/cashier/delivery-inbox.js`: nhận các đơn từ `/api/v1/cashier/delivery-orders` (bảng SQL `DeliveryOrder`), không đọc dữ liệu POS `DonHang`.
4. `CashierDeliveryController.java` + `MarketService.java`: REST API cho nhân viên thu ngân và quản trị; CUSTOMER/SHIPPER không truy xuất được danh sách của tất cả khách. CASHIER được xác nhận, phân công đơn giao trực tiếp.
5. `static/js/customer/delivery-checkout.js`: chỉ tự chọn cấu hình giao hàng **nội bộ UTE Tea**, ưu tiên cấu hình riêng cho cơ sở, không chọn ngẫu nhiên đối tác khác.
6. `database/sqlserver/9_restore_thu_duc_branch.sql`: bổ sung/khôi phục Thủ Đức (không xóa đơn cũ), tạo danh mục, menu và cấu hình giao nội bộ nếu thiếu.
7. `database/sqlserver/CHECK_ORDER_AND_BRANCH_SYNC.sql`: chỉ đọc để xác nhận cơ sở và đơn giao hàng trên đúng database.

## Các bước áp dụng SQL Server local

1. Sao lưu DB trước mọi thao tác cập nhật.
2. Dừng Spring Boot khi thay code. Giải nén source mới.
3. Mở SSMS, chạy `database/sqlserver/CHECK_ORDER_AND_BRANCH_SYNC.sql`. Kiểm tra `DatabaseDangKetNoi` là `UTE_Tea` và bảng Shop/DeliveryOrder có dữ liệu.
4. Nếu chưa có cơ sở Thủ Đức, hoặc chưa có menu/cấu hình giao: chạy **một lần** `database/sqlserver/9_restore_thu_duc_branch.sql` (có thể chạy lại, chỉ thêm dữ liệu thiếu).
5. Chạy lại script chỉ đọc ở bước 3. Cần thấy Thủ Đức là Active, có MonDangBan > 0, có HinhThucGiaoHopLe > 0.
6. Bật profile `local`, so khớp `DB_URL` với chính database vừa chạy SQL (mặc định `jdbc:sqlserver://localhost:1433;databaseName=UTE_Tea;encrypt=true;trustServerCertificate=true`).
7. Khởi động ứng dụng. Mở `/cashier`, khối **Đơn giao hàng mới** phải cập nhật từ API; vào `/cashier/delivery-orders` để nhận đơn, xác nhận và phân công shipper.
8. Đăng nhập CUSTOMER, đặt một đơn giao tận nơi. Trong SSMS, đơn đó phải xuất hiện ở `DeliveryOrder` với `Status = NEW`. Khi CASHIER xác nhận, `Status` chuyển `CONFIRMED`; sau khi phân công thì `ShipperId` khác null và SHIPPER thấy đơn ở `/delivery`.

**Quan trọng:** Hai bảng `DonHang` và `DeliveryOrder` có nghiệp vụ độc lập. Đơn giao tận nơi không tự biến thành đơn POS. Giao diện Thu ngân giờ đọc cả 2 nguồn qua các trang riêng, tránh làm trùng đơn và lệch số tiền.

**Về ảnh “Trang đang tạm thời gián đoạn”:** đó là trang lỗi HTTP 500 từ `GlobalExceptionHandler`. Chỉ từ mã lỗi trên ảnh không suy ra exception gốc. Nếu lỗi vẫn xuất hiện khi mở `/orders`, xem log Spring Boot có dòng `Unhandled application failure [traceId=...]` tương ứng để chẩn đoán; bản mới cung cấp riêng trang `/cashier/delivery-orders` không phụ thuộc vào giao diện `/orders` cũ.

## Giới hạn xác minh

Đã kiểm tra cấu trúc mã nguồn, JavaScript syntax và quyền trên đường API. Chưa thể chạy Maven/Spring Boot hoặc truy cập SQL Server local trên máy người dùng, nên không thể xác nhận database của bạn đã được cập nhật cho tới khi script kiểm tra được chạy trên đúng server.
