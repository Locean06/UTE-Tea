-- UTE TEA / SQL SERVER: read-only environment and order synchronization audit.
-- Chạy trong SSMS trên SQL Server LOCAL, sau khi đã chạy migration tương ứng.
-- KHÔNG DROP/DELETE/UPDATE bất kỳ dữ liệu nào.
USE [UTE_Tea];
GO
SET NOCOUNT ON;
SELECT DB_NAME() AS DatabaseDangDung, @@SERVERNAME AS SqlServerDangDung;

IF OBJECT_ID('dbo.Shop','U') IS NULL OR OBJECT_ID('dbo.MonUong','U') IS NULL
   OR OBJECT_ID('dbo.Carrier','U') IS NULL OR OBJECT_ID('dbo.DeliveryOrder','U') IS NULL
   OR OBJECT_ID('dbo.DeliveryLine','U') IS NULL OR OBJECT_ID('dbo.DonHang','U') IS NULL
    THROW 51201, N'Thiếu bảng cơ bản. Kiểm tra các migration 1..8.', 1;

-- Cơ sở/địa chỉ công khai: cùng nguồn dbo.Shop với API /public/market/shops.
SELECT s.Id AS ShopId, s.Name, s.Area, s.Address, s.Active,
       (SELECT COUNT(*) FROM dbo.MonUong p WHERE p.ShopId=s.Id AND p.HoatDong=1 AND p.DeliveryStock>0) AS SoMonGiaoDuoc,
       (SELECT COUNT(*) FROM dbo.Carrier c WHERE c.Active=1 AND (c.ShopId=s.Id OR c.ShopId IS NULL)
          AND (c.Name LIKE N'%UTE Tea%' OR c.Name LIKE N'%nội bộ%' OR c.Name LIKE N'%Nhân viên%')) AS SoCauHinhGiaoNoiBo,
       (SELECT COUNT(*) FROM dbo.DeliveryOrder o WHERE o.ShopId=s.Id) AS SoDonGiao,
       (SELECT COUNT(*) FROM dbo.DeliveryOrder o WHERE o.ShopId=s.Id AND o.Status='NEW') AS SoDonChoXacNhan
FROM dbo.Shop s ORDER BY s.Id;

SELECT o.Id, o.ShopId, s.Name AS CoSo, o.Status, o.ShipperId,
       o.PaymentMethod, o.Paid, o.TotalAmount, o.CreatedAt,
       (SELECT COUNT(*) FROM dbo.DeliveryLine l WHERE l.OrderId=o.Id) AS SoDongMon
FROM dbo.DeliveryOrder o JOIN dbo.Shop s ON s.Id=o.ShopId
ORDER BY o.Id DESC OFFSET 0 ROWS FETCH NEXT 30 ROWS ONLY;

SELECT VaiTro, COUNT(*) AS SoTaiKhoan FROM dbo.TaiKhoan WHERE HoatDong=1 GROUP BY VaiTro ORDER BY VaiTro;
SELECT (SELECT COUNT(*) FROM dbo.DonHang) AS SoDonPOS_DonHang,
       (SELECT COUNT(*) FROM dbo.DeliveryOrder) AS SoDonGiao_DeliveryOrder;
SELECT TOP(1) DiaChi AS DiaChiCauHinhChung_KhongPhaiDanhSachCoSo FROM dbo.CauHinhChung WHERE MaCauHinh=1;

IF NOT EXISTS (SELECT 1 FROM dbo.Shop WHERE Name LIKE N'UTE Tea%Thủ Đức%' AND Active=1)
    THROW 51202, N'Chưa có Thủ Đức: chạy 9_restore_thu_duc_branch.sql.', 1;
IF EXISTS (SELECT 1 FROM dbo.Shop s WHERE s.Active=1 AND NOT EXISTS (
    SELECT 1 FROM dbo.MonUong p WHERE p.ShopId=s.Id AND p.HoatDong=1 AND p.DeliveryStock>0))
    THROW 51203, N'Có cơ sở không có món giao khả dụng. Kiểm tra menu/tồn kho từng cơ sở.', 1;
IF EXISTS (SELECT 1 FROM dbo.Shop s WHERE s.Active=1 AND NOT EXISTS (
    SELECT 1 FROM dbo.Carrier c WHERE c.Active=1 AND (c.ShopId=s.Id OR c.ShopId IS NULL)
      AND (c.Name LIKE N'%UTE Tea%' OR c.Name LIKE N'%nội bộ%' OR c.Name LIKE N'%Nhân viên%')))
    THROW 51204, N'Có cơ sở chưa có cấu hình giao nội bộ hợp lệ.', 1;
IF EXISTS (SELECT 1 FROM dbo.DeliveryOrder o WHERE NOT EXISTS (
    SELECT 1 FROM dbo.DeliveryLine l WHERE l.OrderId=o.Id))
    THROW 51205, N'Có đơn giao hàng không có dòng món: kiểm tra dữ liệu.', 1;
IF EXISTS (SELECT 1 FROM dbo.DeliveryOrder o WHERE o.ShipperId IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM dbo.TaiKhoan a WHERE a.MaTK=o.ShipperId AND a.VaiTro='SHIPPER'))
    THROW 51206, N'Có đơn được phân công tới tài khoản không có role SHIPPER.', 1;
PRINT N'PASS: Cơ sở, menu, giao nội bộ, đơn giao và người nhận giao có dữ liệu nhất quán ở mức kiểm tra SQL.';
GO
