Các test Chromium này dùng API giả lập, không kết nối SQL Server.
Cài `pip install playwright` và `playwright install chromium`.
Có thể đặt biến môi trường `CHROMIUM_EXECUTABLE` nếu muốn chạy Chromium hệ thống.
Chạy `python tests/browser/test_cashier_flow_browser.py` và `python tests/browser/test_cashier_retry_browser.py`.
