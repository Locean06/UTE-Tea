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
DiscountAmount DECIMAL(18,2) NOT NULL, MinimumAmount DECIMAL(18,2) NOT NULL, StartsAt DATETIME2 NOT NULL, EndsAt DATETIME2 NOT NULL,
UsageLimit INT NOT NULL, UsedCount INT NOT NULL DEFAULT 0, PerAccountLimit INT NOT NULL DEFAULT 1, Active BIT NOT NULL DEFAULT 1,
CONSTRAINT CK_Voucher_Amounts CHECK (DiscountAmount>0 AND MinimumAmount>=0),
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
Version BIGINT NOT NULL DEFAULT 0, CONSTRAINT UQ_DeliveryOrder_Key UNIQUE(AccountId,IdempotencyKey),
CONSTRAINT CK_DeliveryOrder_Status CHECK(Status IN ('NEW','CONFIRMED','SHIPPING','DELIVERED','DELIVERY_FAILED','CANCELLED','RETURN_REQUESTED','RETURNED','REFUNDED')),
CONSTRAINT CK_DeliveryOrder_Method CHECK(PaymentMethod IN ('COD','VNPAY')),
CONSTRAINT CK_DeliveryOrder_Amounts CHECK(Subtotal>=0 AND ShippingFee>=0 AND DiscountAmount>=0 AND DiscountAmount<=Subtotal AND TotalAmount=Subtotal-DiscountAmount+ShippingFee)
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

