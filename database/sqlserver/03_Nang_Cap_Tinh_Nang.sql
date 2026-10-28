-- 03 - MIGRATION TINH NANG / GIAO HANG / VOUCHER
-- Co gang giu nguyen don hang / tai khoan / don POS; backup DB truoc.
-- Neu DB da co du lieu: chay 03, sau do 04 va 05; bo qua 01, 02.
USE [UTE_Tea];
GO


-- ========================================================================
-- NGUON: 6_customer_data.sql
-- ========================================================================
-- Run on an existing UTE_Tea database. Additive, repeatable, no data deletion.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.AccountCart','U') IS NULL
CREATE TABLE dbo.AccountCart (
 AccountId BIGINT NOT NULL PRIMARY KEY REFERENCES dbo.TaiKhoan(MaTK),
 ItemsJson NVARCHAR(MAX) NOT NULL CONSTRAINT DF_AccountCart_Items DEFAULT '[]',
 Revision BIGINT NOT NULL CONSTRAINT DF_AccountCart_Revision DEFAULT 0,
 CONSTRAINT CK_AccountCart_Json CHECK (ISJSON(ItemsJson)=1)
);
IF OBJECT_ID('dbo.DeliveryAddress','U') IS NULL
BEGIN
 CREATE TABLE dbo.DeliveryAddress (
  Id BIGINT IDENTITY PRIMARY KEY, AccountId BIGINT NOT NULL REFERENCES dbo.TaiKhoan(MaTK),
  Recipient NVARCHAR(120) NOT NULL, Phone NVARCHAR(20) NOT NULL,
  AddressLine NVARCHAR(500) NOT NULL, IsDefault BIT NOT NULL DEFAULT 0
 );
 CREATE INDEX IX_DeliveryAddress_Account ON dbo.DeliveryAddress(AccountId);
 CREATE UNIQUE INDEX UQ_DeliveryAddress_Default ON dbo.DeliveryAddress(AccountId) WHERE IsDefault=1;
END;
IF OBJECT_ID('dbo.ProductInterest','U') IS NULL
BEGIN
 CREATE TABLE dbo.ProductInterest (
  Id BIGINT IDENTITY PRIMARY KEY, AccountId BIGINT NOT NULL REFERENCES dbo.TaiKhoan(MaTK),
  ProductId BIGINT NOT NULL REFERENCES dbo.MonUong(MaMon), Favorite BIT NOT NULL DEFAULT 0,
  LastViewedAt DATETIME2 NULL,
  CONSTRAINT UQ_ProductInterest UNIQUE (AccountId, ProductId)
 );
 CREATE INDEX IX_ProductInterest_Favorite ON dbo.ProductInterest(ProductId,Favorite);
END;
COMMIT;
GO


-- ========================================================================
-- NGUON: 7_marketplace.sql
-- ========================================================================
-- Additive migration for existing SQL Server. Apply after 6_customer_data.sql, before app starts.
-- Existing POS rows retain ShopId=NULL; delivery stock never changes warehouse/BOM stock.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.Shop','U') IS NULL
CREATE TABLE dbo.Shop (
Id BIGINT IDENTITY PRIMARY KEY, Name NVARCHAR(120) NOT NULL, OwnerId BIGINT NOT NULL REFERENCES dbo.TaiKhoan(MaTK),
Active BIT NOT NULL DEFAULT 0, ManagerCommissionEnabled BIT NOT NULL DEFAULT 0, CommissionPercent DECIMAL(5,2) NOT NULL DEFAULT 0, Address NVARCHAR(500) NOT NULL,
CreatedAt DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), CONSTRAINT CK_Shop_Commission CHECK (CommissionPercent BETWEEN 0 AND 100)
);
IF OBJECT_ID('dbo.ShopManager','U') IS NULL
CREATE TABLE dbo.ShopManager (
ShopId BIGINT NOT NULL REFERENCES dbo.Shop(Id), AccountId BIGINT NOT NULL REFERENCES dbo.TaiKhoan(MaTK), PRIMARY KEY(ShopId,AccountId)
);
-- Store pickup branch for counter orders (nullable for legacy and staff POS).
IF COL_LENGTH('dbo.DonHang','MaShop') IS NULL
    EXEC sp_executesql N'ALTER TABLE dbo.DonHang ADD MaShop BIGINT NULL';
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.DonHang') AND name=N'FK_DonHang_Shop')
    EXEC sp_executesql N'ALTER TABLE dbo.DonHang WITH CHECK ADD CONSTRAINT FK_DonHang_Shop FOREIGN KEY (MaShop) REFERENCES dbo.Shop(Id)';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.DonHang') AND name=N'IX_DonHang_MaShop')
    EXEC sp_executesql N'CREATE INDEX IX_DonHang_MaShop ON dbo.DonHang(MaShop) WHERE MaShop IS NOT NULL';
