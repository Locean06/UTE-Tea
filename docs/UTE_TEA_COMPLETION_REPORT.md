# Báo cáo triển khai UTE Tea — 25/10/2026

Chỉ chỉnh dự án gốc. Không thay đổi `milktea/`, không push/merge/deploy và không xóa/reset database SQL Server hiện có. Demo/test dùng H2 tạm thời.

## Các module đã triển khai

Danh sách file thay đổi đầy đủ: [changed-files.txt](changed-files.txt). Mã nguồn chính nằm trong `entity/repository/service/market`, `CustomerDataService`, các controller tương ứng và template/script `customer`/`market`.

Commit chức năng: `a8f253e` (giỏ/account, địa chỉ, sở thích, CSRF) và `11207cf` (marketplace, giao hàng, phân quyền, voucher, đánh giá, VNPAY và regression fixes). Không push các commit này.

| Nhóm | Kết quả |
|---|---|
| Catalog | Tìm theo tên có/không dấu; lọc danh mục, shop, khoảng giá; xếp 20 món mới, bán chạy, đánh giá cao, yêu thích, giá tăng/giảm; phân trang và kiểm tra tham số server |
| Giỏ cá nhân | Lưu database theo account; không xóa khi logout; khóa account, version giỏ; giá/topping tính lại từ database; giỏ phiên bàn vẫn dùng service cũ |
| Profile | Nhiều địa chỉ, sửa/xóa, một địa chỉ mặc định; yêu thích và lịch sử xem riêng từng account |
| Shop | Đăng ký shop, Admin kích hoạt; Vendor quản lý sản phẩm/danh mục/tồn bán/voucher/đơn/doanh thu của shop và vẫn mua hàng như User |
| Manager | Phạm vi do Admin phân công; quản lý shop, sản phẩm/danh mục, voucher, vận chuyển, khách từng shop; chỉ sửa chiết khấu khi Admin cấp quyền; không khóa tài khoản ở shop khác |
| Admin | Quản lý account/vai trò/khóa tài khoản; mọi shop và sản phẩm; phân công Manager; chiết khấu từng shop, voucher/carrier toàn hệ thống và tổng doanh thu đơn giao hàng |
| Shipper | Chỉ xem đơn được giao; bắt đầu giao, giao thành công/thất bại, thống kê; Vendor/Admin phân công |
| Đơn giao hàng | Một shop/đơn, snapshot địa chỉ/giá/phí/chiết khấu, tồn bán riêng; trạng thái mới/xác nhận/đang giao/đã giao/thất bại/hủy/trả/hoàn; idempotency gắn account và nội dung; khóa sản phẩm/voucher |
| Voucher | Kiểm tra shop, thời gian, đơn tối thiểu, quota tổng và theo account; giảm không vượt tiền món; khóa khi sử dụng |
| COD | Ghi thu khi Shipper giao thành công; Admin ghi nhận hoàn tiền mặt bằng tham chiếu phiếu thực tế |
| VNPAY sandbox | URL HMAC-SHA512; IPN kiểm chữ ký, merchant, số tiền, phương thức, response/status, mã giao dịch và chống ghi thu lặp; Return URL không xác nhận thanh toán |
| Đánh giá | Chỉ người mua đơn đã giao/đơn quầy hoàn thành và đã trả tiền; 1–5 sao; bình luận ít nhất 50 ký tự; một đánh giá/account/món; tối đa 5 tệp; ảnh JPEG/PNG/WebP ≤5MB, MP4 ≤20MB qua Cloudinary |
| Giao diện | `/marketplace` cho mua giao hàng; `/merchant` cho Vendor/Manager/Admin/Shipper; sổ địa chỉ và sở thích có liên kết từ profile và menu tài khoản; giữ logo/màu UTE Tea và điều hướng mobile cùng hàng |

Demo profile bổ sung **3 shop, 15 sản phẩm giao hàng**, cộng thực đơn quầy hiện có. Không nạp các dữ liệu này vào profile SQL Server `local`/`prod`.

## Chức năng tái sử dụng và bảo toàn

