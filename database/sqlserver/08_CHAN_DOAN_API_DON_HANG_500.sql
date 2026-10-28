-- CHAN DOAN DOC-THUAN: GET /api/v1/cashier/orders -> HTTP 500.
-- KHONG DROP / DELETE / UPDATE / INSERT / ALTER. KHONG sua du lieu.
-- Chay trong SSMS, ket noi DUNG server + databaseName tu DB_URL trong .env.
-- Gui lai cac result sets (co the che MaDH neu can) va log "Caused by" tu Spring Boot.
USE [UTE_Tea];
GO
SET NOCOUNT ON;
PRINT N'CHECK #1: Ten SQL Server va database dang doc';
SELECT DB_NAME() AS DatabaseName, @@SERVERNAME AS SqlServerName;

PRINT N'CHECK #2: Bang/cot bat buoc trong duong doc cashier/orders (THIEU => migration chua du)';
DECLARE @Required TABLE (ObjectName sysname NOT NULL, ColumnName sysname NOT NULL);
INSERT @Required (ObjectName, ColumnName) VALUES
(N'DonHang',N'MaDH'),(N'DonHang',N'MaPhien'),(N'DonHang',N'MaTK'),
(N'DonHang',N'MaShop'),(N'DonHang',N'Nguon'),(N'DonHang',N'TrangThai'),
(N'DonHang',N'ThayTheMaDH'),(N'DonHang',N'MaYeuCau'),(N'DonHang',N'GhiChu'),
(N'DonHang',N'TaoLuc'),(N'DonHang',N'BatDauLuc'),(N'DonHang',N'HoanThanhLuc'),(N'DonHang',N'Version'),
(N'Shop',N'Id'),(N'Shop',N'Name'),(N'Shop',N'Area'),(N'Shop',N'Latitude'),
(N'Shop',N'Longitude'),(N'Shop',N'OwnerId'),(N'Shop',N'Address'),(N'Shop',N'Active'),
(N'Shop',N'ManagerCommissionEnabled'),(N'Shop',N'CommissionPercent'),(N'Shop',N'CreatedAt'),
(N'TaiKhoan',N'MaTK'),(N'TaiKhoan',N'HoTen'),(N'TaiKhoan',N'VaiTro'),
(N'TaiKhoan',N'EmailXacThuc'),(N'TaiKhoan',N'TokenVersion'),
(N'TaiKhoan',N'AvatarUrl'),(N'TaiKhoan',N'AvatarPublicId'),
(N'TaiKhoan',N'MatKhauBam'),(N'TaiKhoan',N'Email'),(N'TaiKhoan',N'TaoLuc'),
(N'TaiKhoan',N'DienThoai'),(N'TaiKhoan',N'HoatDong'),
(N'PhienBan',N'MaPhien'),(N'PhienBan',N'MaBan'),(N'PhienBan',N'TrangThai'),
(N'PhienBan',N'MoLuc'),(N'PhienBan',N'Version'),
(N'Ban',N'MaBan'),(N'Ban',N'TenBan'),(N'Ban',N'Version'),
(N'ChiTietDonHang',N'MaChiTiet'),(N'ChiTietDonHang',N'MaDH'),
(N'ChiTietDonHang',N'MaMon'),(N'ChiTietDonHang',N'TenMonChot'),
(N'ChiTietDonHang',N'SizeChot'),(N'ChiTietDonHang',N'SoLuong'),
(N'ChiTietDonHang',N'DonGiaChot'),(N'ChiTietDonHang',N'GhiChu'),
(N'ChiTietDonHangTopping',N'MaChiTietTopping'),(N'ChiTietDonHangTopping',N'MaChiTiet'),
(N'ChiTietDonHangTopping',N'MaMonTopping'),(N'ChiTietDonHangTopping',N'TenToppingChot'),
(N'ChiTietDonHangTopping',N'DonGiaToppingChot'),
(N'MonUong',N'MaMon'),
(N'HoaDon',N'MaHD'),(N'HoaDon',N'MaDH'),(N'HoaDon',N'TongTien'),
(N'HoaDon',N'TienGiam'),(N'HoaDon',N'PhaiTra'),(N'HoaDon',N'TyLeGiamChot'),
(N'HoaDon',N'LoaiGiamChot'),(N'HoaDon',N'GiaTriGiamChot'),
(N'HoaDon',N'TrangThai'),(N'HoaDon',N'LapLuc'),(N'HoaDon',N'HuyLuc'),
(N'ThanhToan',N'MaTT'),(N'ThanhToan',N'MaHD'),(N'ThanhToan',N'PhuongThuc'),
(N'ThanhToan',N'SoTien'),(N'ThanhToan',N'NguoiThu'),(N'ThanhToan',N'ThuLuc'),(N'ThanhToan',N'ThamChieu'),(N'ThanhToan',N'MaYeuCau'),
(N'ThongBaoThanhToan',N'MaTB'),(N'ThongBaoThanhToan',N'MaHD'),
(N'ThongBaoThanhToan',N'PhuongThuc'),(N'ThongBaoThanhToan',N'ThamChieu'),
(N'ThongBaoThanhToan',N'TaoLuc'),(N'ThongBaoThanhToan',N'MaYeuCau'),
(N'YeuCauHuy',N'MaHuy'),(N'YeuCauHuy',N'MaDH'),(N'YeuCauHuy',N'TrangThai'),
(N'YeuCauHoan',N'MaHoan'),(N'YeuCauHoan',N'MaDH'),(N'YeuCauHoan',N'MaTT'),
(N'YeuCauHoan',N'TrangThai'),(N'YeuCauHoan',N'SoTien'),
(N'YeuCauHoan',N'LyDo'),(N'YeuCauHoan',N'LyDoDuyet'),
(N'YeuCauHoan',N'NguoiDuyet'),(N'YeuCauHoan',N'NguoiHoan'),
(N'YeuCauHoan',N'DuyetLuc'),(N'YeuCauHoan',N'HoanLuc'),
(N'YeuCauHoan',N'ThamChieuHoan'),(N'YeuCauHoan',N'MaYeuCau'),
(N'YeuCauHuy',N'LyDo'),(N'YeuCauHuy',N'LyDoXuLy'),
(N'YeuCauHuy',N'TaoLuc'),(N'YeuCauHuy',N'XuLyLuc'),
(N'YeuCauHuy',N'NguoiXuLy');
SELECT r.ObjectName AS Bang, r.ColumnName AS Cot,
       CASE WHEN t.object_id IS NULL THEN N'THIEU BANG'
            WHEN c.column_id IS NULL THEN N'THIEU COT'
            ELSE N'OK' END AS KetQua
