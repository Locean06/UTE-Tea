-- UTE TEA: manually seed a NEW Neon PostgreSQL demo DB via Neon SQL Editor
-- Updated for GitHub Locean06/UTE-Tea branch deploy.
-- IMPORTANT: Run ONLY after Spring Boot/Hibernate has created the schema.
-- Run with DEMO_SEED_ENABLED=false to avoid running Java seeds in parallel.
-- Inserts are idempotent; no DROP, TRUNCATE or overwrite of passwords.
-- Source: database/sqlserver/02_Du_Lieu_Goc_Menu.sql, translated to PostgreSQL.
-- 21 fictitious demo users (3 each role), hashes only; passwords in PRIVATE CSV.
-- 11 categories, 70 menu variants, 37 materials, 44 stock entries,
-- 13 preparation recipes, 298 per-drink recipe ingredients,
-- 3 demo shops, branch menus, 10 tables, carrier and voucher.
-- No historical orders/invoices/refunds are migrated.

BEGIN;

DO $$
BEGIN
  IF to_regclass('public.taikhoan') IS NULL
     OR to_regclass('public.danhmuc') IS NULL
     OR to_regclass('public.monuong') IS NULL
     OR to_regclass('public.shop') IS NULL
     OR to_regclass('public.nguyenlieu') IS NULL
     OR to_regclass('public.tonnguyenlieu') IS NULL
     OR to_regclass('public.congthucmon') IS NULL
     OR to_regclass('public.congthucsoche') IS NULL
     OR to_regclass('public.chitietctsoche') IS NULL
     OR to_regclass('public.cauhinhchung') IS NULL
     OR to_regclass('public.ban') IS NULL
     OR to_regclass('public.voucher') IS NULL
     OR to_regclass('public.shopmanager') IS NULL
  THEN
    RAISE EXCEPTION 'Hibernate schema is missing. Start Render once with SPRING_PROFILES_ACTIVE=prod, JPA_DDL_AUTO=update, DEMO_SEED_ENABLED=false before running this script.';
  END IF;
END
$$;

-- 01: Users. Never replace credentials of existing accounts.
WITH demo(role_name, email_addr, full_name, passhash) AS (
VALUES
  ('ADMIN', 'nguyenthienan@utetea.vn', 'Nguyễn Thiên An', '$2y$11$kHXxrdOTFR2u3jTclgJuBedhc2aFQKpyrIVis72pZhkgPX4EXioLm'),
  ('ADMIN', 'tranminhkhoa@utetea.vn', 'Trần Minh Khoa', '$2y$11$KNux0KW5eWjXnsmek1ha/.D9.DN59ElePI7EpBS4P.lR3nF5aZ0wS'),
  ('ADMIN', 'lehoangphuc@utetea.vn', 'Lê Hoàng Phúc', '$2y$11$SbPxlEb24hWbaXyoIuldx.B/niXynUjpkEVxWe96mxxC2B/kq3RM2'),
  ('CASHIER', 'phamthuha@utetea.vn', 'Phạm Thu Hà', '$2y$11$c4gKqQEdthsogaZbM.yz9O3YtISwWfOA19ms.bAf.gPBy6D4ml.UK'),
  ('CASHIER', 'vongoclinh@utetea.vn', 'Võ Ngọc Linh', '$2y$11$CE7a216kU82XTWg0ADzAt.P3dZMHNdNbEb307Pj.Tp8jT341FEaxK'),
  ('CASHIER', 'buitramanh@utetea.vn', 'Bùi Trâm Anh', '$2y$11$EWpcsUUGoSUCRPMMUl/8ye9FEI4qizFFWbmYbneUVayErQcNsMi02'),
  ('KITCHEN', 'nguyenducminh@utetea.vn', 'Nguyễn Đức Minh', '$2y$11$mlPY9AmjQOhDplG5SLLp1OA4Li5he1N.4v4xKIiWsEJ0i8EF4Lt9.'),
  ('KITCHEN', 'tranduylong@utetea.vn', 'Trần Duy Long', '$2y$11$6hx0chGpZ.WHdqrgJB/SMuyE4g7HqCNaqGemCIwSkV6QiZtX.eifO'),
  ('KITCHEN', 'hoangbaotran@utetea.vn', 'Hoàng Bảo Trân', '$2y$11$iKR7eDGCRod.x6kqRG0sJeUCOyanXDEMQOIfAqKWa2wvPJEp9dIBa'),
  ('CUSTOMER', 'maiphuongthao@utetea.vn', 'Mai Phương Thảo', '$2y$11$HV63ED89EXuUhHD570WltOTWCVOkuxMbIwDTtfwK0VX1Rt21oR2uy'),
  ('CUSTOMER', 'nguyenkhanhvy@utetea.vn', 'Nguyễn Khánh Vy', '$2y$11$rjft1/yvpWfCbwqnox/ARuZKQ8xZ2HE1MwC63L8MKVTx1mDwoJiu6'),
  ('CUSTOMER', 'levanhoan@utetea.vn', 'Lê Văn Hoàn', '$2y$11$gCWrt6gp.s/Fgi/KRL2xneAyh8a23c.mjSOZC/g7QsYNUmW.v7n2O'),
  ('VENDOR', 'dangthanhbinh@utetea.vn', 'Đặng Thanh Bình', '$2y$11$GcOB15.evysN9C7XS.4bR.4kYIPIrz9VkaADOUmX2PDS7RJU0uLNK'),
  ('VENDOR', 'phamgiahan@utetea.vn', 'Phạm Gia Hân', '$2y$11$qJK.VAsXdRJaMuCaKMEYTe7E58Q4hV8G1J.pV0JsOBdXx3qGMBHAy'),
  ('VENDOR', 'truonghieunghia@utetea.vn', 'Trương Hiếu Nghĩa', '$2y$11$UPiOWX9.GnjwGNouAMDn0OnCQobqDO7XjNVp/3pKRqzcM2LJHoPj.'),
  ('MANAGER', 'dohongnhung@utetea.vn', 'Đỗ Hồng Nhung', '$2y$11$/xDP6/HWSP0MIIzEJ99FvONjFlAmoreK/ha3hiv0prIkFJQCHrE.2'),
  ('MANAGER', 'nguyentuananh@utetea.vn', 'Nguyễn Tuấn Anh', '$2y$11$ULpGQCOGB12JctUtRrl/QOR6dCUMooL/NOdVgiNdYmbzVDyCNmXy6'),
  ('MANAGER', 'lethanhthao@utetea.vn', 'Lê Thanh Thảo', '$2y$11$LzrFKtlNl0HUaDsA1z6JZODqP7nGRCRmXHZwkGEUSYQqMznfThb7u'),
  ('SHIPPER', 'vominhquan@utetea.vn', 'Võ Minh Quân', '$2y$11$D7UQEaF3dcno5jkXoY8piuufnYfOUyxUtozK48eIDyrWPHvYwtsUS'),
  ('SHIPPER', 'tranhuyhoang@utetea.vn', 'Trần Huy Hoàng', '$2y$11$OtyIrFsjEkGNKIssDu3UiubvvcqtBAATitwM.xrTLDtURzpv9hI.W'),
  ('SHIPPER', 'buiduckhang@utetea.vn', 'Bùi Đức Khang', '$2y$11$renhnl.o2/UEwW9fchA59eCH4wDB4iCQL6zJZRk.E/vsSoOiWX37O')
)
INSERT INTO taikhoan (email, matkhaubam, hoten, vaitro, hoatdong, emailxacthuc, tokenversion, taoluc)
SELECT email_addr, passhash, full_name, role_name, true, true, 1, now()
FROM demo
ON CONFLICT (email) DO NOTHING;

-- 02: Global shop settings.
INSERT INTO cauhinhchung (macauhinh, tenquan, diachi, nganhang, sotaikhoan, chutk, tylegiam, loaigiam, giatrigiam, nguoisua, sualuc)
SELECT 1, 'UTE Tea', 'Số 1 Võ Văn Ngân, TP. Thủ Đức',
       'MB Bank', NULL, NULL, 10.00, 'PERCENT', 10.00, mak, now()
FROM (SELECT matk AS mak FROM taikhoan WHERE email='nguyenthienan@utetea.vn' LIMIT 1) admin
ON CONFLICT (macauhinh) DO NOTHING;