- Account/JWT/BCrypt, đăng ký và OTP Email, đổi/quên mật khẩu, Product/Category, Cloudinary upload ảnh món.
- Order/Invoice/Payment, bàn/phiên/giỏ phiên, POS, bếp, kho và snapshot BOM, hủy/hoàn đơn quầy, STOMP/WebSocket và báo cáo cũ.
- Đơn giao hàng có bảng và trạng thái riêng; `ShopId=NULL` giữ nghĩa sản phẩm quầy cũ. Món shop không được đưa vào đơn quầy/topping POS.
- **QR công khai đã bị vô hiệu hóa từ trước nhiệm vụ này** trong SecurityConfig, CustomerPageController và CustomerApp. Không tự mở lại quyền đặt bàn/đọc lịch sử bàn công khai. Các test bảo vệ lịch sử đơn và quyền POS vẫn giữ nguyên.

## Sửa lỗi và bảo mật

- CSRF trước đây bị tắt, endpoint trả token ngẫu nhiên. Nay dùng CSRF cookie/token thật cho HTTP mutations; chỉ miễn đường dẫn SockJS `/ws/**` vì thao tác nghiệp vụ không đi qua STOMP SEND.
- Vòng đời CSRF phù hợp JWT stateless: không xóa token sau mỗi GET đã xác thực; xoay token tại login/logout. HTTP client dùng token cookie hiện tại và gộp các lần khởi tạo token đồng thời.
- Cookie JWT áp dụng `Secure` theo cấu hình, `SameSite=Lax`; production bắt buộc cung cấp JWT/OTP secret qua biến môi trường.
- Không trả chi tiết exception/SQL ra client; lỗi dữ liệu và xung đột trả mã phù hợp.
- Escape tên món/topping/ghi chú khi chèn HTML; giao diện mới dùng DOM/textContent. Sản phẩm Vendor không đi vào hộp chọn biến thể/topping POS.
- Chặn đọc đơn tài khoản khác bằng cách tái sử dụng Idempotency-Key của đơn quầy.
- Version sản phẩm ngăn form cũ ghi đè tồn bán sau checkout; hủy/trả không cộng tồn lặp; callback VNPAY không ghi thu lặp.
- Không báo đặt đơn thất bại chỉ vì bước cập nhật giỏ sau khi tạo đơn gặp xung đột; giữ mã chống lặp khi giỏ giao hàng chưa được cập nhật.
- Sửa lỗi Thymeleaf hiểu nhầm mảng JavaScript trong bộ lọc và tràn thanh tài khoản Admin tại 320px.

## Database và cách chạy

Hai script bổ sung, chạy theo thứ tự trên **database hiện có đã chọn đúng**:

1. `database/sqlserver/6_customer_data.sql`
2. `database/sqlserver/7_marketplace.sql`

Script dùng transaction và kiểm tra tồn tại; không DROP/TRUNCATE bảng, không sửa tên hiển thị hay xóa dữ liệu. Script 7 thay constraint tên toàn cục bằng unique theo shop. Cần SQL Server 2017+ (`TRANSLATE`) và kiểm tra schema thực tế trước khi chạy; **chưa thực thi hai script trên SQL Server thật** trong phiên này.

Bảng mới: `AccountCart`, `DeliveryAddress`, `ProductInterest`, `Shop`, `ShopManager`, `ShopCustomerAccess`, `Carrier`, `Voucher`, `DeliveryOrder`, `DeliveryLine`, `ProductReview`, `ReviewMedia`.

Bảng cập nhật: `MonUong` thêm `ShopId`, `DeliveryStock`, `CatalogVersion`, `SearchName`; `DanhMuc` thêm `ShopId`; unique tên món/danh mục có phạm vi shop. Vai trò mới vẫn lưu trong `TaiKhoan.VaiTro` hiện có.

Sau migration dùng `JPA_DDL_AUTO=validate`/profile `prod` để kiểm schema. Không dùng script `0_DropOldDatabase.sql` cho việc nâng cấp này.

Chạy demo không cần SQL Server:

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Các tài khoản demo mới: `vendor1@ute-tea.demo`, `vendor2@ute-tea.demo`, `vendor3@ute-tea.demo`, `manager@ute-tea.demo`, `shipper@ute-tea.demo`. Cùng mật khẩu cấu hình demo (`Password@123` mặc định); Manager chỉ được gán shop Campus. Admin/thu ngân/bếp/khách demo cũ tiếp tục sử dụng.

