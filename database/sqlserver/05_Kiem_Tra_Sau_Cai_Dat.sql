-- 05 - KIEM TRA SAU KHI CAI DAT; CHI DOC; KHONG INSERT/UPDATE/DELETE/DROP
USE [UTE_Tea];
GO
SET NOCOUNT ON;
PRINT N'Kiem tra ket noi, schema, voucher, tai khoan ngan hang';
SELECT @@SERVERNAME AS SqlServer, DB_NAME() AS DatabaseName, SYSUTCDATETIME() AS CurrentUtc;
GO


-- ========================================================================
-- NGUON: CHECK_SCHEMA_READONLY.sql
-- ========================================================================
-- UTE Tea: read-only verification for source versions using migrations 6, 7 and 8.
-- This script DOES NOT create, modify, or delete data.
USE UTE_Tea;
SET NOCOUNT ON;
DECLARE @Checks TABLE (ObjectName NVARCHAR(128), ColumnName NVARCHAR(128));
INSERT INTO @Checks (ObjectName,ColumnName) VALUES
('MonUong','ShopId'),('MonUong','DeliveryStock'),('MonUong','SearchName'),
('MonUong','CatalogVersion'),('MonUong','MenuCode'),
('DanhMuc','ShopId'),('Shop','Id'),('Shop','Area'),('Shop','Latitude'),
('Shop','Longitude'),('DeliveryAddress','Area'),('DeliveryAddress','Latitude'),
('DeliveryAddress','Longitude'),('DeliveryLine','SnapshotSize'),
('DeliveryLine','Note'),('DeliveryLine','IsTopping'),('DeliveryOrder','Id'),
('Voucher','Id'),('Carrier','Id'),('ProductReview','Id'),('DonHang','MaShop');
SELECT ObjectName,ColumnName,
 CASE WHEN COL_LENGTH(N'dbo.'+ObjectName,ColumnName) IS NULL THEN N'MISSING' ELSE N'OK' END AS CheckResult
FROM @Checks ORDER BY ObjectName,ColumnName;
-- If any check is MISSING, inspect migrations 6_customer_data.sql,
-- 7_marketplace.sql and 8_branch_neutral_cart.sql IN THAT ORDER, with a
-- verified backup; do not rerun 1_schema.sql against an existing database.
GO


-- ========================================================================
-- NGUON: CHECK_ORDER_AND_BRANCH_SYNC.sql
-- ========================================================================
-- CHI DOC; khong DROP / DELETE / UPDATE.
USE UTE_Tea;
SELECT DB_NAME() AS DatabaseDangKetNoi, @@SERVERNAME AS SqlServer;
SELECT s.Id, s.Name, s.Area, s.Active, s.Address,
       (SELECT COUNT(*) FROM dbo.MonUong p WHERE p.ShopId=s.Id AND p.HoatDong=1) AS MonDangBan,
       (SELECT COUNT(*) FROM dbo.Carrier c WHERE (c.ShopId=s.Id OR c.ShopId IS NULL) AND c.Active=1) AS HinhThucGiaoHopLe
FROM dbo.Shop s ORDER BY s.Id;
SELECT TOP(30) o.Id, o.ShopId, s.Name AS CoSo, o.Recipient, o.Status, o.ShipperId,
       o.TotalAmount, o.CreatedAt
FROM dbo.DeliveryOrder o INNER JOIN dbo.Shop s ON s.Id=o.ShopId
ORDER BY o.Id DESC;
SELECT COUNT(*) AS SoDonPOS FROM dbo.DonHang;
SELECT COUNT(*) AS SoDonGiaoHang FROM dbo.DeliveryOrder;
GO


