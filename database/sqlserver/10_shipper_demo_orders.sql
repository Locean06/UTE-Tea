-- UTE Tea / SQL Server LOCAL ONLY.
-- Seed 4 preassigned delivery orders and 1 unassigned one to test the SHIPPER dashboard.
-- Requires scripts 1..9. All names, phone numbers, and addresses below are DEMO ONLY.
USE [UTE_Tea];
GO
SET XACT_ABORT ON;
SET NOCOUNT ON;

DECLARE @CustomerId BIGINT = (SELECT MaTK FROM dbo.TaiKhoan WHERE Email='customer@utetea.local' AND VaiTro='CUSTOMER');
DECLARE @ShipperId BIGINT = (SELECT MaTK FROM dbo.TaiKhoan WHERE Email='shipper@utetea.local' AND VaiTro='SHIPPER');
DECLARE @ShopId BIGINT = (SELECT TOP (1) Id FROM dbo.Shop WHERE Name=N'UTE Tea - Bình Thạnh (Demo)' AND Active=1 ORDER BY Id);
DECLARE @CarrierId BIGINT = (SELECT TOP (1) Id FROM dbo.Carrier WHERE ShopId=@ShopId AND Active=1 ORDER BY Id);
DECLARE @ProductId BIGINT = (SELECT TOP (1) MaMon FROM dbo.MonUong WHERE ShopId=@ShopId AND HoatDong=1 AND DeliveryStock > 0 ORDER BY MaMon);

IF @CustomerId IS NULL OR @ShipperId IS NULL OR @ShopId IS NULL OR @CarrierId IS NULL OR @ProductId IS NULL
    THROW 51040, N'Thiếu tài khoản hoặc cơ sở/menu. Kiểm tra script 1..9 trước.', 1;

DECLARE @Examples TABLE (
 K VARCHAR(64) NOT NULL PRIMARY KEY,
 DeliveryStatus VARCHAR(24) NOT NULL,
 Qty INT NOT NULL,
 AssignShipper BIT NOT NULL,
 IsPaid BIT NOT NULL,
 IsFailure BIT NOT NULL,
 DeliveryDaysAgo INT NOT NULL
);
INSERT INTO @Examples VALUES
 ('UTE_LOCAL_SHIP_CONFIRMED_01', 'CONFIRMED',       2, 1, 0, 0, 0),
 ('UTE_LOCAL_SHIP_SHIPPING_02',  'SHIPPING',        1, 1, 0, 0, 0),
 ('UTE_LOCAL_SHIP_DELIVERED_03', 'DELIVERED',       2, 1, 1, 0, 1),
 ('UTE_LOCAL_SHIP_FAILED_04',    'DELIVERY_FAILED', 1, 1, 0, 1, 1),
 ('UTE_LOCAL_SHIP_UNASSIGNED_05','CONFIRMED',       1, 0, 0, 0, 0);

BEGIN TRY
 BEGIN TRANSACTION;
 INSERT INTO dbo.DeliveryOrder
  (AccountId, ShopId, ShipperId, CarrierId, VoucherId, Recipient, Phone, AddressLine,
   Subtotal, ShippingFee, DiscountAmount, TotalAmount, CommissionPercent,
   Status, PaymentMethod, Paid, GatewayReference, RefundReference,
   IdempotencyKey, RequestHash, CreatedAt, DeliveredAt, DeliveryFailed, Version)
 SELECT
   @CustomerId, @ShopId, CASE WHEN e.AssignShipper=1 THEN @ShipperId ELSE NULL END,
   @CarrierId, NULL, N'Khách hàng DEMO', '0901000004',
   N'Địa chỉ DEMO, Bình Thạnh, TP. Hồ Chí Minh - không giao hàng thật',
   CAST(p.Gia*e.Qty AS DECIMAL(18,2)), c.Fee, 0,
   CAST(p.Gia*e.Qty+c.Fee AS DECIMAL(18,2)), s.CommissionPercent,
   e.DeliveryStatus, 'COD', e.IsPaid, NULL, NULL,
   e.K, CONVERT(VARCHAR(64),HASHBYTES('SHA2_256',e.K),2),
   DATEADD(DAY,-e.DeliveryDaysAgo,SYSUTCDATETIME()),
   CASE WHEN e.DeliveryStatus='DELIVERED' THEN DATEADD(DAY,-e.DeliveryDaysAgo,SYSUTCDATETIME()) ELSE NULL END,
   e.IsFailure, 0
 FROM @Examples AS e
 JOIN dbo.Shop AS s ON s.Id=@ShopId
 JOIN dbo.Carrier AS c ON c.Id=@CarrierId
 JOIN dbo.MonUong AS p ON p.MaMon=@ProductId
 WHERE NOT EXISTS (
   SELECT 1 FROM dbo.DeliveryOrder AS o WITH (UPDLOCK,HOLDLOCK)
   WHERE o.AccountId=@CustomerId AND o.IdempotencyKey=e.K
 );

 INSERT INTO dbo.DeliveryLine
  (OrderId, ProductId, ProductName, Quantity, UnitPrice, SnapshotSize, Note, IsTopping)
 SELECT o.Id, p.MaMon, p.TenMon, e.Qty, p.Gia, p.Size,
        N'Dữ liệu DEMO để kiểm tra role SHIPPER', 0
 FROM @Examples AS e
 JOIN dbo.DeliveryOrder AS o ON o.AccountId=@CustomerId AND o.IdempotencyKey=e.K
 JOIN dbo.MonUong AS p ON p.MaMon=@ProductId
 WHERE NOT EXISTS (SELECT 1 FROM dbo.DeliveryLine AS l WHERE l.OrderId=o.Id);
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

SELECT o.Id, o.Status, o.ShipperId, o.TotalAmount, o.Paid, o.DeliveryFailed,
       o.IdempotencyKey AS DemoOrderKey
FROM dbo.DeliveryOrder AS o
WHERE o.AccountId=@CustomerId AND o.IdempotencyKey LIKE 'UTE_LOCAL_SHIP_%'
ORDER BY o.Id;
GO
