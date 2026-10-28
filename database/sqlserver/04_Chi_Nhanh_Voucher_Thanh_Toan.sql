-- 04 - CHI NHANH DEMO, SHIPPER, VOUCHER, TAI KHOAN THANH TOAN MB
-- PHAI chay 02 va 03 truoc: 02 da tao customer.delivery@utetea.vn va shipper@utetea.vn.
-- Chi dung cho database DEMO. Khong nen chay tren production.
-- Tien hanh sao luu DB truoc khi ap dung; branch addresses are demo placeholders.
USE [UTE_Tea];
GO


-- ========================================================================
-- NGUON: 9_two_demo_branches.sql
-- ========================================================================
-- UTE TEA - Bo sung 2 co so MAU, khong can thiep du lieu cu.
-- SQL Server; chay SAU 7_marketplace.sql va 8_branch_neutral_cart.sql.
-- Co the chay lap lai: chi tao co so / menu / giao hang con thieu.
-- LUU Y: Dia chi ben duoi chi la du lieu demo; thay dia chi thuc truoc khi su dung that.
-- Chay script tren dung database dang duoc ung dung ket noi (moi truong SQL Server).

USE [UTE_Tea];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'dbo.Shop', N'U') IS NULL
    OR OBJECT_ID(N'dbo.MonUong', N'U') IS NULL
    OR OBJECT_ID(N'dbo.DanhMuc', N'U') IS NULL
    OR OBJECT_ID(N'dbo.Carrier', N'U') IS NULL
    OR COL_LENGTH(N'dbo.Shop', N'Area') IS NULL
    OR COL_LENGTH(N'dbo.MonUong', N'MenuCode') IS NULL
BEGIN
    ;THROW 51001, N'Can chay cac migration 7_marketplace.sql va 8_branch_neutral_cart.sql truoc.', 1;
END;

DECLARE @OwnerId BIGINT = (
    SELECT TOP (1) MaTK FROM dbo.TaiKhoan
    WHERE VaiTro = 'ADMIN' AND HoatDong = 1 ORDER BY MaTK
);
IF @OwnerId IS NULL
BEGIN
    ;THROW 51002, N'Can co tai khoan ADMIN dang hoat dong de so huu hai co so demo.', 1;
END;

-- Uu tien menu chung (ShopId IS NULL) de khop cac mon trong gio;
-- neu khong co thi sao chep menu tu mot co so dang hoat dong.
DECLARE @SourceShopId BIGINT = NULL;
IF NOT EXISTS (SELECT 1 FROM dbo.MonUong WHERE ShopId IS NULL AND HoatDong = 1)
BEGIN
    SELECT TOP (1) @SourceShopId = s.Id
    FROM dbo.Shop AS s
    JOIN dbo.MonUong AS p ON p.ShopId = s.Id AND p.HoatDong = 1
    JOIN dbo.DanhMuc AS c ON c.MaDM = p.MaDM AND c.HoatDong = 1
    WHERE s.Active = 1
    GROUP BY s.Id
    ORDER BY COUNT_BIG(*) DESC, s.Id;
    IF @SourceShopId IS NULL
    BEGIN
        ;THROW 51003, N'Chua co menu nguon. Hay tao mon truoc khi them co so demo.', 1;
    END;
END;

DECLARE @NewBranches TABLE (
    Name NVARCHAR(120) NOT NULL PRIMARY KEY,
    Address NVARCHAR(500) NOT NULL,
    Area NVARCHAR(120) NOT NULL
);
INSERT INTO @NewBranches (Name, Address, Area) VALUES
(N'UTE Tea - Bình Thạnh (Demo)', N'Khu vực Bình Thạnh, TP. Hồ Chí Minh (địa chỉ demo)', N'Bình Thạnh'),
(N'UTE Tea - Gò Vấp (Demo)', N'Khu vực Gò Vấp, TP. Hồ Chí Minh (địa chỉ demo)', N'Gò Vấp');

