# PROMPT MẪU — GIẢI THÍCH MỘT TÍNH NĂNG CỦA DỰ ÁN BUSMANAGEMENT

> **File này là công cụ, không phải tài liệu mô tả hệ thống.** Nó chứa một prompt mẫu dùng
> lại nhiều lần: mỗi lần cần hiểu sâu một tính năng, chủ dự án copy phần "PROMPT" bên dưới,
> thay `{TÊN_TÍNH_NĂNG}`, và gửi cho AI. Kết quả là một file giải thích đặt trong
> `docs/development/feature-*.md`.
>
> File này **không** thay thế `01_system_rules.md` — các luật ở đó vẫn áp dụng đầy đủ.

---

## Cách dùng

Thay `{TÊN_TÍNH_NĂNG}` bằng **một tên có thật** trong danh sách dưới đây. Danh sách lấy từ
`docs/development/Proj_functions_summary.md` và `docs/development/THESIS_ROADMAP.md` §5 —
không tự bịa tên mới, vì tên bịa sẽ dẫn tới việc AI gom nhầm nhiều tính năng vào một file.

**Pillar 1 — Operations**

- Extra Trip Recommendation System (chuyến tăng cường tự động)
- Admin Approval Flow (phê duyệt chuyến tăng cường)
- Trip FSM & Bus Status Synchronization
- Business Rule Validation (`validateBusForTrip` / `validateStaffForTrip`)
- Manual Trip CRUD
- Dispatch Center
- Bus Management
- Driver Management
- Route & Station Management
- Incident Management
- Dashboard & Analytics
- Available Resources REST API (`TripRestController`)

**Pillar 2 — Decision Support**

- Driver Recommendation
- Demand Forecast
- Recommendation Engine
- Vehicle Replacement Recommendation
- Cost/Revenue Estimation (`CostParameters`)
- What-if Simulation
- Historical Data Backfill

**Pillar 3 — Customer Portal:** chưa triển khai (Phase 9, NOT STARTED). Không có tính năng
nào ở đây để giải thích.

---

## PROMPT

Bạn là một senior Spring Boot engineer kiêm mentor, đang giúp mình hiểu THẬT SÂU một tính
năng trong dự án BusManagement của mình.

**Tính năng cần giải thích:** {TÊN_TÍNH_NĂNG}

---

### A. Người đọc là ai (quyết định toàn bộ văn phong)

Người đọc file này là **mình — chủ dự án, sinh viên làm đồ án, chưa vững Spring Boot**.
Không phải hội đồng, không phải đồng nghiệp senior. Nghĩa là:

- Mình cần **hiểu tổng quan trước, rồi mới xuống chi tiết**. Đừng ném mình vào code ở
  đoạn đầu. Đi theo đúng 3 bậc của mục D.
- **Mọi thuật ngữ kỹ thuật, lần đầu xuất hiện, phải được giải nghĩa ngay tại chỗ bằng
  một câu đời thường.** Ví dụ: "`@Transactional` — hiểu nôm na là 'làm trọn gói: hoặc
  xong hết, hoặc hoàn tác sạch, không để làm nửa chừng'". Không được giả định mình đã
  biết `@Transactional`, JPQL, LAZY loading, FSM, proxy AOP... là gì.
- Nhưng đích đến là **mình phải tự đọc code và tự sửa bug được**. Nên phần cuối vẫn phải
  chính xác và đủ sâu — dễ hiểu không có nghĩa là làm loãng.

---

### B. Ranh giới của task

- Đây là task **CHỈ VIẾT TÀI LIỆU**. Không sửa một dòng code nào, không sửa file tài liệu
  nào đang có, không tạo test, không chạy app. Sản phẩm duy nhất là **một file `.md` mới**.
- Nếu phát hiện bug hoặc điểm bất thường trong lúc đọc: **ghi vào file .md, không tự sửa** —
  đúng luật Atomic Changes trong `CLAUDE.md`.

---

### C. Phải đọc trước khi viết

