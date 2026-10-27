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
