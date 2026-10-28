-- UTE Tea - Repair SQL Server mapping required by GET /api/v1/cashier/orders.
-- SAFE: no DROP, DELETE, TRUNCATE, or updates to existing orders.
-- IMPORTANT: SQL Server DEFAULT ... WITH VALUES initializes only newly added columns on legacy rows.
-- Back up UTE_Tea first. Execute on the SQL Server instance used by DB_URL.
USE [UTE_Tea];
GO
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF OBJECT_ID(N'dbo.DonHang', N'U') IS NULL OR OBJECT_ID(N'dbo.Shop', N'U') IS NULL
    THROW 52010, N'Thieu bang DonHang/Shop. Chay migration 01-03 (khong chay 01/02 tren DB co du lieu) truoc.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH(N'dbo.DonHang', N'MaShop') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.DonHang ADD MaShop BIGINT NULL';
    IF COL_LENGTH(N'dbo.Shop', N'Area') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.Shop ADD Area NVARCHAR(120) NULL';
    IF COL_LENGTH(N'dbo.Shop', N'Latitude') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.Shop ADD Latitude FLOAT NULL';
    IF COL_LENGTH(N'dbo.Shop', N'Longitude') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.Shop ADD Longitude FLOAT NULL';
    IF OBJECT_ID(N'dbo.HoaDon', N'U') IS NOT NULL
    BEGIN
        IF COL_LENGTH(N'dbo.HoaDon', N'LoaiGiamChot') IS NULL
            EXEC sys.sp_executesql N'ALTER TABLE dbo.HoaDon ADD LoaiGiamChot VARCHAR(16) NOT NULL CONSTRAINT DF_HoaDon_LoaiGiamChot_Patch DEFAULT ''PERCENT'' WITH VALUES';
        IF COL_LENGTH(N'dbo.HoaDon', N'GiaTriGiamChot') IS NULL
            EXEC sys.sp_executesql N'ALTER TABLE dbo.HoaDon ADD GiaTriGiamChot DECIMAL(18,2) NOT NULL CONSTRAINT DF_HoaDon_GiaTriGiamChot_Patch DEFAULT 0 WITH VALUES';
    END;
    IF OBJECT_ID(N'dbo.TaiKhoan', N'U') IS NOT NULL
    BEGIN
        IF COL_LENGTH(N'dbo.TaiKhoan', N'EmailXacThuc') IS NULL
            EXEC sys.sp_executesql N'ALTER TABLE dbo.TaiKhoan ADD EmailXacThuc BIT NOT NULL CONSTRAINT DF_TaiKhoan_EmailXacThuc_Patch DEFAULT 1 WITH VALUES';
        IF COL_LENGTH(N'dbo.TaiKhoan', N'TokenVersion') IS NULL
            EXEC sys.sp_executesql N'ALTER TABLE dbo.TaiKhoan ADD TokenVersion BIGINT NOT NULL CONSTRAINT DF_TaiKhoan_TokenVersion_Patch DEFAULT 1 WITH VALUES';
        IF COL_LENGTH(N'dbo.TaiKhoan', N'AvatarUrl') IS NULL
            EXEC sys.sp_executesql N'ALTER TABLE dbo.TaiKhoan ADD AvatarUrl NVARCHAR(1000) NULL';
        IF COL_LENGTH(N'dbo.TaiKhoan', N'AvatarPublicId') IS NULL
            EXEC sys.sp_executesql N'ALTER TABLE dbo.TaiKhoan ADD AvatarPublicId NVARCHAR(255) NULL';
    END;
    IF COL_LENGTH(N'dbo.DonHang', N'Version') IS NULL
        EXEC sys.sp_executesql N'ALTER TABLE dbo.DonHang ADD Version BIGINT NOT NULL CONSTRAINT DF_DonHang_Version_Patch DEFAULT 0 WITH VALUES';
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID(N'dbo.DonHang') AND name = N'FK_DonHang_Shop')
    BEGIN
        DECLARE @OrphanedPickupShop BIT = 0;
        -- Dynamic SELECT avoids SQL Server binding a newly-added MaShop column before ALTER.
        EXEC sys.sp_executesql
            N'SELECT @hasOrphan = CASE WHEN EXISTS (
                SELECT 1 FROM dbo.DonHang AS d LEFT JOIN dbo.Shop AS s ON s.Id = d.MaShop
                WHERE d.MaShop IS NOT NULL AND s.Id IS NULL
              ) THEN 1 ELSE 0 END',
            N'@hasOrphan BIT OUTPUT', @hasOrphan = @OrphanedPickupShop OUTPUT;
        IF @OrphanedPickupShop = 0
            EXEC sys.sp_executesql N'ALTER TABLE dbo.DonHang WITH CHECK ADD CONSTRAINT FK_DonHang_Shop FOREIGN KEY (MaShop) REFERENCES dbo.Shop(Id)';
        ELSE PRINT N'Canh bao: MaShop trong DonHang co ID khong ton tai tai Shop; khong them FK, khong sua data.';
    END;
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.DonHang') AND name = N'IX_DonHang_MaShop')
        EXEC sys.sp_executesql N'CREATE INDEX IX_DonHang_MaShop ON dbo.DonHang(MaShop) WHERE MaShop IS NOT NULL';
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

-- Diagnose other possible causes of error 500 without altering any rows.
SELECT DB_NAME() AS DatabaseName, @@SERVERNAME AS SqlServer;
SELECT v.ObjectName, v.ColumnName,
       CASE WHEN COL_LENGTH(N'dbo.' + v.ObjectName, v.ColumnName) IS NULL
            THEN N'THIEU' ELSE N'OK' END AS SchemaStatus
FROM (VALUES (N'DonHang',N'MaShop'),(N'Shop',N'Area'),
             (N'Shop',N'Latitude'),(N'Shop',N'Longitude'),
             (N'HoaDon',N'LoaiGiamChot'),(N'HoaDon',N'GiaTriGiamChot'),
             (N'TaiKhoan',N'EmailXacThuc')) AS v(ObjectName, ColumnName);
SELECT TOP (30) d.MaDH, d.Nguon, d.TrangThai, d.MaShop, d.TaoLuc
FROM dbo.DonHang d ORDER BY d.MaDH DESC;
GO
