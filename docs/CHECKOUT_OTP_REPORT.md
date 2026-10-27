# Kết quả sửa checkout, thanh toán và OTP — 27/10/2026

Cập nhật khởi động mới nhất: tiến trình nền đã được dừng để giải phóng cổng 8080 cho IDE; dữ liệu demo chuyển sang file local để giữ qua các lần chạy lại. Xem [báo cáo lỗi khởi động/SMTP](STARTUP_FIX.md).
Cập nhật mới nhất: đã bỏ ô mã giảm giá và chuyển lựa chọn thanh toán sang hộp thoại sau nút Gửi đơn hàng; đã sửa lỗi API chưa được nạp ở ứng dụng đang chạy. Xem [báo cáo sửa checkout/thanh toán](CHECKOUT_PAYMENT_FIX.md).

Cập nhật theo yêu cầu mới: checkout khách hàng đã bỏ hai trường Hình thức sử dụng và Cơ sở UTE-Tea. Hệ thống dùng bàn/cơ sở của QR hợp lệ; ngoài QR mặc định mang về tại cơ sở cấu hình `POS_SHOP_ID`. Khách không phải chọn hai trường này. Build lại thành công với 124 test qua; Chrome xác nhận hai trường không còn trong DOM và 4 tổ hợp checkout vẫn hoạt động trên desktop/mobile. Nhật ký cập nhật: `/tmp/ute-remove-fields-build.log`, `/tmp/ute-remove-fields-browser.log`.

Đã sửa trực tiếp source Spring Boot/Thymeleaf hiện có. Dự án thực tế sử dụng **SQL Server**, không đổi sang MySQL. Không chỉnh sửa database thật trong phiên làm việc này.

## 1. Nguyên nhân và bằng chứng lỗi đăng ký

