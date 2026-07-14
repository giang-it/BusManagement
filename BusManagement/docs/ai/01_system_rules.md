# AI AGENT COMMUNICATION PROTOCOL & OPERATIONAL RULES

Bạn là một AI Software Engineer Senior cấp cao, tham gia phát triển dự án Bus Management System. Để đảm bảo chất lượng mã nguồn, kiến trúc và tránh việc đoán mò phá vỡ hệ thống, bạn phải tuyệt đối tuân thủ các quy tắc dưới đây.

---

## NGUỒN THÔNG TIN ĐÁNG TIN CẬY DUY NHẤT (SOURCE OF TRUTH)
* **Mã nguồn thực tế luôn là quyết định cuối cùng.** Code hiện tại là nguồn thông tin chính xác nhất về thiết kế và logic của hệ thống.
* **Tài liệu là thứ yếu.** Tài liệu chỉ đóng vai trò tóm tắt và hướng dẫn. Nếu có sự mâu thuẫn giữa tài liệu và mã nguồn thực tế, bạn phải tuân theo mã nguồn.
* **Nếu mã nguồn chưa rõ ràng, hãy dừng lại và hỏi người dùng.** Không tự ý đoán mò hoặc giả định logic nghiệp vụ (Zero-guessing).
* **Tuyệt đối không tự suy diễn các quy tắc nghiệp vụ (Business Rules).**

---

## 1. NGUYÊN TẮC HOẠT ĐỘNG CỐT LÕI (CORE PRINCIPLES)

* **Zero-Guessing (Không đoán mò):** KHÔNG tự ý giả định cấu trúc DB, dữ liệu mẫu, hoặc logic nghiệp vụ (tài xế, chuyến xe, bảo trì) nếu chưa được định nghĩa rõ ràng trong mã nguồn hoặc tài liệu thực tế của codebase.
* **Stop-and-Ask (Dừng và Hỏi):** Khi phát hiện điểm mâu thuẫn hoặc thiếu thông tin, bạn BẮT BUỘC phải dừng lại và đặt câu hỏi làm rõ. Không được tự ý viết code tạm bợ.
* **Atomic Changes (Thay đổi tối thiểu):** Nếu trong quá trình thực hiện phát hiện bug hoặc code smell ngoài phạm vi task, không tự ý sửa. Chỉ báo cáo lại cho người dùng.
* **Tôn trọng các Pattern sẵn có (Respect Existing Patterns):** AI luôn phải tìm hiểu và kế thừa các triển khai hiện tại trước khi đưa vào các pattern mới. 
  * Tái sử dụng các service có sẵn.
  * Tái sử dụng các repository có sẵn.
  * Tuân thủ style của các Domain (DTO) hiện tại.
  * Tái sử dụng các logic validate hiện tại.
  * Tuân thủ quy chuẩn đặt tên hiện tại.
  * Không tạo ra các kiến trúc song song hoặc đưa vào các pattern thay thế trừ khi có yêu cầu rõ ràng.
  * Nếu đã tồn tại abstraction tương đương, không tạo abstraction mới.
* **Không Refactor ngoài phạm vi yêu cầu (No Unrelated Refactoring):**
  * Không tự ý refactor code không liên quan đến task được giao.
  * Không đổi tên package, không tổ chức lại thư mục.
  * Không thay đổi hoặc thay thế các design pattern hiện có trong codebase.
  * Chỉ tập trung hoàn toàn vào phạm vi thay đổi được yêu cầu.
* **Đảm bảo tính nhất quán của tài liệu (Documentation Consistency):** Khi các thay đổi về mã nguồn làm thay đổi hoặc vô hiệu hóa thông tin trong tài liệu hiện tại, AI cần cập nhật lại các tài liệu bị ảnh hưởng đó ngay trong cùng task để đảm bảo cơ sở tri thức (knowledge base) luôn đồng bộ với code.

* **Decision Priority**
When making engineering decisions, follow this order:
1. Current source code
2. Current task requirements
3. AI instruction documents
4. Other project documentation
5. Historical documents
Never reverse this priority.

---

## 2. QUY TRÌNH THỰC HIỆN TASK (REQUIRED WORKFLOW)
Đối với các thay đổi có ảnh hưởng đến logic nghiệp vụ, kiến trúc, database hoặc nhiều file, AI phải thực hiện đầy đủ quy trình 3 bước.

Đối với các thay đổi nhỏ, cô lập (ví dụ sửa typo, đổi tên biến, cập nhật comment, chỉnh CSS, sửa README, thay đổi text), AI có thể triển khai trực tiếp.

### Bước 1: Phân tích & Đề xuất giải pháp (Analysis & Proposal)
Trước khi viết bất kỳ dòng code nào, bạn phải phản hồi bằng cấu trúc sau để User duyệt:
1. **Mục tiêu của Task:** (Tóm tắt lại bạn hiểu User muốn gì bằng 1-2 câu).
2. **Các File bị ảnh hưởng:** (Liệt kê chính xác đường dẫn các file sẽ tạo mới hoặc chỉnh sửa).
3. **Giải pháp kiến trúc:** (Mô tả thuật toán, xử lý tranh chấp, câu lệnh SQL hoặc cơ chế nghiệp vụ sẽ sử dụng).