-- ========================================================================
-- NGUON: 18_check_checkout_readonly.sql
-- ========================================================================
-- CHỈ ĐỌC: kiểm tra migration, voucher và ngân hàng trên DB đang chạy.
USE [UTE_Tea];
GO
SELECT DB_NAME() AS DatabaseDangDung,
       OBJECT_ID(N'dbo.Voucher',N'U') AS CoBangVoucher,
       COL_LENGTH('dbo.Voucher','DiscountPercent') AS CoCotDiscountPercent,
       OBJECT_ID(N'dbo.CauHinhChung',N'U') AS CoBangCauHinhChung;
GO
IF OBJECT_ID(N'dbo.Voucher',N'U') IS NOT NULL
AND COL_LENGTH('dbo.Voucher','DiscountPercent') IS NOT NULL
BEGIN
 SELECT Code,DiscountType,DiscountPercent,DiscountAmount,MinimumAmount,ShopId,
        StartsAt,EndsAt,UsageLimit,UsedCount,PerAccountLimit,Active,
        CASE WHEN Active=1 AND StartsAt<=SYSUTCDATETIME() AND EndsAt>SYSUTCDATETIME()
             AND UsedCount<UsageLimit THEN N'Còn hiệu lực theo thời gian/lượt chung'
             ELSE N'Cần kiểm tra hạn/kích hoạt/lượt' END AS TrangThai
 FROM dbo.Voucher WHERE Code='GIAMGIA10';
END;
GO
IF OBJECT_ID(N'dbo.CauHinhChung',N'U') IS NOT NULL
 SELECT MaCauHinh,NganHang,SoTaiKhoan,ChuTK FROM dbo.CauHinhChung WHERE MaCauHinh=1;
GO
-- Kiem tra 8 voucher mau va han dung (gia tri tu server, chi doc).
IF OBJECT_ID(N'dbo.Voucher',N'U') IS NOT NULL
   AND COL_LENGTH(N'dbo.Voucher',N'DiscountType') IS NOT NULL
   AND COL_LENGTH(N'dbo.Voucher',N'DiscountPercent') IS NOT NULL
BEGIN
 EXEC sys.sp_executesql N'
 SELECT Code, DiscountType, DiscountPercent, DiscountAmount, MinimumAmount,
        UsedCount, UsageLimit, Active,
        CASE WHEN Active=1 AND StartsAt<=SYSUTCDATETIME() AND EndsAt>SYSUTCDATETIME()
                   AND UsedCount < UsageLimit THEN N''OK'' ELSE N''KHONG_KHA_DUNG'' END AS TrangThai
 FROM dbo.Voucher
 WHERE Code IN (''GIAMGIA10'',''UTETEA5'',''UTETEA15'',''UTETEA20'',
                ''GIAM10K'',''GIAM20K'',''GIAM30K'',''GIAM50K'')
 ORDER BY Code;';
END
ELSE PRINT N'CAN KIEM TRA: Bang Voucher chua co du DiscountType, DiscountPercent';
GO

-- ========================================================================
-- KIEM TRA DU LIEU DEMO NHAT QUAN (chi SELECT, khong sua du lieu)
-- ========================================================================
USE [UTE_Tea];
GO
SET NOCOUNT ON;
SELECT N'Tai khoan demo' AS HangMuc, Email, VaiTro, HoatDong
FROM dbo.TaiKhoan
WHERE Email IN ('admin@utetea.vn','cashier@utetea.vn','kitchen@utetea.vn',
                'customer.delivery@utetea.vn','shipper@utetea.vn')
ORDER BY Email;

SELECT N'Chi nhanh' AS HangMuc, s.Id, s.Name, s.Active, s.Area,
 (SELECT COUNT_BIG(*) FROM dbo.MonUong p WHERE p.ShopId=s.Id AND p.HoatDong=1) AS SoMon,
 (SELECT COUNT_BIG(*) FROM dbo.Carrier c WHERE c.ShopId=s.Id AND c.Active=1) AS NhanVienGiao
FROM dbo.Shop s ORDER BY s.Id;

