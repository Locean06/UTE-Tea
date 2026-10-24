-- Safe, idempotent brand-only update on the existing UTE_Tea database.
-- DO NOT run 0_DropOldDatabase.sql or 1_schema.sql on production data.
USE [UTE_Tea];
GO
IF OBJECT_ID(N'dbo.CauHinhChung', N'U') IS NOT NULL
BEGIN
    UPDATE dbo.CauHinhChung
       SET TenQuan = N'UTE Tea'
     WHERE TenQuan IS NULL OR TenQuan <> N'UTE Tea';
END;
GO
