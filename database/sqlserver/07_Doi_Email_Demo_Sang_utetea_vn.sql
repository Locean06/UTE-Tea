-- 07 - DOI EMAIL TAI KHOAN DEMO CU SANG @utetea.vn (KHONG XOA DU LIEU)
-- Chay tren DB UTE_Tea dang su dung, sau khi BACKUP.
-- KHONG reset mat khau, khong tao tai khoan moi, giu nguyen MaTK va FK.
-- Script co the chay lai an toan; gap email dich da ton tai thi bao loi va rollback.
USE [UTE_Tea];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'dbo.TaiKhoan',N'U') IS NULL
    THROW 52020, N'Khong tim thay bang dbo.TaiKhoan trong UTE_Tea.', 1;

DECLARE @EmailMap TABLE (
    OldEmail VARCHAR(254) NOT NULL PRIMARY KEY,
    NewEmail VARCHAR(254) NOT NULL UNIQUE
);
INSERT INTO @EmailMap (OldEmail,NewEmail) VALUES
('admin@milktea.vn','admin@utetea.vn'),
('admin2@milktea.vn','admin2@utetea.vn'),
('cashier@milktea.vn','cashier@utetea.vn'),
('cashier2@milktea.vn','cashier2@utetea.vn'),
('cashier3@milktea.vn','cashier3@utetea.vn'),
('kitchen@milktea.vn','kitchen@utetea.vn'),
('kitchen2@milktea.vn','kitchen2@utetea.vn'),
('kitchen3@milktea.vn','kitchen3@utetea.vn'),
('customer@milktea.vn','customer@utetea.vn'),
('customer2@milktea.vn','customer2@utetea.vn'),
('customer3@milktea.vn','customer3@utetea.vn'),
('customer4@milktea.vn','customer4@utetea.vn'),
('customer@utetea.local','customer.delivery@utetea.vn'),
('shipper@utetea.local','shipper@utetea.vn');

BEGIN TRY
  BEGIN TRANSACTION;
  IF EXISTS (
      SELECT 1 FROM @EmailMap m
      WHERE EXISTS(SELECT 1 FROM dbo.TaiKhoan t WHERE t.Email=m.OldEmail)
        AND EXISTS(SELECT 1 FROM dbo.TaiKhoan t WHERE t.Email=m.NewEmail)
  )
    THROW 52021, N'Email dich @utetea.vn da duoc su dung. Dung de kiem tra, khong ghi de tai khoan.',1;

  UPDATE t
     SET Email = m.NewEmail
    FROM dbo.TaiKhoan t
    JOIN @EmailMap m ON m.OldEmail=t.Email;
  DECLARE @Changed INT = @@ROWCOUNT;
  COMMIT TRANSACTION;
  PRINT CONCAT(N'So tai khoan demo doi email: ',@Changed);
END TRY
BEGIN CATCH
  IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
  THROW;
END CATCH;

SELECT MaTK,Email,VaiTro,HoatDong
FROM dbo.TaiKhoan
WHERE Email LIKE '%@utetea.vn'
ORDER BY VaiTro,Email;
GO
