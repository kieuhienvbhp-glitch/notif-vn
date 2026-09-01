# AutoBankPay Pro (Ứng dụng Android)

Ứng dụng đọc thông báo (Notification Listener) trên Android, chuyên dùng để bắt biến động số dư từ các ứng dụng Ngân hàng / Ví điện tử và tự động gửi dữ liệu về Website của bạn (WooCommerce) qua Webhook để duyệt đơn hàng.

## Tính năng chính

- **Bắt thông báo siêu tốc:** Sử dụng `NotificationListenerService` chạy ngầm, không bỏ sót bất kỳ thông báo nhận tiền nào.
- **Lọc ứng dụng thông minh:** Cho phép chọn chính xác ứng dụng Ngân hàng (MB Bank, Vietcombank, Momo, v.v.) để đọc thông báo, bỏ qua các thông báo rác.
- **Tích hợp Webhook mạnh mẽ:** Hỗ trợ đẩy dữ liệu qua API (POST) về website WordPress/WooCommerce để tự động duyệt đơn hàng.
- **Quản lý hàng đợi (Queue):** Không sợ mất dữ liệu khi mất mạng. Ứng dụng tự động lưu lại lịch sử và gửi bù lại khi có mạng (công nghệ Room và WorkManager).
- **Tự khởi động:** Khôi phục trạng thái hoạt động chạy ngầm ngay sau khi bạn khởi động lại điện thoại.
- **Giao diện 100% Tiếng Việt:** Dễ hiểu, dễ cài đặt cho người Việt.

## Hướng dẫn cài đặt (Rất Quan trọng)

Để ứng dụng không bị hệ điều hành tự động tắt ngầm, vui lòng làm theo các bước sau:

1. Mở ứng dụng, tại trang chủ.
2. Bấm nút **Cấp quyền xem thông báo**: Tìm ứng dụng `AutoBankPay Pro` và bật công tắc cho phép.
3. Bấm nút **Cấp quyền chạy ngầm (Pin)**: Cấp quyền không hạn chế chạy ngầm (Unrestricted / No restriction).
4. *(Đặc biệt trên Xiaomi, Oppo, Vivo)*: Vào cài đặt ứng dụng của máy, bật quyền **Tự động khởi chạy (Auto Start)** và khóa ứng dụng trong trình đa nhiệm (chức năng ổ khóa).

## Kết nối với Website (WordPress/WooCommerce)

Tải và cài đặt plugin `auto-bank-pay-pro.zip` lên website của bạn.

Trên điện thoại, cấu hình Webhook như sau:
- **URL Webhook:** `https://ten-website-cua-ban.com/wp-json/autobank/v1/webhook`
- **Phương thức HTTP:** `POST`
- **Xác thực:** Chọn Bearer Token và nhập Token khớp với cài đặt trong Plugin WordPress.

## Giấy phép

Dự án này được phát hành dưới Giấy phép MIT.
Xem tệp [LICENSE](LICENSE) để biết chi tiết.
