-- Run after 6_customer_data.sql, 7_marketplace.sql, 8_branch_neutral_cart.sql
-- and patch_email_verification.sql. Additive; never deletes or reassigns historical orders.
USE UTE_Tea;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.DonHang','ShopId') IS NULL ALTER TABLE dbo.DonHang ADD ShopId BIGINT NULL;
IF COL_LENGTH('dbo.DonHang','VoucherId') IS NULL ALTER TABLE dbo.DonHang ADD VoucherId BIGINT NULL;
IF COL_LENGTH('dbo.DonHang','RequestedPaymentMethod') IS NULL ALTER TABLE dbo.DonHang ADD RequestedPaymentMethod VARCHAR(20) NULL;
IF COL_LENGTH('dbo.DonHang','RequestFingerprint') IS NULL ALTER TABLE dbo.DonHang ADD RequestFingerprint VARCHAR(64) NULL;
IF COL_LENGTH('dbo.Ban','ShopId') IS NULL ALTER TABLE dbo.Ban ADD ShopId BIGINT NULL;
COMMIT;
GO
BEGIN TRANSACTION;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_DonHang_CheckoutShop')
    ALTER TABLE dbo.DonHang ADD CONSTRAINT FK_DonHang_CheckoutShop FOREIGN KEY(ShopId) REFERENCES dbo.Shop(Id);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_DonHang_CheckoutVoucher')
    ALTER TABLE dbo.DonHang ADD CONSTRAINT FK_DonHang_CheckoutVoucher FOREIGN KEY(VoucherId) REFERENCES dbo.Voucher(Id);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_Ban_CheckoutShop')
    ALTER TABLE dbo.Ban ADD CONSTRAINT FK_Ban_CheckoutShop FOREIGN KEY(ShopId) REFERENCES dbo.Shop(Id);
-- Refuse duplicates for operator review; never delete historical rows to make an index pass.
IF EXISTS (SELECT MaYeuCau FROM dbo.DonHang WHERE MaYeuCau IS NOT NULL GROUP BY MaYeuCau HAVING COUNT(*)>1)
    THROW 51000, 'Duplicate order request keys require manual review before migration.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.DonHang') AND name='UX_DonHang_MaYeuCau')
    CREATE UNIQUE INDEX UX_DonHang_MaYeuCau ON dbo.DonHang(MaYeuCau) WHERE MaYeuCau IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.DonHang') AND name='IX_DonHang_CheckoutShop')
    CREATE INDEX IX_DonHang_CheckoutShop ON dbo.DonHang(ShopId,TrangThai);
COMMIT;
GO
-- Map each existing table to its real ShopId explicitly, or configure POS_SHOP_ID
-- for a single legacy POS branch. Never infer a branch from a customer address.
