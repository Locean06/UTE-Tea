# UTE TEA — vai trò nhân viên giao hàng (SHIPPER)

## Các trang và quyền

- Nhân viên giao hàng đăng nhập qua `/login`, sau đó tự chuyển đến **`/delivery`** (trang mới).
- Chỉ tài khoản có vai trò `SHIPPER` được xem `/delivery` và `/api/v1/shipper/**`.
- API chỉ lấy `DeliveryOrder` có `ShipperId` đúng bằng ID từ JWT đang đăng nhập; không nhận `shipperId` từ trình duyệt.
- `ADMIN` quản lý tài khoản SHIPPER trong `/admin/staff`; chủ shop `VENDOR`, người quản lý shop `MANAGER` hoặc ADMIN phân công đơn giao hàng ở `/merchant` (trang quản lý shop/giao hàng).
- Từ `/admin/orders` có liên kết đến kênh `/merchant` để phân công đơn giao hàng.

## Luồng thao tác

1. ADMIN vào `/admin/staff`, tạo tài khoản nhân viên với vai trò **Nhân viên giao hàng (SHIPPER)** và đặt mật khẩu an toàn.
2. Có đơn giao tận nơi mới (`NEW`), chủ shop hoặc ADMIN vào `/merchant` chọn shop, **Xác nhận** đơn để chuyển `CONFIRMED`.
3. Ở đơn `CONFIRMED`, chọn tên người giao từ danh sách nhân viên SHIPPER đang hoạt động, nhấn **Phân công**.
4. Nhân viên giao hàng đăng nhập. Trang `/delivery` cho thấy duy nhất các đơn đã phân công cho tài khoản đó.
5. Nhấn **Bắt đầu giao** (`CONFIRMED → SHIPPING`), sau đó chọn **Giao thành công** (`SHIPPING → DELIVERED`) hoặc **Giao thất bại** (`SHIPPING → DELIVERY_FAILED`). Đơn VNPAY phải được xác nhận đã thanh toán trước khi bắt đầu giao.
6. Các thống kê cá nhân tự cập nhật (tổng đơn được phân công, chờ giao, đang giao, thành công, thất bại, tỷ lệ thành công, COD đã ghi nhận). Tìm kiếm / lọc chỉ áp dụng cho danh sách hiển thị, không thay đổi thống kê tổng.

## Endpoint đã thêm

| Method | Đường dẫn | Quyền | Nội dung |
|---|---|---|---|
| GET | `/api/v1/shipper/workspace` | SHIPPER | Đơn được giao và thống kê cá nhân |
| PUT | `/api/v1/shipper/orders/{id}/status` | SHIPPER | Cập nhật trạng thái theo phiên bản (`version`) |
| GET | `/api/v1/market/shops/{id}/shippers` | ADMIN / VENDOR chủ shop / MANAGER được phân công | Danh sách shipper hoạt động để chọn phân công |

API phân công **đã có từ bản nguồn gốc** và được sử dụng lại: `PUT /api/v1/market/orders/{id}/shipper`.

**Không có migration schema**: dữ liệu gốc đã có enum `Role.SHIPPER`, cột `DeliveryOrder.ShipperId`, đơn giao tận nơi và lifecycle. Không thay đổi các bảng đơn tại quầy.

## Kiểm tra trước triển khai

- Cần build lại ứng dụng từ source; không chạy `target/classes` cũ (file build cũ không được đóng gói).
- Chạy `./mvnw test` và `./mvnw clean package` ở máy có Maven/network; test mới là `courierWorkspaceOnlyListsAssignedOrdersAndProtectsTransitions` trong `MarketIntegrationTest`.
- Thử trên database sao lưu, kiểm tra phân quyền với hai tài khoản SHIPPER khác nhau, thay đổi phân công, kiểm tra stale `version` và chuyển trạng thái.
- Demo profile đã có `shipper@ute-tea.demo`. Với database production, cần **tạo tài khoản SHIPPER trong `/admin/staff`**, không dùng mật khẩu tài khoản demo.

## Tài khoản để đăng nhập

Xem hướng dẫn chi tiết tại [`SHIPPER_ACCOUNT_SETUP.md`](SHIPPER_ACCOUNT_SETUP.md). Với profile `demo`: `shipper@ute-tea.demo` / `Password@123`. Database thật: tạo qua `/admin/staff` hoặc bật `SHIPPER_BOOTSTRAP_ENABLED=true` một lần với email/mật khẩu riêng.
