# Kiểm tra báo cáo đồ án UTE-Tea

Ngày biên soạn: 27/10/2026. Kết quả chính: `Bao_Cao_UTE_Tea.docx`.

## 1. Nội dung đã hoàn thành

- Mở bản Word mẫu gốc làm nền; giữ hệ styles, đánh số Heading, khổ A4, căn lề và 5 section. Tái tạo phần nội dung trong bản sao, giữ đúng 3 chương: **Kiến trúc ứng dụng**, **Sơ đồ hoạt động các chức năng**, **Giao diện hệ thống**. Không đổi thành cấu trúc 6 chương.
- Thay nội dung dự án mẫu bằng nội dung UTE-Tea đối chiếu trực tiếp với Java, JPA, controller, service, repository, DTO, template, JavaScript, cấu hình và SQL. File Word gốc không thay đổi.
- Lập chỉ mục 183 file Java và 583 method; từ điển dữ liệu gồm 46 Entity, 396 thuộc tính khai báo. Phân biệt khóa JPA, cột liên kết, unique/index và các trường nhúng; các ER là hình rút gọn, không thay thế toàn bộ từ điển dữ liệu.
- Mô tả 7 vai trò trong source: CUSTOMER, CASHIER, KITCHEN, ADMIN, VENDOR, MANAGER, SHIPPER. Chương 2 tổ chức thành 5 nhóm tác nhân, phân tích **73 nhóm chức năng**. Một số thao tác CRUD được trình bày chung theo nghiệp vụ; số nhóm không phải số endpoint hay tổng số method.
- Mỗi nhóm chức năng có mục đích, tác nhân, điều kiện trước, đầu vào, các bước xử lý, kết quả/điều kiện sau, lỗi, activity diagram, đoạn code thật, tên class/method, đường dẫn và số dòng, giải thích và lưu ý riêng.
- **73 activity diagram**, 1 sơ đồ kiến trúc, 2 ER rút gọn: tổng **76 PNG và 76 file Mermaid** trong `So_Do/`. Các nhánh dữ liệu rỗng và phản hồi chung khi email không tồn tại được phân biệt với lỗi. Các nhánh gửi lặp và tái sử dụng phiên được ghi rõ.
- **73 mục mã minh họa**, gồm **127 đoạn trích liên tiếp** có manifest số dòng và SHA-256. Các phần lược bỏ có đánh dấu, không viết code giả.
- **27 ảnh chụp thật** từ trình duyệt Chrome/Playwright; được lưu trong `Anh_Giao_Dien/` và chèn vào Chương 3. Có ảnh đăng nhập/đăng ký, khôi phục, menu, chọn món, giỏ, dialog thanh toán, bàn QR, lịch sử/chứng từ, hồ sơ, thu ngân, bếp và quản trị/các vai trò mở rộng. Mỗi ảnh có mục đích, thành phần, thao tác và đường dẫn liên quan.
- Tạo bảng đối chiếu đủ 73 nhóm tại `Bang_Doi_Chieu_Chuc_Nang.md`.
- Cài 4 trường tự động cho mục lục, danh mục hình, danh mục bảng, danh mục mã; các caption dùng SEQ, có số đệm tuần tự đúng. Bật yêu cầu cập nhật trường khi mở Word.

## 2. Kiểm tra có bằng chứng

Kết quả máy đọc được lưu tại `_work/quality_checks.json`; chỉ mục và script tái tạo nằm trong `_work/`.