SELECT N'Voucher' AS HangMuc, v.Code,v.DiscountType,v.DiscountPercent,v.DiscountAmount,
 v.MinimumAmount,v.Active,v.StartsAt,v.EndsAt,
 CASE WHEN v.Active=1 AND v.UsedCount < v.UsageLimit
 AND v.StartsAt<=SYSUTCDATETIME() AND v.EndsAt>SYSUTCDATETIME()
 THEN N'OK' ELSE N'KHONG_KHA_DUNG' END AS KiemTra
FROM dbo.Voucher v ORDER BY v.Code;

SELECT N'Don giao hang demo' AS HangMuc, o.Id,o.IdempotencyKey,o.Status,o.TotalAmount,
 o.ShipperId,
 (SELECT COUNT_BIG(*) FROM dbo.DeliveryLine l WHERE l.OrderId=o.Id) AS SoDong
FROM dbo.DeliveryOrder o
WHERE o.IdempotencyKey LIKE 'UTE_LOCAL_SHIP_%'
ORDER BY o.Id;

SELECT N'Tai khoan nhan tien' AS HangMuc, NganHang,SoTaiKhoan,ChuTK
FROM dbo.CauHinhChung WHERE MaCauHinh=1;
GO
-- Tong hop PASS/FAIL. Neu co FAIL, xem cac ket qua chi tiet ben tren.
SELECT KiemTra,
 CASE WHEN SoLuong=GiaTriMongDoi THEN N'PASS' ELSE N'FAIL' END AS KetQua,
 SoLuong, GiaTriMongDoi
FROM (VALUES
 (N'Customer demo', (SELECT COUNT(*) FROM dbo.TaiKhoan WHERE Email='customer.delivery@utetea.vn' AND VaiTro='CUSTOMER' AND HoatDong=1),1),
 (N'Shipper demo', (SELECT COUNT(*) FROM dbo.TaiKhoan WHERE Email='shipper@utetea.vn' AND VaiTro='SHIPPER' AND HoatDong=1),1),
 (N'Admin demo', (SELECT COUNT(*) FROM dbo.TaiKhoan WHERE Email='admin@utetea.vn' AND VaiTro='ADMIN' AND HoatDong=1),1),
 (N'Chi nhanh co menu (>= 3)', (SELECT COUNT(*) FROM dbo.Shop s WHERE s.Active=1 AND EXISTS(SELECT 1 FROM dbo.MonUong p WHERE p.ShopId=s.Id AND p.HoatDong=1)),3),
 (N'Chi nhanh co giao hang (>= 3)', (SELECT COUNT(*) FROM dbo.Shop s WHERE s.Active=1 AND EXISTS(SELECT 1 FROM dbo.Carrier c WHERE c.ShopId=s.Id AND c.Active=1)),3),
 (N'8 ma voucher', (SELECT COUNT(*) FROM dbo.Voucher WHERE Code IN('GIAMGIA10','UTETEA5','UTETEA15','UTETEA20','GIAM10K','GIAM20K','GIAM30K','GIAM50K')),8),
 (N'5 don giao hang demo', (SELECT COUNT(*) FROM dbo.DeliveryOrder WHERE IdempotencyKey LIKE 'UTE_LOCAL_SHIP_%'),5),
 (N'Don demo deu co dong chi tiet', (SELECT COUNT(*) FROM dbo.DeliveryOrder o WHERE o.IdempotencyKey LIKE 'UTE_LOCAL_SHIP_%' AND EXISTS(SELECT 1 FROM dbo.DeliveryLine l WHERE l.OrderId=o.Id)),5),
 (N'Bank MB', (SELECT COUNT(*) FROM dbo.CauHinhChung WHERE MaCauHinh=1 AND NganHang=N'MB Bank' AND SoTaiKhoan=N'0934197505' AND ChuTK=N'KIEN NGUYEN XUAN LUU'),1)
 ) AS s(KiemTra,SoLuong,GiaTriMongDoi)
ORDER BY KiemTra;
GO
