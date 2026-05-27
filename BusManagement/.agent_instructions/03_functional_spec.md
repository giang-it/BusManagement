# CHỨC NĂNG CỦA BUS MANAGEMENT

## I. Admin

### 1. Quản lý danh mục (CRUD)
* **a) Quản lý xe:** Biển số, số ghế, loại xe, hãng xe, status (ready, traveling, repairing).
* **b) Quản lý tài xế:** Hồ sơ, bằng lái, thông tin liên lạc.
* **c) Quản lý tuyến đường:** Điểm đi, điểm đến, danh sách các trạm dừng, quãng đường.
* **d) Giá vé:** giá theo tuyến đường, loại xe, khung giờ.

### 2. Điều hành thông minh (Smart Scheduling)
* **a) Lập lịch chuyến xe:** Gán Xe + Tài xế + Tuyến đường + Khung giờ.
* **b) Kiểm tra ràng buộc tự động:**
  * **Tài xế:** Không cho phép gán tài xế nếu tổng giờ chạy trong 24h quá > 8 tiếng hoặc chưa nghỉ đủ 2 ngày/tháng. + nghỉ giữa chuyến.
  * **Xe:** Không gán xe đang trong lịch bảo trì.
* **c) Cơ chế "Tăng cường" chuyến:** Khi tỉ lệ lấp đầy ghế > 90%, hệ thống gợi ý Admin mở thêm một chuyến xe phụ cùng khung giờ.
* **d) Thay đổi:** thay vì admin phải tự gán tài và xe thì ta nên để AI tự gán luôn, thêm phụ xe và tài cho mỗi chuyến xe tăng cường. và phải có thời gian nghỉ cho tài và phụ xe.
* **e) Thêm:** nếu chuyến quá 8h thì cần 2 tài xế (là 3 người: 2 tài xế, 1 phụ xe).

### 3. Theo dõi bảo trì & An toàn
* **a) Cảnh báo bảo dưỡng:** Tự động liệt kê các xe đã chạy quá 5.000km kể từ lần bảo trì cuối.
* **b) Theo dõi lịch nghỉ:** Dashboard hiển thị danh sách tài xế đã nghỉ đủ/chưa đủ chỉ tiêu 2 ngày/tháng.

### 4. Thống kê & Báo cáo
* **a) Biểu đồ doanh thu** (theo ngày, tháng, năm).
* **b) Thống kê tuyến đường** đông khách nhất.
* **c) Báo cáo hiệu suất tài xế** (số giờ lái, đánh giá từ khách).

---

## II. Driver/ staff

* **1. Xem lịch trình cá nhân:** Danh sách các chuyến xe được phân công trong ngày/tuần.
* **2. Checkin bằng QR hàng khách**
* **3. Cập nhật trạng thái** (chuẩn bị chạy, đang chạy, đến trạm dừng, hoàn thành)
* **4. Quản lý nhật ký lái xe** (tg bắt đầu/ kết thúc ca làm) (ko làm quá 8h)
* **5. Báo cáo sự cố về trung tâm** (xe hỏng, vấn đề trên đường)

---

## III. Client/ user

* **1. Tìm kiếm chuyến xe** (theo điểm đi/ đến, ngày/ giờ khởi hành)
* **2. Đặt vé xe** (nhập thông tin cá nhân)
  * **2.1.** Chọn ghế, vị trí (ghế ngồi, giường nằm)
  * **2.2.** Thanh toán trực tuyến
* **3. Quản lý vé đã đặt:**
  * **3.1.** Xem lịch sử giao dịch, trạng thái (đã thanh toán, đã hủy, chưa thanh toán)
  * **3.2.** Hủy vé
  * **3.3.** Nhận email QR để checkin
* **4. Đánh giá, phản hồi**

---

## IV. Hệ thống tự động

* **1. Phân quyền (Security):** Sử dụng Spring Security để chia quyền (ROLE_ADMIN, ROLE_DRIVER, ROLE_USER).
* **2. Xử lý đặt ghế đồng thời:** Đảm bảo không có 2 khách cùng đặt 1 ghế trong cùng 1 giây (Locking).
* **3. Tự động hóa (Cron Job):** 00:00 hàng ngày hệ thống tự quét để reset giờ lái và cập nhật lịch bảo trì.
* **4. Thông báo (Notification):** Gửi Email/SMS tự động xác nhận đặt vé hoặc nhắc tài xế sắp đến giờ chạy.