BEGIN TRY
    BEGIN TRANSACTION;

    INSERT INTO dbo.Shop
        (Name, OwnerId, Active, ManagerCommissionEnabled, CommissionPercent,
         Address, Area, Latitude, Longitude, CreatedAt)
    SELECT b.Name, @OwnerId, 1, 0, 0, b.Address, b.Area, NULL, NULL, SYSUTCDATETIME()
    FROM @NewBranches AS b
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Shop AS s WITH (UPDLOCK, HOLDLOCK)
        WHERE s.Name = b.Name
    );

    -- Sao chep danh muc tu menu nguon; chi chen danh muc chua ton tai o co so do.
    INSERT INTO dbo.DanhMuc (TenDM, ChoPhepTopping, HoatDong, ShopId)
    SELECT sc.TenDM, sc.ChoPhepTopping, sc.HoatDong, dest.Id
    FROM @NewBranches AS b
    JOIN dbo.Shop AS dest ON dest.Name = b.Name
    CROSS JOIN dbo.DanhMuc AS sc
    WHERE sc.HoatDong = 1 AND EXISTS (
        SELECT 1 FROM dbo.MonUong AS source
        WHERE source.MaDM = sc.MaDM AND source.HoatDong = 1
          AND ((@SourceShopId IS NULL AND source.ShopId IS NULL)
            OR (@SourceShopId IS NOT NULL AND source.ShopId = @SourceShopId))
    )
      AND NOT EXISTS (
        SELECT 1 FROM dbo.DanhMuc AS c WITH (UPDLOCK, HOLDLOCK)
        WHERE c.ShopId = dest.Id AND c.TenDM = sc.TenDM
    );

    -- Moi co so co ban ghi san pham va ton giao hang RIENG (50 moi bien the).
    -- Gia va ma menu lay tu menu nguon, de khong pha vo quy trinh bao gia hien co.
    INSERT INTO dbo.MonUong
        (MaDM, TenMon, Size, MoTa, Gia, AnhUrl, AnhPublicId,
         HoatDong, ShopId, DeliveryStock, SearchName, MenuCode, CatalogVersion)
    SELECT targetCat.MaDM, p.TenMon, p.Size, p.MoTa, p.Gia, p.AnhUrl,
           NULL, 1, dest.Id, 50, p.SearchName, p.MenuCode, 0
    FROM @NewBranches AS b
    JOIN dbo.Shop AS dest ON dest.Name = b.Name
    JOIN dbo.MonUong AS p
      ON ((@SourceShopId IS NULL AND p.ShopId IS NULL)
       OR (@SourceShopId IS NOT NULL AND p.ShopId = @SourceShopId))
    JOIN dbo.DanhMuc AS sourceCat ON sourceCat.MaDM = p.MaDM
    JOIN dbo.DanhMuc AS targetCat
      ON targetCat.ShopId = dest.Id AND targetCat.TenDM = sourceCat.TenDM
    WHERE p.HoatDong = 1 AND sourceCat.HoatDong = 1
      AND NOT EXISTS (
        SELECT 1 FROM dbo.MonUong AS existing WITH (UPDLOCK, HOLDLOCK)
        WHERE existing.ShopId = dest.Id
          AND existing.TenMon = p.TenMon AND existing.Size = p.Size
    );

    -- Don vi giao hang gan rieng tung co so, khong thay doi cac don vi hien co.
    INSERT INTO dbo.Carrier (ShopId, Name, Fee, Active)
    SELECT dest.Id, N'UTE Tea - Nhân viên giao hàng', 15000, 1
    FROM @NewBranches AS b
    JOIN dbo.Shop AS dest ON dest.Name = b.Name
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Carrier AS c WITH (UPDLOCK, HOLDLOCK)
        WHERE c.ShopId = dest.Id AND c.Name = N'UTE Tea - Nhân viên giao hàng'
    );

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Kiem tra so mon, ton kho va don vi giao cho 2 co so vua them.
SELECT s.Id, s.Name, s.Area, s.Address, s.Active,
       (SELECT COUNT(*) FROM dbo.MonUong AS p WHERE p.ShopId = s.Id AND p.HoatDong = 1) AS SoMonDangBan,
       (SELECT COUNT(*) FROM dbo.Carrier AS c WHERE c.ShopId = s.Id AND c.Active = 1) AS SoDonViGiao