FROM @Required AS r
LEFT JOIN sys.tables AS t ON t.name = r.ObjectName AND t.schema_id = SCHEMA_ID(N'dbo')
LEFT JOIN sys.columns AS c ON c.object_id = t.object_id AND c.name = r.ColumnName
ORDER BY CASE WHEN t.object_id IS NULL OR c.column_id IS NULL THEN 0 ELSE 1 END,
         r.ObjectName, r.ColumnName;

PRINT N'CHECK #3: gia tri enum trong DB khong khop Java';
IF OBJECT_ID(N'dbo.DonHang', N'U') IS NOT NULL
   AND COL_LENGTH(N'dbo.DonHang', N'Nguon') IS NOT NULL
   AND COL_LENGTH(N'dbo.DonHang', N'TrangThai') IS NOT NULL
BEGIN
    EXEC(N'SELECT TOP (30) MaDH, Nguon, TrangThai FROM dbo.DonHang
           WHERE Nguon NOT IN (''TABLE'',''COUNTER'') OR TrangThai NOT IN
                 (''CHO_THANH_TOAN'',''CHO_XAC_NHAN'',''CHO_CHE_BIEN'',''DANG_CHE_BIEN'',''HOAN_THANH'',''DA_HUY'')');
END;
IF OBJECT_ID(N'dbo.TaiKhoan',N'U') IS NOT NULL
    AND COL_LENGTH(N'dbo.TaiKhoan',N'VaiTro') IS NOT NULL
BEGIN
    EXEC(N'SELECT TOP (30) MaTK, VaiTro FROM dbo.TaiKhoan
           WHERE VaiTro NOT IN (''ADMIN'',''CASHIER'',''KITCHEN'',''CUSTOMER'',''SHIPPER'',''VENDOR'',''MANAGER'')');
END;
IF OBJECT_ID(N'dbo.ThanhToan',N'U') IS NOT NULL
    AND COL_LENGTH(N'dbo.ThanhToan',N'PhuongThuc') IS NOT NULL
BEGIN
    EXEC(N'SELECT TOP (30) MaTT, PhuongThuc FROM dbo.ThanhToan
           WHERE PhuongThuc NOT IN (''CASH'',''BANK_TRANSFER'')');
END;
IF OBJECT_ID(N'dbo.ThongBaoThanhToan',N'U') IS NOT NULL
    AND COL_LENGTH(N'dbo.ThongBaoThanhToan',N'PhuongThuc') IS NOT NULL
BEGIN
    EXEC(N'SELECT TOP (30) MaTB, PhuongThuc FROM dbo.ThongBaoThanhToan
           WHERE PhuongThuc NOT IN (''CASH'',''BANK_TRANSFER'')');
END;
IF OBJECT_ID(N'dbo.HoaDon',N'U') IS NOT NULL
    AND COL_LENGTH(N'dbo.HoaDon',N'TrangThai') IS NOT NULL
BEGIN
    EXEC(N'SELECT TOP (30) MaHD, TrangThai FROM dbo.HoaDon
           WHERE TrangThai NOT IN (''HIEU_LUC'',''DA_HUY'')');
END;

IF OBJECT_ID(N'dbo.YeuCauHoan',N'U') IS NOT NULL
    AND COL_LENGTH(N'dbo.YeuCauHoan',N'TrangThai') IS NOT NULL
BEGIN
    EXEC(N'SELECT TOP (30) MaHoan, TrangThai FROM dbo.YeuCauHoan
        WHERE TrangThai NOT IN (''CHO_DUYET'',''DA_DUYET'',''TU_CHOI'',''DA_HOAN'')');
END;
IF OBJECT_ID(N'dbo.YeuCauHuy',N'U') IS NOT NULL
    AND COL_LENGTH(N'dbo.YeuCauHuy',N'TrangThai') IS NOT NULL
BEGIN
    EXEC(N'SELECT TOP (30) MaHuy, TrangThai FROM dbo.YeuCauHuy
        WHERE TrangThai NOT IN (''CHO'',''CHAP_THUAN'',''TU_CHOI'')');
END;

PRINT N'CHECK #4: du lieu don hang bi thieu truong can thiet';
IF OBJECT_ID(N'dbo.DonHang', N'U') IS NOT NULL
    AND COL_LENGTH(N'dbo.DonHang', N'TaoLuc') IS NOT NULL
BEGIN
    EXEC(N'SELECT TOP (30) MaDH FROM dbo.DonHang WHERE TaoLuc IS NULL');
    EXEC(N'SELECT COUNT_BIG(*) AS SoDonHang FROM dbo.DonHang');
END;
IF OBJECT_ID(N'dbo.ChiTietDonHang',N'U') IS NOT NULL
    AND COL_LENGTH(N'dbo.ChiTietDonHang',N'DonGiaChot') IS NOT NULL
    AND COL_LENGTH(N'dbo.ChiTietDonHang',N'SoLuong') IS NOT NULL
BEGIN
    EXEC(N'SELECT TOP (30) MaChiTiet, MaDH FROM dbo.ChiTietDonHang
            WHERE DonGiaChot IS NULL OR SoLuong IS NULL');
END;
PRINT N'KET THUC: Neu tat ca OK nhung van HTTP 500, xem Caused by trong log Spring Boot theo traceId.';
GO