IF COL_LENGTH('dbo.MonUong','ShopId') IS NULL ALTER TABLE dbo.MonUong ADD ShopId BIGINT NULL REFERENCES dbo.Shop(Id);
IF COL_LENGTH('dbo.MonUong','DeliveryStock') IS NULL ALTER TABLE dbo.MonUong ADD DeliveryStock INT NULL;
IF COL_LENGTH('dbo.MonUong','SearchName') IS NULL ALTER TABLE dbo.MonUong ADD SearchName NVARCHAR(240) NULL;
-- SQL Server 2017+: normalize existing Vietnamese names without changing their display name.
EXEC sp_executesql N'UPDATE dbo.MonUong SET SearchName=LOWER(TRANSLATE(TenMon,N''áàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđÁÀẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬÉÈẺẼẸÊẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÚÙỦŨỤƯỨỪỬỮỰÝỲỶỸỴĐ'',N''aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyydAAAAAAAAAAAAAAAAAEEEEEEEEEEEIIIIIOOOOOOOOOOOOOOOOOUUUUUUUUUUUYYYYYD'')) WHERE SearchName IS NULL';
IF COL_LENGTH('dbo.MonUong','CatalogVersion') IS NULL ALTER TABLE dbo.MonUong ADD CatalogVersion BIGINT NOT NULL CONSTRAINT DF_MonUong_CatalogVersion DEFAULT 0;
IF COL_LENGTH('dbo.DanhMuc','ShopId') IS NULL ALTER TABLE dbo.DanhMuc ADD ShopId BIGINT NULL REFERENCES dbo.Shop(Id);
-- Replace only the previous global name constraints with shop-scoped uniqueness.
IF EXISTS(SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID('dbo.MonUong') AND name='UQ_MonUong_Ten_Size') ALTER TABLE dbo.MonUong DROP CONSTRAINT UQ_MonUong_Ten_Size;
IF EXISTS(SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID('dbo.DanhMuc') AND name='UQ_DanhMuc_TenDM') ALTER TABLE dbo.DanhMuc DROP CONSTRAINT UQ_DanhMuc_TenDM;
-- Compile index statements after ALTER TABLE so existing databases can resolve the new ShopId columns.
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.MonUong') AND name='UQ_MonUong_Shop_Ten_Size') EXEC sp_executesql N'CREATE UNIQUE INDEX UQ_MonUong_Shop_Ten_Size ON dbo.MonUong(ShopId,TenMon,Size)';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.DanhMuc') AND name='UQ_DanhMuc_Shop_Name') EXEC sp_executesql N'CREATE UNIQUE INDEX UQ_DanhMuc_Shop_Name ON dbo.DanhMuc(ShopId,TenDM)';
IF OBJECT_ID('dbo.Carrier','U') IS NULL
CREATE TABLE dbo.Carrier (
Id BIGINT IDENTITY PRIMARY KEY, ShopId BIGINT NULL REFERENCES dbo.Shop(Id), Name NVARCHAR(120) NOT NULL, Fee DECIMAL(18,2) NOT NULL,
Active BIT NOT NULL DEFAULT 1, CONSTRAINT CK_Carrier_Fee CHECK(Fee>=0)
);
IF OBJECT_ID('dbo.Voucher','U') IS NULL
CREATE TABLE dbo.Voucher (
Id BIGINT IDENTITY PRIMARY KEY, Code VARCHAR(32) NOT NULL UNIQUE, ShopId BIGINT NULL REFERENCES dbo.Shop(Id),
DiscountAmount DECIMAL(18,2) NOT NULL, DiscountType VARCHAR(16) NOT NULL DEFAULT 'FIXED_AMOUNT', DiscountPercent DECIMAL(5,2) NOT NULL DEFAULT 0, MinimumAmount DECIMAL(18,2) NOT NULL, StartsAt DATETIME2 NOT NULL, EndsAt DATETIME2 NOT NULL,
UsageLimit INT NOT NULL, UsedCount INT NOT NULL DEFAULT 0, PerAccountLimit INT NOT NULL DEFAULT 1, Active BIT NOT NULL DEFAULT 1,
CONSTRAINT CK_Voucher_Amounts CHECK (((DiscountType='FIXED_AMOUNT' AND DiscountAmount>0 AND DiscountPercent=0) OR (DiscountType='PERCENT' AND DiscountAmount=0 AND DiscountPercent>0 AND DiscountPercent<=100)) AND MinimumAmount>=0),
CONSTRAINT CK_Voucher_Limits CHECK (UsageLimit>0 AND UsedCount>=0 AND UsedCount<=UsageLimit AND PerAccountLimit>0),
CONSTRAINT CK_Voucher_Dates CHECK (EndsAt>StartsAt)
);
IF OBJECT_ID('dbo.DeliveryOrder','U') IS NULL
CREATE TABLE dbo.DeliveryOrder (
Id BIGINT IDENTITY PRIMARY KEY, AccountId BIGINT NOT NULL REFERENCES dbo.TaiKhoan(MaTK),
ShopId BIGINT NOT NULL REFERENCES dbo.Shop(Id), ShipperId BIGINT NULL REFERENCES dbo.TaiKhoan(MaTK),
CarrierId BIGINT NOT NULL REFERENCES dbo.Carrier(Id), VoucherId BIGINT NULL REFERENCES dbo.Voucher(Id),
Recipient NVARCHAR(120) NOT NULL, Phone NVARCHAR(20) NOT NULL, AddressLine NVARCHAR(500) NOT NULL,
Subtotal DECIMAL(18,2) NOT NULL, ShippingFee DECIMAL(18,2) NOT NULL, DiscountAmount DECIMAL(18,2) NOT NULL,
TotalAmount DECIMAL(18,2) NOT NULL, CommissionPercent DECIMAL(5,2) NOT NULL,
Status VARCHAR(24) NOT NULL, PaymentMethod VARCHAR(16) NOT NULL, Paid BIT NOT NULL DEFAULT 0,
GatewayReference VARCHAR(120) NULL, RefundReference NVARCHAR(120) NULL,
IdempotencyKey VARCHAR(64) NOT NULL, RequestHash VARCHAR(64) NOT NULL, CreatedAt DATETIME2 NOT NULL, DeliveredAt DATETIME2 NULL, DeliveryFailed BIT NOT NULL DEFAULT 0,
Version BIGINT NOT NULL DEFAULT 0, PreparationStatus VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 CONSTRAINT UQ_DeliveryOrder_Key UNIQUE(AccountId,IdempotencyKey),
 CONSTRAINT CK_DeliveryOrder_Preparation CHECK (PreparationStatus IN ('PENDING','PREPARING','READY')),
 CONSTRAINT CK_DeliveryOrder_Status CHECK(Status IN ('NEW','CONFIRMED','SHIPPING','DELIVERED','DELIVERY_FAILED','CANCELLED','RETURN_REQUESTED','RETURNED','REFUNDED')),
CONSTRAINT CK_DeliveryOrder_Method CHECK(PaymentMethod IN ('COD','VNPAY')),
CONSTRAINT CK_DeliveryOrder_Amounts CHECK(Subtotal>=0 AND ShippingFee>=0 AND DiscountAmount>=0 AND DiscountAmount<=Subtotal+ShippingFee AND TotalAmount=Subtotal-DiscountAmount+ShippingFee)
);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.DeliveryOrder') AND name='UQ_DeliveryOrder_Gateway') CREATE UNIQUE INDEX UQ_DeliveryOrder_Gateway ON dbo.DeliveryOrder(GatewayReference) WHERE GatewayReference IS NOT NULL;
IF OBJECT_ID('dbo.DeliveryLine','U') IS NULL
CREATE TABLE dbo.DeliveryLine (
Id BIGINT IDENTITY PRIMARY KEY, OrderId BIGINT NOT NULL REFERENCES dbo.DeliveryOrder(Id),
ProductId BIGINT NOT NULL REFERENCES dbo.MonUong(MaMon), ProductName NVARCHAR(120) NOT NULL, Quantity INT NOT NULL, UnitPrice DECIMAL(18,2) NOT NULL,
CONSTRAINT CK_DeliveryLine_Quantity CHECK (Quantity BETWEEN 1 AND 99 AND UnitPrice>0)
);
IF OBJECT_ID('dbo.ProductReview','U') IS NULL
CREATE TABLE dbo.ProductReview (
Id BIGINT IDENTITY PRIMARY KEY, AccountId BIGINT NOT NULL REFERENCES dbo.TaiKhoan(MaTK),
ProductId BIGINT NOT NULL REFERENCES dbo.MonUong(MaMon), DeliveryOrderId BIGINT NULL REFERENCES dbo.DeliveryOrder(Id),
CounterOrderId BIGINT NULL REFERENCES dbo.DonHang(MaDH), Stars INT NOT NULL, Comment NVARCHAR(3000) NOT NULL, CreatedAt DATETIME2 NOT NULL,
CONSTRAINT UQ_ProductReview_Account UNIQUE(AccountId,ProductId), CONSTRAINT CK_ProductReview_Stars CHECK (Stars BETWEEN 1 AND 5),
CONSTRAINT CK_ProductReview_Comment CHECK(LEN(Comment)>=50),
CONSTRAINT CK_ProductReview_Purchase CHECK((DeliveryOrderId IS NULL AND CounterOrderId IS NOT NULL) OR (DeliveryOrderId IS NOT NULL AND CounterOrderId IS NULL))
);
IF OBJECT_ID('dbo.ReviewMedia','U') IS NULL
CREATE TABLE dbo.ReviewMedia (
Id BIGINT IDENTITY PRIMARY KEY, ReviewId BIGINT NOT NULL REFERENCES dbo.ProductReview(Id), MediaType VARCHAR(12) NOT NULL,
Url NVARCHAR(1000) NOT NULL, PublicId NVARCHAR(255) NOT NULL, CONSTRAINT CK_ReviewMedia_Type CHECK(MediaType IN ('image','video'))
);
IF OBJECT_ID('dbo.ShopCustomerAccess','U') IS NULL
CREATE TABLE dbo.ShopCustomerAccess (
 Id BIGINT IDENTITY PRIMARY KEY, ShopId BIGINT NOT NULL REFERENCES dbo.Shop(Id),
 AccountId BIGINT NOT NULL REFERENCES dbo.TaiKhoan(MaTK), Blocked BIT NOT NULL DEFAULT 0,
 CONSTRAINT UQ_ShopCustomerAccess UNIQUE(ShopId,AccountId)
);
COMMIT;
GO


