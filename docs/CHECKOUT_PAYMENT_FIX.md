# Sửa lỗi tính tiền và bước chọn thanh toán — 27/10/2026

Cập nhật khởi động mới nhất: tiến trình nền đã được dừng để giải phóng cổng 8080 cho IDE; dữ liệu demo chuyển sang file local để giữ qua các lần chạy lại. Xem [báo cáo lỗi khởi động/SMTP](STARTUP_FIX.md).

## Nguyên nhân lỗi thực tế

Đã tái hiện trên ứng dụng cổng 8080: POST `/api/v1/public/checkout/quote` trả HTTP 500 với thông báo giống ảnh. Java Flight Recorder xác định exception:

`NoResourceFoundException: No static resource api/v1/public/checkout/quote`.

Tiến trình Java đang chạy chưa nạp controller/API mới. Exception của đường dẫn không tồn tại lại bị handler chung biến thành INTERNAL_ERROR, khiến người dùng hiểu nhầm là lỗi tính tiền/database.

## Thay đổi

- Bỏ ô nhập mã giảm giá và nút Áp dụng khỏi checkout khách hàng. Giảm giá chung được tính trên backend như trước; dữ liệu/voucher lịch sử không bị xóa.
- Bỏ phần chọn thanh toán khỏi form xem giỏ.
- Bấm **Gửi đơn hàng** mới mở hộp thoại chọn **Tiền mặt / Chuyển khoản ngân hàng**. Không chọn sẵn phương thức.
- Bấm **Quay lại** hoặc đóng hộp thoại chưa tạo đơn. Chỉ **Xác nhận và gửi đơn** mới gọi API tạo đơn.
- Khóa nút trong lúc gửi, giữ khóa idempotency; thanh toán vẫn pending cho tới khi thu ngân xác nhận thực nhận tiền.
- Lỗi gửi đơn hiển thị ngay trong hộp thoại và giữ giỏ để thử lại. Lỗi tính tiền có nút thử lại.
- Đường dẫn API không tồn tại trả đúng HTTP 404 / RESOURCE_NOT_FOUND, không báo lỗi 500 chung.
- Profile demo dùng đúng cơ sở Campus được seed đầu tiên, không yêu cầu khách chọn cơ sở. Cấu hình production không đổi.

## Ứng dụng đang chạy

Đã khởi động lại cổng 8080 sau khi người dùng xác nhận, dùng bản code mới và phục hồi toàn bộ bản sao H2 demo. Không xuất biến môi trường.

Trước/sau phục hồi: 62 sản phẩm, 9 tài khoản, 0 đơn hàng. Bản sao được kiểm tra phục hồi độc lập trước khi cập nhật; giữ tại thư mục riêng `/private/tmp/ute-checkout-diagnostic/before-update.sql`.

Kiểm tra trực tiếp sau cập nhật:

- API quote cổng 8080 trả HTTP 200, tổng do backend tính, shippingFee=0.
- API không tồn tại trả HTTP 404 đúng định dạng.
- Trang `/cart` có hộp thoại mới, không còn checkoutVoucher/checkoutMethod cũ.

Dữ liệu hiện tại vẫn là H2 demo trong bộ nhớ. Lệnh khởi động cho lần cập nhật này phục hồi bản sao trước khi Hibernate bổ sung schema; không chạy lại seed trên dữ liệu đã có. Tiến trình demo kiểm thử riêng ở 18080 được dừng sau kiểm tra; ứng dụng 8080 được giữ chạy.

## Kiểm chứng

- Build/test đầy đủ: 125 tests, 0 failure, 0 error.
- Bản JAR cuối đóng gói thành công; JavaScript syntax và `git diff --check` qua.
- Chrome desktop 1280px/mobile 390px: đủ 4 tổ hợp mang về/tại quán × tiền mặt/chuyển khoản.
- Browser kiểm tra không còn voucher/select thanh toán cũ; phương thức chỉ hiện sau bấm gửi; quay lại không có request tạo đơn; xác nhận chỉ tạo một đơn.
- Mô phỏng HTTP 503 khi gửi: lỗi hiện trong hộp thoại, nút được mở lại, retry thành công. Ca này không dùng mock để tuyên bố thanh toán thật.
- Giữ ca OTP khi SMTP thiếu cấu hình: màn hình chờ xác thực có thể phục hồi.

Log: `/tmp/ute-payment-step-build.log`, `/tmp/ute-payment-step-package.log`, `/tmp/ute-payment-step-browser-final.log`. Log ứng dụng hiện tại: `/tmp/ute-updated-running-app.log`.

## File sửa trong lần này

- `src/main/resources/templates/fragments/delivery-checkout.html`
- `src/main/resources/static/js/customer/delivery-checkout.js`
- `src/main/resources/static/js/customer/customer.js`
- `src/main/resources/templates/customer/cart.html`
- `src/main/java/vn/edu/ute/milktea/common/ErrorCode.java`
- `src/main/java/vn/edu/ute/milktea/common/GlobalExceptionHandler.java`
- `src/main/resources/application-demo.yml`
- `src/test/java/vn/edu/ute/milktea/security/CheckoutHttpIntegrationTest.java`
- `tests/browser_checkout.py`
- Báo cáo này và liên kết cập nhật trong `docs/CHECKOUT_OTP_REPORT.md`.
