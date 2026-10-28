-- CHỈ ĐỌC: kiểm tra SQL Server UTE_Tea có hỗ trợ mã GIAMGIA10 hay chưa.
-- Chạy trong SQL Server Management Studio với đúng DB mà ứng dụng đang dùng.
USE [UTE_Tea];
GO
SELECT DB_NAME() AS DatabaseDangKiemTra;
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME IN ('Voucher', 'DeliveryOrder')
  AND COLUMN_NAME IN ('DiscountType','DiscountPercent','DiscountAmount','ShippingFee','TotalAmount')
ORDER BY TABLE_NAME, COLUMN_NAME;
GO
IF OBJECT_ID(N'dbo.Voucher', N'U') IS NULL
    PRINT N'LỖI: Thiếu bảng Voucher. Kiểm tra migration 7_marketplace.sql.';
ELSE IF COL_LENGTH('dbo.Voucher','DiscountType') IS NULL OR COL_LENGTH('dbo.Voucher','DiscountPercent') IS NULL
    PRINT N'LỖI: Chưa có cột giảm %. Kiểm tra migration 14_percent_voucher_bill.sql.';
ELSE
BEGIN
    EXEC sp_executesql N'
        SELECT Code, DiscountType, DiscountPercent, DiscountAmount, MinimumAmount,
               StartsAt, EndsAt, UsageLimit, UsedCount, PerAccountLimit, Active,
               CASE WHEN Active=1 AND SYSUTCDATETIME()>=StartsAt
                        AND SYSUTCDATETIME()<EndsAt AND UsedCount<UsageLimit
                    THEN N''Còn hiệu lực theo thời gian và lượt tổng''
                    ELSE N''Không còn hiệu lực'' END AS TrangThai
        FROM dbo.Voucher WHERE Code=N''GIAMGIA10'';';
END;
GO
-- Kiểm tra hóa đơn đã tạo, nếu có (không sửa dữ liệu).
IF OBJECT_ID(N'dbo.DeliveryOrder', N'U') IS NOT NULL
    SELECT TOP (10) Id, Subtotal, ShippingFee, DiscountAmount, TotalAmount,
      Subtotal + ShippingFee - DiscountAmount AS TongTinhLai,
      CASE WHEN TotalAmount=Subtotal+ShippingFee-DiscountAmount THEN N'KHỚP' ELSE N'SAI' END AS DoiChieu
    FROM dbo.DeliveryOrder ORDER BY Id DESC;
GO