-- ========================================================================
-- NGUON: 8_branch_neutral_cart.sql
-- ========================================================================
-- Apply after 6_customer_data.sql and 7_marketplace.sql to the selected existing database.
-- No data deletion, no renaming products and no synthetic coordinates/distances.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.MonUong','MenuCode') IS NULL ALTER TABLE dbo.MonUong ADD MenuCode NVARCHAR(180) NULL;
EXEC sp_executesql N'UPDATE dbo.MonUong SET MenuCode=CONCAT(COALESCE(SearchName,LOWER(TenMon)),''|'',LOWER(LTRIM(RTRIM(Size)))) WHERE MenuCode IS NULL';
-- Only exact normalized name + size is linked automatically. Different names require an explicitly configured shared code.
IF COL_LENGTH('dbo.Shop','Area') IS NULL ALTER TABLE dbo.Shop ADD Area NVARCHAR(120) NULL;
IF COL_LENGTH('dbo.Shop','Latitude') IS NULL ALTER TABLE dbo.Shop ADD Latitude FLOAT NULL;
IF COL_LENGTH('dbo.Shop','Longitude') IS NULL ALTER TABLE dbo.Shop ADD Longitude FLOAT NULL;
IF COL_LENGTH('dbo.DeliveryAddress','Area') IS NULL ALTER TABLE dbo.DeliveryAddress ADD Area NVARCHAR(120) NULL;
IF COL_LENGTH('dbo.DeliveryAddress','Latitude') IS NULL ALTER TABLE dbo.DeliveryAddress ADD Latitude FLOAT NULL;
IF COL_LENGTH('dbo.DeliveryAddress','Longitude') IS NULL ALTER TABLE dbo.DeliveryAddress ADD Longitude FLOAT NULL;
IF COL_LENGTH('dbo.DeliveryLine','SnapshotSize') IS NULL ALTER TABLE dbo.DeliveryLine ADD SnapshotSize NVARCHAR(12) NULL;
IF COL_LENGTH('dbo.DeliveryLine','Note') IS NULL ALTER TABLE dbo.DeliveryLine ADD Note NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.DeliveryLine','IsTopping') IS NULL ALTER TABLE dbo.DeliveryLine ADD IsTopping BIT NOT NULL CONSTRAINT DF_DeliveryLine_IsTopping DEFAULT 0;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.MonUong') AND name='IX_MonUong_Shop_MenuCode')
 EXEC sp_executesql N'CREATE INDEX IX_MonUong_Shop_MenuCode ON dbo.MonUong(ShopId,MenuCode,Size)';