FROM dbo.Shop AS s
WHERE s.Name IN (SELECT Name FROM @NewBranches)
ORDER BY s.Name;
GO


-- ========================================================================
-- NGUON: 9_restore_thu_duc_branch.sql
-- ========================================================================
-- UTE TEA - Phuc hoi co so Thu Duc, khong xoa hay sua cac don hang da dat.
-- SQL Server; chay SAU 7_marketplace.sql va 8_branch_neutral_cart.sql.
-- Co the chay lap lai: chi tao co so / menu / giao hang con thieu.
-- LUU Y: Dia chi ben duoi chi la du lieu demo; thay dia chi thuc truoc khi su dung that.
-- Database dung tren profile local. Kiem tra ten DB truoc khi chay.

USE [UTE_Tea];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'dbo.Shop', N'U') IS NULL
    OR OBJECT_ID(N'dbo.MonUong', N'U') IS NULL
    OR OBJECT_ID(N'dbo.DanhMuc', N'U') IS NULL
    OR OBJECT_ID(N'dbo.Carrier', N'U') IS NULL
    OR COL_LENGTH(N'dbo.Shop', N'Area') IS NULL
    OR COL_LENGTH(N'dbo.MonUong', N'MenuCode') IS NULL
BEGIN
    ;THROW 51001, N'Can chay cac migration 7_marketplace.sql va 8_branch_neutral_cart.sql truoc.', 1;
END;

DECLARE @OwnerId BIGINT = (
    SELECT TOP (1) MaTK FROM dbo.TaiKhoan
    WHERE VaiTro = 'ADMIN' AND HoatDong = 1 ORDER BY MaTK
);
IF @OwnerId IS NULL
BEGIN
    ;THROW 51002, N'Can co tai khoan ADMIN dang hoat dong de so huu co so Thu Duc.', 1;
END;

-- Uu tien menu chung (ShopId IS NULL) de khop cac mon trong gio;
-- neu khong co thi sao chep menu tu mot co so dang hoat dong.
DECLARE @SourceShopId BIGINT = NULL;
IF NOT EXISTS (SELECT 1 FROM dbo.MonUong WHERE ShopId IS NULL AND HoatDong = 1)
BEGIN
    SELECT TOP (1) @SourceShopId = s.Id
    FROM dbo.Shop AS s
    JOIN dbo.MonUong AS p ON p.ShopId = s.Id AND p.HoatDong = 1
    JOIN dbo.DanhMuc AS c ON c.MaDM = p.MaDM AND c.HoatDong = 1
    WHERE s.Active = 1
    GROUP BY s.Id
    ORDER BY COUNT_BIG(*) DESC, s.Id;
    IF @SourceShopId IS NULL
    BEGIN
        ;THROW 51003, N'Chua co menu nguon. Hay tao mon truoc khi them co so demo.', 1;
    END;
END;

-- Neu co so Thu Duc da ton tai, dung lai ban ghi do de tranh trung ShopId.
DECLARE @TargetName NVARCHAR(120);
SELECT TOP (1) @TargetName = Name
FROM dbo.Shop
WHERE Name LIKE N'UTE Tea%Thủ Đức%'
ORDER BY Active DESC, Id ASC;
IF @TargetName IS NULL SET @TargetName = N'UTE Tea - Thủ Đức';
DECLARE @NewBranches TABLE (
    Name NVARCHAR(120) NOT NULL PRIMARY KEY,
    Address NVARCHAR(500) NOT NULL,
    Area NVARCHAR(120) NOT NULL
);
INSERT INTO @NewBranches (Name, Address, Area) VALUES
(@TargetName, N'Khu vực TP. Thủ Đức, TP. Hồ Chí Minh (địa chỉ demo, cần thay khi triển khai thực)', N'Thủ Đức');

