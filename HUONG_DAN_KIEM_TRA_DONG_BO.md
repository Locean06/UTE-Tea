# UTE TEA – Bản đối chiếu và sửa đồng bộ trên UTE-Tea(7).rar

## Đã sửa trong bản mã nguồn này

1. Footer **không dùng** địa chỉ chung `CauHinhChung.DiaChi` làm địa chỉ chi nhánh. Danh sách lấy từ GET `/api/v1/public/market/shops` = bảng `Shop` (chỉ cơ sở Active). Không còn địa chỉ “Số 1 Võ Văn Ngân” cố định ở footer.
2. Đơn giao hàng khách đặt → bảng `DeliveryOrder` → API `/api/v1/cashier/delivery-orders` → bảng/khối đơn giao hàng ở `/cashier` và `/cashier/delivery-orders`. Ngay sau commit, backend phát sự kiện `DELIVERY_ORDER_CHANGED` tới kênh thu ngân; frontend tải lại từ database. Poll 30 giây vẫn dự phòng.
3. Xác nhận `NEW → CONFIRMED`; phân công SHIPPER từ tài khoản hoạt động; SHIPPER xem đơn riêng ở `/delivery` và cập nhật `SHIPPING → DELIVERED/DELIVERY_FAILED`. Tài khoản CUSTOMER xem đơn của mình tại `/my-orders`.
4. Thu ngân chỉ còn **một** nút điều hướng “Đơn giao hàng” trong banner; thông báo đơn mới ở dưới không tạo thêm nút trùng.
5. `database/sqlserver/9_two_demo_branches.sql` dùng rõ `USE UTE_Tea`; `11_verify.sql` buộc có Thủ Đức và cấu hình giao nội bộ; bổ sung kiểm tra chỉ đọc `12_audit_checkout_delivery_sync.sql`.

## Dữ liệu local: CHỌN MỘT TRONG HAI QUY TRÌNH

### Trên database UTE_Tea đã tồn tại (giữ đơn và tài khoản)

1. **Sao lưu** DB `UTE_Tea` trước khi thay đổi.
2. Mở SSMS trên đúng SQL Server local. Chạy `database/sqlserver/CHECK_ORDER_AND_BRANCH_SYNC.sql` (chỉ đọc) và xem cột `SqlServer` + `DatabaseDangKetNoi`.
3. Nếu thiếu Thủ Đức: chạy `database/sqlserver/9_restore_thu_duc_branch.sql` (không DROP DB, chỉ thêm/khôi phục và sao chép menu cần thiết). Không chạy 1_schema.sql.
4. Chạy `database/sqlserver/12_audit_checkout_delivery_sync.sql` để đối chiếu trực tiếp Shop/Carrier/MonUong/DeliveryOrder/TaiKhoan. Sửa từng cảnh báo nếu có.
5. Khởi động lại Spring Boot với `SPRING_PROFILES_ACTIVE=local`, `DB_URL=jdbc:sqlserver://localhost:1433;databaseName=UTE_Tea;encrypt=true;trustServerCertificate=true`, `JPA_DDL_AUTO=none`, mật khẩu DB thật của bạn.

### Dựng database MỚI (mất toàn bộ dữ liệu cũ!)

Chỉ khi thực sự muốn reset, theo thứ tự **1_schema → 2_seed → 3_update_menu... → 4_brand... → 5_fix_brand... → 6_customer_data → 7_marketplace → 8_branch_neutral_cart → 9_two_demo_branches → 9_restore_thu_duc_branch → 10_shipper_demo_orders → 11_verify → 12_audit_checkout_delivery_sync**. Chạy tuần tự trên `UTE_Tea`; không dùng quy trình này để sửa DB đang có đơn thật.

## Kiểm tra 5 role sau triển khai

- CUSTOMER: `/cart`, chọn địa chỉ, Thủ Đức/Bình Thạnh/Gò Vấp, hoàn tất COD, thấy đơn ở `/my-orders`.
- CASHIER: `/cashier` xuất hiện đơn **giao** mới; `/cashier/delivery-orders` xác nhận và phân công; `/orders` là đơn **POS**. Hai loại đơn nằm ở 2 bảng khác nhau nên ID có thể trùng số nhưng không trùng bản ghi.
- SHIPPER: `/delivery`, thấy đơn đã phân công, cập nhật trạng thái.
- KITCHEN: `/kitchen`, luồng POS tại quầy hiện hữu, chưa tự động tiếp nhận `DeliveryOrder` ở đây.
- ADMIN: `/admin` quản lý tài khoản; `/cashier/delivery-orders` quản lý đơn giao.

## Kiểm tra build, cảnh báo và giới hạn

- Kiểm tra tĩnh JS: `node --check <file>` trên toàn bộ `src/main/resources/static/js`.
- Kiểm tra chính sách/cấu trúc: `python tests/static_audit.py`.
- Kiểm tra Spring Boot/DB thực: `./mvnw test` (hoặc `mvnw.cmd test`) và chạy bằng SQL Server local. Chụp log exception nếu API/Thymeleaf còn 500.
- **Chưa xác minh kết nối SQL Server thật trên máy của bạn.** Các script chỉ đọc không thể chứng minh DB đồng bộ nếu chưa chạy tại đúng máy SQL Server.
- **POS `DonHang` và đơn giao `DeliveryOrder` là hai quy trình tách biệt** trong kiến trúc dự án. Đã làm đồng bộ ở màn hình Thu ngân, không giả mạo việc chuyển đơn giao hàng thành đơn POS hay tự động đẩy vào Bếp.
- Các địa chỉ `(Demo)` chỉ là dữ liệu mẫu; không sử dụng làm địa chỉ cửa hàng thực khi triển khai.
