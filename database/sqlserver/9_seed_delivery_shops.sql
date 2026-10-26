-- Seed co so (Shop) + don vi giao hang + menu giao hang cho UTE_Tea tren SQL Server.
-- Chay SAU 8_branch_neutral_cart.sql. Co the chay lai nhieu lan (khong tao trung).
USE UTE_Tea;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- 1. Tai khoan chu co so (VENDOR), dung chung mat khau Password@123 voi admin
IF NOT EXISTS (SELECT 1 FROM dbo.TaiKhoan WHERE Email='vendor1@ute-tea.demo')
INSERT INTO dbo.TaiKhoan (Email, MatKhauBam, HoTen, DienThoai, VaiTro, HoatDong, EmailXacThuc, TokenVersion, TaoLuc)
SELECT 'vendor1@ute-tea.demo', MatKhauBam, N'Chu co so UTE Tea', '0901000099', 'VENDOR', 1, 1, 1, SYSUTCDATETIME()
FROM dbo.TaiKhoan WHERE Email='admin@milktea.vn';

DECLARE @owner BIGINT = (SELECT MaTK FROM dbo.TaiKhoan WHERE Email='vendor1@ute-tea.demo');

-- 2. Co so
IF NOT EXISTS (SELECT 1 FROM dbo.Shop WHERE Name=N'UTE Tea Campus')
INSERT INTO dbo.Shop (Name, OwnerId, Active, ManagerCommissionEnabled, CommissionPercent, Address, CreatedAt, Area)
VALUES (N'UTE Tea Campus', @owner, 1, 0, 5, N'1 Võ Văn Ngân, TP. Thủ Đức', SYSUTCDATETIME(), N'Thủ Đức');
IF NOT EXISTS (SELECT 1 FROM dbo.Shop WHERE Name=N'UTE Tea Làng Đại Học')
INSERT INTO dbo.Shop (Name, OwnerId, Active, ManagerCommissionEnabled, CommissionPercent, Address, CreatedAt, Area)
VALUES (N'UTE Tea Làng Đại Học', @owner, 1, 0, 5, N'2 Võ Văn Ngân, TP. Thủ Đức', SYSUTCDATETIME(), N'Thủ Đức');

-- 3. Don vi giao hang
IF NOT EXISTS (SELECT 1 FROM dbo.Carrier WHERE Name=N'UTE Tea giao tiêu chuẩn')
INSERT INTO dbo.Carrier (ShopId, Name, Fee, Active) VALUES (NULL, N'UTE Tea giao tiêu chuẩn', 15000, 1);

-- 4. Menu giao hang: sao chep danh muc + mon tu menu tai quan (ShopId IS NULL) sang tung co so
DECLARE @shop BIGINT;
DECLARE c CURSOR LOCAL FAST_FORWARD FOR
    SELECT Id FROM dbo.Shop WHERE Name IN (N'UTE Tea Campus', N'UTE Tea Làng Đại Học');
OPEN c; FETCH NEXT FROM c INTO @shop;
WHILE @@FETCH_STATUS = 0
BEGIN
    INSERT INTO dbo.DanhMuc (TenDM, ChoPhepTopping, HoatDong, ShopId)
    SELECT d.TenDM, d.ChoPhepTopping, d.HoatDong, @shop
    FROM dbo.DanhMuc d
    WHERE d.ShopId IS NULL
      AND NOT EXISTS (SELECT 1 FROM dbo.DanhMuc x WHERE x.ShopId=@shop AND x.TenDM=d.TenDM);

    INSERT INTO dbo.MonUong (MaDM, TenMon, Size, MoTa, Gia, AnhUrl, AnhPublicId, HoatDong, ShopId, DeliveryStock, SearchName, MenuCode)
    SELECT dm.MaDM, m.TenMon, m.Size, m.MoTa, m.Gia, m.AnhUrl, m.AnhPublicId, m.HoatDong, @shop, 50, m.SearchName, m.MenuCode
    FROM dbo.MonUong m
    JOIN dbo.DanhMuc src ON src.MaDM = m.MaDM AND src.ShopId IS NULL
    JOIN dbo.DanhMuc dm  ON dm.ShopId = @shop AND dm.TenDM = src.TenDM
    WHERE m.ShopId IS NULL
      AND NOT EXISTS (SELECT 1 FROM dbo.MonUong x WHERE x.ShopId=@shop AND x.TenMon=m.TenMon AND x.Size=m.Size);

    FETCH NEXT FROM c INTO @shop;
END
CLOSE c; DEALLOCATE c;

COMMIT;
GO
SELECT Id, Name, Active FROM dbo.Shop;
SELECT Id, Name, Fee, Active FROM dbo.Carrier;
SELECT ShopId, COUNT(*) AS SoMon FROM dbo.MonUong WHERE ShopId IS NOT NULL GROUP BY ShopId;
