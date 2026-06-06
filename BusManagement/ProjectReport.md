
| Số lượng | Danh mục đánh giá |
| :---- | :---- |
| **4** | Lỗi logic nghiêm trọng |
| **5** | Cảnh báo thiết kế |
| **3** | Thiếu nhất quán |
| **4** | Điểm tốt |

## **🔴 Lỗi logic nghiêm trọng**

### **Bug \#1: BusStatus.TRAVELING không bao giờ được cập nhật tự động**

* **Mô tả:** Enum BusStatus có giá trị TRAVELING nhưng không có code nào tự động chuyển xe sang trạng thái này khi trip chuyển sang DEPARTED. Trạng thái TRAVELING chỉ được kiểm tra trong validate (validateBusForTrip) nhưng chưa bao giờ được gán. Hậu quả: xe được gán vào trip mới dù đang chạy, hoặc kiểm tra isBusBusy() sẽ miss các xe đang chạy thực sự.  
* **Vị trí code:** TripService.java – updateTripStatus()  
* **Giải pháp xử lý:** → Sửa: khi trip chuyển sang DEPARTED, set bus.status \= TRAVELING; khi COMPLETED, set bus.status \= READY

### **Bug \#2: coDrivers lưu User nhưng validate lấy Driver — type mismatch nghiêm trọng**

* **Mô tả:** Trip.coDrivers là List\<User\> (ManyToMany với User). Khi kiểm tra trùng lịch existsOverlappingTripForDriver(), query dùng cd \= :user (đúng). Nhưng trong getDrivingHoursForDate(), code lại check cd.getId().equals(driver.getUserId()) — cd là User (có getId()), driver.getUserId() là Long. Điều này hoạt động nhưng dễ nhầm lẫn vì coDrivers không nhất quán với driver và assistant (cả hai là Driver).  
* **Vị trí code:** Trip.java – coDrivers field / TripService.java – getDrivingHoursForDate()  
* **Giải pháp xử lý:** → Đổi coDrivers thành List\<Driver\> để nhất quán với driver và assistant, hoặc giữ nguyên nhưng document rõ lý do

### **Bug \#3: cancelTrip() trong AdminTripManagementController bypass FSM**

* **Mô tả:** Hàm cancelTrip() trực tiếp set trip.setStatus(CANCELLED) và gọi tripRepository.save() mà không qua TripService.updateTripStatus(). FSM canTransition() định nghĩa rõ COMPLETED → CANCELLED là không hợp lệ, nhưng route này bypass hoàn toàn, cho phép hủy ngay cả chuyến đã hoàn thành.  
* **Vị trí code:** AdminTripManagementController.java – cancelTrip()  
* **Giải pháp xử lý:** → Thay bằng tripService.updateTripStatus(id, TripStatus.CANCELLED) để FSM được thực thi

### **Bug \#4: BusType.getSuitableBusType() bị duplicate y hệt Route.getSuitableBusType()**

* **Mô tả:** BusType có field suitableBusType (ManyToOne tự tham chiếu) và method getSuitableBusType(). Tuy nhiên logic "loại xe phù hợp cho tuyến" hoàn toàn là trách nhiệm của Route, không phải BusType. Hơn nữa Route đã có field suitableBusType riêng. Đây là thiết kế thừa, gây nhầm lẫn.  
* **Vị trí code:** BusType.java – suitableBusType field \+ getSuitableBusType()  
* **Giải pháp xử lý:** → Xóa field suitableBusType và method trùng trong BusType, chỉ giữ tại Route

## **🟡 Cảnh báo thiết kế**

### **Warn \#1: Station được tạo nhưng không liên kết với Route trong DataInitializer**

* **Mô tả:** 7 station được tạo (Hà Nội, Hải Phòng, Sài Gòn...) nhưng RouteStation không có bản ghi nào được seed. RouteStationRepository được inject vào DataInitializer nhưng chưa được dùng. Kết quả: bảng route\_stations luôn rỗng, feature "xem trạm dừng của tuyến" không có dữ liệu test.  
* **Vị trí code:** DataInitializer.java – run()  
* **Giải pháp xử lý:** → Seed RouteStation với stopOrder cho mỗi tuyến, ví dụ tuyến HN-HP có 2 trạm: stopOrder 1 và 2

