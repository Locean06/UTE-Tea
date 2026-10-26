-- Bo sung: voucher giam % / giam phi van chuyen + trang thai don "DA LAY HANG" (PICKED_UP).
-- Chay SAU 7_marketplace.sql va 8_branch_neutral_cart.sql. Co the chay lai nhieu lan, khong xoa du lieu.
USE UTE_Tea;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Voucher','VoucherType') IS NULL
    ALTER TABLE dbo.Voucher ADD VoucherType VARCHAR(16) NOT NULL CONSTRAINT DF_Voucher_VoucherType DEFAULT 'AMOUNT';
IF COL_LENGTH('dbo.Voucher','DiscountPercent') IS NULL
    ALTER TABLE dbo.Voucher ADD DiscountPercent DECIMAL(5,2) NOT NULL CONSTRAINT DF_Voucher_DiscountPercent DEFAULT 0;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_Voucher_Type' AND parent_object_id=OBJECT_ID('dbo.Voucher'))
    EXEC sp_executesql N'ALTER TABLE dbo.Voucher ADD CONSTRAINT CK_Voucher_Type CHECK (VoucherType IN (''AMOUNT'',''PERCENT'',''SHIPPING'') AND DiscountPercent BETWEEN 0 AND 100)';

-- Mo rong tap trang thai hop le cua don giao hang
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_DeliveryOrder_Status' AND parent_object_id=OBJECT_ID('dbo.DeliveryOrder'))
    ALTER TABLE dbo.DeliveryOrder DROP CONSTRAINT CK_DeliveryOrder_Status;
ALTER TABLE dbo.DeliveryOrder ADD CONSTRAINT CK_DeliveryOrder_Status CHECK (Status IN
 ('NEW','CONFIRMED','PICKED_UP','SHIPPING','DELIVERED','DELIVERY_FAILED','CANCELLED','RETURN_REQUESTED','RETURNED','REFUNDED'));

COMMIT;
GO