BEGIN TRY
    BEGIN TRANSACTION;

    INSERT INTO dbo.Shop
        (Name, OwnerId, Active, ManagerCommissionEnabled, CommissionPercent,
         Address, Area, Latitude, Longitude, CreatedAt)
    SELECT b.Name, @OwnerId, 1, 0, 0, b.Address, b.Area, NULL, NULL, SYSUTCDATETIME()
    FROM @NewBranches AS b
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Shop AS s WITH (UPDLOCK, HOLDLOCK)
        WHERE s.Name = b.Name
    );

    -- Kich hoat lai co so Thu Duc neu truoc do da co va bi tam ngung.
    UPDATE s SET Active = 1
    FROM dbo.Shop s JOIN @NewBranches b ON b.Name = s.Name
    WHERE s.Active = 0;

    -- Sao chep danh muc tu menu nguon; chi chen danh muc chua ton tai o co so do.
    INSERT INTO dbo.DanhMuc (TenDM, ChoPhepTopping, HoatDong, ShopId)
    SELECT sc.TenDM, sc.ChoPhepTopping, sc.HoatDong, dest.Id
    FROM @NewBranches AS b
    JOIN dbo.Shop AS dest ON dest.Name = b.Name
    CROSS JOIN dbo.DanhMuc AS sc
    WHERE sc.HoatDong = 1 AND EXISTS (
        SELECT 1 FROM dbo.MonUong AS source
        WHERE source.MaDM = sc.MaDM AND source.HoatDong = 1
          AND ((@SourceShopId IS NULL AND source.ShopId IS NULL)
            OR (@SourceShopId IS NOT NULL AND source.ShopId = @SourceShopId))
    )
      AND NOT EXISTS (
        SELECT 1 FROM dbo.DanhMuc AS c WITH (UPDLOCK, HOLDLOCK)
        WHERE c.ShopId = dest.Id AND c.TenDM = sc.TenDM
    );

    -- Moi co so co ban ghi san pham va ton giao hang RIENG (50 moi bien the).
    -- Gia va ma menu lay tu menu nguon, de khong pha vo quy trinh bao gia hien co.
    INSERT INTO dbo.MonUong
        (MaDM, TenMon, Size, MoTa, Gia, AnhUrl, AnhPublicId,
         HoatDong, ShopId, DeliveryStock, SearchName, MenuCode, CatalogVersion)
    SELECT targetCat.MaDM, p.TenMon, p.Size, p.MoTa, p.Gia, p.AnhUrl,
           NULL, 1, dest.Id, 50, p.SearchName, p.MenuCode, 0
    FROM @NewBranches AS b
    JOIN dbo.Shop AS dest ON dest.Name = b.Name
    JOIN dbo.MonUong AS p
      ON ((@SourceShopId IS NULL AND p.ShopId IS NULL)
       OR (@SourceShopId IS NOT NULL AND p.ShopId = @SourceShopId))
    JOIN dbo.DanhMuc AS sourceCat ON sourceCat.MaDM = p.MaDM
    JOIN dbo.DanhMuc AS targetCat
      ON targetCat.ShopId = dest.Id AND targetCat.TenDM = sourceCat.TenDM
    WHERE p.HoatDong = 1 AND sourceCat.HoatDong = 1
      AND NOT EXISTS (
        SELECT 1 FROM dbo.MonUong AS existing WITH (UPDLOCK, HOLDLOCK)
        WHERE existing.ShopId = dest.Id
          AND existing.TenMon = p.TenMon AND existing.Size = p.Size
    );

    -- Don vi giao hang gan rieng tung co so, khong thay doi cac don vi hien co.
    INSERT INTO dbo.Carrier (ShopId, Name, Fee, Active)
    SELECT dest.Id, N'UTE Tea - Nhân viên giao hàng', 15000, 1
    FROM @NewBranches AS b
    JOIN dbo.Shop AS dest ON dest.Name = b.Name
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Carrier AS c WITH (UPDLOCK, HOLDLOCK)
        WHERE c.ShopId = dest.Id AND c.Name = N'UTE Tea - Nhân viên giao hàng'
    );

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Kiem tra so mon, ton kho va phuong an giao noi bo cho Thu Duc.
SELECT s.Id, s.Name, s.Area, s.Address, s.Active,
       (SELECT COUNT(*) FROM dbo.MonUong AS p WHERE p.ShopId = s.Id AND p.HoatDong = 1) AS SoMonDangBan,
       (SELECT COUNT(*) FROM dbo.Carrier AS c WHERE c.ShopId = s.Id AND c.Active = 1) AS SoDonViGiao
