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
