-- Demo marketplace seed for Supabase SQL Editor.
-- This file is intentionally placed in db/seed, not db/migration, so it will not run automatically.
-- It creates/updates demo vendors from existing auth.users IDs, approves them, assigns plans,
-- creates the category tree shown in the seller category selector, and inserts active products.

DO $$
DECLARE
    v_now timestamptz := now();
    v_item jsonb;
    v_user_id uuid;
    v_vendor_id bigint;
    v_category_id bigint;
    v_brand_id bigint;
    v_product_id bigint;
    v_attr_id bigint;
    v_value_id bigint;
    v_variant_id bigint;
    v_slug text;

    v_vendors jsonb := '[
      {
        "user_id": "074c7cc8-8986-4b55-92b0-c83805c29941",
        "email": "duc@gmail.com",
        "phone": "0901000001",
        "full_name": "Duc Nguyen",
        "shop_name": "TechNova Premium",
        "plan": "premium",
        "category": "Điện thoại & Đồ điện tử",
        "tax_code": "TNP-2026-001",
        "cccd": "079200000001",
        "description": "Shop Premium chuyên điện thoại, phụ kiện và thiết bị thông minh chính hãng, có kiểm tra chất lượng trước khi giao.",
        "logo_url": "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=500&q=80"
      },
      {
        "user_id": "092e87f0-13dc-444a-9976-8173dd533772",
        "email": "phat280405@gmail.com",
        "phone": "0901000002",
        "full_name": "Phat Nguyen",
        "shop_name": "FutureGear Premium",
        "plan": "premium",
        "category": "Máy tính & Thiết bị Văn phòng",
        "tax_code": "FGP-2026-002",
        "cccd": "079200000002",
        "description": "Gian hàng Premium về laptop, linh kiện PC, thiết bị văn phòng và phụ kiện gaming.",
        "logo_url": "https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=500&q=80"
      },
      {
        "user_id": "a9777f65-f473-4971-b4e9-0e75fc1e52a8",
        "email": "ducdu@gmail.com",
        "phone": "0901000003",
        "full_name": "Duc Du",
        "shop_name": "GadgetPlus Hub",
        "plan": "plus",
        "category": "Điện thoại & Đồ điện tử",
        "tax_code": "GPH-2026-003",
        "cccd": "079200000003",
        "description": "Shop Plus tập trung phụ kiện điện thoại, camera, âm thanh và thiết bị giải trí gia đình.",
        "logo_url": "https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=500&q=80"
      },
      {
        "user_id": "77440a45-4ba3-475c-88f4-0964309e228f",
        "email": "ducduy.luong22@gmail.com",
        "phone": "0901000004",
        "full_name": "Duc Duy Luong",
        "shop_name": "MediaWorks Store",
        "plan": "plus",
        "category": "TV & Thiết bị giải trí",
        "tax_code": "MWS-2026-004",
        "cccd": "079200000004",
        "description": "Shop Plus chuyên TV, máy chiếu, đầu phát trực tuyến và thiết bị âm thanh phòng khách.",
        "logo_url": "https://images.unsplash.com/photo-1593305841991-05c297ba4575?auto=format&fit=crop&w=500&q=80"
      },
      {
        "user_id": "7adbaed0-8199-4409-990d-ba016a1af7b7",
        "email": "ducduy12345@gmail.com",
        "phone": "0901000005",
        "full_name": "Duc Duy Tran",
        "shop_name": "FreeTech Corner",
        "plan": "free",
        "category": "Thiết bị mạng",
        "tax_code": "FTC-2026-005",
        "cccd": "079200000005",
        "description": "Shop Free thử nghiệm đăng sản phẩm mạng và phụ kiện công nghệ phổ thông.",
        "logo_url": "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=500&q=80"
      },
      {
        "user_id": "587e6d87-f686-4926-a494-a6add42e4a2b",
        "email": "ducduy123456@gmail.com",
        "phone": "0901000006",
        "full_name": "Duc Duy Pham",
        "shop_name": "HomeNet Free",
        "plan": "free",
        "category": "Máy tính & Thiết bị Văn phòng",
        "tax_code": "HNF-2026-006",
        "cccd": "079200000006",
        "description": "Shop Free bán thử các sản phẩm lưu trữ, chuột bàn phím và thiết bị làm việc tại nhà.",
        "logo_url": "https://images.unsplash.com/photo-1516321497487-e288fb19713f?auto=format&fit=crop&w=500&q=80"
      }
    ]'::jsonb;

    v_categories jsonb := '[
      {"name":"Điện thoại & Đồ điện tử","slug":"dien-thoai-do-dien-tu","parent_slug":null},
      {"name":"Phụ kiện điện thoại","slug":"phu-kien-dien-thoai","parent_slug":"dien-thoai-do-dien-tu"},
      {"name":"Ốp lưng & Bao da","slug":"op-lung-bao-da","parent_slug":"phu-kien-dien-thoai"},
      {"name":"Sạc & Cáp điện thoại","slug":"sac-cap-dien-thoai","parent_slug":"phu-kien-dien-thoai"},
      {"name":"Kính cường lực","slug":"kinh-cuong-luc","parent_slug":"phu-kien-dien-thoai"},
      {"name":"Pin dự phòng","slug":"pin-du-phong","parent_slug":"phu-kien-dien-thoai"},
      {"name":"Tai nghe có dây","slug":"tai-nghe-co-day","parent_slug":"phu-kien-dien-thoai"},
      {"name":"Đế sạc không dây","slug":"de-sac-khong-day","parent_slug":"phu-kien-dien-thoai"},
      {"name":"Camera & Nhiếp ảnh","slug":"camera-nhiep-anh","parent_slug":"dien-thoai-do-dien-tu"},
      {"name":"Máy ảnh compact","slug":"may-anh-compact","parent_slug":"camera-nhiep-anh"},
      {"name":"Máy ảnh mirrorless","slug":"may-anh-mirrorless","parent_slug":"camera-nhiep-anh"},
      {"name":"Máy ảnh DSLR","slug":"may-anh-dslr","parent_slug":"camera-nhiep-anh"},
      {"name":"Camera hành động","slug":"camera-hanh-dong","parent_slug":"camera-nhiep-anh"},
      {"name":"Drone / Máy bay không người lái","slug":"drone-may-bay-khong-nguoi-lai","parent_slug":"camera-nhiep-anh"},
      {"name":"Phụ kiện camera","slug":"phu-kien-camera","parent_slug":"camera-nhiep-anh"},
      {"name":"Âm thanh & Video","slug":"am-thanh-video","parent_slug":"dien-thoai-do-dien-tu"},
      {"name":"Loa Bluetooth","slug":"loa-bluetooth","parent_slug":"am-thanh-video"},
      {"name":"Loa để bàn","slug":"loa-de-ban","parent_slug":"am-thanh-video"},
      {"name":"Tai nghe Bluetooth","slug":"tai-nghe-bluetooth","parent_slug":"am-thanh-video"},
      {"name":"Soundbar","slug":"soundbar","parent_slug":"am-thanh-video"},
      {"name":"Micro & Thu âm","slug":"micro-thu-am","parent_slug":"am-thanh-video"},
      {"name":"Chơi game & Bảng điều khiển","slug":"choi-game-bang-dieu-khien","parent_slug":"dien-thoai-do-dien-tu"},
      {"name":"Bảng điều khiển trò chơi video","slug":"bang-dieu-khien-tro-choi-video","parent_slug":"choi-game-bang-dieu-khien"},
      {"name":"Bảng điều khiển cầm tay","slug":"bang-dieu-khien-cam-tay","parent_slug":"choi-game-bang-dieu-khien"},
      {"name":"Trò chơi điện tử","slug":"tro-choi-dien-tu","parent_slug":"choi-game-bang-dieu-khien"},
      {"name":"Phụ kiện bảng điều khiển","slug":"phu-kien-bang-dieu-khien","parent_slug":"choi-game-bang-dieu-khien"},
      {"name":"Thiết bị thông minh & Thiết bị đeo","slug":"thiet-bi-thong-minh-thiet-bi-deo","parent_slug":"dien-thoai-do-dien-tu"},
      {"name":"Đồng hồ thông minh","slug":"dong-ho-thong-minh","parent_slug":"thiet-bi-thong-minh-thiet-bi-deo"},
      {"name":"Vòng đeo sức khỏe","slug":"vong-deo-suc-khoe","parent_slug":"thiet-bi-thong-minh-thiet-bi-deo"},
      {"name":"Thiết bị nhà thông minh","slug":"thiet-bi-nha-thong-minh","parent_slug":"thiet-bi-thong-minh-thiet-bi-deo"},
      {"name":"Đèn thông minh","slug":"den-thong-minh","parent_slug":"thiet-bi-thong-minh-thiet-bi-deo"},
      {"name":"Điện thoại & Máy tính bảng","slug":"dien-thoai-may-tinh-bang","parent_slug":"dien-thoai-do-dien-tu"},
      {"name":"Điện thoại thông minh","slug":"dien-thoai-thong-minh","parent_slug":"dien-thoai-may-tinh-bang"},
      {"name":"Máy tính bảng","slug":"may-tinh-bang","parent_slug":"dien-thoai-may-tinh-bang"},
      {"name":"Điện thoại phổ thông","slug":"dien-thoai-pho-thong","parent_slug":"dien-thoai-may-tinh-bang"},

      {"name":"Máy tính & Thiết bị Văn phòng","slug":"may-tinh-thiet-bi-van-phong","parent_slug":null},
      {"name":"Máy tính để bàn, Laptop & Máy tính bảng","slug":"may-tinh-de-ban-laptop-may-tinh-bang","parent_slug":"may-tinh-thiet-bi-van-phong"},
      {"name":"Máy tính để bàn","slug":"may-tinh-de-ban","parent_slug":"may-tinh-de-ban-laptop-may-tinh-bang"},
      {"name":"Máy tính xách tay","slug":"may-tinh-xach-tay","parent_slug":"may-tinh-de-ban-laptop-may-tinh-bang"},
      {"name":"Máy tính bảng (PC)","slug":"may-tinh-bang-pc","parent_slug":"may-tinh-de-ban-laptop-may-tinh-bang"},
      {"name":"Phụ kiện máy tính","slug":"phu-kien-may-tinh","parent_slug":"may-tinh-thiet-bi-van-phong"},
      {"name":"Bàn phím","slug":"ban-phim","parent_slug":"phu-kien-may-tinh"},
      {"name":"Chuột máy tính","slug":"chuot-may-tinh","parent_slug":"phu-kien-may-tinh"},
      {"name":"Màn hình","slug":"man-hinh","parent_slug":"phu-kien-may-tinh"},
      {"name":"Tai nghe Gaming","slug":"tai-nghe-gaming","parent_slug":"phu-kien-may-tinh"},
      {"name":"Webcam","slug":"webcam","parent_slug":"phu-kien-may-tinh"},
      {"name":"Loa máy tính","slug":"loa-may-tinh","parent_slug":"phu-kien-may-tinh"},
      {"name":"Lưu trữ","slug":"luu-tru","parent_slug":"may-tinh-thiet-bi-van-phong"},
      {"name":"Ổ cứng HDD","slug":"o-cung-hdd","parent_slug":"luu-tru"},
      {"name":"Ổ cứng SSD","slug":"o-cung-ssd","parent_slug":"luu-tru"},
      {"name":"USB Flash Drive","slug":"usb-flash-drive","parent_slug":"luu-tru"},
      {"name":"Thẻ nhớ","slug":"the-nho","parent_slug":"luu-tru"},
      {"name":"NAS / Network Storage","slug":"nas-network-storage","parent_slug":"luu-tru"},
      {"name":"Linh kiện máy tính","slug":"linh-kien-may-tinh","parent_slug":"may-tinh-thiet-bi-van-phong"},
      {"name":"CPU / Bộ vi xử lý","slug":"cpu-bo-vi-xu-ly","parent_slug":"linh-kien-may-tinh"},
      {"name":"Mainboard / Bo mạch chủ","slug":"mainboard-bo-mach-chu","parent_slug":"linh-kien-may-tinh"},
      {"name":"RAM","slug":"ram","parent_slug":"linh-kien-may-tinh"},
      {"name":"Card đồ họa (GPU)","slug":"card-do-hoa-gpu","parent_slug":"linh-kien-may-tinh"},
      {"name":"Nguồn máy tính (PSU)","slug":"nguon-may-tinh-psu","parent_slug":"linh-kien-may-tinh"},
      {"name":"Tản nhiệt","slug":"tan-nhiet","parent_slug":"linh-kien-may-tinh"},

      {"name":"Thiết bị mạng","slug":"thiet-bi-mang","parent_slug":null},
      {"name":"Router & Access Point","slug":"router-access-point","parent_slug":"thiet-bi-mang"},
      {"name":"Router WiFi","slug":"router-wifi","parent_slug":"router-access-point"},
      {"name":"Access Point","slug":"access-point","parent_slug":"router-access-point"},
      {"name":"Mesh WiFi System","slug":"mesh-wifi-system","parent_slug":"router-access-point"},
      {"name":"Switch & Hub mạng","slug":"switch-hub-mang","parent_slug":"thiet-bi-mang"},
      {"name":"Network Switch","slug":"network-switch","parent_slug":"switch-hub-mang"},
      {"name":"KVM Switch","slug":"kvm-switch","parent_slug":"switch-hub-mang"},

      {"name":"TV & Thiết bị giải trí","slug":"tv-thiet-bi-giai-tri","parent_slug":null},
      {"name":"Smart TV","slug":"smart-tv","parent_slug":"tv-thiet-bi-giai-tri"},
      {"name":"Android TV","slug":"android-tv","parent_slug":"smart-tv"},
      {"name":"QLED TV","slug":"qled-tv","parent_slug":"smart-tv"},
      {"name":"OLED TV","slug":"oled-tv","parent_slug":"smart-tv"},
      {"name":"Đầu phát trực tuyến","slug":"dau-phat-truc-tuyen","parent_slug":"tv-thiet-bi-giai-tri"},
      {"name":"Android TV Box","slug":"android-tv-box","parent_slug":"dau-phat-truc-tuyen"},
      {"name":"Google Chromecast","slug":"google-chromecast","parent_slug":"dau-phat-truc-tuyen"},
      {"name":"Amazon Fire Stick","slug":"amazon-fire-stick","parent_slug":"dau-phat-truc-tuyen"},
      {"name":"Máy chiếu","slug":"may-chieu","parent_slug":"tv-thiet-bi-giai-tri"},
      {"name":"Máy chiếu mini","slug":"may-chieu-mini","parent_slug":"may-chieu"},
      {"name":"Máy chiếu chuẩn","slug":"may-chieu-chuan","parent_slug":"may-chieu"}
    ]'::jsonb;

    v_products jsonb := '[
      {"vendor_email":"duc@gmail.com","category_slug":"dien-thoai-thong-minh","brand":"Apple","name":"iPhone 15 Pro Max 256GB Titanium tự nhiên","slug":"seed-iphone-15-pro-max-256gb-titanium","price":28990000,"stock":16,"image":"https://images.unsplash.com/photo-1695048133142-1a20484d2569?auto=format&fit=crop&w=900&q=80","description":"Điện thoại cao cấp với khung titanium, camera chuyên nghiệp, màn hình sáng và hiệu năng mạnh cho công việc lẫn giải trí."},
      {"vendor_email":"duc@gmail.com","category_slug":"dien-thoai-thong-minh","brand":"Samsung","name":"Samsung Galaxy S24 Ultra 512GB đen titan","slug":"seed-samsung-galaxy-s24-ultra-512gb","price":26990000,"stock":14,"image":"https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=900&q=80","description":"Flagship Android màn hình lớn, bút S Pen, camera zoom xa và pin bền cho người dùng cần một thiết bị đa nhiệm."},
      {"vendor_email":"duc@gmail.com","category_slug":"may-tinh-bang","brand":"Apple","name":"iPad Air M2 11 inch WiFi 128GB","slug":"seed-ipad-air-m2-11-inch-128gb","price":16990000,"stock":20,"image":"https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=900&q=80","description":"Máy tính bảng mỏng nhẹ cho học tập, ghi chú, thiết kế nhanh và làm việc linh hoạt với chip M2."},
      {"vendor_email":"duc@gmail.com","category_slug":"dong-ho-thong-minh","brand":"Apple","name":"Apple Watch Series 9 GPS 45mm","slug":"seed-apple-watch-series-9-45mm","price":9490000,"stock":22,"image":"https://images.unsplash.com/photo-1434493789847-2f02dc6ca35d?auto=format&fit=crop&w=900&q=80","description":"Đồng hồ thông minh theo dõi sức khỏe, luyện tập, nhận thông báo và hỗ trợ hệ sinh thái iPhone."},
      {"vendor_email":"duc@gmail.com","category_slug":"vong-deo-suc-khoe","brand":"Xiaomi","name":"Xiaomi Smart Band 8 Pro chính hãng","slug":"seed-xiaomi-smart-band-8-pro","price":1690000,"stock":35,"image":"https://images.unsplash.com/photo-1576243345690-4e4b79b63288?auto=format&fit=crop&w=900&q=80","description":"Vòng đeo sức khỏe màn hình lớn, theo dõi nhịp tim, giấc ngủ, vận động và pin nhiều ngày."},
      {"vendor_email":"duc@gmail.com","category_slug":"op-lung-bao-da","brand":"UAG","name":"Ốp lưng chống sốc MagSafe cho iPhone 15 Pro Max","slug":"seed-op-lung-magsafe-iphone-15-pro-max","price":690000,"stock":80,"image":"https://images.unsplash.com/photo-1601593346740-925612772716?auto=format&fit=crop&w=900&q=80","description":"Ốp lưng viền chống sốc, tương thích MagSafe, bảo vệ cạnh máy và giữ cảm giác cầm chắc tay."},
      {"vendor_email":"duc@gmail.com","category_slug":"sac-cap-dien-thoai","brand":"Anker","name":"Củ sạc nhanh Anker Nano 30W USB-C","slug":"seed-anker-nano-30w-usb-c","price":420000,"stock":90,"image":"https://images.unsplash.com/photo-1615526675159-e248c3021d3f?auto=format&fit=crop&w=900&q=80","description":"Củ sạc nhỏ gọn hỗ trợ sạc nhanh cho điện thoại, tablet và phụ kiện USB-C."},
      {"vendor_email":"duc@gmail.com","category_slug":"pin-du-phong","brand":"Anker","name":"Pin dự phòng Anker PowerCore 20000mAh PD","slug":"seed-anker-powercore-20000mah-pd","price":1190000,"stock":42,"image":"https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?auto=format&fit=crop&w=900&q=80","description":"Pin dự phòng dung lượng cao, hỗ trợ sạc nhanh PD, phù hợp đi làm, đi học và du lịch."},
      {"vendor_email":"duc@gmail.com","category_slug":"tai-nghe-bluetooth","brand":"Sony","name":"Sony WF-1000XM5 chống ồn chủ động","slug":"seed-sony-wf-1000xm5","price":5490000,"stock":18,"image":"https://images.unsplash.com/photo-1606220945770-b5b6c2c55bf1?auto=format&fit=crop&w=900&q=80","description":"Tai nghe true wireless chống ồn, âm thanh chi tiết, đàm thoại rõ và hộp sạc nhỏ gọn."},
      {"vendor_email":"duc@gmail.com","category_slug":"loa-bluetooth","brand":"JBL","name":"JBL Flip 6 loa Bluetooth chống nước","slug":"seed-jbl-flip-6-bluetooth","price":2490000,"stock":30,"image":"https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?auto=format&fit=crop&w=900&q=80","description":"Loa di động âm bass chắc, chống nước IP67, thích hợp nghe nhạc ngoài trời và trong phòng."},
      {"vendor_email":"duc@gmail.com","category_slug":"de-sac-khong-day","brand":"Samsung","name":"Đế sạc không dây Samsung Duo 15W","slug":"seed-samsung-wireless-charger-duo-15w","price":1090000,"stock":24,"image":"https://images.unsplash.com/photo-1615526675159-e248c3021d3f?auto=format&fit=crop&w=900&q=80","description":"Đế sạc không dây cho điện thoại và đồng hồ, thiết kế gọn trên bàn làm việc."},
      {"vendor_email":"duc@gmail.com","category_slug":"den-thong-minh","brand":"Philips","name":"Bóng đèn thông minh Philips Hue White & Color","slug":"seed-philips-hue-white-color","price":890000,"stock":26,"image":"https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80","description":"Đèn thông minh đổi màu, điều khiển bằng ứng dụng, phù hợp trang trí phòng ngủ và góc làm việc."},

      {"vendor_email":"phat280405@gmail.com","category_slug":"may-tinh-xach-tay","brand":"Apple","name":"MacBook Air 13 inch M3 16GB 512GB","slug":"seed-macbook-air-m3-16gb-512gb","price":32990000,"stock":12,"image":"https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=900&q=80","description":"Laptop mỏng nhẹ, pin lâu, màn hình đẹp và hiệu năng mạnh cho lập trình, thiết kế và học tập."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"may-tinh-xach-tay","brand":"Dell","name":"Dell XPS 13 Plus OLED Intel Core Ultra 7","slug":"seed-dell-xps-13-plus-oled-ultra-7","price":35990000,"stock":8,"image":"https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=900&q=80","description":"Laptop Windows cao cấp với màn hình OLED, thân máy nhôm và hiệu năng văn phòng mạnh."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"may-tinh-de-ban","brand":"ASUS","name":"PC Gaming ASUS ROG RTX 4070 Super","slug":"seed-pc-gaming-asus-rog-rtx-4070-super","price":45990000,"stock":6,"image":"https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=900&q=80","description":"Máy tính để bàn gaming cấu hình cao, tối ưu chơi game 2K, dựng video và stream."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"man-hinh","brand":"LG","name":"Màn hình LG UltraGear 27 inch 2K 165Hz","slug":"seed-lg-ultragear-27-2k-165hz","price":6990000,"stock":18,"image":"https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=900&q=80","description":"Màn hình gaming 2K tần số quét cao, màu sắc tốt, chân đế linh hoạt cho góc máy hiện đại."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"ban-phim","brand":"Logitech","name":"Logitech MX Mechanical Mini Wireless","slug":"seed-logitech-mx-mechanical-mini","price":2890000,"stock":28,"image":"https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=900&q=80","description":"Bàn phím cơ không dây nhỏ gọn, gõ êm, kết nối nhiều thiết bị và pin lâu."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"chuot-may-tinh","brand":"Logitech","name":"Logitech MX Master 3S chuột không dây","slug":"seed-logitech-mx-master-3s","price":2390000,"stock":35,"image":"https://images.unsplash.com/photo-1527814050087-3793815479db?auto=format&fit=crop&w=900&q=80","description":"Chuột công thái học cho làm việc chuyên nghiệp, cuộn siêu nhanh, cảm biến chính xác."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"o-cung-ssd","brand":"Samsung","name":"Samsung 990 Pro SSD NVMe 2TB","slug":"seed-samsung-990-pro-ssd-2tb","price":4990000,"stock":22,"image":"https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=900&q=80","description":"SSD NVMe tốc độ cao cho PC, laptop và workstation cần tải game, phần mềm, dữ liệu nhanh."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"ram","brand":"Kingston","name":"Kingston Fury Beast RGB 32GB DDR5 6000MHz","slug":"seed-kingston-fury-beast-rgb-32gb-ddr5","price":3590000,"stock":20,"image":"https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=900&q=80","description":"Bộ nhớ DDR5 hiệu năng cao, có RGB, phù hợp máy gaming và dựng nội dung."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"card-do-hoa-gpu","brand":"ASUS","name":"ASUS Dual GeForce RTX 4060 Ti OC 8GB","slug":"seed-asus-dual-rtx-4060-ti-oc-8gb","price":11990000,"stock":10,"image":"https://images.unsplash.com/photo-1591488320449-011701bb6704?auto=format&fit=crop&w=900&q=80","description":"Card đồ họa tầm trung mạnh cho gaming Full HD, 2K và tăng tốc xử lý đồ họa."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"cpu-bo-vi-xu-ly","brand":"Intel","name":"Intel Core i7-14700K chính hãng","slug":"seed-intel-core-i7-14700k","price":10990000,"stock":12,"image":"https://images.unsplash.com/photo-1591799265444-d66432b91588?auto=format&fit=crop&w=900&q=80","description":"CPU hiệu năng cao cho gaming, xử lý đa nhiệm, dựng video và công việc nặng."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"webcam","brand":"Logitech","name":"Logitech Brio 4K Webcam họp trực tuyến","slug":"seed-logitech-brio-4k-webcam","price":3890000,"stock":15,"image":"https://images.unsplash.com/photo-1587825140708-dfaf72ae4b04?auto=format&fit=crop&w=900&q=80","description":"Webcam 4K cho họp online, livestream và lớp học trực tuyến với hình ảnh sắc nét."},
      {"vendor_email":"phat280405@gmail.com","category_slug":"tan-nhiet","brand":"Cooler Master","name":"Tản nhiệt nước Cooler Master ML240L V2 RGB","slug":"seed-cooler-master-ml240l-v2-rgb","price":2190000,"stock":14,"image":"https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=900&q=80","description":"Tản nhiệt nước AIO 240mm, hiệu năng ổn định và ánh sáng RGB cho PC gaming."},

      {"vendor_email":"ducdu@gmail.com","category_slug":"kinh-cuong-luc","brand":"Spigen","name":"Kính cường lực iPhone 15 Pro Max full màn","slug":"seed-kinh-cuong-luc-iphone-15-pro-max","price":190000,"stock":120,"image":"https://images.unsplash.com/photo-1601593346740-925612772716?auto=format&fit=crop&w=900&q=80","description":"Kính cường lực trong suốt, phủ chống bám vân tay, bảo vệ màn hình khỏi trầy xước."},
      {"vendor_email":"ducdu@gmail.com","category_slug":"tai-nghe-co-day","brand":"Sony","name":"Tai nghe có dây Sony MDR-EX155AP","slug":"seed-sony-mdr-ex155ap","price":390000,"stock":60,"image":"https://images.unsplash.com/photo-1484704849700-f032a568e944?auto=format&fit=crop&w=900&q=80","description":"Tai nghe có dây nhỏ gọn, micro đàm thoại, âm thanh cân bằng cho nghe nhạc hàng ngày."},
      {"vendor_email":"ducdu@gmail.com","category_slug":"may-anh-compact","brand":"Canon","name":"Canon PowerShot G7 X Mark III","slug":"seed-canon-powershot-g7x-mark-iii","price":17990000,"stock":7,"image":"https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=900&q=80","description":"Máy ảnh compact cho vlog, du lịch và quay video chất lượng cao trong thân máy nhỏ."},
      {"vendor_email":"ducdu@gmail.com","category_slug":"may-anh-mirrorless","brand":"Sony","name":"Sony Alpha A6400 kèm lens 16-50mm","slug":"seed-sony-alpha-a6400-16-50","price":21990000,"stock":6,"image":"https://images.unsplash.com/photo-1502920917128-1aa500764cbd?auto=format&fit=crop&w=900&q=80","description":"Máy ảnh mirrorless lấy nét nhanh, phù hợp chụp chân dung, sản phẩm và quay video."},
      {"vendor_email":"ducdu@gmail.com","category_slug":"camera-hanh-dong","brand":"GoPro","name":"GoPro HERO12 Black chống nước","slug":"seed-gopro-hero12-black","price":9990000,"stock":10,"image":"https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=900&q=80","description":"Camera hành động quay 5.3K, chống nước, chống rung mạnh cho du lịch và thể thao."},
      {"vendor_email":"ducdu@gmail.com","category_slug":"drone-may-bay-khong-nguoi-lai","brand":"DJI","name":"DJI Mini 4 Pro Fly More Combo","slug":"seed-dji-mini-4-pro-fly-more","price":22990000,"stock":5,"image":"https://images.unsplash.com/photo-1508614589041-895b88991e3e?auto=format&fit=crop&w=900&q=80","description":"Drone gọn nhẹ, quay 4K, cảm biến tránh vật cản và bộ phụ kiện bay lâu hơn."},
      {"vendor_email":"ducdu@gmail.com","category_slug":"phu-kien-camera","brand":"Peak Design","name":"Dây đeo máy ảnh Peak Design Slide Lite","slug":"seed-peak-design-slide-lite","price":1290000,"stock":18,"image":"https://images.unsplash.com/photo-1502920917128-1aa500764cbd?auto=format&fit=crop&w=900&q=80","description":"Dây đeo máy ảnh bền, tháo lắp nhanh, phù hợp mirrorless và máy ảnh du lịch."},
      {"vendor_email":"ducdu@gmail.com","category_slug":"micro-thu-am","brand":"Rode","name":"Rode NT-USB Mini micro thu âm podcast","slug":"seed-rode-nt-usb-mini","price":2690000,"stock":13,"image":"https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=900&q=80","description":"Micro USB nhỏ gọn, âm thanh rõ, dùng cho podcast, livestream và họp trực tuyến."},
      {"vendor_email":"ducdu@gmail.com","category_slug":"bang-dieu-khien-cam-tay","brand":"Nintendo","name":"Nintendo Switch OLED White","slug":"seed-nintendo-switch-oled-white","price":7490000,"stock":12,"image":"https://images.unsplash.com/photo-1578303512597-81e6cc155b3e?auto=format&fit=crop&w=900&q=80","description":"Máy chơi game cầm tay màn hình OLED, chơi linh hoạt tại nhà hoặc khi di chuyển."},
      {"vendor_email":"ducdu@gmail.com","category_slug":"tro-choi-dien-tu","brand":"Nintendo","name":"Game The Legend of Zelda Tears of the Kingdom","slug":"seed-zelda-tears-of-the-kingdom-game","price":1290000,"stock":20,"image":"https://images.unsplash.com/photo-1493711662062-fa541adb3fc8?auto=format&fit=crop&w=900&q=80","description":"Băng game phiêu lưu hành động cho Nintendo Switch, bản vật lý mới nguyên seal."},

      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"soundbar","brand":"Samsung","name":"Samsung Soundbar Q-Series 5.1ch","slug":"seed-samsung-soundbar-q-series-5-1","price":8990000,"stock":9,"image":"https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=900&q=80","description":"Soundbar âm thanh vòm cho phòng khách, kết nối không dây và tối ưu xem phim."},
      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"loa-de-ban","brand":"Edifier","name":"Edifier R1280DBs loa bookshelf Bluetooth","slug":"seed-edifier-r1280dbs-bookshelf","price":2990000,"stock":16,"image":"https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=900&q=80","description":"Loa để bàn âm thanh ấm, có Bluetooth và ngõ vào quang học cho PC, TV."},
      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"android-tv","brand":"Sony","name":"Sony Bravia Android TV 55 inch 4K","slug":"seed-sony-bravia-android-tv-55-4k","price":18990000,"stock":7,"image":"https://images.unsplash.com/photo-1593305841991-05c297ba4575?auto=format&fit=crop&w=900&q=80","description":"Smart TV Android 4K màu sắc đẹp, kho ứng dụng phong phú và điều khiển giọng nói."},
      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"qled-tv","brand":"Samsung","name":"Samsung QLED 65 inch Q70D 4K","slug":"seed-samsung-qled-65-q70d-4k","price":24990000,"stock":5,"image":"https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?auto=format&fit=crop&w=900&q=80","description":"TV QLED 65 inch độ sáng cao, màu sắc rực rỡ, phù hợp xem phim và thể thao."},
      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"oled-tv","brand":"LG","name":"LG OLED evo C4 55 inch 4K","slug":"seed-lg-oled-evo-c4-55-4k","price":32990000,"stock":4,"image":"https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?auto=format&fit=crop&w=900&q=80","description":"TV OLED độ tương phản sâu, màu đen tuyệt đối và tần số quét cao cho gaming."},
      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"android-tv-box","brand":"Xiaomi","name":"Xiaomi TV Box S Gen 2 4K","slug":"seed-xiaomi-tv-box-s-gen-2-4k","price":1490000,"stock":25,"image":"https://images.unsplash.com/photo-1601944179066-29786cb9d32a?auto=format&fit=crop&w=900&q=80","description":"Android TV Box 4K nhỏ gọn, nâng cấp TV thường thành smart TV với nhiều ứng dụng."},
      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"google-chromecast","brand":"Google","name":"Google Chromecast with Google TV 4K","slug":"seed-google-chromecast-google-tv-4k","price":1690000,"stock":18,"image":"https://images.unsplash.com/photo-1601944179066-29786cb9d32a?auto=format&fit=crop&w=900&q=80","description":"Thiết bị phát trực tuyến 4K, điều khiển tiện lợi và giao diện Google TV."},
      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"amazon-fire-stick","brand":"Amazon","name":"Amazon Fire TV Stick 4K Max","slug":"seed-amazon-fire-tv-stick-4k-max","price":1590000,"stock":17,"image":"https://images.unsplash.com/photo-1601944179066-29786cb9d32a?auto=format&fit=crop&w=900&q=80","description":"Đầu phát trực tuyến nhỏ gọn hỗ trợ 4K, HDR và điều khiển bằng giọng nói."},
      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"may-chieu-mini","brand":"Anker","name":"Nebula Capsule 3 máy chiếu mini di động","slug":"seed-nebula-capsule-3-mini-projector","price":13990000,"stock":6,"image":"https://images.unsplash.com/photo-1512149673953-1e251807ec7c?auto=format&fit=crop&w=900&q=80","description":"Máy chiếu mini có pin, loa tích hợp, phù hợp xem phim ngoài trời và phòng nhỏ."},
      {"vendor_email":"ducduy.luong22@gmail.com","category_slug":"may-chieu-chuan","brand":"Epson","name":"Epson EB-FH52 máy chiếu Full HD","slug":"seed-epson-eb-fh52-full-hd-projector","price":17990000,"stock":5,"image":"https://images.unsplash.com/photo-1512149673953-1e251807ec7c?auto=format&fit=crop&w=900&q=80","description":"Máy chiếu Full HD độ sáng cao cho lớp học, phòng họp và trình chiếu gia đình."},

      {"vendor_email":"ducduy12345@gmail.com","category_slug":"router-wifi","brand":"TP-Link","name":"TP-Link Archer AX55 WiFi 6 Router","slug":"seed-tp-link-archer-ax55-wifi-6","price":1890000,"stock":20,"image":"https://images.unsplash.com/photo-1606904825846-647eb07f5be2?auto=format&fit=crop&w=900&q=80","description":"Router WiFi 6 tốc độ cao, phủ sóng tốt cho căn hộ và gia đình nhiều thiết bị."},
      {"vendor_email":"ducduy12345@gmail.com","category_slug":"access-point","brand":"Ubiquiti","name":"UniFi U6+ Access Point WiFi 6","slug":"seed-unifi-u6-plus-access-point","price":3290000,"stock":8,"image":"https://images.unsplash.com/photo-1606904825846-647eb07f5be2?auto=format&fit=crop&w=900&q=80","description":"Access Point WiFi 6 gắn trần, quản lý tập trung, phù hợp văn phòng nhỏ và quán cà phê."},
      {"vendor_email":"ducduy12345@gmail.com","category_slug":"network-switch","brand":"TP-Link","name":"TP-Link TL-SG108 Switch Gigabit 8 cổng","slug":"seed-tp-link-tl-sg108-switch-8-port","price":590000,"stock":30,"image":"https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=900&q=80","description":"Switch Gigabit 8 cổng vỏ kim loại, cắm là chạy, ổn định cho mạng gia đình."},

      {"vendor_email":"ducduy123456@gmail.com","category_slug":"usb-flash-drive","brand":"Kingston","name":"USB Kingston DataTraveler Exodia 128GB","slug":"seed-kingston-datatraveler-exodia-128gb","price":190000,"stock":70,"image":"https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=900&q=80","description":"USB 128GB gọn nhẹ, lưu tài liệu, hình ảnh và dữ liệu học tập dễ dàng."},
      {"vendor_email":"ducduy123456@gmail.com","category_slug":"the-nho","brand":"SanDisk","name":"Thẻ nhớ SanDisk Extreme microSD 256GB","slug":"seed-sandisk-extreme-microsd-256gb","price":690000,"stock":45,"image":"https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=900&q=80","description":"Thẻ nhớ tốc độ cao cho camera hành động, điện thoại và máy chơi game cầm tay."},
      {"vendor_email":"ducduy123456@gmail.com","category_slug":"tai-nghe-gaming","brand":"HyperX","name":"HyperX Cloud II Gaming Headset","slug":"seed-hyperx-cloud-ii-gaming-headset","price":1890000,"stock":18,"image":"https://images.unsplash.com/photo-1599669454699-248893623440?auto=format&fit=crop&w=900&q=80","description":"Tai nghe gaming đeo thoải mái, micro tháo rời, âm thanh rõ cho chơi game và họp nhóm."}
    ]'::jsonb;
