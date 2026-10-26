-- UTE Tea: read-only verification for source versions using migrations 6, 7 and 8.
-- This script DOES NOT create, modify, or delete data.
USE UTE_Tea;
SET NOCOUNT ON;
DECLARE @Checks TABLE (ObjectName NVARCHAR(128), ColumnName NVARCHAR(128));
INSERT INTO @Checks (ObjectName,ColumnName) VALUES
('MonUong','ShopId'),('MonUong','DeliveryStock'),('MonUong','SearchName'),
('MonUong','CatalogVersion'),('MonUong','MenuCode'),
('DanhMuc','ShopId'),('Shop','Id'),('Shop','Area'),('Shop','Latitude'),
('Shop','Longitude'),('DeliveryAddress','Area'),('DeliveryAddress','Latitude'),
('DeliveryAddress','Longitude'),('DeliveryLine','SnapshotSize'),
('DeliveryLine','Note'),('DeliveryLine','IsTopping'),('DeliveryOrder','Id'),
('Voucher','Id'),('Carrier','Id'),('ProductReview','Id');
SELECT ObjectName,ColumnName,
 CASE WHEN COL_LENGTH(N'dbo.'+ObjectName,ColumnName) IS NULL THEN N'MISSING' ELSE N'OK' END AS CheckResult
FROM @Checks ORDER BY ObjectName,ColumnName;
-- If any check is MISSING, inspect migrations 6_customer_data.sql,
-- 7_marketplace.sql and 8_branch_neutral_cart.sql IN THAT ORDER, with a
-- verified backup; do not rerun 1_schema.sql against an existing database.
