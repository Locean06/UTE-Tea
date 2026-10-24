-- UTE Tea branding-only patch for an existing database.
-- Do not rerun 1_schema.sql to rename your store: it may drop existing data.
USE [UTE_Tea];
GO
IF OBJECT_ID(N'dbo.CauHinhChung', N'U') IS NOT NULL
BEGIN
    UPDATE dbo.CauHinhChung
       SET TenQuan = N'UTE Tea'
     WHERE MaCauHinh = 1;
END;
GO
