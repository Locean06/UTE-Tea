# Đối chiếu yêu cầu đồ án UTE Tea

Phạm vi: dự án gốc; không sửa `milktea/`. Các migration là bổ sung, không reset dữ liệu.

## Trạng thái trước triển khai

| Yêu cầu | Bằng chứng trong mã nguồn | Kết luận |
|---|---|---|
| Đăng ký, đăng nhập, OTP email, quên mật khẩu, BCrypt/JWT | AuthService, OtpService, JwtAuthenticationFilter | Có; tái sử dụng |
| POS, phiên bàn, giỏ phiên, bếp, kho, BOM, hủy/hoàn, WebSocket | OrderService, SessionCartService, InventoryService, PaymentService | Có; giữ nghiệp vụ hiện tại |
| QR công khai | SecurityConfig, CustomerPageController, CustomerApp.initContext | Đang bị vô hiệu hóa trước nhiệm vụ này; không thể ghi nhận là hoạt động |
| Tìm theo tên/danh mục | filterCatalog trong customer/menu.html | Có ở trình duyệt; thiếu khoảng giá, phân trang và xếp hạng |
| Giỏ cá nhân | CustomerApp | Chỉ localStorage; không gắn tài khoản |
| Nhiều địa chỉ, yêu thích, lịch sử xem | Entity/repository/controller hiện tại | Chưa có |
| Nhiều shop, Vendor/Manager/Shipper | Role chỉ gồm ADMIN/CASHIER/KITCHEN/CUSTOMER | Chưa có |
| Đơn giao hàng, COD, voucher, đánh giá, cổng sandbox | Domain hiện tại là bán tại quầy/bàn | Chưa có; không dùng trạng thái bếp làm trạng thái giao hàng |
| CSRF | SecurityConfig tắt CSRF mặc dù có endpoint lấy token | Cần bảo vệ các thao tác dùng cookie |

## Quy tắc triển khai

- Định danh tài khoản lấy từ JWT đã xác minh, không nhận accountId do client chọn.
- Giá/giảm giá/thành tiền tính trên server; không tin giá từ localStorage.
- Sửa giỏ cá nhân có kiểm tra version và khóa tài khoản; giỏ phiên bàn dùng cơ chế cũ.
- Khóa tài khoản khi thay địa chỉ mặc định để tránh hai địa chỉ mặc định cùng lúc.
- Khách ẩn danh vẫn dùng giỏ tạm trên trình duyệt; đăng xuất không xóa giỏ đã lưu SQL.
- Mỗi module có kiểm thử trước commit; kết quả thực chạy ghi trong báo cáo cuối.