FROM dbo.Shop AS s
WHERE s.Name IN (SELECT Name FROM @NewBranches)
ORDER BY s.Name;
GO


-- ========================================================================
-- NGUON: 10_shipper_demo_orders.sql
-- ========================================================================
-- UTE Tea / SQL Server LOCAL ONLY.
-- Seed 4 preassigned delivery orders and 1 unassigned one to test the SHIPPER dashboard.
-- Requires scripts 1..9. All names, phone numbers, and addresses below are DEMO ONLY.
USE [UTE_Tea];
GO
SET XACT_ABORT ON;
SET NOCOUNT ON;

DECLARE @CustomerId BIGINT = (SELECT MaTK FROM dbo.TaiKhoan WHERE Email='customer.delivery@utetea.vn' AND VaiTro='CUSTOMER');
DECLARE @ShipperId BIGINT = (SELECT MaTK FROM dbo.TaiKhoan WHERE Email='shipper@utetea.vn' AND VaiTro='SHIPPER');
DECLARE @ShopId BIGINT = (SELECT TOP (1) Id FROM dbo.Shop WHERE Name=N'UTE Tea - Bình Thạnh (Demo)' AND Active=1 ORDER BY Id);
DECLARE @CarrierId BIGINT = (SELECT TOP (1) Id FROM dbo.Carrier WHERE ShopId=@ShopId AND Active=1 ORDER BY Id);
DECLARE @ProductId BIGINT = (SELECT TOP (1) MaMon FROM dbo.MonUong WHERE ShopId=@ShopId AND HoatDong=1 AND DeliveryStock > 0 ORDER BY MaMon);

-- Don shipper ben duoi CHI la du lieu DEMO. Khong de tai khoan demo thieu
-- lam dung ca script 04 (voucher va cau hinh thanh toan van duoc xu ly).
IF @CustomerId IS NULL OR @ShipperId IS NULL OR @ShopId IS NULL OR @CarrierId IS NULL OR @ProductId IS NULL
BEGIN
    PRINT N'[SQL 04] Bo qua 5 don giao hang DEMO vi thieu du lieu:';
    IF @CustomerId IS NULL PRINT N'  - Thieu CUSTOMER: customer.delivery@utetea.vn';
    IF @ShipperId IS NULL PRINT N'  - Thieu SHIPPER: shipper@utetea.vn';
    IF @ShopId IS NULL PRINT N'  - Thieu chi nhanh UTE Tea - Binh Thanh (Demo) dang hoat dong';
    IF @CarrierId IS NULL PRINT N'  - Thieu don vi van chuyen dang hoat dong cua chi nhanh';
    IF @ProductId IS NULL PRINT N'  - Thieu san pham dang ban, DeliveryStock > 0 tai chi nhanh';
    PRINT N'  => Khong them don mau; tiep tuc chay phan voucher va cau hinh thanh toan.';
