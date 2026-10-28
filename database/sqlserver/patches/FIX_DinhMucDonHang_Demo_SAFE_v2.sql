/* UTE Tea / SQL Server - backfill ONLY pending demo kitchen orders 03..06.
   Back up UTE_Tea first. Current recipe is used (demo only).
   Never changes inventory, money, order status, or existing snapshots.
*/
USE UTE_Tea;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;

-- DIAGNOSTIC: show why original script found only three eligible orders.
SELECT d.MaDH, d.MaYeuCau, d.TrangThai,
       (SELECT COUNT(*) FROM dbo.DinhMucDonHang s WHERE s.MaDH=d.MaDH) AS SoDongDinhMuc,
       CASE WHEN d.TrangThai IN ('CHO_CHE_BIEN','DANG_CHE_BIEN') THEN N'Có thể xét bổ sung'
            ELSE N'Bỏ qua - trạng thái không còn chờ/đang pha' END AS GhiChu
FROM dbo.DonHang d
WHERE d.MaYeuCau IN ('DH_SEED_03','DH_SEED_04','DH_SEED_05','DH_SEED_06')
ORDER BY d.MaYeuCau;
GO

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @Target TABLE (MaDH BIGINT PRIMARY KEY, MaYeuCau VARCHAR(64));
    INSERT INTO @Target(MaDH, MaYeuCau)
    SELECT d.MaDH,d.MaYeuCau
    FROM dbo.DonHang d WITH (UPDLOCK, HOLDLOCK)
    WHERE d.MaYeuCau IN ('DH_SEED_03','DH_SEED_04','DH_SEED_05','DH_SEED_06')
      AND d.TrangThai IN ('CHO_CHE_BIEN','DANG_CHE_BIEN')
      AND NOT EXISTS (SELECT 1 FROM dbo.DinhMucDonHang s WHERE s.MaDH=d.MaDH);

    IF EXISTS (
        SELECT 1 FROM dbo.ChiTietDonHang d JOIN @Target t ON d.MaDH=t.MaDH
        WHERE NOT EXISTS(SELECT 1 FROM dbo.CongThucMon r WHERE r.MaMon=d.MaMon)
    ) THROW 51103, N'Món chính thiếu công thức; không thay đổi dữ liệu.',1;

    IF EXISTS (
        SELECT 1 FROM dbo.ChiTietDonHangTopping tt
        JOIN dbo.ChiTietDonHang d ON d.MaChiTiet=tt.MaChiTiet
        JOIN @Target t ON t.MaDH=d.MaDH
        WHERE NOT EXISTS(SELECT 1 FROM dbo.CongThucMon r WHERE r.MaMon=tt.MaMonTopping)
    ) THROW 51104, N'Topping thiếu công thức; không thay đổi dữ liệu.',1;

    IF EXISTS (
        SELECT 1 FROM @Target t
        WHERE NOT EXISTS(SELECT 1 FROM dbo.ChiTietDonHang d WHERE d.MaDH=t.MaDH)
    ) THROW 51106, N'Đơn chưa có chi tiết món; không thay đổi dữ liệu.',1;

    ;WITH RequiredMaterials AS (
        SELECT d.MaDH,r.MaNL,CAST(d.SoLuong AS DECIMAL(18,3))*r.DinhLuong AS Qty
        FROM dbo.ChiTietDonHang d
        JOIN @Target t ON t.MaDH=d.MaDH
        JOIN dbo.CongThucMon r ON r.MaMon=d.MaMon
        UNION ALL
        SELECT d.MaDH,r.MaNL,CAST(d.SoLuong AS DECIMAL(18,3))*r.DinhLuong AS Qty
        FROM dbo.ChiTietDonHangTopping tt
        JOIN dbo.ChiTietDonHang d ON d.MaChiTiet=tt.MaChiTiet
        JOIN @Target t ON t.MaDH=d.MaDH
        JOIN dbo.CongThucMon r ON r.MaMon=tt.MaMonTopping
    )
    INSERT INTO dbo.DinhMucDonHang(MaDH,MaNL,TongLuong,LoaiChot,DonViChot)
    SELECT r.MaDH,r.MaNL,SUM(r.Qty),n.Loai,n.DonVi
    FROM RequiredMaterials r
    JOIN dbo.NguyenLieu n ON n.MaNL=r.MaNL
    GROUP BY r.MaDH,r.MaNL,n.Loai,n.DonVi
    HAVING SUM(r.Qty)>0;

    IF EXISTS (
        SELECT 1 FROM @Target t
        WHERE NOT EXISTS(SELECT 1 FROM dbo.DinhMucDonHang s WHERE s.MaDH=t.MaDH)
    ) THROW 51105,N'Một đơn chưa tạo được snapshot; toàn bộ giao dịch rollback.',1;

    SELECT t.MaDH,t.MaYeuCau,COUNT(s.MaNL) AS SoNguyenLieuMoi
    FROM @Target t JOIN dbo.DinhMucDonHang s ON s.MaDH=t.MaDH
    GROUP BY t.MaDH,t.MaYeuCau ORDER BY t.MaDH;

    COMMIT TRANSACTION;
    PRINT N'Hoàn tất: chỉ bổ sung snapshot cho đơn demo còn chờ/đang pha, chưa có snapshot.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

SELECT d.MaDH,d.MaYeuCau,d.TrangThai,
       (SELECT COUNT(*) FROM dbo.DinhMucDonHang s WHERE s.MaDH=d.MaDH) AS SoNguyenLieuDaChot
FROM dbo.DonHang d
WHERE d.MaYeuCau IN ('DH_SEED_03','DH_SEED_04','DH_SEED_05','DH_SEED_06')
ORDER BY d.MaYeuCau;
GO