COMMIT;
GO


-- ========================================================================
-- NGUON: patch_email_verification.sql
-- ========================================================================
-- ============================================================================
-- Migration Patch: Bổ sung xác thực email và quản lý mã OTP an toàn
-- Tương thích ngược: Giữ nguyên toàn bộ tài khoản hiện có (gán EmailXacThuc = 1)
-- ============================================================================

USE UTE_Tea;
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 1. Bổ sung cột EmailXacThuc vào bảng dbo.TaiKhoan nếu chưa có
IF NOT EXISTS (
    SELECT 1 FROM sys.columns 
    WHERE object_id = OBJECT_ID('dbo.TaiKhoan') AND name = 'EmailXacThuc'
)
BEGIN
    PRINT N'-- Đang thêm cột EmailXacThuc vào dbo.TaiKhoan...';
    ALTER TABLE dbo.TaiKhoan 
    ADD EmailXacThuc BIT NOT NULL CONSTRAINT DF_TaiKhoan_EmailXacThuc DEFAULT 1 WITH VALUES;
    
    EXEC('UPDATE dbo.TaiKhoan SET EmailXacThuc = 1 WHERE EmailXacThuc IS NULL OR EmailXacThuc = 0');
    PRINT N'-- Đã thêm cột EmailXacThuc thành công (toàn bộ tài khoản cũ giữ nguyên quyền đăng nhập).';