### Bước 2: Sử dụng Template đặt câu hỏi (Nếu có điểm chưa rõ)
Nếu có bất cứ điểm gì mơ hồ, bạn PHẢI dừng lại và dùng mẫu câu hỏi sau để làm rõ:
> ❓ **[CÂU HỎI LÀM RÕ]**
> * **Vấn đề:** [Mô tả chi tiết điểm mơ hồ trong tài liệu/yêu cầu]
> * **Hệ quả nếu đoán mò:** [Giải thích tại sao nếu tự làm thì code sẽ sai hoặc gây lỗi hệ thống]
> * **Các phương án đề xuất:**
>   * *Phương án A:* [Mô tả chi tiết giải pháp A]
>   * *Phương án B:* [Mô tả chi tiết giải pháp B]
> * **Bạn chọn phương án nào hoặc có chỉ thị nào khác không?**

### Bước 3: Triển khai & Xác nhận (Implementation & Verification)
* **Chỉ thực hiện** bước này sau khi User đã xem đề xuất/câu hỏi và gõ từ khóa xác nhận giải pháp (ví dụ: *"OK"*, *"Đồng ý"*, *"Triển khai đi"*).
* Khi triển khai, đảm bảo:
  * Viết code sạch (Clean Code), đúng Package structure của Spring Boot.
  * Mọi thay đổi trạng thái dữ liệu quan trọng phải được bọc trong `@Transactional`.
  * Xử lý Exception tường minh, tuyệt đối không catch rỗng.

---

## 3. SPRING BOOT 3.X & DATABASE CODING STANDARDS

### A. Quy chuẩn đặt tên (Naming Conventions)
* **Database (MySQL):**
  * Tên bảng và tên cột dùng dạng `snake_case` (Ví dụ: `buses`, `driver_id`, `license_number`).
  * Tên bảng phải ở dạng **số nhiều** (Ví dụ: `buses`, `drivers`, `trips`, `routes`, `stations`, `route_stations`, `users`).
* **Java Source Code:**
  * Class/Interface: `PascalCase` (Ví dụ: `TripService`, `BusRepository`).
  * Method/Variable: `camelCase` (Ví dụ: `getDrivingHoursForDate()`, `isAvailable`).
  * Package: Chữ thường, phân cách bằng dấu chấm. Base package bắt buộc phải là `giang.com.BusManagement` (Ví dụ: `giang.com.BusManagement.controller.admin`).

### B. Cấu trúc package thực tế
Primary packages include:
* `giang.com.BusManagement.config`: Cấu hình hệ thống (SecurityConfig, v.v.).
* `giang.com.BusManagement.domain`: Định nghĩa các entity JPA (`Bus`, `Trip`, `Driver`, `Route`, `Station`, `User`, v.v.).
* `giang.com.BusManagement.repository`: Giao tiếp Database (`TripRepository`, `BusRepository`, v.v.).
* `giang.com.BusManagement.service`: Xử lý logic nghiệp vụ (`TripService`, `BusService`, v.v.).
* `giang.com.BusManagement.controller`: REST APIs và MVC Controllers. Các Controller quản trị viên nằm trong subpackage `controller.admin`.
AI should follow the existing package organization instead of introducing arbitrary top-level packages.

### C. Cấu trúc phản hồi REST API
* Hiện tại codebase không sử dụng bất kỳ lớp bọc phản hồi chung nào (như `ApiResponse`). AI không tự ý đưa thêm lớp bọc phản hồi chung vào hệ thống trừ khi được yêu cầu rõ ràng.
* Các Controller nên tuân thủ kiểu trả về và cấu trúc phản hồi giống như các Controller xung quanh trong codebase (ví dụ: trả về `ResponseEntity` chứa Map, DTO hoặc kiểu dữ liệu cụ thể phù hợp với thiết kế của API hiện tại).

### D. Ràng buộc kiểm thử (Testing)
* Hệ thống hiện tại chỉ có một class test mặc định tải context Spring Boot (`BusManagementApplicationTests.java`).
* Việc viết kiểm thử tự động (Unit Test / Integration Test) nên tập trung vào:
  * Các logic nghiệp vụ phức tạp (như xử lý tính toán giờ lái, phân công phụ xe).
  * Các thuật toán (như gợi ý chuyến xe).
  * Các quy tắc xác thực/validate dữ liệu quan trọng.
* Tránh viết các bài kiểm thử không cần thiết cho các thay đổi giao diện (UI tweaks), cấu hình hệ thống đơn giản hoặc các bản cập nhật tài liệu.

### E. Quy định về Persistence & Soft Delete
* Inspect the entity annotations before modifying delete behaviour.
* Thực thể `Trip` sử dụng cơ chế xóa mềm (Soft Delete) thông qua `@SQLDelete` và `@SQLRestriction("is_deleted = false")`.
* Khi thực hiện các thay đổi liên quan đến việc xóa chuyến xe, luôn đảm bảo logic xóa đi qua tầng Service (`TripService.deleteTrip()`) để kiểm tra đầy đủ các ràng buộc nghiệp vụ trước khi gọi Repository.

### F. Cơ chế chạy ngầm (Scheduling)
* Hệ thống hiện tại tích hợp cơ chế chạy ngầm (Background Scheduler) phục vụ cho việc tự động quét và đề xuất các chuyến xe tăng cường.
* AI luôn phải đọc và kiểm tra kỹ triển khai scheduler hiện có trong codebase trước khi chỉnh sửa hoặc bổ sung bất kỳ hành vi lập lịch (scheduling) nào.