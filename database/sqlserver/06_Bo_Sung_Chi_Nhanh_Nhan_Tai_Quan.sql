-- CHAY file nay NEU database da co 01-05. Khong xoa du lieu.
USE UTE_Tea;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.Shop', N'U') IS NULL OR OBJECT_ID(N'dbo.DonHang', N'U') IS NULL
    THROW 52001, N'Thieu bang Shop hoac DonHang. Can cai 01-05 truoc.', 1;
IF COL_LENGTH(N'dbo.DonHang', N'MaShop') IS NULL
    EXEC sp_executesql N'ALTER TABLE dbo.DonHang ADD MaShop BIGINT NULL';
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.DonHang') AND name=N'FK_DonHang_Shop')
    EXEC sp_executesql N'ALTER TABLE dbo.DonHang WITH CHECK ADD CONSTRAINT FK_DonHang_Shop FOREIGN KEY (MaShop) REFERENCES dbo.Shop(Id)';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.DonHang') AND name=N'IX_DonHang_MaShop')
    EXEC sp_executesql N'CREATE INDEX IX_DonHang_MaShop ON dbo.DonHang(MaShop) WHERE MaShop IS NOT NULL';
COMMIT TRANSACTION;
GO
SELECT c.name FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.DonHang') AND c.name=N'MaShop';