END
ELSE
BEGIN
    PRINT N'-- Cột EmailXacThuc đã tồn tại trong dbo.TaiKhoan.';
    EXEC('UPDATE dbo.TaiKhoan SET EmailXacThuc = 1 WHERE EmailXacThuc IS NULL');
END
GO

-- 2. Tạo bảng dbo.XacThucEmail nếu chưa có
IF OBJECT_ID('dbo.XacThucEmail', 'U') IS NULL
BEGIN
    PRINT N'-- Đang tạo bảng dbo.XacThucEmail...';
    CREATE TABLE dbo.XacThucEmail (
        MaOTP BIGINT IDENTITY(1, 1) PRIMARY KEY,
        Email VARCHAR(254) NOT NULL,
        OtpHash VARCHAR(255) NOT NULL,
        Salt VARCHAR(64) NOT NULL,
        MucDich VARCHAR(32) NOT NULL, -- REGISTER_VERIFY, PASSWORD_RESET
        HetHanLuc DATETIME2 NOT NULL,
        SoLanThu INT NOT NULL CONSTRAINT DF_XacThucEmail_SoLanThu DEFAULT 0,
        ToiDaThu INT NOT NULL CONSTRAINT DF_XacThucEmail_ToiDaThu DEFAULT 5,
        DaDungLuc DATETIME2 NULL,
        VoHieuLuc DATETIME2 NULL,
        GuiCuoiLuc DATETIME2 NOT NULL,
        TaoLuc DATETIME2 NOT NULL
    );

    CREATE INDEX IX_XacThucEmail_Email_MucDich ON dbo.XacThucEmail(Email, MucDich);
    PRINT N'-- Đã tạo bảng dbo.XacThucEmail thành công.';
END
ELSE
BEGIN
    PRINT N'-- Bảng dbo.XacThucEmail đã tồn tại.';
END
GO