END
ELSE
BEGIN
DECLARE @Examples TABLE (
 K VARCHAR(64) NOT NULL PRIMARY KEY,
 DeliveryStatus VARCHAR(24) NOT NULL,
 Qty INT NOT NULL,
 AssignShipper BIT NOT NULL,
 IsPaid BIT NOT NULL,
 IsFailure BIT NOT NULL,
 DeliveryDaysAgo INT NOT NULL
);
INSERT INTO @Examples VALUES
 ('UTE_LOCAL_SHIP_CONFIRMED_01', 'CONFIRMED',       2, 1, 0, 0, 0),
 ('UTE_LOCAL_SHIP_SHIPPING_02',  'SHIPPING',        1, 1, 0, 0, 0),
 ('UTE_LOCAL_SHIP_DELIVERED_03', 'DELIVERED',       2, 1, 1, 0, 1),
 ('UTE_LOCAL_SHIP_FAILED_04',    'DELIVERY_FAILED', 1, 1, 0, 1, 1),
 ('UTE_LOCAL_SHIP_UNASSIGNED_05','CONFIRMED',       1, 0, 0, 0, 0);

BEGIN TRY
 BEGIN TRANSACTION;
 INSERT INTO dbo.DeliveryOrder
  (AccountId, ShopId, ShipperId, CarrierId, VoucherId, Recipient, Phone, AddressLine,
   Subtotal, ShippingFee, DiscountAmount, TotalAmount, CommissionPercent,
   Status, PaymentMethod, Paid, GatewayReference, RefundReference,
   IdempotencyKey, RequestHash, CreatedAt, DeliveredAt, DeliveryFailed, Version)
 SELECT
   @CustomerId, @ShopId, CASE WHEN e.AssignShipper=1 THEN @ShipperId ELSE NULL END,
   @CarrierId, NULL, N'Khách hàng DEMO', '0901000013',
   N'Địa chỉ DEMO, Bình Thạnh, TP. Hồ Chí Minh - không giao hàng thật',
   CAST(p.Gia*e.Qty AS DECIMAL(18,2)), c.Fee, 0,
   CAST(p.Gia*e.Qty+c.Fee AS DECIMAL(18,2)), s.CommissionPercent,
   e.DeliveryStatus, 'COD', e.IsPaid, NULL, NULL,
   e.K, CONVERT(VARCHAR(64),HASHBYTES('SHA2_256',e.K),2),
   DATEADD(DAY,-e.DeliveryDaysAgo,SYSUTCDATETIME()),
   CASE WHEN e.DeliveryStatus='DELIVERED' THEN DATEADD(DAY,-e.DeliveryDaysAgo,SYSUTCDATETIME()) ELSE NULL END,
   e.IsFailure, 0
 FROM @Examples AS e
 JOIN dbo.Shop AS s ON s.Id=@ShopId
 JOIN dbo.Carrier AS c ON c.Id=@CarrierId
 JOIN dbo.MonUong AS p ON p.MaMon=@ProductId
 WHERE NOT EXISTS (
   SELECT 1 FROM dbo.DeliveryOrder AS o WITH (UPDLOCK,HOLDLOCK)
   WHERE o.AccountId=@CustomerId AND o.IdempotencyKey=e.K
 );

 INSERT INTO dbo.DeliveryLine
  (OrderId, ProductId, ProductName, Quantity, UnitPrice, SnapshotSize, Note, IsTopping)
 SELECT o.Id, p.MaMon, p.TenMon, e.Qty, p.Gia, p.Size,
        N'Dữ liệu DEMO để kiểm tra role SHIPPER', 0
 FROM @Examples AS e
 JOIN dbo.DeliveryOrder AS o ON o.AccountId=@CustomerId AND o.IdempotencyKey=e.K
 JOIN dbo.MonUong AS p ON p.MaMon=@ProductId
 WHERE NOT EXISTS (SELECT 1 FROM dbo.DeliveryLine AS l WHERE l.OrderId=o.Id);
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

