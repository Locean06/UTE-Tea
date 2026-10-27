# UTE TEA – Tài khoản đăng nhập nhân viên giao hàng

## 1. Chạy demo (H2, profile `demo`)

Đăng nhập tại `/login`, dùng:

| Trường | Giá trị |
|---|---|
| Email | `shipper@ute-tea.demo` |
| Mật khẩu | `Password@123` |
| Role | `SHIPPER` |
| Sau đăng nhập | `/delivery` |

Tài khoản demo được tạo tự động trong `MarketplaceDemoData` với mật khẩu băm BCrypt độc lập, `active=true`, `emailVerified=true`. Nếu tài khoản đã tồn tại, chương trình không ghi đè mật khẩu/role. Tài khoản demo **không được tạo trong profile `prod` hoặc `local`**.

> Demo profile dùng H2 trong bộ nhớ, chỉ phục vụ thử nghiệm. Mỗi lần restart có thể làm mất dữ liệu thử nghiệm và đơn được giao.

## 2. Dùng SQL Server/PostgreSQL có dữ liệu sẵn (`local`/`prod`)

Có hai phương án:

**Khuyến nghị:** Đăng nhập ADMIN → `/admin/staff` → Tạo nhân viên → Role `Nhân viên giao hàng (SHIPPER)` → nhập email, mật khẩu và tên. Sau đó đăng xuất và đăng nhập tài khoản mới tại `/login`. Cách này không cần script hay bật cờ khởi tạo.

**Tạo tự động một lần bằng biến môi trường:** Cấu hình trong `.env` tại thư mục gốc của dự án hoặc mục *Environment* trên Render:

```dotenv
SHIPPER_BOOTSTRAP_ENABLED=true
SHIPPER_BOOTSTRAP_EMAIL=shipper1@yourcompany.com
SHIPPER_BOOTSTRAP_PASSWORD=ThayBangMatKhauManh2026!#
SHIPPER_BOOTSTRAP_NAME=Nhân viên giao hàng số 1
SHIPPER_BOOTSTRAP_PHONE=0901000005
```

Lưu ý: email trên chỉ là **ví dụ**, không phải tài khoản có sẵn. Chọn email thực và một mật khẩu **riêng, tối thiểu 12 ký tự**, không dùng `Password@123` trên production. Khởi động lại ứng dụng một lần. Nếu email chưa tồn tại, tài khoản `SHIPPER` sẽ được tạo. Sau khi hoàn tất:

1. Đặt `SHIPPER_BOOTSTRAP_ENABLED=false` (hoặc xóa các biến bootstrap) và khởi động/redeploy lại.
2. Vào `/login` bằng email/mật khẩu đã cấu hình; chuyển đến `/delivery`.
3. Đăng nhập tài khoản quản lý/chủ cửa hàng, phân công đơn cho shipper ở `/merchant`.

Cơ chế an toàn: **không thay đổi tài khoản đã tồn tại** (kể cả khi bị khóa), không nâng quyền tài khoản có cùng email nhưng role khác, không ghi mật khẩu vào log, không tự chạy khi chưa bật cờ. Không cần thay đổi schema. Chỉ dùng cơ chế này với DB đã cấu hình kết nối đúng và quyền tạo bản ghi.

## 3. Cách kiểm tra

- Đăng nhập shipper → kiểm tra về `/delivery`.
- Không phân công đơn: thống kê bằng 0, danh sách trống là đúng.
- Tạo đơn giao tận nơi và xác nhận từ tài khoản có quyền quản lý → gán nhân viên giao hàng; vào `/delivery` để thấy đơn.
- Kiểm tra các quyền hạn chế: tài khoản `SHIPPER` không có quyền truy cập trang quản trị `/admin` hoặc sửa đơn của người khác.

## 4. Các file đã chỉnh trong lần cập nhật tài khoản

- `src/main/java/vn/edu/ute/milktea/config/MarketplaceDemoData.java` – tài khoản shipper demo.
- `src/main/java/vn/edu/ute/milktea/config/ShipperAccountBootstrap.java` – khởi tạo tài khoản an toàn theo yêu cầu trên DB hiện có.
- `src/main/resources/application.yml` – biến môi trường.
- `.env.example` – mẫu cấu hình.
- `src/test/java/vn/edu/ute/milktea/config/ShipperAccountBootstrapTest.java` – kiểm thử cấp quyền và tính idempotent.

Mọi phần khác (quản lý đơn được phân công, thống kê và giao diện) được giữ nguyên từ bản SHIPPER trước đó.