PRINT N'>>> Patch xác thực email & OTP hoàn tất thành công!';
GO
-- ========================================================================
-- NGUON: 14_percent_voucher_bill.sql (migration schema, KHONG seed voucher)
-- ========================================================================
-- UTE TEA: mở rộng voucher giảm % trên toàn hóa đơn, giữ nguyên dữ liệu cũ.
-- CHỈ chạy khi đã có migration 7_marketplace.sql, trên đúng SQL Server UTE_Tea.
-- Sao lưu database trước; script không DROP bảng, không xoá đơn, không thay mã cũ.
USE [UTE_Tea];
GO
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRAN;
 IF OBJECT_ID(N'dbo.Voucher',N'U') IS NULL OR OBJECT_ID(N'dbo.DeliveryOrder',N'U') IS NULL
  THROW 51401, N'Cần chạy schema marketplace trước.', 1;
 IF COL_LENGTH('dbo.Voucher','DiscountType') IS NULL
  EXEC(N'ALTER TABLE dbo.Voucher ADD DiscountType VARCHAR(16) NOT NULL CONSTRAINT DF_Voucher_DiscountType DEFAULT ''FIXED_AMOUNT'';');
 IF COL_LENGTH('dbo.Voucher','DiscountPercent') IS NULL
  EXEC(N'ALTER TABLE dbo.Voucher ADD DiscountPercent DECIMAL(5,2) NOT NULL CONSTRAINT DF_Voucher_DiscountPercent DEFAULT (0);');
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_Voucher_Amounts' AND parent_object_id=OBJECT_ID('dbo.Voucher'))
  ALTER TABLE dbo.Voucher DROP CONSTRAINT CK_Voucher_Amounts;
 EXEC sp_executesql N'ALTER TABLE dbo.Voucher WITH CHECK ADD CONSTRAINT CK_Voucher_Amounts CHECK
  (((DiscountType=''FIXED_AMOUNT'' AND DiscountAmount>0 AND DiscountPercent=0) OR
    (DiscountType=''PERCENT'' AND DiscountAmount=0 AND DiscountPercent>0 AND DiscountPercent<=100)) AND MinimumAmount>=0)';
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_DeliveryOrder_Amounts' AND parent_object_id=OBJECT_ID('dbo.DeliveryOrder'))
  ALTER TABLE dbo.DeliveryOrder DROP CONSTRAINT CK_DeliveryOrder_Amounts;
 ALTER TABLE dbo.DeliveryOrder WITH CHECK ADD CONSTRAINT CK_DeliveryOrder_Amounts CHECK
  (Subtotal>=0 AND ShippingFee>=0 AND DiscountAmount>=0 AND DiscountAmount<=Subtotal+ShippingFee AND TotalAmount=Subtotal-DiscountAmount+ShippingFee);
 -- Khong tao voucher o migration: 8 voucher demo duoc khoi tao DUY NHAT o file 04.
 COMMIT;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;
GO
SELECT Code,DiscountType,DiscountPercent,DiscountAmount,Active FROM dbo.Voucher WHERE Code='GIAMGIA10';
GO


-- ========================================================================
-- NGUON: 15_kitchen_delivery_flow.sql
-- ========================================================================
-- Chạy SAU 7_marketplace.sql và 14_percent_voucher_bill.sql khi dùng DB hiện có.
-- Giữ nguyên dữ liệu/đơn đã đặt; KHÔNG DROP bảng, KHÔNG DELETE.
USE [UTE_Tea];
GO
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRAN;
 IF OBJECT_ID(N'dbo.DeliveryOrder',N'U') IS NULL
    THROW 51501, N'Cần có bảng DeliveryOrder trước khi thêm luồng bếp.', 1;
 IF COL_LENGTH('dbo.DeliveryOrder','PreparationStatus') IS NULL
    EXEC sp_executesql N'ALTER TABLE dbo.DeliveryOrder ADD PreparationStatus VARCHAR(16) NOT NULL CONSTRAINT DF_DeliveryOrder_PreparationStatus DEFAULT ''PENDING'' WITH VALUES';
 IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_DeliveryOrder_Preparation' AND parent_object_id=OBJECT_ID(N'dbo.DeliveryOrder'))
    EXEC sp_executesql N'ALTER TABLE dbo.DeliveryOrder WITH CHECK ADD CONSTRAINT CK_DeliveryOrder_Preparation CHECK (PreparationStatus IN (''PENDING'',''PREPARING'',''READY''))';
 COMMIT;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;
SELECT PreparationStatus,Status,COUNT_BIG(*) AS Orders FROM dbo.DeliveryOrder GROUP BY PreparationStatus,Status;
GO