### **Warn \#2: createManualTrip() set status ACTIVE ngay, bỏ qua flow PENDING\_APPROVAL**

* **Mô tả:** Khi Admin tạo trip thủ công, createManualTrip() hardcode trip.setStatus(TripStatus.ACTIVE). Trong khi đó, controller trước đó đã set trip.setStatus(TripStatus.ACTIVE) rồi truyền vào service. Vừa validate nghiêm ngặt (bằng lái, giờ lái, xung đột lịch) vừa skip PENDING\_APPROVAL là không nhất quán với flow chuyến AI đề xuất.  
* **Vị trí code:** TripService.java – createManualTrip() / AdminTripManagementController.java  
* **Giải pháp xử lý:** → Quyết định rõ: trip thủ công có qua PENDING\_APPROVAL không? Nếu không thì remove bước set status trong controller, chỉ set trong service

### **Warn \#3: findBestAvailableBus() dùng READY filter nhưng TRAVELING xe vẫn có thể conflict**

* **Mô tả:** findBestAvailableBus() chỉ query xe READY. Nhưng do Bug \#1, xe TRAVELING không được update status. Nếu một xe READY đang được dùng trong trip DEPARTED (nhưng status vẫn là READY vì không ai update), nó vẫn lọt qua, và chỉ bị chặn bởi isBusBusy(). Đây là may mắn chứ không phải thiết kế đúng.  
* **Vị trí code:** TripService.java – findBestAvailableBus()  
* **Giải pháp xử lý:** → Sau khi sửa Bug \#1, logic này sẽ chuẩn hơn. Hiện tại nên thêm assert hoặc log cảnh báo

### **Warn \#4: AdminTripManagementController inject trực tiếp Repository — vi phạm layered architecture**

* **Mô tả:** Controller inject TripRepository, RouteRepository, BusRepository, DriverRepository trực tiếp và gọi tripRepository.findAllWithDetails(), tripRepository.deleteById()... Điều này bypass Service layer, không được kiểm tra bởi business logic (validate, FSM).  
* **Vị trí code:** AdminTripManagementController.java  
* **Giải pháp xử lý:** → Chuyển các thao tác DB vào TripService, controller chỉ gọi service method

### **Warn \#5: ddl-auto=create: mỗi lần restart xóa toàn bộ dữ liệu**

* **Mô tả:** spring.jpa.hibernate.ddl-auto=create trong application.properties kết hợp với DataInitializer.deleteAll() đảm bảo mỗi lần start lại xóa sạch. Điều này phù hợp cho demo nhưng dễ gây mất dữ liệu ngoài ý muốn nếu deploy lên môi trường thực.  
* **Vị trí code:** application.properties  
* **Giải pháp xử lý:** → Dùng profile riêng: dev dùng create, prod dùng validate hoặc none

## **🔵 Thiếu nhất quán**

### **Incon \#1: Route dùng String cho điểm đi/đến, không dùng Station entity**

* **Mô tả:** Route có departurePoint và destinationPoint là String, trong khi hệ thống đã có entity Station. DataInitializer tạo cả hai nhưng chúng hoàn toàn tách rời. Tên trong Route ("Hà Nội", "Sài Gòn") không khớp với stationName ("Bến xe Mỹ Đình", "Bến xe Miền Đông").  
* **Vị trí code:** Route.java vs Station.java / DataInitializer.java  
* **Giải pháp xử lý:** → Thêm departureStation và destinationStation (ManyToOne Station) vào Route, hoặc ít nhất đồng nhất String names

### **Incon \#2: hasAlreadySuggested() không check DEPARTED và COMPLETED**

* **Mô tả:** Hàm kiểm tra tránh tạo chuyến tăng cường trùng chỉ check PENDING\_APPROVAL, ACTIVE, CANCELLED. Không check DEPARTED và COMPLETED. Nghĩa là nếu chuyến tăng cường đã DEPARTED, AI sẽ tạo thêm một chuyến tăng cường thứ hai cho cùng chuyến gốc.  
* **Vị trí code:** TripService.java – hasAlreadySuggested()  
* **Giải pháp xử lý:** → Thêm check DEPARTED và COMPLETED vào hasAlreadySuggested()

