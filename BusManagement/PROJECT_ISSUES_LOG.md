# BÁO CÁO ĐÁNH GIÁ VÀ DANH SÁCH LỖI DỰ ÁN (PROJECT REVIEW & ISSUES LOG)

Ngày đánh giá: 02/05/2026
Người đánh giá: Antigravity AI

---

## 1. Lỗi Logic Nghiệp Vụ (Critical Logic Issues) 

### 1.1. Vòng lặp vô hạn Chuyến tăng cường (Extra Trip Loop) (fixed)
- **Vấn đề:** `TripService.scanAndSuggestExtraTrips()` quét tất cả các chuyến `ACTIVE` có độ lấp đầy > 90%. Nếu một chuyến tăng cường (Extra Trip) cũng đạt > 90% vé, AI sẽ tiếp tục tạo thêm một chuyến tăng cường khác cho chính nó, dẫn đến vòng lặp vô hạn.
- **đã kiểm tra:** done
- **tôi kết luận** vấn đề này, đúng là nếu chuyến tăng cường sau khi được duyệt và đạt 90% vé thì sẽ tiếp tục tạo thêm một chuyến tăng cường khác cho chính nó, dẫn đến vòng lặp vô hạn. nhưng theo thực tế nếu chuyến tăng cường này vẫn là chuyến hot thì vẫn phải tăng cường đúng không?. hay tôi đang hiểu sai

### 1.2. Phân công tài xế cho chuyến siêu dài (> 8h) không hợp lệ
- **Vấn đề:** Chuyến đi dài (VD: Hà Nội - Sài Gòn 30 tiếng) nhưng hệ thống chỉ gán 1 tài xế chính và 1 phụ xe. Logic validate hiện tại chỉ tính 8h hiệu dụng (`Math.min(duration, 8.0)`), cho phép 1 người chạy suốt 30h. Điều này vi phạm luật an toàn giao thông và không thực tế.
- **đã kiểm tra:** - not yet, sẽ phát triển sau

### 1.3. Thiếu Validate thời gian tại Backend (fixed)
- **Vấn đề:** Admin có thể tạo chuyến xe với thời gian đến trước thời gian khởi hành. Backend (`AdminTripManagementController`) không kiểm tra logic này, dẫn đến sai lệch trong tính toán giờ lái (ra số âm).
- **đã kiểm tra:** done
- **tôi kết luận:** vấn đề này, đúng như lỗi miêu tả, nó như vậy cả ở trong lúc sửa chứ ko riêng lúc tạo

### 1.4. Cho phép sửa số ghế nhỏ hơn số vé đã bán (fixed)
- **Vấn đề:** Admin có thể cập nhật `totalSeats` thấp hơn số lượng `ticketsSold` hiện tại mà không bị chặn.
- **đã kiểm tra:** done
- **tôi kết luận:** vấn đề này, đúng như lỗi miêu tả


---

## 2. Bảo Mật & Nhất Quán Dữ Liệu (Security & Consistency)

### 2.1. Mật khẩu người dùng lưu dạng Plain Text
- **Vấn đề:** Mật khẩu chưa được mã hóa BCrypt. Phần mã hóa trong `AdminService` đang bị comment lại.`.

### 2.2. Xử lý Exception chưa tối ưu
- **Vấn đề:** Bắt lỗi chung `catch (Exception e)` và hiển thị trực tiếp ra UI. Điều này có thể lộ thông tin nhạy cảm của Database hoặc Stack trace dài dòng gây khó chịu cho người dùng.

### 2.3. Cập nhật chuyến xe bỏ sót thuộc tính:
- **Vấn đề:** Ở updateTrip(), hàm này copy từng field từ DTO sang Entity hiện tại nhưng lại không update ticketsSold, và không có @Version (Optimistic Locking). Nếu 1 khách hàng đang mua vé cùng lúc Admin đang bấm cập nhật, số lượng vé đã bán có thể bị ghi đè/sai lệch.

---

## 3. Giao Diện & Điều Hướng (UI/UX)

### 3.1. Các liên kết Dashboard gây lỗi 404
- **Vấn đề:** Các nút "Quản Lý Trạm/Bến Xe" và "Quản Lý Loại Xe" trỏ đến các endpoint `/admin/master-data/...` không tồn tại trong Controller.

---

## 4. Dư thừa & Code Smells (Redundancy)

### 4.1. Hardcode hằng số cấu hình
- **Vấn đề:** Các tham số như `MIN_REST_BETWEEN_TRIPS_MINUTES` (30p) và `BUS_PREP_BUFFER_HOURS` (1h) đang được fix cứng trong mã nguồn.

### 4.2. Dư thừa định nghĩa Column
- **Vấn đề:** `ticketsSold` đã được gán mặc định bằng 0 trong Java nhưng vẫn khai báo `columnDefinition = "INT DEFAULT 0"` trong Hibernate.

### 4.3. Dư thừa kiểm tra Validation ở Controller: 
- **Vấn đề:** Ở AdminTripManagementController, logic ném Exception cho Route, Bus, Driver (như "Không tìm thấy chuyến") đang bị lặp lại trước khi gọi xuống Service. Bạn có thể chuyển hẳn việc tìm kiếm ID này vào trong TripService để Controller sạch sẽ và ngắn gọn hơn.


## 5. tôi tự thấy lỗi
### 5.1. không kiểm tra ngày khi tính tổng số giờ lái (fixed)
- **Vấn đề:** khi admin tạo chuyến mới, trong lúc gán tài xế, hệ thống có kiểm tra nếu lái tổng quá số giờ lái sẽ báo lỗi nhưng dường như bỏ qua ngày, ví dụ hôm nay tx1 đã lái 4h thì nếu gán tiếp chuyến chạy 7h thì sẽ báo lỗi nhưng nếu để chạy vào ngày khác thì hệ thống vẫn báo lỗi đã lái 4h, mặc dù ngày đó chưa lái giờ nào  
### 5.2. không cảnh báo xe sát bảo trì
- **Vấn đề:** khi sửa chyến hoặc tạo chuyến, nếu gán xe sát bảo trì thì hệ thống ko cảnh báo 
### 5.3. số ghế của xe vượt ngưỡng
- **Vấn đề:** không có bất kỳ kiểm tra số ghế vượt ngưỡng trong lúc sửa hoặc tạo chuyến (tôi không chắc đã có tính năng gán số ghế khi tạo xe chưa, ví dụ limosine ghi 22 ghế lúc tạo, như vậy db có thực sự lưu 22 ghế không hay chỉ là ghi tạm)như vậy nếu khi sửa hoặc tạo chuyến nếu để số ghế vượt ngưỡng của xe thì sao?
### 5.4. sửa chuyến, không cảnh báo nếu gán tài xế quá giờ lái
- **Vấn đề:** 