1. **Đọc `docs/development/THESIS_ROADMAP.md` trước tiên** — đây là nguồn sự thật của dự án:
   §5 (phase nào giao tính năng này, mục "As delivered"), §9 Developer Notes (lý do thiết kế
   **đã được chủ dự án duyệt**), §6 Hidden Costs Register. Nếu §9 đã trả lời câu hỏi "vì sao
   thiết kế như vậy" thì **trích lại nó**, đừng tự nghĩ ra một lý do khác.
2. **Đọc code thật**: các file `.java`, `.html` (Thymeleaf), file cấu hình liên quan
   (Controller, Service, Repository, Entity, DTO, template, config). KHÔNG đoán mò, KHÔNG bịa.
   - Nếu tính năng nằm trong một class lớn dùng chung — điển hình là `TripService.java`
     (1.526 dòng, chứa ít nhất 4 tính năng đan xen nhau) — thì **phải chỉ đích danh method và
     khoảng dòng**, không được coi cả class là "tính năng".
3. **Đối chiếu lịch sử lỗi** ở `docs/todo/current_bugs_found.md` (defect #1–#19; hiện còn mở
   #2, #3, #4, #5, #18, #19) và `docs/reports/project_report.md`. **Tôn trọng các mục
   "ĐÃ LOẠI — không phải lỗi, đừng nêu lại"** trong file đó: không nêu lại chúng như phát
   hiện mới. Lưu ý `docs/archive/ProjectReport.md` là bản archive đã lỗi thời — chỉ dùng làm
   bối cảnh lịch sử, không trích như trạng thái hiện tại.
4. **Tài liệu phụ để đối chiếu**: `current_functional_spec.md`, `database_schema.md`,
   `trip_lifecycle_fsm.md`, `Proj_functions_summary.md`. Khi tài liệu mâu thuẫn với code thì
   **code thắng** — và hãy nói rõ cho mình biết chỗ mâu thuẫn đó.
5. Nếu thấy code chưa rõ ràng, mâu thuẫn hoặc thiếu, **chỉ rõ ra thay vì im lặng bỏ qua**.
   Phân loại mỗi phát hiện theo đúng 3 nhóm dự án đang dùng: *không phải lỗi (ý định vẫn
   đúng)* / *quyết định đã hết hiệu lực* / *lỗi thật*. Với lỗi thật, phải chứng minh nó tiếp
   cận được trên dữ liệu hiện tại, kèm `file:dòng`.

---

### D. Output: một file `.md` duy nhất

Đặt tại `docs/development/feature-{ten-khong-dau}.md`
(ví dụ: `docs/development/feature-extra-trip-recommendation.md`).

Cấu trúc **3 phần, đi từ dễ đến khó**:

#### PHẦN 1 — Tính năng này là gì (ngôn ngữ đời thường, không có thuật ngữ)

- Tính năng này **giải quyết vấn đề gì trong thực tế** của một công ty xe khách? Nếu không
  có nó thì ai khổ, khổ như thế nào?
- Kể một **kịch bản cụ thể**: "Anh admin ngồi ở văn phòng, thấy chuyến Hà Nội–Hải Phòng
  sắp đầy khách. Lúc đó hệ thống sẽ..." — kể tuần tự, dễ hình dung, chưa động đến code.
- Dùng ẩn dụ đời thường cho các vai (Controller như lễ tân nhận yêu cầu, Service như người
  xử lý việc, Repository như người vào kho lấy đồ...).
- **Mục tiêu:** đọc xong phần này, một người không biết lập trình vẫn hiểu tính năng tồn tại
  để làm gì và chạy theo logic nào ở mức ý tưởng.

#### PHẦN 2 — Bản đồ tổng quan (cầu nối giữa Phần 1 và Phần 3)

Đây là phần giúp mình không bị hụt chân khi bước vào code. Yêu cầu:

- **Bảng ánh xạ**: mỗi ý niệm ở Phần 1 tương ứng với file/class nào có thật trong dự án.
  Ví dụ: "người xử lý việc" → `TripService.java`.
- **Danh sách các mảnh ghép** của tính năng: liệt kê đúng những file thật sự tham gia
  (Controller / Service / Repository / Entity / DTO / template), mỗi file **một dòng** nói nó
  chịu trách nhiệm gì. Chưa đi vào từng method.
- **Một sơ đồ ASCII đơn giản** cho thấy dữ liệu chảy từ đâu tới đâu.
- **3–5 câu "điều cốt lõi cần nhớ"** — nếu chỉ nhớ được vài điều về tính năng này thì nhớ gì.

#### PHẦN 3 — Chi tiết kỹ thuật (đủ để tự đọc hiểu và tự sửa bug)

Bám sát code thật, trình bày theo 5 mục:

1. **Luồng chạy chi tiết (Flow)** — từ điểm bắt đầu đi qua từng lớp, ghi rõ **tên class, tên
   method, số dòng** ở từng bước. Lưu ý điểm bắt đầu **không nhất thiết là một HTTP request**:
   trong dự án này có tính năng khởi động bằng job chạy nền (`@Scheduled`), có tính năng khởi
   động bằng JavaScript gọi REST. Nói rõ trigger thật là gì.
2. **Cơ chế hoạt động** — các annotation, cấu trúc, pattern được dùng (`@Transactional`,
   `@MapsId`, `@BatchSize`, `@SQLDelete`/`@SQLRestriction`, JPQL, FSM...): **nó là gì** (giải
   nghĩa đời thường), **nó làm gì ở đây**, và **tại sao chỗ này cần nó**.
3. **Vì sao thiết kế như vậy (Why)** — với mỗi quyết định quan trọng: tránh được lỗi gì, đảm
   bảo tính đúng đắn nào, và tại sao **không** chọn cách đơn giản hơn. Ưu tiên trích §9
   Developer Notes của roadmap; nếu §9 không có thì nói rõ "đây là suy luận của tôi từ code".
4. **Các điểm dễ gây bug / cạm bẫy** — chỗ nào dễ hiểu nhầm, dễ sửa sai. Nếu tính năng này
   từng có bug thật đã fix (xem `current_bugs_found.md`), giải thích **bug đó xuất phát từ
   việc hiểu sai điều gì** — đây là phần mình học được nhiều nhất, đừng viết qua loa.
5. **Liên kết với phần khác của hệ thống** — tính năng này ảnh hưởng ai, bị ảnh hưởng bởi ai.

---

### E. Văn phong

- **Phần 1:** đơn giản tối đa, không thuật ngữ, không sợ "nói hơi ngây thơ".
- **Phần 2:** rõ ràng, nhiều bảng và sơ đồ, ít chữ.
- **Phần 3:** chính xác, chặt chẽ, đủ để tự debug — nhưng **ưu tiên rõ ràng hơn là dài**.
  Không lan man, không nhắc lại điều đã nói ở Phần 1/2.
- Viết bằng **tiếng Việt**, nhưng **giữ nguyên tên file / class / method / annotation / enum
  bằng tiếng Anh** đúng như trong code — không dịch, không đổi hoa thường.
- Luôn trích `file:dòng` khi nhắc tới code cụ thể.
- **Phân biệt rõ điều gì ĐỌC ĐƯỢC TỪ CODE và điều gì là SUY LUẬN của bạn.** Suy luận phải
  gắn nhãn. Nếu một khẳng định về hành vi lúc chạy không suy ra được từ code, ghi
  "chưa kiểm chứng" thay vì nói chắc.

---

### F. Sau khi tạo file

Tóm tắt ngắn gọn 3–5 dòng: những điều quan trọng nhất mình cần nhớ về tính năng này, và
(nếu có) những chỗ bạn thấy đáng ngờ nhưng chưa đủ căn cứ để kết luận.

---

## Ghi chú bảo trì cho file này

- Danh sách tính năng ở trên phản ánh trạng thái Phase 0→8. **Khi Phase 9 (Customer Portal)
  hoàn thành, phải bổ sung các tính năng mới vào danh sách** — nếu không, người dùng prompt
  sẽ tự bịa tên.
- Các số liệu trích trong mục C (số dòng `TripService.java`, dải defect `#1–#19`, danh sách
  defect còn mở) là ảnh chụp tại 2026-08-11. Chúng chỉ dùng để định hướng, không phải hằng
  số nghiệp vụ — nếu lệch thì đọc file gốc, đừng tin con số ở đây.