### **Incon \#3: countBusyTripsByUserId và findAllTripsByDriverOnDate — query trùng lặp, một cái dùng userId, một cái dùng Driver entity**

* **Mô tả:** TripRepository có 2 nhóm query kiểm tra driver bận: nhóm dùng Driver entity làm param (existsOverlappingTripForDriver) và nhóm dùng Long driverId (countBusyTripsAnyRole, findAllTripsByDriverOnDate). Hai approach khác nhau cho cùng mục đích, TripService chỉ dùng nhóm đầu, nhóm sau không được gọi ở đâu.  
* **Vị trí code:** TripRepository.java  
* **Giải pháp xử lý:** → Xóa countBusyTripsAnyRole và findAllTripsByDriverOnDate nếu không dùng, hoặc migrate sang dùng chúng nhất quán

## **🟢 Điểm thiết kế tốt**

### **Tốt \#1: FSM canTransition() được thiết kế rõ ràng, whitelist-based**

* **Mô tả:** Logic chuyển trạng thái trip theo pattern whitelist — chỉ cho phép các transition hợp lệ rõ ràng, từ chối tất cả các trường hợp không được liệt kê. COMPLETED và CANCELLED là terminal states đúng. Tốt hơn blacklist nhiều.

### **Tốt \#2: Auto-assign với fallback rõ ràng và logging chi tiết**

* **Mô tả:** autoAssignResources() có logic fallback rõ ràng: ưu tiên xe/tài xế tốt nhất, khi không có thì dùng fallback kém hơn với cảnh báo. Các thông báo System.out đủ rõ để debug trên demo.

### **Tốt \#3: JOIN FETCH nhất quán trong TripRepository, tránh N+1**

* **Mô tả:** findAllWithDetails(), findByStatusWithDetails(), findByIdWithDetails() đều dùng DISTINCT \+ LEFT JOIN FETCH đầy đủ cho route, bus, busType, driver, assistant, coDrivers. Đây là cách xử lý lazy loading đúng.

### **Tốt \#4: Buffer time được tách thành hằng số, không hardcode**

* **Mô tả:** MIN\_REST\_BETWEEN\_TRIPS\_MINUTES \= 30 và BUS\_PREP\_BUFFER\_HOURS \= 1 được đặt là static final constants, dễ thay đổi và document rõ ràng ý nghĩa.



Viewed DataInitializer.java:250-267
Searched for "availableDrivers"
Viewed approve-form.html:1-407
Searched for "assistant"
Viewed trip-create-form.html:1-306

Qua phân tích và đối chiếu logic giữa tầng Backend (xử lý AI tự động, hàm Validate ràng buộc) và giao diện Frontend (màn hình phân công thủ công, tạo/sửa chuyến đi), mình phát hiện ra một số **vấn đề bất nhất quán và lỗi logic** quan trọng sau đây:

---

### 1. Bất nhất về Ràng buộc Giờ lái của Phụ xe (Assistant)

*   **Vấn đề:** 
    *   Trong nghiệp vụ hệ thống, **Phụ xe không trực tiếp lái xe** (được chứng minh tại hàm `getDrivingHoursForDate` dòng 470, giờ lái của phụ xe trên chuyến đó được tính bằng `0.0`).
    *   Tuy nhiên, khi AI tự động phân công phụ xe bằng hàm `findBestAvailableDriver` (dòng 246), hệ thống lại áp dụng điều kiện lọc: `getDrivingHoursForDate(d, departure, null) + effectiveHours <= 8.0`.
    *   Tương tự, ở màn hình phân công thủ công (`approve-form.html` dòng 290), dropdown chọn phụ xe sử dụng danh sách `availableDrivers` (đã bị lọc bởi điều kiện tổng giờ lái $\le$ 8h).
*   **Hệ quả:** 
    *   **AI tự động:** Một tài xế đã lái xe 5 tiếng trong ngày vẫn hoàn toàn đủ điều kiện làm phụ xe cho một chuyến đi 4 tiếng tiếp theo (vì làm phụ xe không tính giờ lái, tổng giờ lái của họ vẫn là 5h $\le$ 8h). Thế nhưng AI sẽ bỏ qua tài xế này vì tính toán sai lệch: `5h + 4h = 9h > 8h`.
    *   **Admin thủ công:** Admin bị giới hạn, không thể chọn một tài xế làm phụ xe nếu người đó có số giờ lái dự kiến vượt quá 8h, dù điều này hoàn toàn hợp lệ dưới góc độ an toàn đường bộ.