- Workspace không có `.env`; môi trường chạy không có `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `OTP_SECRET`. Cấu hình SMTP nguồn để trống username/password. Vì vậy môi trường này chưa có điều kiện gửi Gmail thật. E2E trên JAR demo xác nhận lỗi cấu hình SMTP và chuyển sang màn hình OTP để người dùng gửi lại sau khi khôi phục cấu hình.
- Trước khi sửa, `AuthService.register`/`resendRegisterOtp` gọi SMTP bên trong transaction lưu tài khoản/OTP. `MailService` ném `BusinessException` khi SMTP lỗi; transaction rollback dữ liệu đang chờ xác thực. Giờ SMTP được thực hiện sau commit. Kiểm thử transaction thật xác nhận sai mật khẩu SMTP hoặc timeout vẫn giữ một tài khoản chưa kích hoạt và OTP băm trong database.
- `OtpService.verifyOtp` cập nhật số lần sai/thu hồi mã rồi ném `BusinessException`. Transaction mặc định rollback những cập nhật này, kể cả khi được gọi từ `AuthService`. Đã cấu hình không rollback với lỗi nghiệp vụ ở cả hai tầng xác thực. Integration test đọc lại dữ liệu đã commit sau từng lần sai, xác nhận tăng 1→5 và thu hồi mã.
- Cooldown trước đây chỉ nhìn mã còn hiệu lực; thu hồi mã có thể bỏ qua cooldown. Giờ kiểm tra bản ghi mới nhất kể cả mã đã bị thu hồi, đồng thời khóa bản ghi để kiểm tra OTP và khóa tài khoản khi đăng ký lại/gửi lại.
- Đã bỏ email gửi mặc định ghi cứng và khóa HMAC mặc định công khai; yêu cầu `OTP_SECRET` tối thiểu 32 ký tự. Thông báo lỗi SMTP không trả/log nội dung exception có thể chứa dữ liệu nhạy cảm. Test gài OTP/mật khẩu vào exception xác nhận cả response lẫn log không lộ chúng.

Không kết luận App Password Gmail thật bị sai: không có thông tin SMTP thật hoặc log vận hành để kiểm chứng điều đó. Mock SMTP chỉ chứng minh cách code xử lý lỗi/gửi thư, không chứng minh thư tới hộp thư Gmail.

## 2. Đặt hàng và cơ sở

- Checkout chung cho `/cart` và `/marketplace`: Mang về/Tại quán → cơ sở → bàn nếu hợp lệ → giỏ có thể chỉnh sửa → voucher → tiền mặt/chuyển khoản → tạm tính/giảm giá/tổng → xác nhận.
- Không có địa chỉ nhận hàng, đơn vị vận chuyển hay phí ship. API quote trả `shippingFee=0`; tổng POS chỉ gồm sản phẩm/topping trừ giảm giá.
- Tái sử dụng `Order`, `Invoice`, `DiningTable`, `TableSession`, `GuestAccessService`, giỏ tài khoản/giỏ tạm và luồng thu ngân/bếp cũ. Không tạo hệ thống đơn hàng thay thế.
- Đơn mới lưu `ShopId`; `COUNTER` tương đương TAKEAWAY, `TABLE`/phiên bàn tương đương DINE_IN. Response có `fulfillmentMode`, tên cơ sở và bàn.
- Mang về cho khách chọn cơ sở đang hoạt động. Backend ánh xạ mã món/size và topping sang menu của cơ sở, dùng giá database, không dùng giá/tổng frontend để tính tiền. `expectedTotal` chỉ dùng phát hiện thay đổi giá và yêu cầu kiểm tra lại.
- Tại quán phải có QR bí mật đúng bàn hoặc token phiên còn hiệu lực đúng bàn. Không tự gán bàn theo ID; không đổi cơ sở bàn; từ chối phiên cũ, đóng hoặc giả mạo. Xem QR chưa mở phiên; đơn đầu tiên mới mở phiên.
- Giữ giỏ cá nhân của khách; không xóa giỏ phiên dùng chung khi một khách gửi đơn từ giỏ riêng. Luồng giỏ phiên POS vẫn được giữ.
- Giữ giảm giá chung; voucher hợp lệ cộng vào mức giảm hiện có nhưng tổng giảm không vượt tạm tính. Kiểm tra hạn dùng, cơ sở, số lượt toàn hệ thống/cá nhân; khóa voucher và chỉ tiêu thụ khi tạo đơn, không tiêu thụ khi quote/replay.
- Sửa lỗi danh mục có từ trước: nhóm theo shop làm nhân bản cùng món ở nhiều cơ sở; giờ danh mục gom theo mã món/size dùng chung và giữ các size riêng. Regression test cũ đã qua.
- Chặn endpoint tạo đơn giao hàng cũ tại controller. Bảng/cột, API đọc và giao diện lịch sử đơn giao hàng cũ vẫn được giữ.

## 3. Thanh toán và quyền nhân viên

- Đơn lưu `RequestedPaymentMethod=CASH/BANK_TRANSFER`. Hóa đơn chưa có khoản thu được trả về `PENDING_PAYMENT`; không chèn `Payment` chỉ vì chọn phương thức.
- Tiền mặt hiển thị: “Đặt hàng thành công. Vui lòng thanh toán tiền mặt tại quầy”.
- Chuyển khoản hiển thị ngân hàng/số tài khoản/chủ tài khoản từ Cài đặt quản trị, số tiền backend và nội dung duy nhất `UTETEA ORD<id>`.
- Chỉ tạo URL VietQR khi tài khoản/chủ tài khoản đã cấu hình và có `VIETQR_BANK_BIN` hợp lệ. URL chứa số tiền hóa đơn và nội dung đơn, không có số tài khoản nhận tiền mẫu. Seed mới cũng không tự điền ngân hàng; không xóa cấu hình đã lưu của database hiện có.
- “Tôi đã chuyển khoản” chỉ tạo thông báo. Thu ngân kiểm tra giao dịch rồi xác nhận bằng API ghi thu hiện có. Không thêm webhook giả hoặc tự xác nhận ngân hàng.
- Đơn có khóa chống gửi lặp và fingerprint nội dung; cơ sở/bàn được khóa khi tạo. Unique index bảo vệ key ở database. Gửi lại cùng key của cùng chủ đơn trả lại đơn cũ; thay nội dung bị từ chối. Khách ẩn danh không được dùng key để đọc chứng từ của người khác; khi chưa có token riêng, replay bị từ chối thay vì tạo thêm đơn.
- Giữ khóa hóa đơn và chống ghi thu lặp; thông báo chuyển khoản cũng khóa theo thứ tự đơn → hóa đơn.
- Danh sách thu ngân/bếp và thao tác xác nhận/chế biến/ghi thu kiểm tra cơ sở. ADMIN có phạm vi quản trị; CASHIER/KITCHEN cần được phân công cho cơ sở qua quan hệ `ShopManager` hiện có. UI phân công đã cho chọn các vai trò này. Chứng từ cũ chưa có cơ sở giữ cách truy cập lịch sử của POS cũ.

## 4. Backend/database

Migration mới: `database/sqlserver/9_pos_branch_checkout.sql`.

Thêm các cột nullable để giữ tương thích lịch sử: `DonHang.ShopId`, `VoucherId`, `RequestedPaymentMethod`, `RequestFingerprint`, `Ban.ShopId`; thêm FK và index, bao gồm unique index có điều kiện cho `MaYeuCau`. Nếu phát hiện key lịch sử trùng, migration dừng để người quản trị xem xét, không xóa bản ghi. Không sửa dữ liệu giao hàng cũ hay tự suy đoán cơ sở lịch sử.

Đã bỏ giá trị ngân hàng mẫu khỏi Java initializer/SQL seed mới. Không chạy lại seed/schema dựng mới trên database đang vận hành.

## 5. Kết quả kiểm tra

- `bash mvnw -q package`: **thành công**; **123 test, 0 failure, 0 error, 0 skipped**. JAR: `target/milktea-0.0.1-SNAPSHOT.jar`.
- Unit/integration: giá theo cơ sở, topping, tổng không ship, voucher, replay, giả mạo QR/phiên/cơ sở/tổng, token phiên hợp lệ, bộ lọc cơ sở thu ngân, ghi thu lặp, bếp nhận đơn đã xác nhận nhưng chưa thu tiền, thông báo chuyển khoản không làm PAID, VietQR đúng tiền/nội dung.
- HTTP thực: đủ 4 tổ hợp mang về/tại quán × CASH/BANK_TRANSFER, replay cùng key, khách không có quyền gọi API thu ngân, thông báo chuyển khoản không ghi thu; register/verify/resend public nhưng vẫn kiểm tra CSRF và validation.
- OTP qua Spring proxy/database H2: tài khoản pending, OTP 6 chữ số chỉ lưu HMAC/salt, đăng nhập bị chặn trước xác thực, mã đúng kích hoạt và cho đăng nhập, sai mã lưu số lần thử/thu hồi, hết hạn, gửi lại, trùng tài khoản đã xác thực, giới hạn gửi lại, SMTP authentication failure/timeout, thiếu cấu hình, bảo mật log.
- `node --check` cho JavaScript checkout/customer/confirmation: qua. `git diff --check`: qua.
- Chrome E2E trên JAR demo H2: **4/4 luồng checkout**, desktop 1280px và mobile 390px, chỉnh số lượng trong form không vô tình gửi đơn, không có trường giao hàng, cơ sở bàn bị khóa, không tràn ngang, trạng thái chưa thanh toán, không có JavaScript exception trong các luồng này. Thêm **1 ca đăng ký khi SMTP thiếu cấu hình**: hiển thị màn hình OTP có lỗi rõ ràng và nút gửi lại theo cooldown.
- Script tái chạy E2E: `python3 tests/browser_checkout.py` (chỉ trỏ localhost:18080/demo). Kết quả Maven nằm trong `target/surefire-reports`. Nhật ký phiên kiểm tra: `/tmp/ute-final-build.log`, `/tmp/ute-browser-final.log`.

## 6. Chưa kiểm chứng thực tế

- Gửi/nhận thư Gmail thật: thiếu tài khoản SMTP/App Password; không gửi thư thật trong phiên này.
- Chuyển tiền ngân hàng thật: chưa có cấu hình ngân hàng thực và không có dịch vụ đối soát tự động. Thu ngân vẫn xác minh thủ công.
- Thực thi migration trên SQL Server thật: chưa có cấu hình kết nối môi trường triển khai; test database dùng H2. Migration SQL Server cần chạy và kiểm tra ở môi trường phù hợp trước khi đưa bản này vào vận hành.
- Không chứng nhận tải đồng thời lớn hoặc toàn bộ luồng E2E quản trị/kho ngoài phạm vi. Bếp/kho vẫn dùng công thức hiện có; món mới cần có công thức hợp lệ, không tự tạo định lượng giả.

## 7. Cấu hình cần điền

Sao chép `.env.example` thành `.env`, điền dữ liệu thực tế (file được import như Java properties, không thêm dấu nháy kiểu shell):

| Biến | Ý nghĩa |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Kết nối SQL Server hiện có |
| `JPA_DDL_AUTO` | Dùng `none`/`validate` sau khi áp dụng migration |
| `MAIL_HOST`, `MAIL_PORT` | Mặc định Gmail `smtp.gmail.com:587` |
| `MAIL_USERNAME` | Tài khoản SMTP thực |
| `MAIL_PASSWORD` | Google App Password hợp lệ, không dùng mật khẩu đăng nhập Gmail; bỏ khoảng trắng khi sao chép |
| `MAIL_FROM` | Địa chỉ gửi hợp lệ; nếu khai báo rỗng thì cần điền, hoặc bỏ biến để dùng `MAIL_USERNAME` |
| `MAIL_SENDER_NAME` | Tên hiển thị, mặc định UTE Tea |
| `OTP_SECRET` | Khóa bí mật ngẫu nhiên riêng, ít nhất 32 ký tự; có thể tự sinh bằng `openssl rand -hex 32` |
| `POS_SHOP_ID` | ID cơ sở thực cho menu/bàn POS cũ chưa có `ShopId`; `0` nghĩa là chưa cấu hình |
| `VIETQR_BANK_BIN` | BIN ngân hàng 6 chữ số, khớp tài khoản cấu hình trong quản trị; bỏ trống thì không tạo QR |

SMTP bật authentication, STARTTLS bắt buộc và timeout 5 giây. Số tài khoản, tên ngân hàng và chủ tài khoản nằm trong **Quản trị → Cài đặt**, không điền tài khoản giả vào source.

## 8. Chạy lại và kiểm tra thủ công

1. Với database hiện có, áp dụng migration 9 sau các migration 6/7/8; áp dụng `patch_email_verification.sql` nếu chưa có schema OTP. Không chạy lại `0_DropOldDatabase.sql`, `1_schema.sql` hoặc seed để nâng cấp dữ liệu đang dùng.
2. Tạo/xác nhận các cơ sở thực. Gán từng `Ban.ShopId` đúng cơ sở, hoặc đặt `POS_SHOP_ID` cho một cơ sở POS cũ. Không chọn đại một ShopId khi không rõ dữ liệu. Đảm bảo menu cơ sở và công thức món/topping đã cấu hình.
3. Đăng nhập ADMIN, mở `/merchant`, chọn cơ sở rồi phân công CASHIER/KITCHEN trong mục nhân viên được phân công. Cấu hình ngân hàng tại Cài đặt nếu dùng chuyển khoản.
4. Điền SMTP/OTP và DB trong `.env`, chạy `bash mvnw spring-boot:run`. Build/test bằng `bash mvnw package`. POM đã cấu hình Mockito agent để tránh lỗi self-attach của JDK trong môi trường hạn chế.
5. Thử `/cart` với mang về: chọn cơ sở, sửa món/số lượng, voucher, lần lượt tiền mặt/chuyển khoản. Kiểm tra tổng, cơ sở, trạng thái pending và chứng từ riêng sau đặt.
6. Mở QR hợp lệ `/?qr=<MaQR>`, thêm món và vào giỏ: phải thấy cơ sở/bàn đúng, không đổi cơ sở tại quán. QR/phiên giả hoặc cũ phải bị từ chối; không vào QR thì không được tự chọn bàn.
7. Mở thu ngân đúng cơ sở để xác nhận đơn/ghi thu sau khi nhận tiền thực. Xác nhận bếp thấy đơn sau điều phối; thử tài khoản cơ sở khác không được thao tác. Nút “Tôi đã chuyển khoản” không được làm đơn PAID.
8. Đăng ký email thật, kiểm tra hộp thư/spam, nhập đúng/sai/hết hạn, thử gửi lại, đăng nhập trước/sau xác thực. Nếu SMTP hỏng, tài khoản vẫn chưa kích hoạt và có thể gửi lại sau khi khôi phục cấu hình/cooldown.

Demo tách biệt để chạy browser test (H2 tạm, không dùng database thật):

```sh
java -jar target/milktea-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo --server.port=18080 --app.pos.shop-id=1 --app.otp.secret=demo-only-otp-key-at-least-32-characters
python3 tests/browser_checkout.py
```

Chuỗi OTP ở lệnh demo chỉ dành cho dữ liệu H2 tạm; cấu hình thật cần khóa riêng. Browser test cần Playwright và Chromium/Google Chrome. Server demo trong phiên kiểm tra đã được dừng sau khi kiểm tra.

## 9. Danh sách file thay đổi

Không tính `.DS_Store` vốn đã thay đổi trước phiên làm việc. Ngoài báo cáo này:

- `.env.example`
- `database/sqlserver/2_seed.sql`
- `database/sqlserver/9_pos_branch_checkout.sql`
- `pom.xml`
- `src/main/java/vn/edu/ute/milktea/config/DataInitializer.java`
- `src/main/java/vn/edu/ute/milktea/config/MarketplaceDemoData.java`
- `src/main/java/vn/edu/ute/milktea/controller/MarketController.java`
- `src/main/java/vn/edu/ute/milktea/controller/OrderController.java`
- `src/main/java/vn/edu/ute/milktea/controller/TableController.java`
- `src/main/java/vn/edu/ute/milktea/dto/OrderDto.java`
- `src/main/java/vn/edu/ute/milktea/dto/TableDto.java`
- `src/main/java/vn/edu/ute/milktea/entity/order/Order.java`
- `src/main/java/vn/edu/ute/milktea/entity/table/DiningTable.java`
- `src/main/java/vn/edu/ute/milktea/repository/account/AccountRepository.java`
- `src/main/java/vn/edu/ute/milktea/repository/account/EmailVerificationOtpRepository.java`
- `src/main/java/vn/edu/ute/milktea/repository/market/ShopRepository.java`
- `src/main/java/vn/edu/ute/milktea/repository/order/OrderRepository.java`
- `src/main/java/vn/edu/ute/milktea/security/SecurityConfig.java`
- `src/main/java/vn/edu/ute/milktea/service/auth/AuthService.java`
- `src/main/java/vn/edu/ute/milktea/service/auth/OtpService.java`
- `src/main/java/vn/edu/ute/milktea/service/mail/MailService.java`
- `src/main/java/vn/edu/ute/milktea/service/market/CatalogSearchService.java`
- `src/main/java/vn/edu/ute/milktea/service/market/MarketService.java`
- `src/main/java/vn/edu/ute/milktea/service/order/OrderBranchService.java`
- `src/main/java/vn/edu/ute/milktea/service/order/OrderService.java`
- `src/main/java/vn/edu/ute/milktea/service/payment/PaymentService.java`
- `src/main/java/vn/edu/ute/milktea/service/table/TableContextService.java`
- `src/main/java/vn/edu/ute/milktea/service/table/TableSessionService.java`
- `src/main/resources/application-test.yml`
- `src/main/resources/application.yml`
- `src/main/resources/static/js/customer/customer.js`
- `src/main/resources/static/js/customer/delivery-checkout.js`
- `src/main/resources/static/js/customer/order-confirmation.js`
- `src/main/resources/static/js/market/portal.js`
- `src/main/resources/templates/auth/login.html`
- `src/main/resources/templates/customer/cart.html`
- `src/main/resources/templates/customer/marketplace.html`
- `src/main/resources/templates/customer/order-confirmation.html`
- `src/main/resources/templates/fragments/delivery-checkout.html`
- `src/main/resources/templates/market/portal.html`
- `src/test/java/vn/edu/ute/milktea/security/CheckoutHttpIntegrationTest.java`
- `src/test/java/vn/edu/ute/milktea/security/OrderHistoryAndStaffAccessTest.java`
- `src/test/java/vn/edu/ute/milktea/service/AuthEmailVerificationFlowTest.java`
- `src/test/java/vn/edu/ute/milktea/service/CheckoutFlowIntegrationTest.java`
- `src/test/java/vn/edu/ute/milktea/service/MailConfigurationTest.java`
- `src/test/java/vn/edu/ute/milktea/service/OrderIdempotencyAccessTest.java`
- `src/test/java/vn/edu/ute/milktea/service/OtpTransactionIntegrationTest.java`
- `tests/browser_checkout.py`