VNPAY dùng `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL`; đăng ký IPN HTTPS trỏ `/api/v1/public/vnpay/ipn`. Chỉ dùng endpoint sandbox cố định; thiếu khóa thì từ chối VNPAY và cho chọn COD. Đối chiếu [tài liệu VNPAY chính thức](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

Cloudinary dùng `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. Production cần `JWT_SECRET_BASE64`, `OTP_SECRET` và các biến SMTP/database của dự án.

## Kiểm thử đã chạy

- `mvn -o clean test`: **78 test, 0 failure, 0 error**, gồm 51 test cũ và 27 test mới; build sạch để loại trừ class cũ trong `target/`.
- Integration H2: phân quyền chéo shop, grant Manager/chiết khấu, hạn chế khách theo shop, giỏ/địa chỉ/yêu thích/lịch sử riêng, giá server, voucher, stock version, trạng thái, đánh giá sau mua, sáu kiểu sắp xếp và tìm không dấu.
- Hai test đa luồng có transaction riêng: hai người mua cùng món cuối; hai người dùng lượt voucher cuối. Chỉ một đơn thành công và rollback không làm mất tồn.
- VNPAY: vector chữ ký cố định tính độc lập; chữ ký giả, sai merchant/số tiền, giao dịch thất bại và IPN gửi lại không ghi thu thêm.
- HTTP/Tomcat thật: login thiếu CSRF trả 403; sau login và nhiều GET đã xác thực, token CSRF vẫn dùng được cho PUT giỏ hàng.
- Media: từ chối account khác, tệp ảnh/video giả mạo và quá dung lượng trước khi upload.
- Chrome: 15 lượt kiểm tra trang chủ/giỏ/sổ địa chỉ ở 320, 372, 390, 768, 1440px; giỏ còn sau logout/login, version cũ trả 409, POST thiếu CSRF bị chặn.
- Chrome: **35 lượt kiểm tra trang responsive** cho Customer/Vendor/Manager/Admin/Shipper ở cùng năm kích thước; đã mở form quản trị và chọn shop; không tràn ngang/lỗi JavaScript. Luồng COD → Vendor xác nhận → phân công → Shipper giao → đánh giá chạy thực tế; truy cập chéo shop và đánh giá trước nhận hàng trả 403.
- Chrome: tạo đơn quầy → thu ngân xác nhận/thu tiền → bếp bắt đầu/hoàn thành; tồn BEP giảm, hoàn thành lần hai không trừ thêm; đánh giá đơn quầy thành công; payload tên món dạng HTML không thực thi và không lọt vào hộp thoại POS.
- Chrome thao tác form thật: thêm món vào giỏ SQL → tạo đúng một đơn COD → xóa món đã đặt và cập nhật huy hiệu giỏ. SockJS ép dùng XHR polling nhận được cả hai sự kiện trạng thái bếp sau khi đăng ký topic; HTTP fallback không bị CSRF chặn.
- `node --check` cho JavaScript sửa đổi và `git diff --check`: đạt.

## Phần chưa hoàn thành hoặc chưa thể xác minh

- **Không coi đồ án đã được nghiệm thu hoàn toàn.** Migration chưa chạy trên SQL Server thật; test database thực chạy bằng H2.
- Chưa có merchant sandbox/URL IPN công khai nên chưa thanh toán qua màn hình VNPAY thật. Có kiểm thử xử lý IPN tại ứng dụng; không tự ghi nhận thành công bằng Return URL.
- **Chưa tích hợp API hoàn tiền VNPAY tự động.** Đơn VNPAY hủy/trả đã thu tiền được giữ trạng thái cần đối soát; không dùng nút hoàn tiền mặt để giả hoàn tiền qua cổng.
- Chưa có credential SMTP/Cloudinary để kiểm gửi mail và upload media thật. Luồng OTP cũ và validate/quyền media đã được kiểm thử; upload thật còn cần kiểm tra ở môi trường cấu hình đầy đủ.
- Carrier là cấu hình và workflow nội bộ; chưa gọi API hãng vận chuyển thật. Đơn giao hàng mới dùng tải lại dữ liệu; realtime POS/bếp/phiên cũ được bảo toàn.
- Catalog trang chủ tái sử dụng HTML SSR hiện có rồi áp kết quả phân trang API; API phân trang ở database nhưng HTML ban đầu vẫn tải danh sách món. Cần tối ưu nếu dữ liệu tăng lớn.
- Không mở lại QR công khai đã bị tắt sẵn; nếu yêu cầu giảng viên bắt buộc luồng này, cần triển khai lại theo token phiên bàn và kiểm thử quyền riêng, không nới quyền lịch sử bàn.