SELECT o.Id, o.Status, o.ShipperId, o.TotalAmount, o.Paid, o.DeliveryFailed,
       o.IdempotencyKey AS DemoOrderKey
FROM dbo.DeliveryOrder AS o
WHERE o.AccountId=@CustomerId AND o.IdempotencyKey LIKE 'UTE_LOCAL_SHIP_%'
ORDER BY o.Id;
END; -- Ket thuc nhanh du du lieu demo shipper
GO
-- ========================================================================
-- NGUON: 19_seed_voucher_ute_tea.sql (8 voucher mau)
-- ========================================================================
/*
 UTE Tea - Them du lieu demo vao dbo.Voucher (SQL Server)
 YEU CAU: DB UTE_Tea da chay database/sqlserver/7_marketplace.sql
         va 14_percent_voucher_bill.sql (neu DB cu chua co cot giam theo %).
 An toan khi chay lap lai: KHONG xoa voucher/don hang, KHONG reset UsedCount.
 Chi cap nhat ma demo GIAMGIA10 de sua cau hinh cu; ma con lai chi INSERT neu chua co.
 Chi nen su dung tren DB demo/test. Backup DB truoc khi ap dung.
 */
USE [UTE_Tea];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'dbo.Voucher', N'U') IS NULL
    THROW 51901, N'Khong tim thay dbo.Voucher. Hay chay 7_marketplace.sql truoc.', 1;
IF COL_LENGTH('dbo.Voucher', 'DiscountType') IS NULL
    OR COL_LENGTH('dbo.Voucher', 'DiscountPercent') IS NULL
    THROW 51902, N'Thieu cot giam theo phan tram. Hay chay 14_percent_voucher_bill.sql truoc.', 1;
GO

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @Seed TABLE (
        Code VARCHAR(32) PRIMARY KEY,
        DiscountAmount DECIMAL(18,2) NOT NULL,
        DiscountType VARCHAR(16) NOT NULL,
        DiscountPercent DECIMAL(5,2) NOT NULL,
        MinimumAmount DECIMAL(18,2) NOT NULL,
        StartsAt DATETIME2 NOT NULL,
        EndsAt DATETIME2 NOT NULL,
        UsageLimit INT NOT NULL,
        PerAccountLimit INT NOT NULL
    );

    INSERT INTO @Seed
        (Code, DiscountAmount, DiscountType, DiscountPercent, MinimumAmount,
         StartsAt, EndsAt, UsageLimit, PerAccountLimit)
    VALUES
        ('GIAMGIA10', 0, 'PERCENT', 10,      0, '2026-01-01T00:00:00', '2027-12-31T23:59:59', 100000, 1000),
        ('UTETEA5',   0, 'PERCENT',  5,  30000, '2026-01-01T00:00:00', '2027-12-31T23:59:59', 10000, 5),
        ('UTETEA15',  0, 'PERCENT', 15, 100000, '2026-01-01T00:00:00', '2027-12-31T23:59:59', 10000, 3),
        ('UTETEA20',  0, 'PERCENT', 20, 150000, '2026-01-01T00:00:00', '2027-12-31T23:59:59', 10000, 2),
        ('GIAM10K', 10000, 'FIXED_AMOUNT', 0,  50000, '2026-01-01T00:00:00', '2027-12-31T23:59:59', 10000, 3),
        ('GIAM20K', 20000, 'FIXED_AMOUNT', 0, 100000, '2026-01-01T00:00:00', '2027-12-31T23:59:59', 10000, 2),
        ('GIAM30K', 30000, 'FIXED_AMOUNT', 0, 200000, '2026-01-01T00:00:00', '2027-12-31T23:59:59', 10000, 2),
        ('GIAM50K', 50000, 'FIXED_AMOUNT', 0, 300000, '2026-01-01T00:00:00', '2027-12-31T23:59:59', 10000, 1);

    -- Sua MA DEMO cu GIAMGIA10 neu da ton tai, khong thay doi UsedCount.
    UPDATE v
    SET v.ShopId = NULL,
        v.DiscountAmount = s.DiscountAmount,
        v.DiscountType = s.DiscountType,
        v.DiscountPercent = s.DiscountPercent,
        v.MinimumAmount = s.MinimumAmount,
        v.StartsAt = s.StartsAt,
        v.EndsAt = s.EndsAt,
        v.UsageLimit = CASE
            WHEN v.UsageLimit < v.UsedCount + 1000 THEN v.UsedCount + 1000
            ELSE v.UsageLimit
        END,
        v.PerAccountLimit = CASE
            WHEN v.PerAccountLimit < s.PerAccountLimit THEN s.PerAccountLimit
            ELSE v.PerAccountLimit
        END,
        v.Active = 1
    FROM dbo.Voucher AS v
    INNER JOIN @Seed AS s ON s.Code = v.Code
    WHERE v.Code = 'GIAMGIA10';

    -- Chi tao voucher chua co: khong ghi de du lieu/hieu luc voucher khac.
    INSERT INTO dbo.Voucher
        (Code, ShopId, DiscountAmount, DiscountType, DiscountPercent,
         MinimumAmount, StartsAt, EndsAt, UsageLimit, UsedCount,
         PerAccountLimit, Active)
    SELECT s.Code, NULL, s.DiscountAmount, s.DiscountType, s.DiscountPercent,
           s.MinimumAmount, s.StartsAt, s.EndsAt, s.UsageLimit, 0,
           s.PerAccountLimit, 1
    FROM @Seed AS s
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Voucher WITH (UPDLOCK, HOLDLOCK)
        WHERE Code = s.Code
    );

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