*   **Giải pháp:** Nên viết riêng một hàm `findBestAvailableAssistant` (chỉ kiểm tra bằng lái còn hạn và không trùng lịch chạy, bỏ qua kiểm tra số giờ lái tối đa của chuyến đi mới) hoặc truyền thêm một cờ `isAssistantRole` vào hàm tìm kiếm.

---

### 2. Mâu thuẫn thông tin hiển thị và Validate thực tế khi thiếu Phụ xe (Chuyến đi > 8h)

*   **Vấn đề:** 
    *   Ở backend, hàm `validateStaffForTrip` (dòng 723) quy định cực kỳ nghiêm ngặt: **Chuyến đi trên 8 tiếng bắt buộc phải có phụ xe**, nếu thiếu sẽ bị chặn cứng (`throw IllegalArgumentException`).
    *   Tuy nhiên, trên giao diện duyệt chuyến tăng cường [approve-form.html:L178](file:///e:/Y4%20S2%20/PRETHESIS/ProjectBackup/BusManagement/BusManagement/src/main/resources/templates/admin/approve-form.html#L178) lại hiển thị thông báo: 
        > *"Không tìm được phụ xe: Tất cả phụ xe đều bận hoặc đã đủ 8h. Chuyến vẫn có thể chạy nhưng không có phụ xe."*
*   **Hệ quả:** Admin đọc giao diện thấy thông báo chuyến vẫn chạy được mà không có phụ xe, nhưng khi bấm nút *"Xác nhận & Kích hoạt chuyến"*, hệ thống backend sẽ chặn lại và báo lỗi vi phạm ràng buộc, gây hoang mang cho người dùng.
*   **Giải pháp:** Đồng bộ lại giao diện. Nếu thời lượng chuyến xe kéo dài trên 8 tiếng và thiếu phụ xe, nút kích hoạt chuyến đi phải bị vô hiệu hóa (disabled) và thông báo cảnh báo phải ghi rõ đây là lỗi chặn cứng (hard block), yêu cầu Admin phân công thủ công phụ xe thì chuyến đi mới được duyệt.

---

### 3. Sự không đồng bộ trong Trải nghiệm Gán nhân sự thủ công (Manual Assignment)

*   **Màn hình duyệt chuyến tăng cường thủ công (`approve-form.html`):** Chỉ hiển thị danh sách tài xế/xe đã lọc sẵn (chỉ gồm những tài nguyên thực sự rảnh và không vi phạm). Đây là một thiết kế rất tốt.
*   **Màn hình tạo/sửa chuyến xe thủ công (`trip-create-form.html` & `trip-edit-form.html`):** Lại hiển thị toàn bộ xe và tài xế trong cơ sở dữ liệu.
*   **Hệ quả:** Admin tạo chuyến xe mới phải tự "đoán" xem tài xế nào rảnh và chưa quá 8h chạy để gán vào. Nếu gán sai, chỉ khi bấm submit hệ thống mới báo lỗi từ Backend. 
*   **Giải pháp:** Nên tối ưu hóa màn hình tạo/sửa chuyến xe thủ công bằng cách sử dụng API để lọc động danh sách xe và tài xế hợp lệ dựa trên khoảng thời gian khởi hành/dự kiến đến mà Admin đã nhập.

---

### 4. Kiểm tra trùng lặp trên giao diện tạo chuyến

*   **Vấn đề:** Khi Admin chọn tài xế phụ (Co-drivers) thông qua giao diện JS động, dropdown tài xế phụ vẫn hiển thị toàn bộ danh sách tài xế, cho phép Admin chọn trùng tài xế phụ với tài xế chính hoặc trùng giữa các tài xế phụ với nhau. Lỗi này chỉ bị phát hiện khi gửi dữ liệu lên server (chặn bởi `validateStaffForTrip`).
*   **Giải pháp:** Thêm một đoạn logic JavaScript nhỏ trên Frontend để tự động loại bỏ các tài xế đã được chọn ở dropdown Tài chính/Tài phụ khác khỏi danh sách lựa chọn của các dropdown còn lại.