| Kiểm tra | Kết quả |
|---|---|
| File DOCX mở bằng python-docx, ZIP/CRC và XML | Đạt; không có member hỏng hay XML lỗi |
| Số chương Heading 1 | Đúng 3 chương theo yêu cầu |
| Section và page setup đối chiếu mẫu | 5 section; khổ giấy và các lề giữ nguyên |
| Trích đoạn code đối chiếu dòng source | 127/127 đoạn khớp SHA-256 |
| File Java so với bản chụp đầu nhiệm vụ | 183/183 không thay đổi |
| Hash file Word mẫu gốc | Không thay đổi |
| Tìm tên dự án mẫu, nghiệp vụ cũ, tên/IDs sinh viên mẫu trong XML | Không còn trong file DOCX kết quả |
| So hash ảnh với 93 ảnh mẫu | Không có ảnh mẫu giữ nguyên trong DOCX kết quả |
| Quan hệ ảnh trong DOCX | Không có target ảnh bị thiếu |
| PNG sơ đồ và ảnh UI | 103/103 tệp hợp lệ |
| Số thứ tự caption đệm theo loại và chương | Tuần tự, không trùng/nhảy số |
| Chạy ứng dụng để chụp ảnh | Có; H2 riêng trên cổng 18081, đã dừng sau khi chụp |
| Gọi API chuẩn bị dữ liệu chụp | 12 request: 8 lần HTTP 200, 4 lần HTTP 201 |

Các API trên bao gồm chuẩn bị đơn thực tế và thao tác vận hành phục vụ ảnh, **không phải** chứng nhận kiểm thử toàn bộ hệ thống. Nhiệm vụ này không chạy lại toàn bộ regression test, không xác minh SQL Server production và không thay đổi source để phục vụ báo cáo.

## 3. Phân biệt phần đã triển khai với phần chưa đủ bằng chứng

- Checkout mới phục vụ mang về hoặc tại bàn qua ngữ cảnh hợp lệ. Người dùng chọn tiền mặt/chuyển khoản sau khi bấm gửi đơn. Chọn phương thức, mở QR hay bấm thông báo chuyển khoản không đồng nghĩa đã thu tiền; Payment do thu ngân ghi nhận.
- Giao diện khách không còn ô hình thức, chọn cơ sở hay nhập voucher; cấu hình cơ sở và giảm giá phía server vẫn tồn tại. Không suy diễn việc bỏ ô nhập thành xóa toàn bộ module khuyến mãi.
- DeliveryOrder, phân công SHIPPER, các trạng thái giao/hoàn và VNPAY là mã tương thích/lịch sử. Controller checkout giao hàng mới đang từ chối; không trình bày như quy trình giao tận nơi hiện đang phục vụ khách.
- Phạm vi đơn POS/nhân sự có kiểm tra cơ sở; kho nguyên liệu hiện theo nguyên liệu/vị trí, chưa phải mọi dữ liệu kho đều tách theo chi nhánh. Một số module hủy/hoàn tiền cũ chưa đồng nhất hoàn toàn về phạm vi. Đã ghi hạn chế trong báo cáo.
- Source có tạo đánh giá theo bằng chứng mua, và upload media. Không tìm thấy chức năng kiểm duyệt/phản hồi đánh giá tương ứng để khẳng định đã triển khai.
- SMTP/OTP, Cloudinary và VNPAY có mã tích hợp; không gửi thư thật, không upload lên tài khoản thật, không thực hiện giao dịch merchant trong phiên biên soạn. Không xác nhận các cấu hình production đang hoạt động.
- Chuyển khoản POS được đối soát thủ công bởi thu ngân, không chứng minh tích hợp API đối soát ngân hàng tự động.

## 4. Thông tin cần chủ đồ án bổ sung

Các thông tin chưa được xác nhận được đánh dấu `[CẦN BỔ SUNG]`, không lấy của báo cáo mẫu:

1. Cơ quan chủ quản, tên trường, khoa/bộ môn và logo chính thức.
2. Tên học phần, lớp, giảng viên hướng dẫn.
3. Họ tên và MSSV từng thành viên; điều chỉnh số dòng nhóm nếu cần.
4. Địa điểm trình bày trên bìa; xác nhận thời gian nộp.
5. Nhận xét, đánh giá và chữ ký của giảng viên.

