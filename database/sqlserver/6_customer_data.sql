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