-- Ket qua sau khi chay. Trang thai duoc kiem tra theo gio UTC nhu backend.
SELECT Id, Code, ShopId, DiscountType, DiscountAmount, DiscountPercent,
       MinimumAmount, StartsAt, EndsAt, UsageLimit, UsedCount,
       PerAccountLimit, Active,
       CASE WHEN Active = 1
                  AND SYSUTCDATETIME() >= StartsAt
                  AND SYSUTCDATETIME() < EndsAt
                  AND UsedCount < UsageLimit
            THEN N'Con hieu luc' ELSE N'Khong kha dung' END AS CurrentStatus
FROM dbo.Voucher
WHERE Code IN ('GIAMGIA10', 'UTETEA5', 'UTETEA15', 'UTETEA20',
               'GIAM10K', 'GIAM20K', 'GIAM30K', 'GIAM50K')
ORDER BY Id;
GO
-- ========================================================================
-- NGUON: 17_mb_bank_recipient_settings.sql
-- ========================================================================
-- UTE-Tea: Cập nhật tài khoản nhận chuyển khoản từ ảnh VietQR do chủ dự án cung cấp.
-- CHỈ thay đổi 3 trường ngân hàng, không đụng các trường cấu hình khác.
-- Chạy một lần trên SQL Server UTE_Tea đang được backend sử dụng.
USE [UTE_Tea];
GO
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRAN;
    IF OBJECT_ID(N'dbo.CauHinhChung', N'U') IS NULL
        THROW 51711, N'Chưa có bảng CauHinhChung.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.CauHinhChung WHERE MaCauHinh=1)
        THROW 51712, N'Chưa có bản ghi cấu hình quán MaCauHinh=1.', 1;
    UPDATE dbo.CauHinhChung
    SET NganHang=N'MB Bank', SoTaiKhoan=N'0934197505', ChuTK=N'KIEN NGUYEN XUAN LUU',
        SuaLuc=SYSUTCDATETIME()
    WHERE MaCauHinh=1;
    COMMIT;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK;
    THROW;
END CATCH;
GO
SELECT MaCauHinh,NganHang,SoTaiKhoan,ChuTK FROM dbo.CauHinhChung WHERE MaCauHinh=1;
GO