BEGIN
    -- Supabase imports/manual inserts can leave identity sequences behind existing IDs.
    -- Sync every identity sequence touched by this seed before inserting rows.
    PERFORM setval(pg_get_serial_sequence('public.categories', 'id'), COALESCE((SELECT MAX(id) FROM public.categories), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.vendors', 'id'), COALESCE((SELECT MAX(id) FROM public.vendors), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.vendor_subscription_plans', 'id'), COALESCE((SELECT MAX(id) FROM public.vendor_subscription_plans), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.vendor_bank_accounts', 'id'), COALESCE((SELECT MAX(id) FROM public.vendor_bank_accounts), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.brands', 'id'), COALESCE((SELECT MAX(id) FROM public.brands), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.products', 'id'), COALESCE((SELECT MAX(id) FROM public.products), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.product_media', 'id'), COALESCE((SELECT MAX(id) FROM public.product_media), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.product_attributes', 'id'), COALESCE((SELECT MAX(id) FROM public.product_attributes), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.product_attribute_values', 'id'), COALESCE((SELECT MAX(id) FROM public.product_attribute_values), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.product_variants', 'id'), COALESCE((SELECT MAX(id) FROM public.product_variants), 0) + 1, false);
    PERFORM setval(pg_get_serial_sequence('public.variant_attribute_values', 'id'), COALESCE((SELECT MAX(id) FROM public.variant_attribute_values), 0) + 1, false);

    FOR v_item IN SELECT value FROM jsonb_array_elements(v_vendors)
    LOOP
        v_user_id := (v_item->>'user_id')::uuid;

        IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = v_user_id) THEN
            RAISE NOTICE 'Skip vendor %, missing auth.users id %', v_item->>'email', v_user_id;
            CONTINUE;
        END IF;

        INSERT INTO public.profiles (
            id, email, phone, full_name, avatar_url, role, is_active, date_of_birth, created_at
        )
        VALUES (
            v_user_id,
            v_item->>'email',
            v_item->>'phone',
            v_item->>'full_name',
            v_item->>'logo_url',
            'vendor',
            true,
            DATE '1998-01-01',
            v_now
        )
        ON CONFLICT (id) DO UPDATE
        SET
            full_name = EXCLUDED.full_name,
            avatar_url = EXCLUDED.avatar_url,
            role = 'vendor',
            is_active = true;

        INSERT INTO public.vendors (
            user_id, shop_name, description, logo_url, email, phone, avg_rating, created_at,
            cccd, tax_code, status, cccd_front_image_url, cccd_back_image_url, face_image_url, category
        )
        VALUES (
            v_user_id,
            v_item->>'shop_name',
            v_item->>'description',
            v_item->>'logo_url',
            v_item->>'email',
            v_item->>'phone',
            4.80,
            v_now,
            v_item->>'cccd',
            v_item->>'tax_code',
            'approved',
            'https://placehold.co/900x560/eff6ff/1e3a8a?text=CCCD+Front',
            'https://placehold.co/900x560/f8fafc/334155?text=CCCD+Back',
            v_item->>'logo_url',
            v_item->>'category'
        )
        ON CONFLICT (user_id) DO UPDATE
        SET
            shop_name = EXCLUDED.shop_name,
            description = EXCLUDED.description,
            logo_url = EXCLUDED.logo_url,
            email = EXCLUDED.email,
            phone = EXCLUDED.phone,
            avg_rating = EXCLUDED.avg_rating,
            cccd = EXCLUDED.cccd,
            tax_code = EXCLUDED.tax_code,
            status = 'approved',
            cccd_front_image_url = EXCLUDED.cccd_front_image_url,
            cccd_back_image_url = EXCLUDED.cccd_back_image_url,
            face_image_url = EXCLUDED.face_image_url,
            category = EXCLUDED.category;

        SELECT id INTO v_vendor_id FROM public.vendors WHERE user_id = v_user_id;

        INSERT INTO public.vendor_subscription_plans (
            vendor_id, plan_type, total_slots, used_slots, started_at, expires_at, is_active
        )
        VALUES (
            v_vendor_id,
            v_item->>'plan',
            CASE v_item->>'plan' WHEN 'premium' THEN -1 WHEN 'plus' THEN 20 ELSE 3 END,
            0,
            v_now,
            v_now + INTERVAL '30 days',
            true
        )
        ON CONFLICT (vendor_id) DO UPDATE
        SET
            plan_type = EXCLUDED.plan_type,
            total_slots = EXCLUDED.total_slots,
            started_at = EXCLUDED.started_at,
            expires_at = EXCLUDED.expires_at,
            is_active = true;

        INSERT INTO public.vendor_bank_accounts (
            vendor_id, bank_name, bank_account_name, bank_account_number
        )
        SELECT
            v_vendor_id,
            'Vietcombank',
            v_item->>'shop_name',
            '9704' || right(regexp_replace(v_user_id::text, '[^0-9]', '', 'g'), 10)
        WHERE NOT EXISTS (
            SELECT 1
            FROM public.vendor_bank_accounts
            WHERE vendor_id = v_vendor_id
        );
    END LOOP;

    FOR v_item IN SELECT value FROM jsonb_array_elements(v_categories)
    LOOP
        INSERT INTO public.categories (name, parent_id, slug, image_url, is_active)
        VALUES (
            v_item->>'name',
            (
                SELECT id
                FROM public.categories
                WHERE slug = NULLIF(v_item->>'parent_slug', '')
            ),
            v_item->>'slug',
            'https://placehold.co/600x400/f8fafc/334155?text=' || replace(v_item->>'name', ' ', '+'),
            true
        )
        ON CONFLICT (slug) DO UPDATE
        SET
            name = EXCLUDED.name,
            parent_id = EXCLUDED.parent_id,
            image_url = EXCLUDED.image_url,
            is_active = true;
    END LOOP;

    FOR v_item IN SELECT value FROM jsonb_array_elements(v_products)
    LOOP
        SELECT v.id
        INTO v_vendor_id
        FROM public.vendors v
        WHERE v.email = v_item->>'vendor_email'
          AND v.status = 'approved'
        ORDER BY v.id
        LIMIT 1;

        IF v_vendor_id IS NULL THEN
            RAISE NOTICE 'Skip product %, missing approved vendor %', v_item->>'name', v_item->>'vendor_email';
            CONTINUE;
        END IF;

        SELECT id INTO v_category_id
        FROM public.categories
        WHERE slug = v_item->>'category_slug';

        IF v_category_id IS NULL THEN
            RAISE NOTICE 'Skip product %, missing category %', v_item->>'name', v_item->>'category_slug';
            CONTINUE;
        END IF;

        INSERT INTO public.brands (name, logo_url, is_active)
        VALUES (
            v_item->>'brand',
            'https://placehold.co/400x200/ffffff/111827?text=' || replace(v_item->>'brand', ' ', '+'),
            true
        )
        ON CONFLICT (name) DO UPDATE
        SET logo_url = EXCLUDED.logo_url, is_active = true
        RETURNING id INTO v_brand_id;

        v_slug := v_item->>'slug';

        INSERT INTO public.products (
            vendor_id, category_id, name, slug, description, sold_count, avg_rating, created_at,
            brand_id, status, contains_dangerous_goods, warranty_type, origin_country, condition,
            parcel_weight_g, parcel_width, parcel_length, parcel_height, delivery_method, updated_at,
            is_active, reject_reason
        )
        VALUES (
            v_vendor_id,
            v_category_id,
            v_item->>'name',
            v_slug,
            v_item->>'description',
            0,
            4.70,
            v_now,
            v_brand_id,
            'active',
            'no',
            'official',
            'Việt Nam',
            'new',
            900,
            20,
            25,
            12,
            'default',
            v_now,
            true,
            NULL
        )
        ON CONFLICT (slug) DO UPDATE
        SET
            vendor_id = EXCLUDED.vendor_id,
            category_id = EXCLUDED.category_id,
            name = EXCLUDED.name,
            description = EXCLUDED.description,
            avg_rating = EXCLUDED.avg_rating,
            brand_id = EXCLUDED.brand_id,
            status = 'active',
            contains_dangerous_goods = EXCLUDED.contains_dangerous_goods,
            warranty_type = EXCLUDED.warranty_type,
            origin_country = EXCLUDED.origin_country,
            condition = EXCLUDED.condition,
            parcel_weight_g = EXCLUDED.parcel_weight_g,
            parcel_width = EXCLUDED.parcel_width,
            parcel_length = EXCLUDED.parcel_length,
            parcel_height = EXCLUDED.parcel_height,
            delivery_method = EXCLUDED.delivery_method,
            updated_at = v_now,
            is_active = true,
            reject_reason = NULL
        RETURNING id INTO v_product_id;

        DELETE FROM public.variant_attribute_values
        WHERE variant_id IN (
            SELECT id FROM public.product_variants WHERE product_id = v_product_id
        );

        DELETE FROM public.product_variants WHERE product_id = v_product_id;
        DELETE FROM public.product_attribute_values
        WHERE attribute_id IN (
            SELECT id FROM public.product_attributes WHERE product_id = v_product_id
        );
        DELETE FROM public.product_attributes WHERE product_id = v_product_id;
        DELETE FROM public.product_media WHERE product_id = v_product_id;

        INSERT INTO public.product_media (product_id, media_url, is_main, media_type, sort_order)
        VALUES
            (v_product_id, v_item->>'image', true, 'image', 0),
            (v_product_id, 'https://placehold.co/900x900/f8fafc/334155?text=' || replace(v_item->>'brand', ' ', '+'), false, 'image', 1);

        INSERT INTO public.product_attributes (product_id, name, sort_order)
        VALUES (v_product_id, 'Tình trạng', 0)
        RETURNING id INTO v_attr_id;

        INSERT INTO public.product_attribute_values (attribute_id, value, image_url, sort_order)
        VALUES (v_attr_id, 'Mới 100%', v_item->>'image', 0)
        RETURNING id INTO v_value_id;

        INSERT INTO public.product_variants (
            product_id, sku, price, stock, is_active, seller_sku, combination_key,
            discount_percent, parcel_weight_g, image_url
        )
        VALUES (
            v_product_id,
            upper(replace(v_slug, '-', '_')) || '_STD',
            (v_item->>'price')::numeric,
            (v_item->>'stock')::integer,
            true,
            upper(replace(v_slug, '-', '_')) || '_SELLER',
            'Mới 100%',
            0,
            900,
            v_item->>'image'
        )
        RETURNING id INTO v_variant_id;

        INSERT INTO public.variant_attribute_values (variant_id, attribute_value_id)
        VALUES (v_variant_id, v_value_id);
    END LOOP;

    UPDATE public.vendor_subscription_plans plan
    SET used_slots = CASE
        WHEN plan.plan_type = 'premium' THEN 0
        ELSE LEAST(plan.total_slots, product_counts.active_count)
    END
    FROM (
        SELECT vendor_id, count(*)::integer AS active_count
        FROM public.products
        WHERE status = 'active'
          AND slug LIKE 'seed-%'
        GROUP BY vendor_id
    ) product_counts
    WHERE plan.vendor_id = product_counts.vendor_id;

    RAISE NOTICE 'Demo marketplace seed completed.';
END $$;