Không thiếu ảnh đối với 27 màn hình đã liệt kê trong báo cáo. Không chèn ảnh giả cho các bước email hộp thư thật, upload Cloudinary, giao dịch VNPAY hay màn hình chưa tồn tại. Nếu cần chứng minh các tích hợp này khi bảo vệ, cần bổ sung ảnh/chứng từ từ môi trường đã cấu hình và che dữ liệu riêng.

## 5. Kết quả kiểm tra trực quan và giới hạn xuất bản

Đã xuất DOCX bằng **Apple Pages** ra PDF nội bộ và dùng **PDFKit** dựng ảnh để xem các trang đại diện: bìa, phần đầu, mục lục, kiến trúc, từ điển Entity, activity, đoạn code, ảnh giao diện và phần cuối. Đã sửa ô thông tin sinh viên quá hẹp, giảm khoảng trống cuối bìa, sửa đường nối sơ đồ và cách đánh số SEQ để tránh caption lặp khi chuyển đổi.

Bản PDF kiểm tra cuối có 239 trang; ngày tháng đã nằm cùng trang bìa sau khi sửa. Số trang này chỉ là kết quả dàn trang của Pages.

Pages xử lý viền nghệ thuật bìa và định dạng số chương khác Word. Do đó **không phát hành `Bao_Cao_UTE_Tea.pdf` như bản chính thức**. Bản xem thử và các ảnh kiểm tra nằm trong `_work/`, không dùng để nộp. Không khẳng định đã kiểm tra thủ công từng trang hay đã xác nhận bố cục xuất PDF nguyên vẹn bằng Microsoft Word.

Việc cập nhật trường qua AppleScript/Word đã được thử; không xác nhận hoàn tất mục lục và phân trang của lần tạo DOCX cuối cùng. DOCX cuối giữ các trường mục lục và số đệm caption đúng; cần cập nhật trong Word trước khi in/nộp:

1. Mở `Bao_Cao_UTE_Tea.docx` bằng Microsoft Word.
2. Nhấn **Cmd+A** trên macOS hoặc **Ctrl+A** trên Windows, rồi **F9** (có thể cần **Fn+F9**).
3. Với từng mục lục/danh mục, chọn **Update Field → Update entire table**. Cập nhật lại số trang sau khi điền thông tin bìa.
4. Kiểm tra Print Layout, đặc biệt viền bìa, ngắt section và bảng dài; sau đó xuất PDF bằng Word nếu cần bản nộp.

Số trang của PDF xem thử thuộc bộ máy dàn trang Pages, không dùng làm số trang chính thức cho DOCX. Số trang Word có thể đổi khi cập nhật trường, thay font hoặc điền thông tin hành chính.

## 6. Bảo vệ dữ liệu và khả năng tiếp tục

- Không đọc `.env` để biên soạn; không đưa mật khẩu, khóa cấu hình SMTP, API key hay token của môi trường người dùng vào tài liệu.
- Ứng dụng chụp ảnh dùng cấu hình H2 tách biệt, không nhập `.env`, không dùng SMTP thật. Không khởi động lại hay sửa dữ liệu ứng dụng ở cổng 8080.
- Không chỉnh sửa source code trong nhiệm vụ báo cáo. Các thay đổi có sẵn trong Git trước nhiệm vụ được giữ nguyên; việc kiểm tra hash Java so sánh với đầu nhiệm vụ, không so với HEAD.
- File Word mẫu gốc được giữ nguyên. Bản báo cáo cũ có sẵn được sao lưu ở `_work/Bao_Cao_UTE_Tea_truoc_chinh_sua.docx`.
- `_work/` là hồ sơ làm việc, chứa bản mẫu/probe/bản xem thử và backup; các kiểm tra loại bỏ nội dung dự án mẫu áp dụng cho **DOCX kết quả**, không áp dụng cho các bản sao lưu này. Khi nộp bài, chỉ dùng DOCX chính cùng các thư mục ảnh/sơ đồ cần thiết.
