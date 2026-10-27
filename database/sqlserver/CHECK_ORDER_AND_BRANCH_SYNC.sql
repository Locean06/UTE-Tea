-- CHI DOC; khong DROP / DELETE / UPDATE.
USE UTE_Tea;
SELECT DB_NAME() AS DatabaseDangKetNoi, @@SERVERNAME AS SqlServer;
SELECT s.Id, s.Name, s.Area, s.Active, s.Address,
       (SELECT COUNT(*) FROM dbo.MonUong p WHERE p.ShopId=s.Id AND p.HoatDong=1) AS MonDangBan,
       (SELECT COUNT(*) FROM dbo.Carrier c WHERE (c.ShopId=s.Id OR c.ShopId IS NULL) AND c.Active=1) AS HinhThucGiaoHopLe
FROM dbo.Shop s ORDER BY s.Id;
SELECT TOP(30) o.Id, o.ShopId, s.Name AS CoSo, o.Recipient, o.Status, o.ShipperId,
       o.TotalAmount, o.CreatedAt
FROM dbo.DeliveryOrder o INNER JOIN dbo.Shop s ON s.Id=o.ShopId
ORDER BY o.Id DESC;
SELECT COUNT(*) AS SoDonPOS FROM dbo.DonHang;
SELECT COUNT(*) AS SoDonGiaoHang FROM dbo.DeliveryOrder;