-- 03: Global menu categories.
WITH demo(category_name, topping_allowed) AS (
VALUES
  ('Trà Sữa Truyền Thống', true),
  ('Trà Sữa Ô Long', true),
  ('Trà Sữa Lài', true),
  ('Trà Trái Cây Tươi', true),
  ('Cà Phê Truyền Thống', true),
  ('Cà Phê Sáng Tạo', true),
  ('Sữa Tươi Trân Châu', true),
  ('Macchiato & Váng Sữa', true),
  ('Đá Xay & Sinh Tố', true),
  ('Trà Thảo Mộc & Thanh Nhiệt', true),
  ('Topping Cao Cấp', false)
)
INSERT INTO danhmuc (tendm, hoatdong, chopheptopping)
SELECT d.category_name, true, d.topping_allowed
FROM demo d
WHERE NOT EXISTS (SELECT 1 FROM danhmuc x WHERE x.shopid IS NULL AND x.tendm=d.category_name);

-- 04: 70 menu entries: 30 drinks x 2 sizes, plus 10 toppings.
WITH demo(category_name, item_name, size_name, price_vnd, description, search_name, menu_code) AS (
VALUES
  ('Trà Sữa Truyền Thống', 'Trà Sữa Truyền Thống', 'M', 25000, 'Hương vị trà đen đậm đà hòa quyện cùng sữa béo thơm ngon', 'tra sua truyen thong', 'tra sua truyen thong|m'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Truyền Thống', 'L', 30000, 'Trà sữa truyền thống phiên bản ly lớn 700ml sảng khoái', 'tra sua truyen thong', 'tra sua truyen thong|l'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Thái Xanh', 'M', 28000, 'Trà xanh Thái Lan thanh mát, thơm ngát thảo mộc', 'tra sua thai xanh', 'tra sua thai xanh|m'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Thái Xanh', 'L', 33000, 'Trà sữa Thái xanh giải nhiệt ngày hè ly lớn', 'tra sua thai xanh', 'tra sua thai xanh|l'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Thái Đỏ', 'M', 28000, 'Trà đỏ Thái Lan truyền thống thơm nồng vị thảo mộc', 'tra sua thai do', 'tra sua thai do|m'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Thái Đỏ', 'L', 33000, 'Trà Thái đỏ đậm vị béo ngậy ly lớn', 'tra sua thai do', 'tra sua thai do|l'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Socola Đậm Đà', 'M', 32000, 'Vị socola hảo hạng kết hợp trà đen thơm lừng', 'tra sua socola dam da', 'tra sua socola dam da|m'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Socola Đậm Đà', 'L', 37000, 'Trà sữa socola đậm đà ly lớn', 'tra sua socola dam da', 'tra sua socola dam da|l'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Khoai Môn Hoàng Kim', 'M', 32000, 'Khoai môn bùi béo tự nhiên hòa quyện sữa tươi', 'tra sua khoai mon hoang kim', 'tra sua khoai mon hoang kim|m'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Khoai Môn Hoàng Kim', 'L', 37000, 'Trà sữa khoai môn thơm bùi ly lớn', 'tra sua khoai mon hoang kim', 'tra sua khoai mon hoang kim|l'),
  ('Trà Sữa Ô Long', 'Trà Sữa Ô Long Nướng', 'M', 30000, 'Hương vị ô long rang cháy đặc trưng thơm lừng khó cưỡng', 'tra sua o long nuong', 'tra sua o long nuong|m'),
  ('Trà Sữa Ô Long', 'Trà Sữa Ô Long Nướng', 'L', 35000, 'Ô long nướng đậm vị khói thơm ngậy ly lớn', 'tra sua o long nuong', 'tra sua o long nuong|l'),
  ('Trà Sữa Ô Long', 'Trà Sữa Ô Long Thiết Quan Âm', 'M', 32000, 'Trà thiết quan âm thượng hạng hậu ngọt thanh tao', 'tra sua o long thiet quan am', 'tra sua o long thiet quan am|m'),
  ('Trà Sữa Ô Long', 'Trà Sữa Ô Long Thiết Quan Âm', 'L', 38000, 'Thiết quan âm sữa thơm quý phái ly lớn', 'tra sua o long thiet quan am', 'tra sua o long thiet quan am|l'),
  ('Trà Sữa Lài', 'Trà Sữa Lài Thơm', 'M', 28000, 'Hoa lài tự nhiên thơm ngát hòa cùng sữa dịu ngọt', 'tra sua lai thom', 'tra sua lai thom|m'),
  ('Trà Sữa Lài', 'Trà Sữa Lài Thơm', 'L', 33000, 'Trà sữa lài ngọt thanh thư giãn ly lớn', 'tra sua lai thom', 'tra sua lai thom|l'),
  ('Trà Sữa Lài', 'Trà Sữa Lài Kem Cheese', 'M', 35000, 'Trà sữa lài thanh tao kèm váng phô mai béo ngậy', 'tra sua lai kem cheese', 'tra sua lai kem cheese|m'),
  ('Trà Sữa Lài', 'Trà Sữa Lài Kem Cheese', 'L', 42000, 'Trà sữa lài phủ kem cheese ngậy béo ly lớn', 'tra sua lai kem cheese', 'tra sua lai kem cheese|l'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Matcha Nhật Bản', 'M', 35000, 'Bột matcha Uji thượng phẩm thơm lừng vị trà xanh mộc', 'tra sua matcha nhat ban', 'tra sua matcha nhat ban|m'),
  ('Trà Sữa Truyền Thống', 'Trà Sữa Matcha Nhật Bản', 'L', 42000, 'Matcha sữa đậm đà chuẩn vị Nhật ly lớn', 'tra sua matcha nhat ban', 'tra sua matcha nhat ban|l'),
  ('Trà Trái Cây Tươi', 'Trà Đào Cam Sả', 'M', 28000, 'Trà đen ủ lạnh cùng cam vàng mọng nước và sả tươi đập dập', 'tra dao cam sa', 'tra dao cam sa|m'),
  ('Trà Trái Cây Tươi', 'Trà Đào Cam Sả', 'L', 33000, 'Trà đào cam sả thanh nhiệt tươi mới ly lớn', 'tra dao cam sa', 'tra dao cam sa|l'),
  ('Trà Trái Cây Tươi', 'Trà Vải Hoa Hồng', 'M', 30000, 'Vải thiều ngọt lịm phảng phất hương hoa hồng lãng mạn', 'tra vai hoa hong', 'tra vai hoa hong|m'),
  ('Trà Trái Cây Tươi', 'Trà Vải Hoa Hồng', 'L', 35000, 'Trà vải hoa hồng quý phái ngọt ngào ly lớn', 'tra vai hoa hong', 'tra vai hoa hong|l'),
  ('Trà Trái Cây Tươi', 'Trà Dâu Tây Nhiệt Đới', 'M', 32000, 'Dâu tây Đà Lạt tươi giã tay thơm lừng vị quả mọng', 'tra dau tay nhiet doi', 'tra dau tay nhiet doi|m'),
  ('Trà Trái Cây Tươi', 'Trà Dâu Tây Nhiệt Đới', 'L', 38000, 'Trà dâu tây tươi mát rượi ly lớn', 'tra dau tay nhiet doi', 'tra dau tay nhiet doi|l'),
  ('Trà Trái Cây Tươi', 'Trà Xoài Chanh Dây', 'M', 30000, 'Vị chua ngọt bùng nổ giữa xoài cát chín và chanh leo', 'tra xoai chanh day', 'tra xoai chanh day|m'),
  ('Trà Trái Cây Tươi', 'Trà Xoài Chanh Dây', 'L', 35000, 'Trà xoài chanh leo bừng tỉnh năng lượng ly lớn', 'tra xoai chanh day', 'tra xoai chanh day|l'),
  ('Trà Trái Cây Tươi', 'Trà Ổi Hồng Hạt Chia', 'M', 32000, 'Nước cốt ổi hồng thơm ngát giàu vitamin kèm hạt chia', 'tra oi hong hat chia', 'tra oi hong hat chia|m'),
  ('Trà Trái Cây Tươi', 'Trà Ổi Hồng Hạt Chia', 'L', 37000, 'Trà ổi hồng bổ dưỡng da sáng dáng thon ly lớn', 'tra oi hong hat chia', 'tra oi hong hat chia|l'),
  ('Trà Trái Cây Tươi', 'Trà Táo Xanh Bạc Hà', 'M', 30000, 'Táo xanh giòn rụm kết hợp bạc hà mát lạnh the mát', 'tra tao xanh bac ha', 'tra tao xanh bac ha|m'),
  ('Trà Trái Cây Tươi', 'Trà Táo Xanh Bạc Hà', 'L', 35000, 'Trà táo xanh the mát giải nhiệt ly lớn', 'tra tao xanh bac ha', 'tra tao xanh bac ha|l'),
  ('Trà Trái Cây Tươi', 'Trà Bưởi Mật Ong', 'M', 32000, 'Tép bưởi hồng mọng nước cùng mật ong hoa nhãn dịu ngọt', 'tra buoi mat ong', 'tra buoi mat ong|m'),
  ('Trà Trái Cây Tươi', 'Trà Bưởi Mật Ong', 'L', 38000, 'Trà bưởi hồng mật ong thanh lọc cơ thể ly lớn', 'tra buoi mat ong', 'tra buoi mat ong|l'),
  ('Trà Trái Cây Tươi', 'Trà Mãng Cầu Xiêm', 'M', 35000, 'Mãng cầu xiêm chua ngọt tự nhiên giã tay đậm đà', 'tra mang cau xiem', 'tra mang cau xiem|m'),
  ('Trà Trái Cây Tươi', 'Trà Mãng Cầu Xiêm', 'L', 42000, 'Trà mãng cầu xiêm hot trend tươi mát ly lớn', 'tra mang cau xiem', 'tra mang cau xiem|l'),
  ('Cà Phê Truyền Thống', 'Cà Phê Đen Đá', 'M', 20000, 'Cà phê robusta rang mộc pha phin truyền thống đậm đà', 'ca phe den da', 'ca phe den da|m'),
  ('Cà Phê Truyền Thống', 'Cà Phê Đen Đá', 'L', 25000, 'Cà phê đen đá đậm vị tỉnh táo ngày dài', 'ca phe den da', 'ca phe den da|l'),
  ('Cà Phê Truyền Thống', 'Cà Phê Sữa Đá', 'M', 25000, 'Cà phê pha phin truyền thống hòa cùng sữa đặc sánh ngọt', 'ca phe sua da', 'ca phe sua da|m'),
  ('Cà Phê Truyền Thống', 'Cà Phê Sữa Đá', 'L', 30000, 'Cà phê sữa đá chuẩn gu Sài Gòn ly lớn', 'ca phe sua da', 'ca phe sua da|l'),
  ('Cà Phê Sáng Tạo', 'Cà Phê Muối Xứ Huế', 'M', 28000, 'Lớp kem sữa muối mằn mặn hòa cùng cà phê thơm nồng', 'ca phe muoi xu hue', 'ca phe muoi xu hue|m'),
  ('Cà Phê Sáng Tạo', 'Cà Phê Muối Xứ Huế', 'L', 34000, 'Cà phê muối béo mặn thơm ngậy ly lớn', 'ca phe muoi xu hue', 'ca phe muoi xu hue|l'),
  ('Cà Phê Sáng Tạo', 'Bạc Xỉu Ba Tầng', 'M', 28000, 'Tầng sữa ngọt dịu, sữa tươi thơm mát và cà phê đậm vị', 'bac xiu ba tang', 'bac xiu ba tang|m'),
  ('Cà Phê Sáng Tạo', 'Bạc Xỉu Ba Tầng', 'L', 33000, 'Bạc xỉu ba tầng ngọt ngào êm ái ly lớn', 'bac xiu ba tang', 'bac xiu ba tang|l'),
  ('Sữa Tươi Trân Châu', 'Sữa Tươi Trân Châu Đường Đen', 'M', 35000, 'Sữa tươi thanh trùng Đà Lạt cùng trân châu nấu đường đen dẻo quánh', 'sua tuoi tran chau duong den', 'sua tuoi tran chau duong den|m'),
  ('Sữa Tươi Trân Châu', 'Sữa Tươi Trân Châu Đường Đen', 'L', 42000, 'Sữa tươi đường đen thơm ngon nức tiếng ly lớn', 'sua tuoi tran chau duong den', 'sua tuoi tran chau duong den|l'),
  ('Sữa Tươi Trân Châu', 'Sữa Tươi Sương Sáo Thảo Mộc', 'M', 32000, 'Sữa tươi béo nhẹ kết hợp sương sáo Đài Loan mát lành', 'sua tuoi suong sao thao moc', 'sua tuoi suong sao thao moc|m'),
  ('Sữa Tươi Trân Châu', 'Sữa Tươi Sương Sáo Thảo Mộc', 'L', 38000, 'Sữa tươi sương sáo thanh lọc cơ thể ly lớn', 'sua tuoi suong sao thao moc', 'sua tuoi suong sao thao moc|l'),
  ('Macchiato & Váng Sữa', 'Trà Ô Long Váng Sữa Macchiato', 'M', 35000, 'Cốt trà ô long thượng hạng phủ lớp macchiato kem sữa béo ngậy', 'tra o long vang sua macchiato', 'tra o long vang sua macchiato|m'),
  ('Macchiato & Váng Sữa', 'Trà Ô Long Váng Sữa Macchiato', 'L', 42000, 'Ô long macchiato béo ngọt cân bằng ly lớn', 'tra o long vang sua macchiato', 'tra o long vang sua macchiato|l'),
  ('Macchiato & Váng Sữa', 'Trà Đen Macchiato Muối Biển', 'M', 32000, 'Trà đen đậm đà tương phản với kem muối biển béo ngậy', 'tra den macchiato muoi bien', 'tra den macchiato muoi bien|m'),
  ('Macchiato & Váng Sữa', 'Trà Đen Macchiato Muối Biển', 'L', 38000, 'Trà đen macchiato muối biển thơm ngon ly lớn', 'tra den macchiato muoi bien', 'tra den macchiato muoi bien|l'),
  ('Đá Xay & Sinh Tố', 'Matcha Đá Xay Kem Tuyết', 'M', 40000, 'Matcha xay nhuyễn cùng đá và sữa, phủ bông kem whipping tươi', 'matcha da xay kem tuyet', 'matcha da xay kem tuyet|m'),
  ('Đá Xay & Sinh Tố', 'Matcha Đá Xay Kem Tuyết', 'L', 48000, 'Matcha đá xay kem tuyết mát lạnh sảng khoái ly lớn', 'matcha da xay kem tuyet', 'matcha da xay kem tuyet|l'),
  ('Đá Xay & Sinh Tố', 'Cà Phê Đá Xay Caramel', 'M', 38000, 'Espresso xay đá cùng xốt caramel ngọt bùi phủ kem whipping', 'ca phe da xay caramel', 'ca phe da xay caramel|m'),
  ('Đá Xay & Sinh Tố', 'Cà Phê Đá Xay Caramel', 'L', 45000, 'Cà phê caramel đá xay béo ngọt nồng nàn ly lớn', 'ca phe da xay caramel', 'ca phe da xay caramel|l'),
  ('Đá Xay & Sinh Tố', 'Sinh Tố Bơ Dừa Thơm Béo', 'M', 42000, 'Bơ sáp Đắk Lắk dẻo quánh xay cùng nước cốt dừa béo bùi', 'sinh to bo dua thom beo', 'sinh to bo dua thom beo|m'),
  ('Đá Xay & Sinh Tố', 'Sinh Tố Bơ Dừa Thơm Béo', 'L', 50000, 'Sinh tố bơ dừa thơm ngậy bổ dưỡng ly lớn', 'sinh to bo dua thom beo', 'sinh to bo dua thom beo|l'),
  ('Trà Thảo Mộc & Thanh Nhiệt', 'Trà Hoa Cúc Mật Ong Hạt Sen', 'M', 32000, 'Hoa cúc sấy khô ủ ấm mật ong rừng và hạt sen bùi ngậy', 'tra hoa cuc mat ong hat sen', 'tra hoa cuc mat ong hat sen|m'),
  ('Trà Thảo Mộc & Thanh Nhiệt', 'Trà Hoa Cúc Mật Ong Hạt Sen', 'L', 38000, 'Trà hoa cúc hạt sen an thần thanh nhiệt ly lớn', 'tra hoa cuc mat ong hat sen', 'tra hoa cuc mat ong hat sen|l'),
  ('Topping Cao Cấp', 'Trân Châu Đen Hoàng Kim', 'M', 6000, 'Trân châu dẻo dai hoàng kim nấu đường đen ngọt ngào', 'tran chau den hoang kim', 'tran chau den hoang kim|m'),
  ('Topping Cao Cấp', 'Trân Châu Trắng 3Q', 'M', 7000, 'Trân châu trắng giòn sần sật nhai vui miệng', 'tran chau trang 3q', 'tran chau trang 3q|m'),
  ('Topping Cao Cấp', 'Thạch Đào Giòn Ngọt', 'M', 8000, 'Thạch đào dai giòn thơm nức mùi quả chín', 'thach dao gion ngot', 'thach dao gion ngot|m'),
  ('Topping Cao Cấp', 'Kem Cheese Phô Mai Macchiato', 'M', 10000, 'Lớp kem cheese mằn mặn béo ngậy tan chảy trên đầu lưỡi', 'kem cheese pho mai macchiato', 'kem cheese pho mai macchiato|m'),
  ('Topping Cao Cấp', 'Pudding Trứng Vàng', 'M', 8000, 'Pudding mềm mịn núng nính thơm ngậy vị trứng gà', 'pudding trung vang', 'pudding trung vang|m'),
  ('Topping Cao Cấp', 'Thạch Phô Mai Tươi', 'M', 10000, 'Viên phô mai bọc thạch trong suốt béo ngậy', 'thach pho mai tuoi', 'thach pho mai tuoi|m'),
  ('Topping Cao Cấp', 'Sương Sáo Thảo Mộc Đài Loan', 'M', 6000, 'Sương sáo thanh mát thảo mộc giải nhiệt tức thì', 'suong sao thao moc dai loan', 'suong sao thao moc dai loan|m'),
  ('Topping Cao Cấp', 'Thạch Củ Năng Giòn', 'M', 8000, 'Củ năng bọc bột lọc giòn ngọt sảng khoái', 'thach cu nang gion', 'thach cu nang gion|m'),
  ('Topping Cao Cấp', 'Nha Đam Tươi Đường Phèn', 'M', 7000, 'Nha đam giòn mát nấu đường phèn thanh khiết', 'nha dam tuoi duong phen', 'nha dam tuoi duong phen|m'),
  ('Topping Cao Cấp', 'Khúc Bạch Hạnh Nhân', 'M', 10000, 'Khúc bạch phô mai thơm lừng rắc hạnh nhân giòn', 'khuc bach hanh nhan', 'khuc bach hanh nhan|m')
)
INSERT INTO monuong (madm, tenmon, size, gia, mota, searchname, menucode,
                     anhurl, hoatdong, catalogversion)
SELECT c.madm, d.item_name, d.size_name, d.price_vnd, d.description,
       d.search_name, d.menu_code, '/images/ute-tea-product-fallback.svg',
       true, 0
FROM demo d
JOIN danhmuc c ON c.shopid IS NULL AND c.tendm=d.category_name
WHERE NOT EXISTS (
  SELECT 1 FROM monuong p WHERE p.shopid IS NULL AND p.tenmon=d.item_name AND p.size=d.size_name
);

-- 05: Materials.
WITH demo(material_name, material_type, unit_name) AS (
VALUES
  ('Trà Đen Cổ Thụ', 'THO', 'kg'),
  ('Trà Ô Long Kim Tuyên', 'THO', 'kg'),
  ('Trà Lài Thượng Hạng', 'THO', 'kg'),
  ('Cà Phê Hạt Rang Mộc', 'THO', 'kg'),
  ('Sữa Đặc Có Đường', 'THO', 'kg'),
  ('Đường Cát Trắng', 'THO', 'kg'),
  ('Đường Đen Nữ Hoàng', 'THO', 'kg'),
  ('Sữa Tươi Thanh Trùng', 'THO', 'kg'),
  ('Bột Béo Thực Vật', 'THO', 'kg'),
  ('Bột Kem Cheese Anchor', 'THO', 'kg'),
  ('Trân Châu Thô Đen', 'THO', 'kg'),
  ('Hạt Trân Châu Trắng 3Q', 'THO', 'kg'),
  ('Đào Ngâm Đóng Hộp', 'THO', 'kg'),
  ('Cam Vàng Tươi', 'THO', 'kg'),
  ('Sả Cây Tươi', 'THO', 'kg'),
  ('Dâu Tây Đà Lạt', 'THO', 'kg'),
  ('Củ Năng Tươi', 'THO', 'kg'),
  ('Sương Sáo Khô', 'THO', 'kg'),
  ('Nha Đam Tươi', 'THO', 'kg'),
  ('Bột Pudding Trứng', 'THO', 'kg'),
  ('Ly Nhựa PP 500ml', 'THO', 'cai'),
  ('Ly Nhựa PP 700ml', 'THO', 'cai'),
  ('Ống Hút Trân Châu', 'THO', 'cai'),
  ('Màng Ép Miệng Ly', 'THO', 'cai'),
  ('Nước Cốt Trà Đen', 'SOCHE', 'ml'),
  ('Nước Cốt Trà Ô Long', 'SOCHE', 'ml'),
  ('Nước Cốt Trà Lài', 'SOCHE', 'ml'),
  ('Nước Cốt Cà Phê Pha Phin', 'SOCHE', 'ml'),
  ('Trân Châu Nấu Chín Dẻo', 'SOCHE', 'g'),
  ('Trân Châu Nấu Đường Đen', 'SOCHE', 'g'),
  ('Sốt Kem Cheese Đánh Bông', 'SOCHE', 'ml'),
  ('Pudding Trứng Nấu Chín', 'SOCHE', 'g'),
  ('Thạch Phô Mai Tươi Đúc Khuôn', 'SOCHE', 'g'),
  ('Thạch Củ Năng Rim Đường', 'SOCHE', 'g'),
  ('Thạch Sương Sáo Nấu Đông', 'SOCHE', 'g'),
  ('Khúc Bạch Cắt Viên', 'SOCHE', 'g'),
  ('Nước Đường Pha Sẵn', 'SOCHE', 'ml')
)
INSERT INTO nguyenlieu (tennl, loai, donvi, hoatdong)
SELECT d.material_name, d.material_type, d.unit_name, true
FROM demo d
ON CONFLICT (tennl) DO NOTHING;

-- 06: Two-location warehouse stock. Existing quantities are never reset.
WITH demo(material_name, location_name, quantity, warning_level) AS (
VALUES
  ('Trà Đen Cổ Thụ', 'KHO', 60.000, 10.000),
  ('Trà Ô Long Kim Tuyên', 'KHO', 50.000, 8.000),
  ('Trà Lài Thượng Hạng', 'KHO', 45.000, 8.000),
  ('Cà Phê Hạt Rang Mộc', 'KHO', 40.000, 5.000),
  ('Sữa Đặc Có Đường', 'KHO', 120.000, 15.000),
  ('Đường Cát Trắng', 'KHO', 150.000, 20.000),
  ('Đường Đen Nữ Hoàng', 'KHO', 80.000, 10.000),
  ('Sữa Tươi Thanh Trùng', 'KHO', 100.000, 15.000),
  ('Trân Châu Thô Đen', 'KHO', 90.000, 10.000),
  ('Hạt Trân Châu Trắng 3Q', 'KHO', 50.000, 5.000),
  ('Đào Ngâm Đóng Hộp', 'KHO', 40.000, 5.000),
  ('Bột Kem Cheese Anchor', 'KHO', 35.000, 5.000),
  ('Bột Pudding Trứng', 'KHO', 30.000, 5.000),
  ('Ly Nhựa PP 500ml', 'KHO', 2000.000, 300.000),
  ('Ly Nhựa PP 700ml', 'KHO', 2000.000, 300.000),
  ('Ống Hút Trân Châu', 'KHO', 3000.000, 500.000),
  ('Cam Vàng Tươi', 'KHO', 4.000, 10.000),
  ('Sả Cây Tươi', 'KHO', 2.500, 5.000),
  ('Dâu Tây Đà Lạt', 'KHO', 1.800, 4.000),
  ('Củ Năng Tươi', 'KHO', 0.000, 8.000),
  ('Sương Sáo Khô', 'KHO', 0.000, 5.000),
  ('Bột Béo Thực Vật', 'KHO', 25.000, NULL),
  ('Nha Đam Tươi', 'KHO', 15.000, NULL),
  ('Màng Ép Miệng Ly', 'KHO', 20.000, NULL),
  ('Nước Cốt Trà Đen', 'BEP', 7000.000, 1000.000),
  ('Nước Cốt Trà Ô Long', 'BEP', 6000.000, 1000.000),
  ('Nước Cốt Cà Phê Pha Phin', 'BEP', 3000.000, 500.000),
  ('Trân Châu Nấu Chín Dẻo', 'BEP', 3500.000, 500.000),
  ('Sốt Kem Cheese Đánh Bông', 'BEP', 2500.000, 400.000),
  ('Pudding Trứng Nấu Chín', 'BEP', 2000.000, 300.000),
  ('Sữa Đặc Có Đường', 'BEP', 15.000, 2.000),
  ('Đường Cát Trắng', 'BEP', 20.000, 2.000),
  ('Ly Nhựa PP 500ml', 'BEP', 300.000, 50.000),
  ('Ly Nhựa PP 700ml', 'BEP', 300.000, 50.000),
  ('Ống Hút Trân Châu', 'BEP', 400.000, 60.000),
  ('Nước Cốt Trà Lài', 'BEP', 350.000, 1000.000),
  ('Trân Châu Nấu Đường Đen', 'BEP', 180.000, 500.000),
  ('Cam Vàng Tươi', 'BEP', 0.800, 2.000),
  ('Đào Ngâm Đóng Hộp', 'BEP', 0.500, 2.000),
  ('Thạch Củ Năng Rim Đường', 'BEP', 0.000, 500.000),
  ('Thạch Sương Sáo Nấu Đông', 'BEP', 0.000, 400.000),
  ('Thạch Phô Mai Tươi Đúc Khuôn', 'BEP', 1200.000, NULL),
  ('Khúc Bạch Cắt Viên', 'BEP', 800.000, NULL),
  ('Nước Đường Pha Sẵn', 'BEP', 2500.000, NULL)
)
INSERT INTO tonnguyenlieu (manl, vitri, soluong, nguongcanhbao, version)
SELECT m.manl, d.location_name, d.quantity, d.warning_level, 0
FROM demo d
JOIN nguyenlieu m ON m.tennl=d.material_name
ON CONFLICT (manl, vitri) DO NOTHING;

-- 07: Preparation recipes from local SQL.
WITH demo(recipe_name, output_name, output_qty, tolerance_pct, input_name, input_qty) AS (
VALUES
  ('Ủ Nước Cốt Trà Đen', 'Nước Cốt Trà Đen', 3000.000, 15.00, 'Trà Đen Cổ Thụ', 1.000),
  ('Ủ Nước Cốt Trà Ô Long', 'Nước Cốt Trà Ô Long', 3000.000, 15.00, 'Trà Ô Long Kim Tuyên', 1.000),
  ('Ủ Nước Cốt Trà Lài', 'Nước Cốt Trà Lài', 3500.000, 15.00, 'Trà Lài Thượng Hạng', 1.000),
  ('Chiết Xuất Cà Phê Phin', 'Nước Cốt Cà Phê Pha Phin', 2500.000, 10.00, 'Cà Phê Hạt Rang Mộc', 1.000),
  ('Nấu Trân Châu Đen Dẻo', 'Trân Châu Nấu Chín Dẻo', 2500.000, 12.00, 'Trân Châu Thô Đen', 1.000),
  ('Nấu Trân Châu Đường Đen', 'Trân Châu Nấu Đường Đen', 2800.000, 12.00, 'Trân Châu Thô Đen', 1.000),
  ('Đánh Bông Kem Cheese', 'Sốt Kem Cheese Đánh Bông', 2000.000, 8.00, 'Bột Kem Cheese Anchor', 1.000),
  ('Nấu Pudding Trứng', 'Pudding Trứng Nấu Chín', 2500.000, 10.00, 'Bột Pudding Trứng', 1.000),
  ('Chế Biến Thạch Củ Năng Rim', 'Thạch Củ Năng Rim Đường', 1500.000, 10.00, 'Củ Năng Tươi', 1.000),
  ('Nấu Thạch Sương Sáo Đông', 'Thạch Sương Sáo Nấu Đông', 3000.000, 15.00, 'Sương Sáo Khô', 0.500),
  ('Làm Khúc Bạch Cắt Viên', 'Khúc Bạch Cắt Viên', 2500.000, 10.00, 'Bột Pudding Trứng', 1.000),
  ('Đúc Khuôn Thạch Phô Mai Tươi', 'Thạch Phô Mai Tươi Đúc Khuôn', 2000.000, 10.00, 'Bột Kem Cheese Anchor', 1.000),
  ('Nấu Nước Đường Si-rô', 'Nước Đường Pha Sẵn', 3000.000, 5.00, 'Đường Cát Trắng', 2.500)
)
INSERT INTO congthucsoche (tenct, manldaura, luongdaurachuan, nguongcanhbaotyle, hoatdong, version)
SELECT d.recipe_name, m.manl, d.output_qty, d.tolerance_pct, true, 1
FROM demo d JOIN nguyenlieu m ON m.tennl=d.output_name
WHERE NOT EXISTS (
  SELECT 1 FROM congthucsoche c WHERE c.tenct=d.recipe_name
);

WITH demo(recipe_name, output_name, output_qty, tolerance_pct, input_name, input_qty) AS (
VALUES
  ('Ủ Nước Cốt Trà Đen', 'Nước Cốt Trà Đen', 3000.000, 15.00, 'Trà Đen Cổ Thụ', 1.000),
  ('Ủ Nước Cốt Trà Ô Long', 'Nước Cốt Trà Ô Long', 3000.000, 15.00, 'Trà Ô Long Kim Tuyên', 1.000),
  ('Ủ Nước Cốt Trà Lài', 'Nước Cốt Trà Lài', 3500.000, 15.00, 'Trà Lài Thượng Hạng', 1.000),
  ('Chiết Xuất Cà Phê Phin', 'Nước Cốt Cà Phê Pha Phin', 2500.000, 10.00, 'Cà Phê Hạt Rang Mộc', 1.000),
  ('Nấu Trân Châu Đen Dẻo', 'Trân Châu Nấu Chín Dẻo', 2500.000, 12.00, 'Trân Châu Thô Đen', 1.000),
  ('Nấu Trân Châu Đường Đen', 'Trân Châu Nấu Đường Đen', 2800.000, 12.00, 'Trân Châu Thô Đen', 1.000),
  ('Đánh Bông Kem Cheese', 'Sốt Kem Cheese Đánh Bông', 2000.000, 8.00, 'Bột Kem Cheese Anchor', 1.000),
  ('Nấu Pudding Trứng', 'Pudding Trứng Nấu Chín', 2500.000, 10.00, 'Bột Pudding Trứng', 1.000),
  ('Chế Biến Thạch Củ Năng Rim', 'Thạch Củ Năng Rim Đường', 1500.000, 10.00, 'Củ Năng Tươi', 1.000),
  ('Nấu Thạch Sương Sáo Đông', 'Thạch Sương Sáo Nấu Đông', 3000.000, 15.00, 'Sương Sáo Khô', 0.500),
  ('Làm Khúc Bạch Cắt Viên', 'Khúc Bạch Cắt Viên', 2500.000, 10.00, 'Bột Pudding Trứng', 1.000),
  ('Đúc Khuôn Thạch Phô Mai Tươi', 'Thạch Phô Mai Tươi Đúc Khuôn', 2000.000, 10.00, 'Bột Kem Cheese Anchor', 1.000),
  ('Nấu Nước Đường Si-rô', 'Nước Đường Pha Sẵn', 3000.000, 5.00, 'Đường Cát Trắng', 2.500)
)
INSERT INTO chitietctsoche (mactsc, manl, luongdauvaochuan)
SELECT c.mactsc, m.manl, d.input_qty
FROM demo d
JOIN congthucsoche c ON c.tenct=d.recipe_name
JOIN nguyenlieu m ON m.tennl=d.input_name
ON CONFLICT (mactsc, manl) DO NOTHING;

-- 08: Three branches. Owners have VENDOR roles; manager has MANAGER role.
WITH demo(shop_name, shop_address, owner_email) AS (
VALUES
  ('UTE Tea Campus', '1 Võ Văn Ngân, TP. Thủ Đức', 'dangthanhbinh@utetea.vn'),
  ('UTE Tea Làng Đại Học', '2 Võ Văn Ngân, TP. Thủ Đức', 'phamgiahan@utetea.vn'),
  ('UTE Tea Thủ Đức', '3 Võ Văn Ngân, TP. Thủ Đức', 'truonghieunghia@utetea.vn')
)
INSERT INTO shop (name, ownerid, active, managercommissionenabled, commissionpercent, address, area, createdat)
SELECT d.shop_name, a.matk, true, false, 5.00, d.shop_address, 'Thủ Đức', now()
FROM demo d JOIN taikhoan a ON a.email=d.owner_email
WHERE NOT EXISTS (SELECT 1 FROM shop s WHERE s.name=d.shop_name);

INSERT INTO shopmanager (shopid, accountid)
SELECT s.id, a.matk
FROM shop s CROSS JOIN taikhoan a
WHERE s.name='UTE Tea Campus' AND a.email='dohongnhung@utetea.vn'
  AND NOT EXISTS (SELECT 1 FROM shopmanager sm WHERE sm.shopid=s.id AND sm.accountid=a.matk);

-- 09: Duplicate each global category and product to the 3 shops for marketplace.
INSERT INTO danhmuc (shopid, tendm, hoatdong, chopheptopping)
SELECT s.id, c.tendm, true, c.chopheptopping
FROM shop s CROSS JOIN danhmuc c
WHERE s.name IN ('UTE Tea Campus','UTE Tea Làng Đại Học','UTE Tea Thủ Đức')
  AND c.shopid IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM danhmuc x WHERE x.shopid=s.id AND x.tendm=c.tendm
  );

INSERT INTO monuong (shopid, madm, tenmon, size, gia, mota, searchname,
                     menucode, anhurl, deliverystock, hoatdong, catalogversion)
SELECT s.id, sc.madm, p.tenmon, p.size, p.gia, p.mota, p.searchname,
       p.menucode, p.anhurl, 50, true, 0
FROM shop s
JOIN danhmuc sc ON sc.shopid=s.id
JOIN danhmuc gc ON gc.shopid IS NULL AND gc.tendm=sc.tendm
JOIN monuong p ON p.shopid IS NULL AND p.madm=gc.madm
WHERE s.name IN ('UTE Tea Campus','UTE Tea Làng Đại Học','UTE Tea Thủ Đức')
  AND NOT EXISTS (
    SELECT 1 FROM monuong x WHERE x.shopid=s.id AND x.tenmon=p.tenmon AND x.size=p.size
  );

-- 10: Ingredient quantities for all matching global + branch menu products.
WITH demo(item_name, size_name, material_name, quantity) AS (
VALUES
  ('Trà Sữa Truyền Thống', 'M', 'Nước Cốt Trà Đen', 150.000),
  ('Trà Sữa Truyền Thống', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Truyền Thống', 'M', 'Đường Cát Trắng', 0.020),
  ('Trà Sữa Truyền Thống', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Truyền Thống', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Truyền Thống', 'L', 'Nước Cốt Trà Đen', 200.000),
  ('Trà Sữa Truyền Thống', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Truyền Thống', 'L', 'Đường Cát Trắng', 0.025),
  ('Trà Sữa Truyền Thống', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Truyền Thống', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Thái Xanh', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Sữa Thái Xanh', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Thái Xanh', 'M', 'Bột Béo Thực Vật', 0.020),
  ('Trà Sữa Thái Xanh', 'M', 'Nước Đường Pha Sẵn', 20.000),
  ('Trà Sữa Thái Xanh', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Thái Xanh', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Thái Xanh', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Sữa Thái Xanh', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Thái Xanh', 'L', 'Bột Béo Thực Vật', 0.025),
  ('Trà Sữa Thái Xanh', 'L', 'Nước Đường Pha Sẵn', 30.000),
  ('Trà Sữa Thái Xanh', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Thái Xanh', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Thái Đỏ', 'M', 'Nước Cốt Trà Đen', 150.000),
  ('Trà Sữa Thái Đỏ', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Thái Đỏ', 'M', 'Bột Béo Thực Vật', 0.020),
  ('Trà Sữa Thái Đỏ', 'M', 'Nước Đường Pha Sẵn', 20.000),
  ('Trà Sữa Thái Đỏ', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Thái Đỏ', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Thái Đỏ', 'L', 'Nước Cốt Trà Đen', 200.000),
  ('Trà Sữa Thái Đỏ', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Thái Đỏ', 'L', 'Bột Béo Thực Vật', 0.025),
  ('Trà Sữa Thái Đỏ', 'L', 'Nước Đường Pha Sẵn', 30.000),
  ('Trà Sữa Thái Đỏ', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Thái Đỏ', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Socola Đậm Đà', 'M', 'Nước Cốt Trà Đen', 150.000),
  ('Trà Sữa Socola Đậm Đà', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Socola Đậm Đà', 'M', 'Bột Béo Thực Vật', 0.020),
  ('Trà Sữa Socola Đậm Đà', 'M', 'Đường Đen Nữ Hoàng', 0.020),
  ('Trà Sữa Socola Đậm Đà', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Socola Đậm Đà', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Socola Đậm Đà', 'L', 'Nước Cốt Trà Đen', 200.000),
  ('Trà Sữa Socola Đậm Đà', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Socola Đậm Đà', 'L', 'Bột Béo Thực Vật', 0.025),
  ('Trà Sữa Socola Đậm Đà', 'L', 'Đường Đen Nữ Hoàng', 0.025),
  ('Trà Sữa Socola Đậm Đà', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Socola Đậm Đà', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'M', 'Bột Béo Thực Vật', 0.020),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'M', 'Sữa Tươi Thanh Trùng', 0.050),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'L', 'Bột Béo Thực Vật', 0.025),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'L', 'Sữa Tươi Thanh Trùng', 0.080),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Khoai Môn Hoàng Kim', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Ô Long Nướng', 'M', 'Nước Cốt Trà Ô Long', 150.000),
  ('Trà Sữa Ô Long Nướng', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Ô Long Nướng', 'M', 'Đường Cát Trắng', 0.020),
  ('Trà Sữa Ô Long Nướng', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Ô Long Nướng', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Ô Long Nướng', 'L', 'Nước Cốt Trà Ô Long', 200.000),
  ('Trà Sữa Ô Long Nướng', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Ô Long Nướng', 'L', 'Đường Cát Trắng', 0.025),
  ('Trà Sữa Ô Long Nướng', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Ô Long Nướng', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'M', 'Nước Cốt Trà Ô Long', 150.000),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'M', 'Đường Cát Trắng', 0.020),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'L', 'Nước Cốt Trà Ô Long', 200.000),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'L', 'Đường Cát Trắng', 0.025),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Ô Long Thiết Quan Âm', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Lài Thơm', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Sữa Lài Thơm', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Lài Thơm', 'M', 'Đường Cát Trắng', 0.020),
  ('Trà Sữa Lài Thơm', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Lài Thơm', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Lài Thơm', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Sữa Lài Thơm', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Lài Thơm', 'L', 'Đường Cát Trắng', 0.025),
  ('Trà Sữa Lài Thơm', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Lài Thơm', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Lài Kem Cheese', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Sữa Lài Kem Cheese', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Lài Kem Cheese', 'M', 'Sốt Kem Cheese Đánh Bông', 40.000),
  ('Trà Sữa Lài Kem Cheese', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Lài Kem Cheese', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Lài Kem Cheese', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Sữa Lài Kem Cheese', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Lài Kem Cheese', 'L', 'Sốt Kem Cheese Đánh Bông', 60.000),
  ('Trà Sữa Lài Kem Cheese', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Lài Kem Cheese', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Matcha Nhật Bản', 'M', 'Nước Cốt Trà Lài', 120.000),
  ('Trà Sữa Matcha Nhật Bản', 'M', 'Sữa Tươi Thanh Trùng', 0.100),
  ('Trà Sữa Matcha Nhật Bản', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Trà Sữa Matcha Nhật Bản', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Sữa Matcha Nhật Bản', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Sữa Matcha Nhật Bản', 'L', 'Nước Cốt Trà Lài', 160.000),
  ('Trà Sữa Matcha Nhật Bản', 'L', 'Sữa Tươi Thanh Trùng', 0.140),
  ('Trà Sữa Matcha Nhật Bản', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Trà Sữa Matcha Nhật Bản', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Sữa Matcha Nhật Bản', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Đào Cam Sả', 'M', 'Nước Cốt Trà Đen', 120.000),
  ('Trà Đào Cam Sả', 'M', 'Đào Ngâm Đóng Hộp', 0.040),
  ('Trà Đào Cam Sả', 'M', 'Cam Vàng Tươi', 0.030),
  ('Trà Đào Cam Sả', 'M', 'Sả Cây Tươi', 0.010),
  ('Trà Đào Cam Sả', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Đào Cam Sả', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Đào Cam Sả', 'L', 'Nước Cốt Trà Đen', 160.000),
  ('Trà Đào Cam Sả', 'L', 'Đào Ngâm Đóng Hộp', 0.060),
  ('Trà Đào Cam Sả', 'L', 'Cam Vàng Tươi', 0.045),
  ('Trà Đào Cam Sả', 'L', 'Sả Cây Tươi', 0.015),
  ('Trà Đào Cam Sả', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Đào Cam Sả', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Vải Hoa Hồng', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Vải Hoa Hồng', 'M', 'Nước Đường Pha Sẵn', 30.000),
  ('Trà Vải Hoa Hồng', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Vải Hoa Hồng', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Vải Hoa Hồng', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Vải Hoa Hồng', 'L', 'Nước Đường Pha Sẵn', 40.000),
  ('Trà Vải Hoa Hồng', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Vải Hoa Hồng', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Dâu Tây Nhiệt Đới', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Dâu Tây Nhiệt Đới', 'M', 'Dâu Tây Đà Lạt', 0.040),
  ('Trà Dâu Tây Nhiệt Đới', 'M', 'Nước Đường Pha Sẵn', 25.000),
  ('Trà Dâu Tây Nhiệt Đới', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Dâu Tây Nhiệt Đới', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Dâu Tây Nhiệt Đới', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Dâu Tây Nhiệt Đới', 'L', 'Dâu Tây Đà Lạt', 0.060),
  ('Trà Dâu Tây Nhiệt Đới', 'L', 'Nước Đường Pha Sẵn', 35.000),
  ('Trà Dâu Tây Nhiệt Đới', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Dâu Tây Nhiệt Đới', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Xoài Chanh Dây', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Xoài Chanh Dây', 'M', 'Nước Đường Pha Sẵn', 30.000),
  ('Trà Xoài Chanh Dây', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Xoài Chanh Dây', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Xoài Chanh Dây', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Xoài Chanh Dây', 'L', 'Nước Đường Pha Sẵn', 40.000),
  ('Trà Xoài Chanh Dây', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Xoài Chanh Dây', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Ổi Hồng Hạt Chia', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Ổi Hồng Hạt Chia', 'M', 'Nước Đường Pha Sẵn', 30.000),
  ('Trà Ổi Hồng Hạt Chia', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Ổi Hồng Hạt Chia', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Ổi Hồng Hạt Chia', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Ổi Hồng Hạt Chia', 'L', 'Nước Đường Pha Sẵn', 40.000),
  ('Trà Ổi Hồng Hạt Chia', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Ổi Hồng Hạt Chia', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Táo Xanh Bạc Hà', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Táo Xanh Bạc Hà', 'M', 'Nước Đường Pha Sẵn', 25.000),
  ('Trà Táo Xanh Bạc Hà', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Táo Xanh Bạc Hà', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Táo Xanh Bạc Hà', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Táo Xanh Bạc Hà', 'L', 'Nước Đường Pha Sẵn', 35.000),
  ('Trà Táo Xanh Bạc Hà', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Táo Xanh Bạc Hà', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Bưởi Mật Ong', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Bưởi Mật Ong', 'M', 'Nước Đường Pha Sẵn', 30.000),
  ('Trà Bưởi Mật Ong', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Bưởi Mật Ong', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Bưởi Mật Ong', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Bưởi Mật Ong', 'L', 'Nước Đường Pha Sẵn', 40.000),
  ('Trà Bưởi Mật Ong', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Bưởi Mật Ong', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Mãng Cầu Xiêm', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Mãng Cầu Xiêm', 'M', 'Nước Đường Pha Sẵn', 30.000),
  ('Trà Mãng Cầu Xiêm', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Mãng Cầu Xiêm', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Mãng Cầu Xiêm', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Mãng Cầu Xiêm', 'L', 'Nước Đường Pha Sẵn', 40.000),
  ('Trà Mãng Cầu Xiêm', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Mãng Cầu Xiêm', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Cà Phê Đen Đá', 'M', 'Nước Cốt Cà Phê Pha Phin', 60.000),
  ('Cà Phê Đen Đá', 'M', 'Đường Cát Trắng', 0.020),
  ('Cà Phê Đen Đá', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Cà Phê Đen Đá', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Cà Phê Đen Đá', 'L', 'Nước Cốt Cà Phê Pha Phin', 90.000),
  ('Cà Phê Đen Đá', 'L', 'Đường Cát Trắng', 0.030),
  ('Cà Phê Đen Đá', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Cà Phê Đen Đá', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Cà Phê Sữa Đá', 'M', 'Nước Cốt Cà Phê Pha Phin', 60.000),
  ('Cà Phê Sữa Đá', 'M', 'Sữa Đặc Có Đường', 0.040),
  ('Cà Phê Sữa Đá', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Cà Phê Sữa Đá', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Cà Phê Sữa Đá', 'L', 'Nước Cốt Cà Phê Pha Phin', 90.000),
  ('Cà Phê Sữa Đá', 'L', 'Sữa Đặc Có Đường', 0.055),
  ('Cà Phê Sữa Đá', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Cà Phê Sữa Đá', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Cà Phê Muối Xứ Huế', 'M', 'Nước Cốt Cà Phê Pha Phin', 60.000),
  ('Cà Phê Muối Xứ Huế', 'M', 'Sữa Đặc Có Đường', 0.030),
  ('Cà Phê Muối Xứ Huế', 'M', 'Sốt Kem Cheese Đánh Bông', 30.000),
  ('Cà Phê Muối Xứ Huế', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Cà Phê Muối Xứ Huế', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Cà Phê Muối Xứ Huế', 'L', 'Nước Cốt Cà Phê Pha Phin', 90.000),
  ('Cà Phê Muối Xứ Huế', 'L', 'Sữa Đặc Có Đường', 0.040),
  ('Cà Phê Muối Xứ Huế', 'L', 'Sốt Kem Cheese Đánh Bông', 45.000),
  ('Cà Phê Muối Xứ Huế', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Cà Phê Muối Xứ Huế', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Bạc Xỉu Ba Tầng', 'M', 'Nước Cốt Cà Phê Pha Phin', 40.000),
  ('Bạc Xỉu Ba Tầng', 'M', 'Sữa Đặc Có Đường', 0.035),
  ('Bạc Xỉu Ba Tầng', 'M', 'Sữa Tươi Thanh Trùng', 0.100),
  ('Bạc Xỉu Ba Tầng', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Bạc Xỉu Ba Tầng', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Bạc Xỉu Ba Tầng', 'L', 'Nước Cốt Cà Phê Pha Phin', 55.000),
  ('Bạc Xỉu Ba Tầng', 'L', 'Sữa Đặc Có Đường', 0.045),
  ('Bạc Xỉu Ba Tầng', 'L', 'Sữa Tươi Thanh Trùng', 0.150),
  ('Bạc Xỉu Ba Tầng', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Bạc Xỉu Ba Tầng', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Sữa Tươi Trân Châu Đường Đen', 'M', 'Sữa Tươi Thanh Trùng', 0.200),
  ('Sữa Tươi Trân Châu Đường Đen', 'M', 'Trân Châu Nấu Đường Đen', 60.000),
  ('Sữa Tươi Trân Châu Đường Đen', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Sữa Tươi Trân Châu Đường Đen', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Sữa Tươi Trân Châu Đường Đen', 'L', 'Sữa Tươi Thanh Trùng', 0.280),
  ('Sữa Tươi Trân Châu Đường Đen', 'L', 'Trân Châu Nấu Đường Đen', 80.000),
  ('Sữa Tươi Trân Châu Đường Đen', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Sữa Tươi Trân Châu Đường Đen', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'M', 'Sữa Tươi Thanh Trùng', 0.200),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'M', 'Thạch Sương Sáo Nấu Đông', 60.000),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'M', 'Nước Đường Pha Sẵn', 15.000),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'L', 'Sữa Tươi Thanh Trùng', 0.280),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'L', 'Thạch Sương Sáo Nấu Đông', 85.000),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'L', 'Nước Đường Pha Sẵn', 20.000),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Sữa Tươi Sương Sáo Thảo Mộc', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Ô Long Váng Sữa Macchiato', 'M', 'Nước Cốt Trà Ô Long', 150.000),
  ('Trà Ô Long Váng Sữa Macchiato', 'M', 'Sốt Kem Cheese Đánh Bông', 50.000),
  ('Trà Ô Long Váng Sữa Macchiato', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Ô Long Váng Sữa Macchiato', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Ô Long Váng Sữa Macchiato', 'L', 'Nước Cốt Trà Ô Long', 200.000),
  ('Trà Ô Long Váng Sữa Macchiato', 'L', 'Sốt Kem Cheese Đánh Bông', 70.000),
  ('Trà Ô Long Váng Sữa Macchiato', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Ô Long Váng Sữa Macchiato', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Đen Macchiato Muối Biển', 'M', 'Nước Cốt Trà Đen', 150.000),
  ('Trà Đen Macchiato Muối Biển', 'M', 'Sốt Kem Cheese Đánh Bông', 50.000),
  ('Trà Đen Macchiato Muối Biển', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Đen Macchiato Muối Biển', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Đen Macchiato Muối Biển', 'L', 'Nước Cốt Trà Đen', 200.000),
  ('Trà Đen Macchiato Muối Biển', 'L', 'Sốt Kem Cheese Đánh Bông', 70.000),
  ('Trà Đen Macchiato Muối Biển', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Đen Macchiato Muối Biển', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Matcha Đá Xay Kem Tuyết', 'M', 'Sữa Tươi Thanh Trùng', 0.150),
  ('Matcha Đá Xay Kem Tuyết', 'M', 'Sữa Đặc Có Đường', 0.035),
  ('Matcha Đá Xay Kem Tuyết', 'M', 'Sốt Kem Cheese Đánh Bông', 40.000),
  ('Matcha Đá Xay Kem Tuyết', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Matcha Đá Xay Kem Tuyết', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Matcha Đá Xay Kem Tuyết', 'L', 'Sữa Tươi Thanh Trùng', 0.200),
  ('Matcha Đá Xay Kem Tuyết', 'L', 'Sữa Đặc Có Đường', 0.045),
  ('Matcha Đá Xay Kem Tuyết', 'L', 'Sốt Kem Cheese Đánh Bông', 60.000),
  ('Matcha Đá Xay Kem Tuyết', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Matcha Đá Xay Kem Tuyết', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Cà Phê Đá Xay Caramel', 'M', 'Nước Cốt Cà Phê Pha Phin', 60.000),
  ('Cà Phê Đá Xay Caramel', 'M', 'Sữa Đặc Có Đường', 0.035),
  ('Cà Phê Đá Xay Caramel', 'M', 'Đường Đen Nữ Hoàng', 0.020),
  ('Cà Phê Đá Xay Caramel', 'M', 'Sữa Tươi Thanh Trùng', 0.100),
  ('Cà Phê Đá Xay Caramel', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Cà Phê Đá Xay Caramel', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Cà Phê Đá Xay Caramel', 'L', 'Nước Cốt Cà Phê Pha Phin', 90.000),
  ('Cà Phê Đá Xay Caramel', 'L', 'Sữa Đặc Có Đường', 0.045),
  ('Cà Phê Đá Xay Caramel', 'L', 'Đường Đen Nữ Hoàng', 0.025),
  ('Cà Phê Đá Xay Caramel', 'L', 'Sữa Tươi Thanh Trùng', 0.140),
  ('Cà Phê Đá Xay Caramel', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Cà Phê Đá Xay Caramel', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'M', 'Sữa Tươi Thanh Trùng', 0.150),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'M', 'Sữa Đặc Có Đường', 0.040),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'M', 'Nước Đường Pha Sẵn', 20.000),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'L', 'Sữa Tươi Thanh Trùng', 0.200),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'L', 'Sữa Đặc Có Đường', 0.055),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'L', 'Nước Đường Pha Sẵn', 30.000),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Sinh Tố Bơ Dừa Thơm Béo', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trà Hoa Cúc Mật Ong Hạt Sen', 'M', 'Nước Cốt Trà Lài', 150.000),
  ('Trà Hoa Cúc Mật Ong Hạt Sen', 'M', 'Nước Đường Pha Sẵn', 25.000),
  ('Trà Hoa Cúc Mật Ong Hạt Sen', 'M', 'Ly Nhựa PP 500ml', 1.000),
  ('Trà Hoa Cúc Mật Ong Hạt Sen', 'M', 'Ống Hút Trân Châu', 1.000),
  ('Trà Hoa Cúc Mật Ong Hạt Sen', 'L', 'Nước Cốt Trà Lài', 200.000),
  ('Trà Hoa Cúc Mật Ong Hạt Sen', 'L', 'Nước Đường Pha Sẵn', 35.000),
  ('Trà Hoa Cúc Mật Ong Hạt Sen', 'L', 'Ly Nhựa PP 700ml', 1.000),
  ('Trà Hoa Cúc Mật Ong Hạt Sen', 'L', 'Ống Hút Trân Châu', 1.000),
  ('Trân Châu Đen Hoàng Kim', 'M', 'Trân Châu Nấu Chín Dẻo', 50.000),
  ('Trân Châu Trắng 3Q', 'M', 'Hạt Trân Châu Trắng 3Q', 0.050),
  ('Thạch Đào Giòn Ngọt', 'M', 'Đào Ngâm Đóng Hộp', 0.050),
  ('Kem Cheese Phô Mai Macchiato', 'M', 'Sốt Kem Cheese Đánh Bông', 50.000),
  ('Pudding Trứng Vàng', 'M', 'Pudding Trứng Nấu Chín', 60.000),
  ('Thạch Phô Mai Tươi', 'M', 'Thạch Phô Mai Tươi Đúc Khuôn', 50.000),
  ('Sương Sáo Thảo Mộc Đài Loan', 'M', 'Thạch Sương Sáo Nấu Đông', 50.000),
  ('Thạch Củ Năng Giòn', 'M', 'Thạch Củ Năng Rim Đường', 50.000),
  ('Nha Đam Tươi Đường Phèn', 'M', 'Nha Đam Tươi', 0.050),
  ('Khúc Bạch Hạnh Nhân', 'M', 'Khúc Bạch Cắt Viên', 50.000)
)
INSERT INTO congthucmon (mamon, manl, dinhluong)
SELECT p.mamon, m.manl, d.quantity
FROM demo d
JOIN monuong p ON p.tenmon=d.item_name AND p.size=d.size_name
JOIN nguyenlieu m ON m.tennl=d.material_name
ON CONFLICT (mamon, manl) DO NOTHING;

-- 11: Dine-in tables: no active sessions by default.
INSERT INTO ban (tenban, maqr, trangthai, hoatdong, version)
SELECT 'Bàn ' || lpad(n::text, 2, '0'),
       'TABLE_QR_BAN' || lpad(n::text, 2, '0') || '_SECURE_TOKEN_XYZ' || n,
       'TRONG', true, 0
FROM generate_series(1, 10) n
ON CONFLICT (tenban) DO NOTHING;

-- 12: Delivery carrier + sample voucher.
INSERT INTO carrier (name, fee, active)
SELECT 'UTE Tea giao tiêu chuẩn', 15000, true
WHERE NOT EXISTS (SELECT 1 FROM carrier WHERE name='UTE Tea giao tiêu chuẩn');

INSERT INTO voucher (code, discountamount, discounttype, discountpercent,
                     minimumamount, startsat, endsat, usagelimit, usedcount, peraccountlimit, active)
VALUES ('GIAMGIA10', 0, 'PERCENT', 10, 0,
        '2026-01-01 00:00:00+00', '2032-12-31 23:59:59+00', 100000, 0, 1000, true)
ON CONFLICT (code) DO NOTHING;

COMMIT;

-- 13: Results (read only):
SELECT vaitro, count(*) AS total_accounts FROM taikhoan GROUP BY vaitro ORDER BY vaitro;
SELECT 'danhmuc' AS entity, count(*) FROM danhmuc UNION ALL
SELECT 'monuong', count(*) FROM monuong UNION ALL
SELECT 'nguyenlieu', count(*) FROM nguyenlieu UNION ALL
SELECT 'tonnguyenlieu', count(*) FROM tonnguyenlieu UNION ALL
SELECT 'congthucsoche', count(*) FROM congthucsoche UNION ALL
SELECT 'congthucmon', count(*) FROM congthucmon UNION ALL
SELECT 'shop', count(*) FROM shop UNION ALL
SELECT 'ban', count(*) FROM ban UNION ALL
SELECT 'voucher', count(*) FROM voucher;
