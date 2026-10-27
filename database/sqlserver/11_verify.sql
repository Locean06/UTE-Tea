-- Verification after rebuilding UTE_Tea. Read-only except THROW when expectations are not met.
USE [UTE_Tea];
GO
SET NOCOUNT ON;
IF (SELECT COUNT_BIG(*) FROM dbo.TaiKhoan) <> 5
    THROW 51101, N'Số tài khoản không bằng 5.', 1;
IF (SELECT COUNT(DISTINCT VaiTro) FROM dbo.TaiKhoan) <> 5
    THROW 51102, N'Không đủ 5 role khác nhau.', 1;
IF EXISTS (SELECT 1 FROM dbo.TaiKhoan
           WHERE HoatDong<>1 OR EmailXacThuc<>1
              OR VaiTro NOT IN ('ADMIN','CASHIER','KITCHEN','CUSTOMER','SHIPPER')
              OR MatKhauBam NOT LIKE '$2a$10$%')
    THROW 51103, N'Tài khoản chưa đúng role, chưa xác thực hoặc thiếu BCrypt.', 1;
IF (SELECT COUNT_BIG(*) FROM dbo.TaiKhoan
      WHERE Email IN ('admin@utetea.local','cashier@utetea.local','kitchen@utetea.local',
                      'customer@utetea.local','shipper@utetea.local')) <> 5
    THROW 51104, N'Thiếu email mẫu đã định nghĩa.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.MonUong) OR NOT EXISTS (SELECT 1 FROM dbo.DanhMuc)
    THROW 51105, N'Chưa có thực đơn hoặc danh mục.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.Shop WHERE Name=N'UTE Tea - Bình Thạnh (Demo)')
 OR NOT EXISTS (SELECT 1 FROM dbo.Shop WHERE Name=N'UTE Tea - Gò Vấp (Demo)')
    THROW 51106, N'Chưa có đủ 2 chi nhánh mẫu.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.Shop WHERE Name LIKE N'UTE Tea%Thủ Đức%' AND Active=1)
    THROW 51109, N'Thiếu cơ sở Thủ Đức. Chạy 9_restore_thu_duc_branch.sql sau 9_two_demo_branches.sql.', 1;
IF COL_LENGTH('dbo.DeliveryOrder','ShipperId') IS NULL
 OR COL_LENGTH('dbo.MonUong','MenuCode') IS NULL
    THROW 51107, N'Thiếu bảng/cột marketplace.', 1;
IF EXISTS (
  SELECT 1 FROM dbo.Shop s
  WHERE s.Active=1 AND NOT EXISTS (
    SELECT 1 FROM dbo.Carrier c WHERE c.ShopId=s.Id AND c.Active=1
      AND (c.Name LIKE N'%UTE Tea%' OR c.Name LIKE N'%nội bộ%' OR c.Name LIKE N'%Nhân viên%')
  )
) THROW 51110, N'Có cơ sở hoạt động chưa cấu hình giao hàng nội bộ.', 1;
IF (SELECT COUNT(*) FROM dbo.DeliveryOrder
    WHERE ShipperId=(SELECT MaTK FROM dbo.TaiKhoan WHERE VaiTro='SHIPPER')) < 4
    THROW 51108, N'Chưa có đủ các đơn mẫu phân công cho SHIPPER.', 1;

SELECT MaTK, Email, VaiTro, HoTen, HoatDong, EmailXacThuc
FROM dbo.TaiKhoan ORDER BY MaTK;
SELECT N'Tài khoản' AS HangMuc, COUNT_BIG(*) AS SoLuong FROM dbo.TaiKhoan
UNION ALL SELECT N'Danh mục', COUNT_BIG(*) FROM dbo.DanhMuc
UNION ALL SELECT N'Món uống', COUNT_BIG(*) FROM dbo.MonUong
UNION ALL SELECT N'Cơ sở', COUNT_BIG(*) FROM dbo.Shop
UNION ALL SELECT N'Đơn giao hàng', COUNT_BIG(*) FROM dbo.DeliveryOrder
UNION ALL SELECT N'Đơn được phân cho SHIPPER', COUNT_BIG(*)
    FROM dbo.DeliveryOrder WHERE ShipperId=(SELECT MaTK FROM dbo.TaiKhoan WHERE VaiTro='SHIPPER');
PRINT N'OK: Reset UTE_Tea hoàn tất: 5 tài khoản; schema, menu, marketplace và SHIPPER demo đã sẵn sàng.';
GO
