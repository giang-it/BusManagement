# Kế hoạch và tiến độ: cặp tuyến và điều độ theo dự báo

> **File theo dõi qua nhiều phiên.** Cuối mỗi phiên làm việc phải cập nhật:
> - mục 1 (trạng thái);
> - mục 2 (bảng giai đoạn);
> - mục 4 (đánh `[x]`, kèm ngày và chỗ kiểm chứng);
> - thêm một dòng vào mục 5 (nhật ký).
>
> Tài liệu liên quan:
> - Quyết định: [`route_pairs_decisions.md`](route_pairs_decisions.md) (QĐ-18…QĐ-27, các mục chưa chốt CM-1…CM-25).
> - Đợt trước (dự báo): [`forecast_progress.md`](forecast_progress.md), [`forecast_decisions.md`](forecast_decisions.md).
> - `THESIS_ROADMAP.md` vẫn là tracker chung của cả dự án. §7 trỏ về file này, §8 ghi một mục ngắn cho mỗi phiên.

## Bắt đầu một phiên mới: đọc theo thứ tự

1. `THESIS_ROADMAP.md`: §7 và §10.
2. File này: mục 1, 2 và giai đoạn đang làm ở mục 4.
3. `route_pairs_decisions.md`: mục 1 (bảng QĐ) và mục 3 (chưa chốt).

---

## 1. Trạng thái hiện tại (cập nhật 2026-11-25)

- **Mục tiêu của đợt** (theo ý thầy hướng dẫn):
  - màn dự báo gọn lại, chỉ còn 3 cặp tuyến hai chiều quanh Bến xe Miền Đông;
  - xe chạy theo vòng đi–về;
  - dự báo dùng để thêm vòng (tăng cường), tự tạo chuyến chờ duyệt, và quyết định giữ xe tăng cường ở bến xa hay cho về;
  - nhìn vào màn hình là thấy chiều nào đã được tăng cường.
- **Đã chốt:** QĐ-18…QĐ-27 (ngày 2026-11-11, 13 và 16).
- **Đã lập kế hoạch:** 11 giai đoạn, CT0…CT10 (mục 2 và 4).
- **Kế hoạch được duyệt** ngày 2026-11-16 (*"oke. chốt hãy thực hiện đi"*).
- **CT0 xong:**
  - CM-1: đã bỏ diff format của `ForecastService.java`.
  - CM-11: lỗi #34 và 7 mục có hướng xử lý đã ghi vào `current_bugs_found.md`.
- **CT1 xong, chưa commit.**
  - `mvnw test` 217/217.
  - Thử làm hỏng: đặt lại công thức cũ thì đúng 2 test đỏ.
  - `verify` trên DB thật (chỉ đọc), DB giống hệt trước và sau. Chi tiết ở mục 4.
- **CT2 xong, chưa commit** (chủ dự án bảo *"làm CT02 luôn đi"*):
  - tuyến 10, 11 đổi sang Limousine 22 qua form tuyến trên DB thật (có sao lưu trước);
  - 3 cặp đúng QĐ-19 và QĐ-20;
  - dữ liệu mẫu khớp DB thật (CM-13);
  - `mvnw test` 217/217. Chi tiết ở mục 4.
- **CT0–CT2 đã commit và push ngày 2026-11-17** (chủ dự án: *"oke, hãy commit và push đi"*):
  - `963aa71`: CT1, code + test;
  - `bbedfd8`: CT2, dữ liệu mẫu;
  - `ed38ce6`: bài học;
  - commit `docs:` ngay sau đó: kế hoạch, quyết định, sổ lỗi, các tài liệu còn lại;
  - trước khi commit đã chạy lại `verify` trên app thật (PID 21568): 11 trang chính trả 200, What-if 200% ra 85, log sạch.
- **2026-11-17 (sau):** chốt CM-2 (H1 "về ngay") và CM-3 (tâm 100%). Tách CT5 thành CT5a (trước CT3) và CT5b (sau CT4).
- **CT5a xong ngày 2026-11-17** (CM-5 cách B, CM-12 cách (a)):
  - bến nhà cho xe và tài xế; luật vị trí `domain/LocationRule`;
  - DB thật: 20 xe và 36 tài xế có bến nhà Miền Đông, có sao lưu trước;
  - việc chọn xe giống hệt trước (chưa bật);
  - test 230/230.

  Chi tiết ở mục 4.
- **CT5a đã commit và push:** `4c3ce51` (code + test), `d39c17e` (bài học), và commit `docs:` ngay sau đó.
  - Trước khi commit đã kiểm lại một lượt trên app thật: 16 trang trả 200; việc chọn xe giống hệt mốc; danh sách và form đúng; log sạch.
- **CT3 xong trên bản sao ngày 2026-11-17, đã commit và push** (chủ dự án: *"hãy làm CT03 đi"*, rồi *"chốt CM-16 giữ 6%, kiểm tra lại rồi commit và push"*):
  - bộ sinh mới: danh sách tuyến `backfill.route-ids`, chung mức theo cặp, khách về = r × khách đi, xe và tài xế chạy vòng, xe tăng cường "về ngay", gửi xe lên trước cho đợt về;
  - tổng duyệt trên bản sao `busmanagement_ct3`: PART A → B → bộ sinh → C, mọi cột vi phạm = 0; DB thật giống hệt trước và sau;
  - màn Dự báo trên bản sao: 18 chuỗi; tự chấm 16,5% (SN4 17,9%);
  - test 243/243.

  Chi tiết và số đo ở mục 4, CT3.
  - CM-16 chốt: giữ 6% hủy và bão theo từng chiều.
  - Kiểm lại trước khi commit: test 243/243; PART C chạy lại trên bản sao vẫn 0 vi phạm; `verify` trên app thật (profile mặc định): 16 trang trả 200, log sạch, bộ sinh không chạy, DB thật giống hệt.
  - Commit `7cb8fb4` (code + test), `9bb463f` (bài học), và commit `docs:` ngay sau đó; đã push.
- **CT4 xong trên DB thật ngày 2026-11-17, chưa commit** (chủ dự án giao 5 bước, PART B *"KHÔNG XÓA, HÃY SAO LƯU LẠI ĐỂ MỞ LẠI NẾU CẦN"*):
  - sao lưu (đã khôi phục thử) → PART A → lưu trữ lịch sử GĐ3 bằng xoá mềm (không xoá dòng nào) → bộ sinh → PART C 0 vi phạm;
  - DB thật giống hệt bản tổng duyệt tới từng chuyến;
  - `verify`: 16 trang 200, màn Dự báo 18 chuỗi.

  Chi tiết ở mục 4, CT4.
- **Tài liệu CT4 đã commit** `6b6ecf8` ngày 2026-11-17 (chủ dự án chỉ bảo "commit"), push cùng CT5b.
- **CT5b xong ngày 2026-11-17, đã commit và push** (bắt đầu ở phiên `d7e11adb`, dừng giữa chừng theo lời chủ dự án, làm tiếp ở phiên `8bf87e0f`; chủ dự án: *"tôi hiểu rồi, commit và push đi"*). Commit `5f52879` (code + test), `345c8be` (bài học), và commit `docs:` ngay sau đó:
  - máy (job 10 giây, màn Đề xuất) chỉ chọn xe và người vừa chuỗi vị trí, xét hai phía; dropdown Duyệt/Sửa/Tạo mời đủ, đúng bến lên đầu, có ghi chú; validator không chặn;
  - sửa câu vị trí của người chậm (0,46 s một lần → ≈ 0,01 s, tách theo vai);
  - test 259/259; thử làm hỏng 4 chỗ thì đúng 2 test mới đỏ;
  - `verify` trên DB thật (chỉ đọc) và trên bản sao (ghi); DB thật giống hệt trước và sau.

  Chi tiết ở mục 4, CT5b. Phát hiện lỗi #35 (có sẵn, không thuộc CT5b): form Tạo và màn Duyệt thủ công mất 23–28 giây.
- **Lỗi #35 và #36 đã sửa ngày 2026-11-17; đã commit và push ngày 2026-11-22:** `7fb50f4` (code + test), `c615164` (bài học), rồi commit `docs:` ngay sau đó. Chủ dự án: *"sau đó sửa #35 đảm bảo mọi thứ đều nhất quán, logic và phù hợp chuẩn với project"*. Không thuộc giai đoạn CT nào; chi tiết ở `current_bugs_found.md` #35, #36.
  - Lịch của một người (bận, giờ lái) hỏi riêng từng vai, như câu vị trí CT5b; khoá sắp xếp theo giờ lái được nhớ.
  - Sửa luôn #36, phát hiện khi sửa: chuyến có ≥ 2 tài xế phụ bị chia giờ sai **theo cả hai chiều**.
  - Test 261/261, thử làm hỏng 5 cách.
  - `verify` DB thật chỉ đọc: API form Tạo 28 → 7 s (có tuyến), 23 → 2,4 s (không tuyến); kết quả giống hệt. A/B trên bản sao: màn Duyệt thủ công 67 → 6 s.
- **CT6 xong ngày 2026-11-19; đã commit và push ngày 2026-11-23** (chủ dự án duyệt mọi đề xuất: *"oke, tôi đồng ý tất cả, hãy kiểm tra những chức năng, file liên quan và làm … xong thì cũng test luôn"*; rồi *"check lại đảm bảo mọi thứ hoạt động đúng và logic, sau đó commit và push"*). Commit `5b38e71` (code + test), `8957099` (bài học), `c36fef3` (tài liệu):
  - trang `/admin/timetable`: cặp tuyến suy bằng đảo bến, một nguồn giờ chạy và danh sách tuyến cho bộ sinh lẫn app, nút "Tạo lịch" xếp mọi khung theo giờ bằng `autoAssignResources` của job 10 giây trên một context lập lịch;
  - test 273/273 (+12); thử làm hỏng 7 chỗ, chỗ nào cũng đúng test cần đỏ;
  - `verify` trên bản sao rồi DB thật (có sao lưu): 125 chuyến mở bán + 1 chờ phân công cho 20–26/11, 7 phép kiểm SQL độc lập 0 vi phạm;
  - DB thật: đóng 5 chuyến demo `DEPARTED` (CM-22), thêm 126 chuyến.
  - Kiểm lại trước khi commit (2026-11-23): test 273/273 trên đúng bản CT6; bản sao DB thật, chạy bằng bản CT6 + CT7 (CT7 không đổi đường lập lịch), "Tạo lịch" 27/11–03/12: 121 chuyến mở bán + 5 chờ phân công, cùng phép kiểm SQL độc lập 0 vi phạm; DB thật giống hệt trước và sau. Phát hiện **CM-25** (lịch nhìn trước không có bước bảo trì: từ 27/11 cặp Đà Lạt chạy chủ yếu bằng Limousine 22).

  Chi tiết ở mục 4, CT6. Làm trong worktree, vì `#35/#36` vẫn chưa commit và app 8080 của chủ dự án chạy từ `target/classes`.
- **CT7 xong ngày 2026-11-22; đã commit và push ngày 2026-11-23** (chủ dự án duyệt 5 đề xuất: *"tôi đồng ý với những đề xuất của bạn (từ 1-5)"*, rồi *"hãy suy luận và tiến hành thực hiện code theo như đã đề xuất"*; rồi *"check lại … sau đó commit và push"*). Commit `fae225d` (CT7a), `3e513f0` (CT7b), `deeece9` (bài học), rồi commit `docs:` ngay sau đó:
  - **CT7a:** màn Đề xuất tính lãi cả vòng (PA-B: về ngay / đưa xe tới / không cần / tính như về rỗng), chỉ đề xuất phần khung còn thiếu (2a); What-if tính tiền cả vòng (W1); một phép tính tiền chung `service/LoopEconomics`;
  - **CT7b:** trang mới `/admin/analytics/pair-plan`; bảng 7 ngày của màn Dự báo gom theo cặp;
  - test 292/292 (+19), thử làm hỏng 8 chỗ;
  - `verify` DB thật chỉ đọc (DB giống hệt): đủ điều kiện 19 → 22, có lãi 7 → 3 (cả vòng); phép kiểm độc lập 41 lượt, 0 vi phạm; bản sao: "Tạo lịch" 27/11–03/12 vẫn chạy.
  - Kiểm lại trước khi commit (2026-11-23): test 288/288 trên đúng bản CT7a và 292/292 trên bản CT7 đủ; `verify` DB thật chỉ đọc từ worktree `wt-ct7`: 24 lượt gọi trả 200 (16 trang, trang kế hoạch cho từng ngày trong 7 ngày, What-if 150 %), log sạch; 25 thẻ, 24 đủ điều kiện (10 về ngay, 11 đưa xe tới, 3 không cần); phép kiểm độc lập 45 lượt trên 15 xe và 22 người: 0 trùng giờ, 0 đứt chuỗi vị trí, 0 ngày quá 8 giờ, chi phí khớp phép tính tay ở cả 24 thẻ; 1 thẻ có lượt về do một xe khác chạy (đúng "cách hiểu khi cài" ở QĐ-22); DB thật giống hệt trước và sau.

  Chi tiết ở mục 4, CT7. Mở **CM-24**: lịch phủ cả tuần thì hết tài xế cho chuyến thêm (1/25 trên bản sao, cả bản CT6; đo lại 2026-11-23: 2/25).
- **CT7c (CM-24 cách PA-A) và CT7d (CM-25 cách M1) xong ngày 2026-11-23; đã commit và push ngày 2026-11-25** (chủ dự án: *"tôi duyệt CM24 và CM25 như đã bàn bạc. hãy xem xét kỹ thực hiện nó một cách logic, phù hợp và nhất quán với project"*; sau khi tự test: *"tôi kích hoạt rồi, commit và push đi"*). Commit `f708f44` (CT7c), `eabf985` (CT7d), `afc27dd` (bài học), rồi commit `docs:` ngay sau đó:
  - CT7c: màn Đề xuất chọn cả vòng một lần, một xe + một tài xế (`TripService.selectLoop`); không xếp được cả vòng thì thiếu tài nguyên;
  - CT7d: lịch thường lệ đặt ngày bảo trì ở bến nhà (cột mới `buses.planned_maintenance_date`); ngày đó xe không chạy, hôm sau km từ 0; mọi bộ chọn của máy cộng km đã xếp và quãng về bến nhà;
  - `mvnw test` **303/303** (+11); thử làm hỏng 11 chỗ, chỗ nào cũng đúng test cần đỏ;
  - bản sao DB thật sau "Tạo lịch" 27/11–03/12, cùng dữ liệu, cùng ngày — code trước CT7c/CT7d → code mới: lịch 121 mở bán + 5 chờ phân công → 125 + 1; cặp Đà Lạt 9/42 → 29/42 chuyến giường nằm; xe kẹt ở bến xa 5 → 1; màn Đề xuất 2/25 → 15/25 thẻ đủ điều kiện (chỉ CT7c trên đúng lịch cũ: 19/25, nhưng phép kiểm thấy 12 lượt vượt mốc bảo trì, xe 29A-001.14 tới 5.990 km); DB thật (chỉ đọc) 24/25 → 25/25; phép kiểm độc lập 0 vi phạm; DB thật giống hệt trước và sau (chỉ thêm cột, trống).
  - **Chủ dự án tự test trên DB thật (2026-11-25):** sao lưu `ProjectBackup\db\busmanagement_2026-11-24_truoc-lich-27-11.sql` (chụp 00:10:46; 48.008 chuyến, đúng số chuyến trước khi tạo lịch), rồi "Tạo lịch" 27/11–03/12 lúc 00:15: **125 mở bán + 1 chờ phân công**, 3 ngày bảo trì (29A-001.14 ngày 27/11, .03 ngày 29/11, .04 ngày 30/11), cặp Đà Lạt 29 giường nằm + 12 Limousine + 1 chưa có xe, 0 chuyến chạm ngày bảo trì — khớp bản sao B từng số.
  - **Xe 29A-001.15 kẹt ở Đà Lạt — đóng: đưa về bằng tay, không thêm luật "lượt về tới 100%".** Chủ dự án tạo tay chuyến 06:00 27/11 bằng xe .15, rồi xoá nó (khung 06:00 đã có #51487; tài xế 21 đang ở Miền Đông), và thay vào đó: duyệt chuyến chờ #51493 (Đà Lạt → Miền Đông 12:00 27/11) bằng xe .15 + tài xế 18 (người duy nhất ở Đà Lạt rảnh giờ đó), bỏ phụ xe của #51517 (18:00 28/11) để chuỗi vị trí của tài xế 18 liền. Cổng kiểm tra cho qua vì chỉ xét km thật (220 + 300); tính cả lịch xe lên 4.720 km, nên máy không xếp xe .15 nữa cho tới khi bảo trì.
  - **Kiểm lại trước khi commit (2026-11-25):** test **294/294** trên đúng bản CT7c (dựng lại từ patch đã chụp, khớp bản chụp) và **303/303** trên bản đủ, trong worktree mới `wt-precommit`; `verify` DB thật chỉ đọc từ worktree đó (cổng 8099): 29 lượt gọi trả 200 (18 trang, 3 form xe, form sửa #51493, trang kế hoạch cho 7 ngày), log sạch; trang xe, form xe, Lịch thường lệ, Kế hoạch cặp tuyến hiện đúng 3 ngày bảo trì; màn Đề xuất 25 thẻ, **15 đủ điều kiện** (7 về ngay, 8 đưa xe tới; cả 15 vòng cùng một xe), 10 thiếu tài nguyên đều ghi "Không xếp được cả vòng …", 0 "tính như về rỗng"; `check_cards.py` 30 lượt (8 xe, 10 người) 0 vi phạm; phép kiểm chuỗi trên 251 chuyến từ 20/11: 0 trùng giờ, 0 đứt chuỗi (xe và người), 0 ngày quá 8 giờ; `check_maint.py` 27/11–03/12: 0 chạm ngày bảo trì, 0 xe kẹt, 1 lượt vượt mốc = #51493 do người chọn (đúng ý); DB thật giống hệt trước và sau.

  Chi tiết ở mục 4, CT7c và CT7d.
- **Việc tiếp theo:** #35/#36 đã commit và push ngày 2026-11-22; CT6 và CT7 ngày 2026-11-23; CT7c + CT7d ngày 2026-11-25. Kế tiếp là **CT8**: vòng thảo luận đầu giai đoạn chốt CM-10 (giờ chạy job đêm, khi dự báo thiếu dữ liệu), CM-15 (giờ khởi hành chuyến tự tạo), câu chữ mới của roadmap §4 "No daily cron job" (QĐ-24 mục 7), có nối "Tạo lịch" thường lệ vào job đêm không (CM-14), và **CM-23** — nay đã thấy trên DB thật: 89 chuyến thường lệ 20–24/11 qua giờ vẫn `ACTIVE`, máy vẫn cộng km của chúng (4.720 km của xe .15 có cả km 20–24/11). Chuyến #51360 (Vũng Tàu → Miền Đông 06:00 20/11, chờ chọn xe) đã được chủ dự án **hủy** ngày 2026-11-22, khi giờ đi đã qua. Còn mở: CM-23 (chuyến thường lệ đã qua giờ mà không ai vận hành, và lần chạy bổ sung lịch sử) — chủ dự án bảo *"bàn sau"* (2026-11-22); ~~**CM-24**, **CM-25**~~ — đã chốt và làm ở CT7c, CT7d (2026-11-23). ~~Xe 29A-001.15 kẹt ở Đà Lạt~~ — đóng ngày 2026-11-25: chủ dự án đưa về bằng tay qua #51493 (xem gạch đầu dòng CT7c/CT7d ở trên). Tuỳ chủ dự án: gộp phép tính vị trí 4 lần/người của CT5b, index ghép. G3-7: xoá các bản sao cũ (`busmanagement_gd2`, `busmanagement_ct3`, `busmanagement_ct6`) khi chủ dự án đồng ý.
- **Dữ liệu:**
  - DB thật: 4 tuyến 10–13 và bến Vũng Tàu do chủ dự án tạo ngày 2026-11-16;
  - CT2 đổi loại xe của tuyến 10, 11;
  - sao lưu trước CT2: `ProjectBackup\db\busmanagement_2026-11-16_truoc-CT2.sql`.

---

## 2. Các giai đoạn

Nhãn trạng thái: `CHƯA BẮT ĐẦU` / `ĐÃ CHỐT THIẾT KẾ` / `ĐANG LÀM` / `XONG, CHƯA COMMIT` / `ĐÃ COMMIT`.

**Thứ tự:** CT0 → CT1 → CT2 → **CT5a** → CT3 → CT4 → **CT5b** → CT6 → CT7 → **CT7c** → **CT7d** → CT8 → CT9 → CT10.
- CT5 được tách đôi ngày 2026-11-17. Định nghĩa vị trí và bến nhà (CT5a) làm trước bộ sinh, để bộ sinh tuân đúng luật đó. Bật vị trí vào việc chọn xe (CT5b) làm sau khi có lịch sử mới.
- Lý do đo trên DB thật: trên lịch sử cũ, 11/20 xe "đứng" ở bến ngoài 3 cặp.
- CT1 không phụ thuộc CT2–CT4, nên có thể làm xen kẽ.
- Mọi giai đoạn khác phải theo đúng thứ tự, vì giai đoạn sau dùng kết quả của giai đoạn trước (cột "Cần trước").

| GĐ | Nội dung | QĐ | Cần trước | Ghi DB thật? | Trạng thái | Ngày |
|---|---|---|---|---|---|---|
| CT0 | Khung theo dõi, ghi quyết định, dọn việc tồn | tất cả | — | không | ĐÃ COMMIT (commit `docs:` sau `ed38ce6`), đã push | 2026-11-16 → 17 |
| CT1 | Công thức số chuyến: max(2, ⌈nhu cầu ÷ ghế⌉) | QĐ-18 | CT0 (CM-1) | không | ĐÃ COMMIT `963aa71`, `ed38ce6`, đã push | 2026-11-16 → 17 |
| CT2 | Cấu hình tuyến và loại xe; dữ liệu mẫu | QĐ-19, QĐ-20 | CT0 | **có** (loại xe tuyến 10, 11) | ĐÃ COMMIT `bbedfd8`, `ed38ce6`, đã push | 2026-11-16 → 17 |
| CT5a | Định nghĩa vị trí (luật dùng chung cho bộ sinh và app), bến nhà xe và tài xế; **chưa bật** vào việc chọn xe | QĐ-23 | CT2 | **có** (bến nhà) | ĐÃ COMMIT `4c3ce51`, `d39c17e` + commit `docs:` sau đó, đã push | 2026-11-17 |
| CT3 | Bộ sinh mới: danh sách tuyến, chung mức theo cặp, khách về ±15%, xe chạy vòng, "về ngay" — chạy trên bản sao | QĐ-21 | CT2, CT5a | không | XONG trên bản sao; ĐÃ COMMIT `7cb8fb4`, `9bb463f` + commit `docs:` sau đó, đã push | 2026-11-17 |
| CT4 | Thay lịch sử trên DB thật (lịch sử GĐ3 được lưu trữ, không xoá) | QĐ-21 | CT3 đã commit | **có** | XONG trên DB thật; tài liệu ĐÃ COMMIT `6b6ecf8`, đã push | 2026-11-17 |
| CT5b | Bật vị trí vào việc chọn xe, tài xế (AI và dropdown) | QĐ-23 | CT4 | không | ĐÃ COMMIT `5f52879`, `345c8be` + commit `docs:` sau đó, đã push | 2026-11-17 |
| CT6 | Cặp tuyến và lịch thường lệ; tạo chuyến thường lệ tương lai | QĐ-22 | CT5b | **có** (chuyến tương lai) | ĐÃ COMMIT `5b38e71`, `8957099`, `c36fef3`, đã push; DB thật có lịch 20–26/11 | 2026-11-19 → 23 |
| CT7 | Màn "Kế hoạch cặp tuyến" (M2), lãi cả vòng, trừ chuyến đã có | QĐ-22 | CT6 | không | ĐÃ COMMIT `fae225d` (CT7a, con số), `3e513f0` (CT7b, màn hình), `deeece9` + commit `docs:` sau đó, đã push | 2026-11-22 → 23 |
| CT7c | Chọn cả vòng một lần, một xe + một tài xế (CM-24 PA-A) | QĐ-22, QĐ-26 | CT7 | không | ĐÃ COMMIT `f708f44`, `afc27dd` + commit `docs:` sau đó, đã push | 2026-11-23 → 25 |
| CT7d | Ngày bảo trì trong lịch; máy cộng km đã xếp và quãng về (CM-25 M1) | QĐ-22 | CT7c | **có** (cột mới trên `buses`, khi app khởi động) | ĐÃ COMMIT `eabf985`, `afc27dd` + commit `docs:` sau đó, đã push; DB thật có lịch 27/11–03/12 (chủ dự án tạo 2026-11-25) | 2026-11-23 → 25 |
| CT8 | Tự tạo chuyến chờ duyệt mỗi đêm; sửa job 10 giây | QĐ-24 | CT7d | **có** (chuyến chờ duyệt) | CHƯA BẮT ĐẦU | |
| CT9 | Luật giữ–về, tiền chờ, kíp lái ở lại cùng xe | QĐ-25, QĐ-26 | CT8 | có | CHƯA BẮT ĐẦU | |
| CT10 | Tài liệu, bài học, chuẩn bị bảo vệ | — | CT9 | không | CHƯA BẮT ĐẦU | |

---

## 3. Sự thật đã đo khi lập kế hoạch (2026-11-16; dùng cho các giai đoạn sau)

**Dữ liệu (DB thật `busmanagement`, chỉ đọc):**

1. **Tuyến:**

   | id | Tuyến | Km | Phút | Loại xe gợi ý | holidayFlow | Số chuyến |
   |---|---|---|---|---|---|---|
   | 1 | MĐ → Niệm Nghĩa | 120 | 120 | Limousine 22 | LEAVING_HUB | 4.252 |
   | 2 | Miền Đông → Đà Lạt | 300 | 360 | Giường nằm 40 | LEAVING_HUB | 4.266 |
   | 3 | Đà Nẵng → Huế | 100 | 120 | Giường nằm 40 | — | 4.199 |
   | 4 | Niệm Nghĩa → MĐ | 120 | 90 | Giường nằm 40 | TOWARD_HUB | 4.260 |
   | 5 | Cần Thơ → Miền Đông | 180 | 210 | Ghế ngồi 50 | TOWARD_HUB | 4.233 |
   | 6 | MĐ → Miền Đông | 1.700 | 1.800 | Giường nằm 40 | — | 1 |
   | 8 | MĐ → Đà Nẵng → Cần Thơ | 1.800 | 3.000 | Giường nằm 40 | LEAVING_HUB | 0 |
   | 9 | A → b → c → d (tuyến thử) | 4.500 | 4.500 | Giường nằm 40 | — | 0 |
   | 10 | Miền Đông → Vũng Tàu | 100 | 120 | Ghế ngồi 50 (QĐ-20: đổi sang Limousine 22) | LEAVING_HUB | 0 |
   | 11 | Vũng Tàu → Miền Đông | 100 | 120 | Ghế ngồi 50 (QĐ-20: đổi sang Limousine 22) | TOWARD_HUB | 0 |
   | 12 | Đà Lạt → Miền Đông | 300 | 360 | Giường nằm 40 | TOWARD_HUB | 0 |
   | 13 | Miền Đông → Cần Thơ | 180 | 210 | Ghế ngồi 50 | LEAVING_HUB | 0 |

   Có 3 loại xe trên DB thật: Limousine 22 (id 1), Giường nằm 40 (id 2), Ghế ngồi 50 (id 3).
   - `DataInitializer` (dữ liệu mẫu) cũng có đủ 3 loại xe, và đội xe mẫu cũng là 8 / 7 / 5.
   - Chỗ khác DB thật chỉ có hai: loại xe gợi ý của tuyến Miền Đông→Đà Lạt và Cần Thơ→Miền Đông (mẫu ghi Limousine); và thiếu bến Vũng Tàu cùng 4 tuyến chiều ngược (CM-13, sửa ở CT2).
   - *(Sửa 2026-11-16: bản đầu ghi "dữ liệu mẫu không có loại Ghế ngồi", sai. Grep lúc đó chỉ tìm "Limousine\|Giường".)*
   - *Sau CT2:* tuyến 10 và 11 dùng Limousine 22.
2. **Đội xe 20 chiếc:**
   - Limousine 8 (7 READY, 1 TRAVELING);
   - Giường nằm 7 (3 READY, 3 TRAVELING, 1 REPAIRING);
   - Ghế ngồi 5 (3 READY, 2 TRAVELING).

   36 tài xế.
3. **Chuyến:**
   - 21.192 chuyến do bộ sinh GĐ3 tạo, có `created_at` = 2026-10-22: 19.487 COMPLETED, 342 chuyến tăng cường COMPLETED, 1.363 CANCELLED. Khoảng thời gian 01/01/2023 → 21/10/2026.
   - 17 chuyến (chưa xoá mềm) do chủ dự án tạo từ 2026-07-16 tới 2026-09-23. Một số vẫn ở trạng thái ACTIVE hoặc DEPARTED dù giờ đi đã qua.
   - **0 chuyến tương lai.**
4. **Bản sao `busmanagement_gd2` vẫn còn** (G3-7 chưa đóng). Có thể làm mới nó để tổng duyệt CT3.

**Code (số dòng theo HEAD `cf71dd5`):**

5. **Bộ sinh** (`config/HistoricalDataBackfill.java`):
   - `eligibleRoutes()` (dòng 495) lấy **mọi** tuyến ≤ 480 phút, nên hiện nay sẽ ra 9 tuyến.
   - Mỗi xe và mỗi tài xế tối đa 1 chuyến/ngày. Khung thiếu xe bị bỏ trống (dòng 378).
   - Chạy bổ sung thì **bỏ qua cả ngày** đã có dữ liệu bộ sinh.
   - Giá vé = 1.000đ/km (dòng 674).
   - Chuyến tăng cường chạy sau chuyến gốc 30/40/50 phút (`EXTRA_MINUTES`, dòng 146).
6. **Quy luật** (`config/HistorySimulation.java`): giờ (06h 72%, 12h 58%, 18h 77% số ghế), thứ trong tuần theo chiều, tăng 4%/năm, trôi, nhảy bậc (35%/năm, ±15%), mùa, Tết và lễ nhỏ theo chiều, bão, huỷ 6%, nhiễu nhị thức âm (size 40). Mỗi tuyến tự rút ngẫu nhiên theo id tuyến (các luồng `LEVEL`, `WALK`, `JUMP`, `JITTER`).
7. **Mô hình** (`service/SlotDemandModel.java`):
   - Hệ số lễ theo (dịp lễ, ngày lệch), gộp theo nhóm chiều, cộng một phần lệch riêng của từng chuỗi, dùng chung cho mọi năm.
   - Test tham chiếu đọc file đóng băng trong `src/test/resources/forecast/`, nên sinh lại dữ liệu **không** làm đỏ các test này.
8. **Công thức** (`service/ForecastService.java`):
   - `isHot` dòng 482, `tripsNeeded` dòng 491, javadoc của lớp dòng 58;
   - số ghế xe chuẩn lấy theo `suitableBusType` (dòng 289);
   - `FORECAST_HORIZON_DAYS` = 7 (dòng 121), `PREVIEW_DAYS` = 35 (dòng 124).

   Bản đang nằm trên đĩa đã bị IDE format lại, nên số dòng khác (CM-1).
9. **Màn Đề xuất** (`service/RecommendationService.java`):
   - chỉ đọc (javadoc dòng 84, `@Transactional(readOnly = true)` dòng 129);
   - chi phí chỉ tính một chiều (`estimateCost`, dòng 407);
   - mỗi khung được giả định có đúng một xe chuẩn (`seatsBefore` bắt đầu bằng số ghế chuẩn) và **không đọc các chuyến đã có**;
   - `MAX_DAILY_DRIVING_HOURS` = 8 (dòng 115).
10. **Job 10 giây** (`service/TripService.java`):
    - `@Scheduled(fixedRate = 10_000)` ở dòng 76; `isHotTrip` dòng 99 hỏi `Trip.needsReinforcement()`;
    - `createExtraTrip` tạo chuyến +30 phút (dòng 156);
    - không bỏ qua chuyến tăng cường khi xét chuyến gốc;
    - `@EnableScheduling` ở `BusManagementApplication.java:25`.
11. **Chọn xe:** `findBestAvailableBus` (`TripService.java:308`) lấy xe READY, không bận ±1 giờ, chưa tới ngưỡng bảo trì, ưu tiên đúng loại. **Không xét vị trí.**
    - `Route` có `getDepartureStation()`/`getDestinationStation()` (dòng 115, 129), nên suy được cặp tuyến và vị trí xe mà không phải đổi schema của `Route`.
    - `Bus` và `Driver` không có trường vị trí hay bến nhà.
12. **Không có "lịch thường lệ" trong DB** (Hidden Cost #10c).
    - "Bảng điều hành" `/admin/dispatch` (`DispatchController`) là màn **chuyển trạng thái** chuyến (xuất phát, huỷ, hoàn thành), không phải màn kế hoạch.
13. **Roadmap §4 Non-Goal "No daily cron job"** (`THESIS_ROADMAP.md:53`). Dòng đó tự ghi rằng thêm bộ hẹn giờ là quyết định riêng của chủ dự án.
14. **Test liên quan tới công thức số chuyến:** `ForecastServiceRulesTest` (dòng 57–81), chú thích "⌈45 ÷ 19,8⌉" ở `WhatIfSimulationServiceTest` (dòng 39) và `RecommendationServiceTest` (dòng 80). Với 45 khách thì công thức mới vẫn ra 3 chuyến, nên các ca 45 khách không đổi kết quả.

**Phát hiện phụ chưa có trong sổ lỗi nào** (ghi ở CT0, theo CM-11):

| # | Phát hiện | Chỗ | Xử lý ở |
|---|---|---|---|
| P1 | Giờ lái chỉ cộng vào ngày khởi hành. **Đã ghi thành lỗi #34** | `TripService.getDrivingHoursForDate` ~752 | Chưa sửa. Chuyến thường lệ của 3 cặp không dính; chuyến tăng cường +30 phút trên Đà Lạt 18:00 thì dính 0,5 giờ (QĐ-26). Sửa cùng L1 |
| P2 | Mức 8 giờ bị dùng như "mỗi chuyến" (⌈thời gian ÷ 8⌉ tài xế, min(phần chia, 8)). **Không phải lỗi:** roadmap §9 gọi đây là "per-trip share cap" | `TripService` ~236, ~497 | Xem lại khi làm cặp dài (QĐ-27) |
| P3 | Màn Đề xuất chỉ tính chi phí một chiều | `RecommendationService.java:407` | CT7 |
| P4 | Màn Đề xuất không trừ chuyến đã có; chuyến tạo tay không gắn với chuyến gốc, nên job vẫn thêm được | `RecommendationService`; `AdminTripManagementController.java:178` | CT7, CT8 |
| P5 | Job không bỏ qua chuyến tăng cường, nên tăng cường có thể nối chuỗi +30 phút | `TripService.scanAndSuggestExtraTrips` dòng 76 | CT8 |
| P6 | Tài xế chỉ bận ±30 phút quanh chuyến và không có vị trí | `TripService` | CT5, CT9 |
| P7 | `tripsNeeded` chia cho 0,9 × ghế (quyết định đã hết hợp, không phải lỗi) | `ForecastService.java:491` | CT1 |
| P8 | Hai chiều MĐ↔NN gợi ý hai loại xe khác nhau (22 / 40) | tuyến 1, 4 | Chỉ ghi lại; hai tuyến này ngoài phạm vi (QĐ-19) |

---

## 4. Việc của từng giai đoạn

> **Quy tắc chung cho mọi giai đoạn:**
> - Bắt đầu bằng một **vòng thảo luận ngắn**: đối chiếu với code, chốt các mục CM của giai đoạn, ghi vào `route_pairs_decisions.md`. Sau đó mới làm.
> - Mỗi việc xong thì đánh `[x]`, kèm ngày và chỗ kiểm chứng (lệnh, số đo, file:dòng).
> - Mỗi giai đoạn xong thì app vẫn chạy được.
> - Kiểm chứng bằng `mvnw test` **và** chạy app thật (skill `verify`).
> - Chủ dự án tự test rồi mới commit.

### CT0 — Khung theo dõi, ghi quyết định, dọn việc tồn (chỉ tài liệu)

**Mục tiêu:** có kế hoạch được duyệt và một chỗ duy nhất ghi quyết định, trước khi đụng vào code.

- [x] Tạo `route_pairs_decisions.md` (QĐ-18…QĐ-27, CM-1…CM-15). 2026-11-16.
- [x] Tạo file này. 2026-11-16.
- [x] `THESIS_ROADMAP.md`: thêm một mục ở §7 trỏ về đây, một mục ở §8. 2026-11-16.
- [x] Chủ dự án duyệt kế hoạch. 2026-11-16 (*"oke. chốt hãy thực hiện đi"*).
- [x] **CM-1:** bỏ diff format của `ForecastService.java`. 2026-11-16.
  - Đã lưu bản sao ra scratchpad của phiên, rồi `git checkout -- <file>`.
  - Sau đó file giống hệt HEAD: `tripsNeeded` ở dòng 491, đúng số mà bài 14 trích (`FS:491-496`).
- [x] **CM-11:** ghi vào `docs/todo/current_bugs_found.md`, mục "Ghi khi lập kế hoạch đợt cặp tuyến (2026-11-16)". 2026-11-16.
  - Đã grep cả `project_report.md`: không mục nào trùng.
  - **P1 thành lỗi #34 (sai sót thật).** SQL trên DB thật đếm được 41 chuyến lái qua nửa đêm: 37 chuyến tăng cường Đà Lạt 18:30 (mỗi chuyến 0,5 giờ rơi sang ngày sau); chuyến #4 (3,88 giờ); chuyến #14 (17,48 giờ).
  - **P2 không phải lỗi:** roadmap §9 gọi đây là "per-trip share cap".
  - **P3, P7:** quyết định đã hết hợp. **P4, P5, P6:** có hướng sửa ở CT5–CT9. **P8:** dữ liệu ngoài phạm vi.
  - **Sửa một câu sai ở QĐ-26:** bản đầu ghi "lỗi giờ lái theo ngày khởi hành không phát sinh". Thật ra nó vẫn phát sinh với chuyến tăng cường +30 phút của job trên tuyến Đà Lạt.
- [x] Commit `docs:` khi chủ dự án bảo. 2026-11-17: commit `docs:` ngay sau `ed38ce6`, đã push.

**Xong khi:** kế hoạch được duyệt, CM-1 và CM-11 đã chốt.

### CT1 — Công thức số chuyến (QĐ-18)

**Mục tiêu:** khung 40 khách / xe 22 chỗ cần 2 chuyến, không phải 3. Định nghĩa "nóng" giữ nguyên.

**Cần trước:** CM-1 (vì CT1 sửa đúng file `ForecastService.java`).

- [x] `ForecastService.tripsNeeded` thành `Math.max(2, (int) Math.ceil(demand / standardSeats))` khi nóng (dòng 495). 2026-11-16.
  - Đã sửa javadoc của lớp (dòng 56–58), của hàm (dòng 486–490), và tiêu đề khối (dòng 478).
  - **Số dòng giữ nguyên** (883 dòng như HEAD), để không lệch các trích dẫn trong bài học.
  - Hằng `REINFORCEMENT_THRESHOLD` vẫn dùng cho `isHot`.
- [x] Các chỗ khác nêu công thức cũ, cũng giữ nguyên số dòng. 2026-11-16.
  - Javadoc `ForecastPointDto.tripsNeeded` (dòng 46).
  - Chú thích trên màn Dự báo (`demand-forecast.html:200`): *"khi đó số chuyến cần = ⌈nhu cầu ÷ ghế xe chuẩn⌉, ít nhất 2"*.
  - `WhatIfSimulationService` và `RecommendationService` không nêu công thức, nên không phải sửa.
- [x] `ForecastServiceRulesTest`. 2026-11-16.
  - Phép quét 0–300 khách × xe 22/40/50 chỗ giờ so với công thức mới.
  - Chú thích về cái bẫy làm tròn viết lại cho công thức mới.
  - Các ca GĐ5 không đổi kết quả.
  - Thêm 5 ca QĐ-18: 40/22 → 2, 44/22 → 2, 44,01/22 → 3, 21/22 → 2, 45/22 → 3.
- [x] `RecommendationServiceTest.scarceBus_goesToTheMostProfitableExtraTrip_notTheEarliest` **đỏ ở lần chạy đầu, đúng như dự kiến**. 2026-11-16.
  - Khung 07:00 có khoảng 40 khách / 22 chỗ: công thức cũ ra 3 chuyến, mới ra 2. Tổng thẻ 21 → 14, thẻ lỗ 14 → 7.
  - Ý của test (xe khan hiếm về chuyến lãi nhất, không phải chuyến sớm nhất) vẫn kiểm được. Đã sửa số và chú thích.
  - Chú thích "⌈45 ÷ 19,8⌉" ở test này và `WhatIfSimulationServiceTest` đổi thành "⌈45 ÷ 22⌉" (vẫn ra 3).
- [x] `mvnw test`: **217/217**, 0 lỗi. 2026-11-16.
  - **Thử làm hỏng:** đặt lại mẫu số `0,9 × ghế` thì đúng 2 test đỏ: `reinforcementFlag_andTripsNeeded_areOneDefinition` và `scarceBus_…`. Sau đó đã khôi phục.
- [x] `verify` trên DB thật, chỉ đọc. 2026-11-16. Chạy cùng app ở cổng 8099, trước khi sửa (PID 27560) và sau khi sửa (PID 8228).
  - **Ở nhu cầu 100%, mọi con số giống hệt:** 31 khung nóng, 31 thẻ, 3 lãi / 28 lỗ, What-if 31 chuyến thêm.
    - Lý do: hôm nay không khung nào cần ≥ 3 chuyến theo công thức cũ. Cả 31 khung đều "cần 2 chuyến".
    - Số khung nóng không đổi (đúng yêu cầu), số thẻ không tăng.
  - **What-if khi tăng nhu cầu:** 130%: 55 → 54 chuyến thêm; 150%: 63 → 62; 200%: 103 → 85.
  - Màn Dự báo hiện chú thích mới. Log không có `TemplateProcessingException`.
  - Số dòng 6 bảng và MD5 của `trips`, `buses` giống hệt trước và sau.
- [x] Tài liệu. 2026-11-16.
  - `forecast_decisions.md`: dòng QĐ-16 ở mục 1, G5-6, thêm dòng lịch sử.
  - `current_functional_spec.md`, `Proj_functions_summary.md`, `02_project_context.md`, `docs/testing/test_case.md`.
  - Roadmap §5 Phase 6 (Output); §9 ghi chú GĐ5 được đánh dấu "superseded in part", không viết lại.
  - Bài 14: mục 3 tóm tắt, ví dụ 39,9 khách (3 → 2), bảng 8.4 (thêm dòng 40 khách), cái bẫy, 8.7, bài tập (a). Build lại HTML bằng `_build/build.mjs`; diff HTML chỉ chạm đúng các đoạn đã sửa.
  - **Giữ nguyên, vì là bản ghi lịch sử:** các mục nhật ký roadmap §7/§8 của GĐ5, `forecast_progress.md` sự thật 1, M-4.

**Xong khi:** test xanh; ba màn hiển thị đúng công thức mới; tài liệu khớp code.

### CT2 — Cấu hình tuyến, loại xe và dữ liệu mẫu (QĐ-19, QĐ-20)

**Mục tiêu:** DB thật có đúng 3 cặp với loại xe đã chốt, trước khi sinh dữ liệu. Lý do: bộ sinh tính nhu cầu theo số ghế xe chuẩn của tuyến.

**Cần chốt:** CM-13.

- [x] **Sao lưu** DB thật trước khi ghi: `E:\Y4 S2\PRETHESIS\ProjectBackup\db\busmanagement_2026-11-16_truoc-CT2.sql` (3,7 MB, kết thúc bằng "Dump completed"). 2026-11-16.
- [x] **Đổi loại xe gợi ý của tuyến 10 và 11 sang Limousine 22** qua chính form tuyến. 2026-11-16.
  - App ở cổng 8099, PID 15052. Gửi `POST /admin/routes/edit/{id}` với đủ giá trị cũ, chỉ đổi `suitableBusType=1`; mỗi lần một phiên cookie riêng.
  - Cả hai trả 302 kèm "Cập nhật tuyến đường thành công!".
  - Diff bảng `routes` + `route_stations` trước/sau: chỉ `suitable_bus_type_id` của tuyến 10, 11 đổi từ 3 sang 1. Km, thời gian, hướng dòng khách, lộ trình giữ nguyên.
  - Số dòng 6 bảng và MD5 của `trips`, `buses` giống hệt đầu phiên. Log không có lỗi template.
- [x] **SQL chỉ đọc kiểm 3 cặp.** 2026-11-16. Cặp 2/12, 10/11, 13/5 đều:
  - đảo đúng bến;
  - cùng km, cùng thời gian, cùng loại xe (Giường nằm 40 / Limousine 22 / Ghế ngồi 50);
  - hướng dòng khách `LEAVING_HUB/TOWARD_HUB`;
  - mỗi tuyến đúng 2 bến.
- [x] **CM-13: sửa `DataInitializer`** cho khớp DB thật. 2026-11-16.
  - Thêm bến Vũng Tàu (dòng 173).
  - Đổi loại xe gợi ý: Miền Đông→Đà Lạt sang Giường nằm, Cần Thơ→Miền Đông sang Ghế ngồi.
  - Thêm 4 tuyến chiều ngược (dòng 196–202), đúng thứ tự tạo trên DB thật.
  - **Giữ nguyên 6 tuyến cũ**, đúng QĐ-19 ("không xoá tuyến cũ"). Các chuyến mẫu không đổi. Không test nào dùng profile `demo`.
- [x] **Chạy thử dữ liệu mẫu an toàn.** 2026-11-16.
  - Dùng một lớp test tạm với `@ActiveProfiles("demo")`. Cấu hình test luôn đè datasource sang `busmanagement_test`, nên không thể chạm DB thật.
  - Kết quả: 8 bến, 10 tuyến, 3 cặp quanh Miền Đông khớp đủ các điều kiện trên; 1/1 test xanh.
  - DB thật trước/sau vẫn 21.211 chuyến, 12 tuyến.
  - **Đã xoá lớp test tạm** (cùng báo cáo surefire của nó). Không giữ lâu dài, vì seed ghi vào DB test sẽ làm sai các test khác.
- [x] `mvnw test` **217/217** sau khi xoá lớp tạm. 2026-11-16.
- [x] **Tài liệu.** 2026-11-16.
  - `setup_guide.md` và `database_schema.md` chỉ mô tả seed chung chung, không phải sửa.
  - `test_case.md`: TC_STA_001 đổi 7 → 8 bến; ca seed đổi 7 bến / 6 tuyến → 8 / 10.
  - `Proj_functions_summary.md` §4.
  - **Sửa luôn các trích dẫn đã lệch từ GĐ1 (`79ba96c`) mà GĐ7 rà sót:** bài 02 (`:166-173`, 8 bến, DB thật 12 bến), bài 03 (`:175-202`, `:249-262`, `:256`, `:258-259`, `:194`, 10 tuyến), bài 04 (`:304-314`), bài 05 (`:346`). Build lại HTML bài 02–05; diff HTML chỉ chạm đúng các dòng đã sửa.

**Xong khi:** 6 tuyến trên DB thật đúng QĐ-19, QĐ-20; seed khớp (nếu CM-13 chọn sửa).

### CT5a — Định nghĩa vị trí và bến nhà (QĐ-23; tách ra ngày 2026-11-17)

**Mục tiêu:** luật "xe hay tài xế đang ở bến nào" được viết **một lần**, để bộ sinh (CT3), script kiểm (PART C) và app (CT5b) cùng dùng. Mỗi xe, mỗi tài xế có bến nhà. **Hành vi chọn xe của app chưa đổi.**

**Cần trước:** CT2. **Đã chốt 2026-11-17:** CM-5 (cách B: cứng cho máy, mềm cho người, áp cả tài xế), CM-12 (cách (a): cột riêng trên xe và tài xế).

- [x] **Bến nhà:** cột `home_station_id` trên `buses` và `drivers`. Hibernate tự thêm khi khởi động (`ddl-auto=update`), cho phép null, có khoá ngoại tới `stations`. 2026-11-17.
  - Field `Bus.homeStation` và `Driver.homeStation` (LAZY, `@ToString.Exclude`) đặt **ở cuối lớp**. Với `Driver`, tên annotation viết đầy đủ để không thêm dòng import. Nhờ vậy không dòng nào đang được trích bị xê dịch (tiền lệ `Route.holidayFlow`).
  - **Bắt buộc:**
    - `BusService.validate()` gọi `requireHomeStation()`, nên áp cho cả tạo lẫn sửa; `updateBus()` chép trường ngay trước `validate()`.
    - `DriverService.createDriver()` kiểm **trước mọi lần ghi**; `updateDriver()` chép qua `requireHomeStation()`.
  - **Form:** ô "Bến Nhà" `required` ở cuối form xe và form tài xế. Danh sách bến lấy từ `@ModelAttribute("stations")` ở cuối `AdminBusController` / `AdminDriverController`, tiêm `StationService`, vẫn đúng mẫu "chỉ tiêm Service".
  - **Danh sách:** thêm cột "Bến Nhà" ngay trước cột "Hành Động".
  - **Phát hiện khi kiểm:** `th:field` làm mất `selected` của dòng nhắc, nên xe mới âm thầm nhận bến đầu danh sách (Mỹ Đình). Đã sửa ô bến nhà bằng `name` + `th:selected`. Ô loại xe có sẵn cùng lỗi, đã ghi vào sổ lỗi (mục nhỏ 2026-11-17), chưa sửa.
- [x] **Luật vị trí viết ở đúng một chỗ:** `domain/LocationRule`. 2026-11-17.
  - Gồm `Leg`, `legOf(Trip)`, `lastLegArrivedBy`, `stationAt`, và `canDepart` (hai bản: nhận cả danh sách lượt, hoặc nhận sẵn lượt gần nhất cho bộ sinh).
  - Thời gian nghỉ do nơi gọi truyền vào, không khai lại hằng số của `TripService`.
  - **Chưa** gắn vào `findBestAvailableBus` hay `findBestAvailableDriver`.
- [x] **Test**, 2026-11-17:
  - `LocationRuleTest`: 6 test.
  - `BusServiceTest` +3 (tạo thiếu bến nhà, sửa thiếu bến nhà, đổi bến nhà); helper form có bến nhà.
  - `DriverServiceHomeStationTest`: 4 test, mới.
  - Test "sửa thiếu bến nhà" của xe lần đầu đỏ, vì đọc bản trong bộ nhớ của chính transaction. Đã sửa để đọc lại từ DB (`flush` + `clear`), đúng điều app thật đảm bảo (transaction bị huỷ).
  - **Thử làm hỏng:** bỏ kiểm ở `validate()`, bỏ kiểm ở `createDriver()`, bỏ điều kiện "nghỉ đủ" trong `canDepart` thì đúng 4 test mong đợi đỏ. Đã khôi phục.
- [x] **Ghi DB thật**, 2026-11-17.
  - Sao lưu `ProjectBackup\db\busmanagement_2026-11-17_truoc-CT5a.sql` (3,7 MB, đầy đủ).
  - App mới tự thêm 2 cột; sau đó `UPDATE … SET home_station_id = 3` đổi đúng 20 xe và 36 tài xế.
  - So với ảnh chụp trước CT5a: số dòng 7 bảng và MD5 của `trips`, `buses` (trừ cột mới), `drivers` **giống hệt**; chỉ có thêm 2 cột.
- [x] **`verify` chỉ đọc trên DB thật** (cổng 8099, PID 10300), 2026-11-17:
  - Danh sách xe và tài xế hiện "Bến xe Miền Đông" 20 và 36 lần.
  - Form sửa chọn sẵn đúng một bến. Form tạo hiện dòng nhắc có `selected`.
  - **Việc chọn xe y như trước:** so với mốc chụp lúc code chưa đổi (PID 27848), giống hệt từng byte ở danh sách xe / tài xế khả dụng của hai khung giờ, màn Đề xuất, What-if, ô chọn xe của màn Duyệt (chuyến #8). Màn Dự báo chỉ khác dòng "Tính lúc … trong … ms".
  - Log không có `TemplateProcessingException` hay `LazyInitializationException`.
- [x] **`verify` có ghi, trên bản sao** (`busmanagement_test` sao từ DB thật, khớp số dòng và MD5; cổng 8098, PID 21796, 10 kết nối, tất cả vào bản sao), 2026-11-17:
  - xe: sửa sang Đà Lạt → lưu; sửa thiếu bến nhà → từ chối, giữ Đà Lạt; tạo thiếu → từ chối, số xe không đổi; tạo có → lưu;
  - tài xế: sửa sang Cần Thơ → lưu, các trường khác giống DB thật tới MD5; sửa thiếu → từ chối, giữ Cần Thơ; tạo thiếu → từ chối, **không** sinh tài khoản (37 → 37); tạo có → lưu.
- [x] **Tài liệu**, 2026-11-17:
  - `database_schema.md` (2 cột, quan hệ, sơ đồ ER), spec, summary, `test_case.md` (TC_BUS_012, TC_BUS_013), sổ lỗi (mục nhỏ ô loại xe).
  - Bài 01 và 04: bảng thành phần, mục điều cốt lõi.
  - **Trỏ lại 72 trích dẫn số dòng** bằng script so dòng với HEAD, cộng 6 chỗ sửa tay. Ba ghi chép có ngày ở `Proj_functions_summary.md` (dòng 332, 342, 343, "kiểm 2026-09-22") **cố ý giữ nguyên**, vì đó là số của code ngày ấy.
  - Build lại HTML bài 00a, 00b, 01, 03, 04, 05.

**Xong khi:** mọi xe và tài xế có bến nhà; luật vị trí có test; app chọn xe y như cũ.

### CT3 — Bộ sinh mới, chạy trên bản sao (QĐ-21)

**Mục tiêu:** lịch sử 3 cặp Sài Gòn có quy luật cặp, khách về sau lễ ±15%, và xe chạy theo vòng. Kiểm chứng đầy đủ trên bản sao trước khi đụng DB thật.

**Cần trước:** CT2, CT5a. **Đã chốt 2026-11-17:** CM-2 (H1 "về ngay", QĐ-21 (d)), CM-3 (tâm 100%, QĐ-21 (b)). Bến nhà và luật vị trí lấy từ CT5a.

- [x] **Danh sách tuyến** (2026-11-17): thuộc tính `backfill.route-ids`, mặc định `2,12,5,13,10,11`, thay cho việc lọc ≤ 480 phút. Bộ sinh DỪNG nếu một tuyến không có trong DB, dài quá 8 giờ, thiếu bến, hoặc không khớp **đúng một** chiều ngược trong danh sách (theo đề xuất CM-6).
- [x] **`HistorySimulation`, chung mức theo cặp:** `LEVEL`, `WALK`, `JUMP`, `JITTER` rút theo khoá cặp (`RouteProfile.pairKey()` = id nhỏ hơn của hai chiều). Mùa, thứ, hình dạng lễ, bão, hủy, nhiễu, ý định tăng cường vẫn theo từng chiều.
- [x] **Khách về sau lễ:** với mỗi (cặp, dịp lễ, năm), biên độ đợt về được giải bằng chia đôi khoảng sao cho tổng khách dư đợt về = r × tổng khách dư đợt đi; r đều trong [0,85; 1,15], luồng mới `RETURN = 11`. Mô phỏng chạy tới hết năm sau (`horizonEnd`) để luôn thấy trọn một đợt lễ, nên chạy bổ sung ra đúng cùng con số.
- [x] **Xe chạy vòng** — lớp mới `HistoricalDataBackfill.Dispatcher`, giữ trạng thái qua các ngày:
  - xếp theo giờ đi; cùng giờ thì xáo theo ngày như cũ;
  - chuyến thường: xe ở đúng bến, nghỉ ≥ 1 giờ, đúng loại trước, **chờ lâu nhất đi trước**; tài xế ở đúng bến, nghỉ ≥ 30 phút, lái ≤ 8 giờ trong **mỗi ngày lịch** (chuyến qua đêm tách tại 00:00, `minutesByDay`);
  - khung không có xe hay tài xế hợp lệ thì bỏ trống và được đếm;
  - xe rảnh cho chuyến thêm = xe **đang ở bến nhà**, chưa có lượt; xe lớn nhất trước. Xe của lịch thường lệ đang ở bến xa không bị lấy đi;
  - "về ngay" (QĐ-21 (d)): xe tăng cường về ở chuyến chiều về sớm nhất mà xe và tài xế kịp; không tìm được lượt về (3 ngày) thì không chạy chuyến thêm đó;
  - gửi xe lên trước: nhu cầu thêm xe ở chiều về (hôm nay và ngày mai) được chia cho chuyến chiều đi **muộn nhất còn kịp**, mỗi khung tối đa 3 chuyến thêm;
  - **thêm so với kế hoạch:** chuyến thường ở bến xa bị hủy thì xe và tài xế đứng yên rồi cũng về ngay. Thiếu luật này thì mỗi lần hủy để dư một xe ở bến xa và đội xe trôi dần ra bến xa (test `busesParkedAtFarEnds_doNotDriftOverTheYears` bắt được);
  - ngày đầu: mọi người ở bến nhà đọc từ DB; mọi phép "đang ở đâu, chạy tiếp được chưa" gọi `LocationRule.canDepart`.
- [x] **Chuyến có sẵn của chủ dự án** (#33): vẫn giữ xe và người trong mọi ngày chúng chạm tới. **Cách hiểu khi cài (chưa chốt):** chúng nằm ngoài vòng xe của bộ sinh, chỉ chặn, không đổi vị trí của ai. Lý do: 19 chuyến này là chuyến thử tay, tự chúng đã không nối nhau (ví dụ xe 2 chạy Miền Đông → Đà Lạt ngày 16/7 rồi Mỹ Đình → Niệm Nghĩa ngày 19/7).
- [x] **Chạy bổ sung:** chỉ nối tiếp; ngày đã sinh phải liền một mạch từ EPOCH, không thì DỪNG. `Dispatcher.resume` đọc lượt cuối của từng xe, tài xế và giờ lái vắt qua ngày đầu từ chính các chuyến đã ghi. Chỗ có thể lệch so với chạy liền: lượt tăng cường vắt qua ranh giới (xe ở bến xa về ở chuyến sớm nhất).
- [x] Viết lại chú thích đầu `HistoricalDataBackfill` và thêm mục "cặp tuyến" vào chú thích đầu `HistorySimulation`.
- [x] **Test:**
  - `HistorySimulationTest` 10 (thêm 3): tất định cho cặp, chung mức theo cặp, r = tỉ lệ khách dư về/đi và tâm 100%;
  - `HistoricalDataBackfillPlanTest` 14, viết lại trên 3 cặp và đội xe như DB thật, 1.389 ngày: vị trí liên tục và nghỉ đủ cho xe và tài xế, ≤ 8 giờ/ngày lịch, bằng lái, vé ≤ ghế, khung chiều đi không thiếu xe, chiều về chỉ thiếu khi chuyến đưa xe lên bị hủy, xe tăng cường về ngay, đợt về có xe gửi lên trước, số xe ở bến xa không trôi, #33;
  - `HistoricalDataBackfillGenerateTest` 11 (thêm 4): vòng xe trên DB test, chạy bổ sung nối tiếp, từ chối ngày đứt quãng, tuyến thiếu chiều ngược, tuyến có hai chiều ngược, xe thiếu bến nhà.
- [x] **Script** trong `docs/development/route_pairs_research/` (README có cách chạy): PART A `ct3_check_old_history.sql`, PART B `ct3_delete_old_history.sql`, PART C `ct3_check_new_history.sql`.
- [x] **Tổng duyệt trên bản sao** `busmanagement_ct3` (tạo mới từ dump DB thật sáng 2026-11-17; `busmanagement_gd2` để yên vì G3-7):
  - bản sao giống hệt DB thật (số dòng, MD5 `trips`, `buses`, `drivers`, `routes`);
  - PART A đúng số kỳ vọng: 21.192 chuyến GĐ3 (342 thêm), không gì trỏ vào, 3.252.020 km cần trừ;
  - PART B: lần đầu dừng ở lỗi MySQL 1137 (bảng tạm đọc hai lần trong một câu) **trước mọi lệnh ghi**; sửa script rồi chạy lại: còn 19 chuyến, odometer 3.270.405 → 18.385, km từ lần bảo dưỡng 18.355 trước và sau;
  - bộ sinh (JVM thứ hai cổng 8098, 10 kết nối, tất cả vào `busmanagement_ct3`): 1.416 ngày, 26.670 chuyến, file nhu cầu thật 25.488 khung;
  - PART C: **mọi cột vi phạm = 0**;
  - DB thật giống hệt trước và sau.
- [x] **Số đo trên bản sao** (GĐ3 trên DB thật để so, trong ngoặc):
  - tự chấm màn Dự báo 8 tuần: **16,5%**, SN4 sạch 17,9% (16,06% / 17,54%). Tết 2026: 17,8%, SN4 39,6% (19,83% / 35,92%);
  - màn Dự báo **18 chuỗi** (6 tuyến × 3 giờ), bỏ qua 4 nhóm thiếu dữ liệu (tuyến chỉ còn chuyến tay của chủ dự án); 25 ô cần tăng cường trong 7 ngày tới;
  - khung bán hết: 14,5% (20,9%);
  - khung thiếu xe: 791, **toàn bộ ở chiều về** (tuyến 5: 244, 11: 269, 12: 278) — chuyến chiều đi đưa xe lên đã bị hủy. Bộ dự báo chỉ đọc chuyến COMPLETED nên khung này cũng như khung bị hủy;
  - chuyến tăng cường: 1.973 (342), trong đó 1.429 chuyến 0 vé. Chia ra: lượt đi của chuyến thêm 612 (369 không vé), lượt về của chuyến thêm 612 (427), về vì chuyến thường ở bến xa bị hủy 736 (623), khác 13;
  - tổng nhu cầu hai chiều mỗi cặp lệch nhau dưới 1,1% (Vũng Tàu 55.713 / 56.243; Đà Lạt 136.670 / 138.447; Cần Thơ 166.163 / 167.999);
  - tài xế: 5.426 ngày-người chạy từ 2 chuyến trở lên; nhiều nhất 440 phút một ngày;
  - cuối lịch sử: 16 xe ở Miền Đông, 3 ở Đà Lạt, 1 ở Cần Thơ.
- [x] `mvnw test` 243/243.
- [x] **CM-16 chốt 2026-11-17: giữ 6%** (bão và hủy vẫn theo từng chiều). Ghi ở QĐ-21.
- [x] **Kiểm lại trước khi commit:**
  - test 243/243;
  - PART C chạy lại trên bản sao: 18/18 cột vi phạm = 0;
  - `verify` trên app thật (profile mặc định, PID 19752): 16 trang trả 200, log sạch, 0 dòng `[Backfill]`; màn Dự báo vẫn là dữ liệu GĐ3 (15 chuỗi, tới 21/10/2026, tự chấm 16,1%);
  - DB thật giống hệt ảnh chụp đầu CT3.
- [x] **Commit** (không trailer Co-Authored-By): `7cb8fb4` (`feat(backfill):` code + test), `9bb463f` (`docs(study):` bài học), rồi commit `docs:`. Đã push.
- [x] Tài liệu: ghi chú QĐ-10 ở `forecast_decisions.md`, README thư mục script, `setup_guide.md` §4.3, spec, tóm tắt chức năng, roadmap, bài học trỏ lại dòng.

**Xong khi:** bản sao có lịch sử mới, PART C báo 0 vi phạm, các số đo được ghi lại, test xanh. Code commit khi chủ dự án bảo, vì CT4 cần code đã commit.

### CT4 — Thay lịch sử trên DB thật (QĐ-21; làm như GĐ3/QĐ-14)

**Mục tiêu:** DB thật mang lịch sử mới, giống hệt bản tổng duyệt tới từng chuyến.

**Điều kiện trước khi chạy:** code CT3 đã commit; app đã tắt; DB thật vẫn giống lúc sao lưu; **chủ dự án xác nhận lần cuối**.

- [x] **Sao lưu** (2026-11-17) vào `E:\Y4 S2\PRETHESIS\ProjectBackup\db\`:
  - `busmanagement_2026-11-17_truoc-CT4.sql` — toàn bộ DB thật. Khôi phục thử vào `busmanagement_test`: `CHECKSUM TABLE` 11/11 bảng và ảnh chụp giống hệt;
  - `busmanagement_2026-11-17_lich-su-GD3_chi-bang-trips.sql` — riêng 21.192 dòng `trips` của GĐ3.
- [x] **Chủ dự án xác nhận lần cuối** bằng 5 bước, và sửa PART B: *"KHÔNG XÓA, HÃY SAO LƯU LẠI ĐỂ MỞ LẠI NẾU CẦN lịch sử GĐ3 và trừ km khỏi odometer"*. Ghi ở QĐ-21.
  - Cách làm: xoá mềm (`is_deleted = 1`) bằng script mới `ct4_archive_old_history.sql`.
  - Mở lại bằng `ct4_restore_old_history.sql`.
  - Đã kiểm trước: app không có truy vấn SQL thuần hay màn hình nào đọc chuyến đã xoá mềm.
- [x] **Tổng duyệt cách làm mới** trên `busmanagement_test` (khôi phục từ bản sao lưu CT4):
  - PART A → lưu trữ → bộ sinh → PART C cho 0 vi phạm;
  - 26.670 chuyến **giống hệt tổng duyệt CT3** (MD5 trên mọi chuyến và odometer);
  - script mở lại trả về **đúng** dữ liệu đang hiện và odometer của DB thật trước CT4 (cùng MD5), chạy lần hai không đổi gì.
- [x] **Trên DB thật:**
  - PART A: 21.192 chuyến GĐ3, không gì trỏ vào, 3.252.020 km;
  - lưu trữ: 21.192 dòng `is_deleted = 1`, bảng vẫn đủ 21.211 dòng, 17 chuyến còn hiện; odometer 3.270.405 → 18.385, km từ lần bảo dưỡng 18.355 trước và sau;
  - bộ sinh (profile `backfill`, datasource mặc định, 10 kết nối vào `busmanagement`): 26.670 chuyến, **giống hệt tổng duyệt tới từng chuyến** (cùng MD5);
  - PART C: **mọi cột vi phạm = 0**; odometer 4.858.345 = 18.385 + 4.839.960.
- [x] File nhu cầu thật `nhu-cau-that_2023-01-01_2026-11-16_CT4.csv` để cạnh bản sao lưu, ngoài git. Giống từng byte với file của tổng duyệt.
- [x] **`verify`** (profile mặc định):
  - 16 trang trả 200, log sạch;
  - màn Dự báo 18 chuỗi, tới 16/11/2026, 14,5% bán hết, 25 ô cần tăng cường, tự chấm 16,5% (SN4 17,9%), Tết 2026 17,8% (39,6%);
  - Đề xuất 25 thẻ (7 có lãi, 18 lỗ dự kiến, 0 thiếu tài nguyên);
  - What-if: 25 chuyến thêm cần, phủ 25;
  - không trang phân tích nào còn tên bến của tuyến cũ (Mỹ Đình, Niệm Nghĩa, Đà Nẵng, Huế);
  - DB không đổi sau verify.
- [ ] G3-7: xoá bản sao cũ (`busmanagement_gd2`, `busmanagement_ct3`) khi chủ dự án đồng ý.
- [x] Ghi kết quả vào file này, README thư mục script, roadmap §7, §8 và các tài liệu mô tả dữ liệu. **Chưa commit** — chờ chủ dự án bảo.

**Xong khi:** PART C báo 0 vi phạm trên DB thật, kết quả giống tổng duyệt, các trang chạy.

### CT5b — Bật vị trí vào việc chọn xe và tài xế (QĐ-23)

**Mục tiêu:** khi máy chọn xe hay tài xế, nó chỉ chọn người và xe đang ở bến xuất phát.

**Cần trước:** CT4 (lịch sử mới, vị trí có nghĩa). Bến nhà, luật vị trí, CM-5 và CM-12 đã có từ CT5a.

- [x] **AI:** bộ lọc vị trí trong `findBestAvailableBus` (`TripService.java:327`) và `findBestAvailableDriver` (`:499`), ở cả bản hỏi DB lẫn bản `AvailabilityContext`. 2026-11-17.
  - Luật **hai phía**, `LocationRule.fits`: lúc khởi hành đứng ở bến đi, **và** chuyến kế tiếp đã nhận đi từ bến đến.
    - Phía sau được thêm khi `verify` thấy màn Đề xuất giao xe 29A-001.04 cả Đà Lạt 06:00 lẫn 18:00 ngày 22/11, vì thẻ được xét theo lãi.
    - Ghi ở QĐ-23, "cách cài ở CT5b".
  - `AvailabilityContext` có thêm 4 bảng lượt (trước / sau khoảng đã nạp, cho xe và cho người). `buildAvailabilityContext` nạp chúng qua `withLocations` (`TripService.java:571`).
  - Phần vị trí nằm ở mục mới cuối `TripService` ("VỊ TRÍ XE VÀ NGƯỜI", `:1937`). Các dòng 1–1934 chỉ sửa tại chỗ, nên không trích dẫn nào của bài học bị lệch.
  - **Câu vị trí của người, sửa 2026-11-17 (phiên `8bf87e0f`):**
    - Bản đầu gộp ba vai bằng `driver = ? OR assistant = ? OR ? MEMBER OF coDrivers`. MySQL quét cả bảng (`type=ALL`, 47.709 dòng): 0,46 s một lần, API form Tạo hơn 2 phút.
    - Nay mỗi chiều hỏi ba câu theo vai: hai câu dẫn xuất `findFirstByDriverAnd…` / `findFirstByAssistantAnd…`, và một câu JPQL `JOIN t.coDrivers cd WHERE cd = :driver`. `personTripArrivingBy` / `personTripDepartingFrom` lấy chuyến muộn nhất / sớm nhất (giờ, rồi id), nên ra đúng chuyến mà câu gộp sẽ ra.
    - Ba câu cộng lại ≈ 0,012 s.
- [x] **Luồng thủ công** (CM-5 cách B), 2026-11-17:
  - dropdown Duyệt / Sửa xếp ai vừa chuỗi lên đầu (`TripService.java:1504`, `:1535`, `:1563`, `withPeopleAtStartFirst`);
  - ghi chú `busLocationNotes` / `personLocationNotes`: "đang ở …", "chưa rõ đang ở đâu …", "chuyến kế tiếp đi từ …";
  - form Tạo gửi `routeId` cho `/api/admin/trips/available-resources`; JSON có thêm khoá `note`;
  - `validateBusForTrip` / `validateStaffForTrip` không đổi.
- [x] **Test:**
  - `TripServiceLocationTest` 14 test, mới. 2 test "mọi vai trên xe" thêm ở phiên `8bf87e0f`.
  - `LocationRuleTest` 8 (+2).
  - `TripServiceAvailabilityContextTest` và các test chọn xe cũ vẫn xanh.
  - `mvnw test` **259/259**.
  - **Thử làm hỏng:** bỏ câu tài xế phụ phía trước, bỏ câu phụ xe phía sau, đổi "muộn nhất" thành "sớm nhất", bỏ câu tài xế phụ phía sau. Lần nào cũng đúng 2 test mới đỏ. Đã khôi phục, khớp từng byte.
- [x] **`verify` trên DB thật** (profile mặc định, chỉ đọc, PID 28688), 2026-11-17:
  - 19 trang trả 200, log sạch. DB giống hệt trước và sau (47.881 dòng `trips`, MD5 `85b6136f…`).
  - **Màn Đề xuất:** 25 thẻ, 19 đủ điều kiện, 6 thiếu tài nguyên (5 Đà Lạt → Miền Đông, 1 Cần Thơ → Miền Đông).
    - Bộ kiểm độc lập lấy vị trí xuất phát từ SQL, rồi đi theo từng xe, từng người: **0 vi phạm** chuỗi, 0 chồng giờ.
    - Thẻ thiếu xe là đúng luật. Ở Đà Lạt chỉ có 29A-001.04 `READY`; xe 15, 19 đang `TRAVELING`. Ở Cần Thơ chỉ có xe 7, đang `TRAVELING`.
  - **Form Sửa chuyến 2535:** ghi chú của 11 xe, 36 tài xế, 36 phụ xe khớp vị trí tính bằng SQL tại giờ khởi hành của chuyến: 0 chỗ lệch.
  - **API form Tạo, 6 tuyến:** vẫn mời đủ 11 xe, ai vừa chuỗi luôn đứng trước.
    - Tuyến 12: chỉ 29A-001.04, đứng đầu.
    - Tuyến 11: không ai vừa.
    - Không gửi tuyến: không ghi chú, thứ tự như cũ.
  - **Thời gian API:** khoảng 28 s. Phần CT5b chỉ ≈ 3 s; trước khi sửa câu vị trí, phần này ≈ 114 s. 23 s còn lại là hai câu có sẵn, ghi thành **lỗi #35**.
- [x] **`verify` có ghi trên bản sao** (`busmanagement_test`, cổng 8098, PID 29488; 10 kết nối vào bản sao, 0 vào DB thật), 2026-11-17:
  - Xe 29A-001.01 + tài xế #2 chạy vòng tạo qua form: tuyến 10 lúc 06:00, tuyến 11 lúc 12:00, ngày 21/11.
  - Chuyến tuyến 11 nóng → tăng cường #51358 **không xe** (ở Vũng Tàu chỉ có xe đang bận). Màn Duyệt mời đủ 10 xe / 30 người, ai cũng có ghi chú.
  - Chuyến tuyến 10 nóng → #51359 được giao **11A-111.11** (Limousine 0 km ở Miền Đông) và kíp #3/#5 ở Miền Đông. Mở lại màn Duyệt của #51358: 11A-111.11 và kíp đó lên đầu, không ghi chú.
  - Từ chối #51358 → job tạo lại #51360 với **11A-111.11 + kíp #5/#3**, tức một vòng đi–về đúng chuỗi. Xác nhận 1-click → `ACTIVE`.
  - Ghi thành `TC_AI_019`, `TC_AI_020`, `TC_APR_017`.
- [x] **Tài liệu**, 2026-11-17:
  - spec (bến nhà xe và tài xế; mục mới "Location in bus and driver selection"; giới hạn của màn Đề xuất);
  - summary (§3, §7.6, §7.7);
  - bài 00a, 01, 04, 06 (§6.1, §9.1, §9.4), dựng lại HTML;
  - `test_case.md` (3 TC, bảng thống kê);
  - QĐ-23 "cách cài ở CT5b";
  - sổ lỗi (#35; mục "tài xế không có vị trí");
  - roadmap §7, §8.
  - `database_schema.md` không đổi: CT5b không thêm cột hay index.
- **Đầu vào cho CT7, đo ở `verify`:** màn Đề xuất xét thẻ một lượt theo lãi.
  - Xe 29A-001.02 lên Đà Lạt ở thẻ lãi thấp nhất (#25, 19/11 06:00), nên không giúp được 4 thẻ Đà Lạt → Miền Đông đã xét trước nó.
  - Kết quả nhất quán nhưng chưa tối ưu. Kế hoạch theo vòng (M2) phải xếp cả hai chiều cùng lúc.
- [x] **Commit**, 2026-11-17 (chủ dự án: *"tôi hiểu rồi, commit và push đi"*):
  - trước khi commit chạy lại `verify` ngắn trên DB thật (PID 18364): 5 trang 200, API tuyến 12 vẫn đưa 29A-001.04 lên đầu, log sạch;
  - commit `5f52879` (`feat(location):` code + test), `345c8be` (`docs(study):` bài học), rồi commit `docs:`; không trailer Co-Authored-By;
  - push lên `origin/temp`, cùng `6b6ecf8`.

**Lưu ý:** roadmap §3 yêu cầu sửa `TripService` ít nhất có thể. Vị trí là một bộ lọc mới chen vào dòng ứng viên sẵn có, đúng mẫu "luật và khả dụng đan xen". Không dựng thêm tầng service song song.

**Xong khi:** máy không còn chọn xe hay tài xế đang ở bến khác; test xanh; DB thật có bến nhà.

### CT6 — Cặp tuyến và lịch thường lệ (QĐ-22; Hidden Cost #10c)

**Mục tiêu:** app biết tuyến nào đi cặp với tuyến nào, có lịch 06/12/18, và tạo được chuyến thường lệ tương lai theo vòng xe.

**Cần chốt:** CM-4, CM-6, CM-14. **Đã chốt 2026-11-19**, cùng sáu điểm tìm ra khi đối chiếu code và DB: CM-17…CM-22 (`route_pairs_decisions.md`).

- [x] **Suy cặp** (CM-6), 2026-11-19: `RouteService.reverseRoutesOf(route, routes)` (static, cuối lớp, không xê dịch dòng bài 03 trích), đảo bến đầu/cuối, trả mọi tuyến khớp. Bộ lập lịch chỉ ghép khi có đúng một, và chỉ giữ tuyến khi chiều ngược cũng qua (lượt 2). Test `RouteServiceReverseRouteTest` (3).
- [x] **Nguồn giờ chạy thường lệ** (CM-4): `domain/RegularTimetable` (`DEPARTURE_HOURS`, `DEFAULT_ROUTE_IDS`, `ROUTE_IDS_PROPERTY`). `HistorySimulation.DEPARTURE_HOURS` và `HistoricalDataBackfill.routeIds` trỏ về đó, sửa tại chỗ, không thêm dòng. Trước CT6 chỉ có một bản (`HistorySimulation.java:68`), nhưng nó package-private nên app không đọc được.
- [x] **Tạo chuyến thường lệ cho N ngày tới** (`service/RegularTimetableService.generate`):
  - mọi khung xếp chung một trục giờ (CM-19); xe và người chọn bằng `autoAssignResources` của job 10 giây (luật vị trí CT5b), trên context lập lịch `TripService.buildTimetableContext` (CM-21: xe `TRAVELING` có chuyến giải thích được xếp; km chuyến chưa xong cộng vào bảo trì);
  - cổng dry-run rồi lưu qua `createManualTrip` → `ACTIVE` (CM-14); chuyến vừa xếp được giữ chỗ trong context;
  - không tạo trùng: khung có chuyến chính ở mọi trạng thái thì bỏ qua (CM-18, `TripRepository.findMainTripDeparturesBetween`);
  - giá = `RecommendationService.loadLatestPrices` (CM-20); không ai đứng đúng bến → `PENDING_APPROVAL` chưa có xe (CM-17);
  - nút cho Admin; mặc định 7 ngày, trần 14 (CM-21).
- [x] **Controller** `AdminTimetableController` (`/admin/timetable`, chỉ tiêm service); trang `timetable.html`; ô "Lịch Thường Lệ" ở cuối lưới `dashboard.html` (không xê dịch dòng bài 01–03 trích); nhãn "📅 Chuyến thường lệ" ở hàng chờ duyệt, tiêu đề "Phê duyệt chuyến thường lệ" ở màn Duyệt.
- [x] **Test**, 2026-11-19:
  - `RegularTimetableServiceTest` (9), `RouteServiceReverseRouteTest` (3). `mvnw test` **273/273**.
  - **Thử làm hỏng 7 chỗ**, lần nào cũng đỏ đúng test cần đỏ: bỏ km cam kết → test bảo trì; chỉ xe `READY` → test xe đang chạy; nhận mọi xe `TRAVELING` → cùng test đó; xếp theo tuyến trước → test vòng xe + bảo trì; không giữ chỗ → hai test đó; bỏ lượt 2 ghép cặp → test cặp tuyến; bỏ kiểm khung đã có → hai test khung. Khôi phục, khớp từng byte.
- [x] **`verify` trên bản sao** `busmanagement_ct6` (cổng 8098, PID 32296; 10 kết nối vào bản sao, 0 vào DB thật):
  - trang trước khi bấm: 3 cặp, giá theo giờ, 0/18 khung mỗi ngày;
  - đóng 5 chuyến demo `DEPARTED` bằng nút "Hoàn thành" (CM-22): 5 xe về `READY`, odometer cộng đúng km;
  - "Tạo lịch" 20/11, 7 ngày: **125 mở bán + 1 chờ phân công** (Vũng Tàu → Miền Đông 06:00 20/11), 19,6 s; bấm lại: 0 + 0, bỏ qua 126 khung, 2,4 s;
  - phép kiểm SQL độc lập (`ct6_check.sql`: vị trí xe, vị trí người, chồng giờ xe ±1 giờ, người ±30 phút, giờ lái/ngày, km bảo trì, số ghế, giá): **0 vi phạm**;
  - mỗi cặp đúng loại xe gợi ý: Vũng Tàu 2 Limousine/ngày, Cần Thơ 2 Ghế ngồi (3 ở ngày đầu), Đà Lạt 4 Giường nằm;
  - 17 trang 200, log sạch; duyệt tay #51360 bằng 29A-001.12 → `ACTIVE`, 22 ghế.
- [x] **`verify` trên DB thật** (cổng 8099, PID 30340), sau sao lưu `ProjectBackup\db\busmanagement_2026-11-19_truoc-CT6.sql` (khôi phục thử, `CHECKSUM TABLE` khớp):
  - màn Đề xuất trước: 18 thẻ đủ điều kiện, 7 thiếu tài nguyên;
  - đóng 5 chuyến demo `DEPARTED` (#3, #6, #11, #12, #13); "Tạo lịch" 20/11, 7 ngày: 125 + 1, 21,2 s; phép kiểm SQL: 0 vi phạm;
  - màn Đề xuất sau: 17 / 8 — xe của chuyến chính nay bị coi là bận (nửa đầu của Hidden Cost #10c);
  - màn Dự báo tự chấm vẫn 16,5% (SN4 17,9%), như sau CT4; 18 trang 200, log sạch;
  - DB thật: 48.008 chuyến (47.882 + 126); #51360 để chờ chủ dự án chọn xe.
- [x] **Tài liệu**, 2026-11-19: spec (mục mới "Regular Timetable"), summary (§7.5, §7.6, §20 mới), `trip_lifecycle_fsm.md` §2, `test_case.md` (TC_TRIP_045…048, bảng thống kê), QĐ-22 "cách cài ở CT6" và CM-4/6/14/17…23, roadmap §6 (#10c), §7, §8, bài 01 (ghi chú: chuyến 13 đã đóng). `database_schema.md` không đổi: CT6 không thêm cột hay bảng.
- **Phát hiện khi làm, ghi lại:**
  - **CM-23 (mở):** chuyến thường lệ đã qua giờ mà không ai bấm "Xuất phát/Hoàn thành" vẫn `ACTIVE`. Chúng hiện ở nhóm "Trễ giờ" của Bảng điều hành. Lần chạy bổ sung lịch sử sau này cũng bỏ qua cả ngày đó (`HistoricalDataBackfill.existingGeneratorDays`, `:1229-1241`, coi mọi chuyến lúc 06/12/18:00 là "dạng bộ sinh"), nên dự báo không có quan sát cho những ngày ấy.
  - **Kíp quay đầu ở Đà Lạt đổi vai.** Ví dụ 21/11, xe 29A-001.14: đi 06:00 với tài xế A1 và phụ xe A2, về 18:00 với A2 lái và A1 làm phụ xe. Luật hiện hành cho phép, vì 8 giờ chỉ tính vai lái (lỗi #13), nhưng hai người trực 06:00–24:00. Đưa vào CT9 / tối ưu kíp (QĐ-26, QĐ-27).
  - **Bộ dựng bài học** (`docs/study/_build/build.mjs`) không tách được tiêu đề từ file `.md` có CRLF, như file `.md` trong worktree. Vì vậy HTML bài 00b, 06 của phiên #35 có tiêu đề trang rỗng. Đã sửa: một dòng chuẩn hoá CRLF → LF lúc đọc. Đã dựng lại 00b, 06, 01; dựng file LF cho kết quả y hệt bản đã commit.
- [x] **Commit**, 2026-11-23 (chủ dự án: *"check lại đảm bảo mọi thứ hoạt động đúng và logic, sau đó commit và push"*): `5b38e71` (code + test), `8957099` (bài 01), `c36fef3` (tài liệu); đã push. Trước đó kiểm lại: test 273/273 trên đúng bản CT6 (worktree `wt-ct6`), "Tạo lịch" 27/11–03/12 trên bản sao DB thật (bản CT6 + CT7) 121 + 5 chuyến, `ct6_check.sql` 0 vi phạm. Phát hiện CM-25 (bảo trì trong lịch nhìn trước).

**Xong khi:** DB thật có chuyến thường lệ tương lai cho 3 cặp, mỗi xe chạy đúng vòng. **Đạt 2026-11-19** (20–26/11).

### CT7 — Màn "Kế hoạch cặp tuyến" (M2), lãi cả vòng (QĐ-22)

**Mục tiêu:** nhìn vào là thấy chiều nào được tăng cường, xe lấy từ đâu, đi rồi về thế nào, và lãi cả vòng.

**Cần chốt:** CM-7, CM-9. **Đã chốt 2026-11-22**, cùng PA-B, cách 2a và việc chia CT7a / CT7b (chủ dự án: *"tôi đồng ý với những đề xuất của bạn (từ 1-5)"*; ghi ở QĐ-22, "cách cài lãi cả vòng ở CT7").

Làm trong worktree `wt-ct7` ngoài repo (app 8080 của chủ dự án chạy từ `target/classes`), sửa trên cây chính rồi chép sang để biên dịch và test. CT6 vẫn chưa commit; bản CT6 sạch trước khi sửa CT7 nằm ở `ProjectBackup\patches\2026-11-22_ct6_truoc-CT7\`.

**CT7a — con số:**
- [x] **Một phép tính tiền chung** `service/LoopEconomics` (mới, package-private), 2026-11-22: chi phí một lượt (`:335`, trước bị chép ở `RecommendationService.estimateCost` và `WhatIfSimulationService.tripCost`), giá vé lịch sử (`latestPrices`, trước bị chép ở What-if), chuyến đã có theo khung (`inSlot` `:124`), tiền của lượt đi kèm (`companion` `:153`), lượt đi kèm theo kế hoạch (`planned` `:182`), giờ "về ngay" / "đưa xe tới" (`returnTimes` `:220`, `bringTimes` `:240`).
- [x] **Lãi cả vòng — PA-B** (P3): `RecommendationService.buildCard` (`:314`) chọn xe ở bến đi; không có thì "đưa xe tới trước" (`bringBefore` `:440`, giữ chỗ thử rồi trả lại bằng `AvailabilityContext.release` mới `:168`); rồi "về ngay" (`returnAfter` `:460`) hay "không cần" (`endsAtHome` `:612`). Mọi lượt qua `selectBestAvailableBus/Driver` và cổng dry-run (`assignLeg` `:474`). DTO: `LoopLegKind`, `RecommendationCardDto.CompanionLeg`, tiền riêng của chuyến thêm (`tripRevenue`, `tripCost`), `existingTrips/Seats/Assumed`; `RecommendationViewDto.alreadyInSlotCount`.
- [x] **Trừ chuyến đã có — cách 2a** (P4): `TripRepository.findSlotSeatsBetween` (`:681`, cuối file), trạng thái `RecommendationService.SLOT_STATUSES` (`:143`, mọi trạng thái trừ CANCELLED).
- [x] **What-if — W1** (CM-7): tiền cả vòng theo kế hoạch, trừ chuyến đã có (giữ bất biến "ở 100% cùng số chuyến thêm"), năng lực không đổi; bỏ hai bản chép (`tripCost`, `loadLatestPrices`); trang ghi giới hạn.
- [x] Màn Đề xuất: cột "Lượt đi kèm", tiền "cả vòng" kèm phần của chuyến thêm, "khung đã có M chuyến · S ghế" / "chưa có lịch", số chuyến thêm đã có; bỏ câu cũ "lịch thường lệ chưa có trong hệ thống".

**CT7b — màn hình:**
- [x] **Trang mới** `/admin/analytics/pair-plan` (CM-9): `PairPlanController`, `PairPlanService.build` (`:100`), `PairPlanViewDto`, `pair-plan.html`. Tổng quan 7 ngày (★ / thiếu / lãi cả vòng theo cặp, theo ngày); mỗi cặp một bảng cho ngày đang xem — hai dòng khung (dự báo, ghế đã có, "cần N", số ★), mỗi xe một hàng theo giờ 06/12/18, ★ chuyến thêm và lượt đi kèm, "tối ngủ ở", ghi chú của hàng ★; danh sách chuyến thêm chưa xếp được; dòng tóm tắt số xe, số ★, km lượt đi kèm, lãi cả vòng. Truy vấn `TripRepository.findPlanTrips` (`:691`). Ô "Kế Hoạch Cặp Tuyến" ở cuối lưới trang chủ (không xê dịch dòng bài học trích), nút ở màn Đề xuất, liên kết dưới bảng Dự báo.
- [x] **Màn Dự báo gom theo cặp** (M1): `PairPlanService.forecastByPair` (`:392`), key `pairGroups` ở `ForecastController`; bảng 7 ngày có dòng tiêu đề cặp, chiều rời bến nhà trước. Chỉ đổi thứ tự hiển thị.

**Test** (2026-11-22): `mvnw test` **292/292** (273 + 19).
- Mới: `LoopEconomicsTest` (9, không Spring), `RecommendationLoopTest` (6: về ngay ở khung sớm nhất; What-if khớp tổng các thẻ khi xe đi đúng kế hoạch; luật 8 giờ đẩy lượt về sang sáng hôm sau; đưa xe tới bằng khung muộn nhất còn kịp; xe kẹt ở bến xa về bến nhà không cần lượt đi kèm; trừ chuyến đã có, ghế thật, chuyến hủy không tính), `PairPlanServiceTest` (4).
- Sửa đúng 2 test cũ khẳng định chi phí một chiều: `RecommendationServiceTest` (820.000 → cả vòng 1.640.000đ, tuyến test không có chiều ngược nên tính như một lượt về rỗng), `WhatIfSimulationServiceTest` (3.280.000 → 6.560.000đ). Test "What-if khớp tổng các thẻ" vẫn xanh, không sửa.
- **Thử làm hỏng 8 chỗ** trong worktree, chỗ nào cũng đúng test mong đợi đỏ: giờ về sắp ngược; lượt về không giữ chỗ; giờ đưa xe tới sắp ngược; bỏ chuyến đã có; bỏ loại "không cần"; lượt đi kèm chen trước nhu cầu của khung; chỉ thử một giờ về; What-if bỏ chi phí lượt đi kèm. Đã chép lại bản đúng.

**`verify`** (2026-11-22, 23:29–23:55):
- **DB thật, chỉ đọc** (app từ worktree, cổng 8099, PID 24712): 16 trang 200, log sạch; Đề xuất 9,0 s, Kế hoạch cặp tuyến 6–7 s, Dự báo 12 s (lần đầu khớp), What-if 0,9 s. DB giống hệt trước và sau (MD5 chuyến và xe).
  - **Trước / sau trên cùng dữ liệu** (bản CT6 chạy lại ở cổng 8097, chỉ đọc): thẻ đủ điều kiện **19 → 22** (3 thẻ ở bến xa nay được "đưa xe tới"), thiếu tài nguyên 6 → 3; có lãi / lỗ **7 / 18 → 3 / 22** (lãi một chiều → lãi cả vòng). What-if: chi phí 44,05 → **88,1 triệu** (đúng gấp đôi), doanh thu 35,59 → 36,66 triệu, lãi −8,46 → **−51,44 triệu**; "chỉ chạy chuyến có lãi" 7 chuyến +7,06 triệu → 1 chuyến +1,38 triệu.
  - Lượt đi kèm của 25 thẻ: 10 về ngay, 10 đưa xe tới, 3 không cần (xe của lịch 26/11 kẹt ở Đà Lạt vì lịch chưa tạo cho 27–29/11), 2 tính như về rỗng.
  - **Phép kiểm độc lập** (đọc thẻ từ HTML, dò bằng SQL): 22 thẻ đủ điều kiện → 41 lượt giữ chỗ trên 15 xe và 23 người: **0** trùng giờ (xe ±1 giờ, người ±30 phút), **0** đứt chuỗi vị trí, **0** ngày quá 8 giờ lái; chi phí cả vòng khớp phép tính tay ở cả 22 thẻ; mọi lượt về đi sau giờ tới + 1 giờ, mọi lượt đưa xe tới về trước giờ đi 1 giờ, đúng tuyến chiều ngược.
- **Bản sao** (`busmanagement_test` khôi phục từ DB thật, cổng 8098, 10 kết nối vào bản sao, 0 vào DB thật): "Tạo lịch" 27/11–03/12 bằng code CT7: 121 mở bán + 5 chờ phân công, 30 s — lịch CT6 vẫn chạy. Sau đó màn Đề xuất chỉ còn **1/25** thẻ đủ điều kiện; **bản CT6 trên cùng bản sao cũng 1/25** → ghi thành **CM-24** (dưới). Phép kiểm độc lập trên bản sao: 0 vi phạm.

**Phát hiện khi làm, ghi lại:**
- **CM-24 (mở):** lịch thường lệ phủ cả tuần thì hết tài xế cho chuyến thêm. Lịch gán phụ xe cho mọi chuyến (121/121), mỗi ngày dùng 22–26 / 33 tài xế; và luật vị trí hai phía (CT5b) xét riêng chuyến thêm: lúc 18:00 27/11 có 15 tài xế rảnh ở Miền Đông nhưng chuyến kế tiếp của họ lại đi từ Miền Đông, nên bị loại khỏi chuyến Miền Đông → Cần Thơ, dù lượt về sẽ đưa họ về kịp. Đề xuất "chọn cả vòng một lần" (một xe + một kíp cho cả vòng), chưa làm.
- Màn Đề xuất ở 27–29/11 trên DB thật dùng xe của lịch 26/11 đang kẹt ở bến xa như xe rảnh (loại "không cần"). Đúng luật, nhưng là vì chưa bấm "Tạo lịch" cho những ngày đó; trang kế hoạch ghi rõ "chưa có lịch".

- [x] **Tài liệu**, 2026-11-22: QĐ-22 "cách cài lãi cả vòng ở CT7", CM-7, CM-9 (đóng), CM-24 (mở), lịch sử; spec (Reinforcement Recommendation, What-if, Demand Forecast, mục mới "Route-Pair Plan"); summary; `test_case.md`; roadmap §6 (#10c), §7, §8; bài 14 (ví dụ tiền cả vòng, bảng gom theo cặp) và trỏ lại trích dẫn ở bài 00a, 03, 05, 06, 14 (dựng lại HTML); sổ lỗi (ba mục "đã có hướng xử lý" ghi đã làm ở CT7); javadoc `CostParameterService` (sửa tại chỗ, không đổi số dòng). `database_schema.md` không đổi: không thêm cột hay bảng.
- [x] **Commit**, 2026-11-23, sau CT6: `fae225d` `feat(recommendation)` (CT7a; test 288/288 trên đúng bản đó), `3e513f0` `feat(pair-plan)` (CT7b; 292/292), `deeece9` `docs(study)` (bài 00a, 03, 05, 06, 14), rồi commit `docs:`; đã push. Trước đó `verify` DB thật chỉ đọc và phép kiểm độc lập 45 lượt, 0 vi phạm (mục 1).

**Xong khi:** màn mới chạy trên DB thật; số liệu khớp phép tính tay ở một vài thẻ. **Đạt 2026-11-22** (22/22 thẻ khớp phép tính tay).

### CT7c — Chọn cả vòng một lần (CM-24 cách PA-A; QĐ-22, QĐ-26)

**Mục tiêu:** khi lịch thường lệ phủ cả tuần, màn Đề xuất vẫn tìm được xe và kíp cho chuyến thêm. **Đã chốt 2026-11-23** (ghi ở QĐ-22, "cách cài cả vòng một lần ở CT7c").

- [x] `TripService.selectLoop` + `driverForLoop` (cuối lớp, chỉ cộng thêm): một xe + một tài xế cho cả khối bến nhà → bến nhà; bận phủ cả khối (xe ±1 giờ, người ±30 phút); vị trí `LocationRule.fits` với cả khối là một lượt; giờ lái theo ngày khởi hành từng lượt, ≤ 8 giờ; bằng lái mọi ngày; bảo trì km mọi lượt. 2026-11-23.
- [x] `RecommendationService.buildCard` viết lại: `chooseLoop` (về ngay / không cần / đưa xe tới / tính như về rỗng chỉ khi không có cặp), `firstLoop`, cổng dry-run cho từng lượt, giữ chỗ cả vòng; thiếu cả vòng → NO_RESOURCE kèm lý do. Bỏ `AvailabilityContext.release`. Chú thích cuối `recommendation.html`. 2026-11-23.
- [x] Test: `RecommendationLoopTest` +2 (8): đúng ca CM-24 (tài xế duy nhất có chuyến kế tiếp đi từ bến nhà vẫn chạy cả vòng; xét riêng chuyến thêm thì không ai lái được); không xếp được cả vòng → thiếu tài nguyên. Hai test đỏ trên code HEAD. Thử làm hỏng (S11, xét tài xế theo lượt đầu): test CM-24 đỏ.
- [x] `verify` — bản sao A (code HEAD, sau "Tạo lịch" 27/11–03/12): 2/25; cùng bản sao, chỉ CT7c: **19/25**, cả 17 vòng có lượt đi kèm cùng một xe. Mô phỏng phiên trước dự đoán 21/25.
- [x] Tài liệu: QĐ-22, CM-24 (đóng), spec, summary, `test_case.md` (TC_RC_012, TC_RC_013, TC_RC_008 sửa), bài 06 (dòng gọi cổng), bài 14.
- [x] Commit `f708f44` ngày 2026-11-25, dựng từ bản chụp riêng của CT7c (`ProjectBackup\patches\2026-11-23_ct7c_cm24\`) thẳng vào index; test 294/294 trên đúng bản đó. Đã push.

### CT7d — Bảo trì trong lịch (CM-25 cách M1; QĐ-22)

**Mục tiêu:** lịch nhìn trước có bước bảo trì, để cặp Đà Lạt không phải chạy xe khác loại sau khoảng 10 ngày. **Đã chốt 2026-11-23** (ghi ở QĐ-22, "cách cài bảo trì trong lịch ở CT7d": R1–R5, ai đặt ngày, giới hạn).

- [x] Cột `buses.planned_maintenance_date` (`Bus.plannedMaintenanceDate`, cuối lớp), `Bus.hasPlannedMaintenance`, `Bus.reachesNearMaintenance` (định nghĩa duy nhất của mốc 90%; dòng 75 sửa tại chỗ). 2026-11-23.
- [x] `TripRepository.findCommittedKm`, `sumCommittedKmOfBus` (cuối file); `AvailabilityContext`: sổ km đã cam kết cho mọi context (chuyến chưa xong + chuyến giữ chỗ), `lookAhead(xe)`. 2026-11-23.
- [x] `TripService`: bộ chọn một chuyến (dòng 328–330 sửa tại chỗ), `selectLoop`, validator (+4 dòng sau 1178: chặn ngày bảo trì; km từ 0 sau ngày đó), hai ô chọn xe (tại chỗ), `buildAvailabilityContext` nạp sổ km (dòng 571 tại chỗ), `buildTimetableContext` / `reserveRegularTrip` gọn lại; mục cuối "BẢO TRÌ TRONG LỊCH" (R1–R5, `planDueMaintenance`). 2026-11-23.
- [x] `RegularTimetableService` gọi `planDueMaintenance` trước mỗi khung; `MaintenancePlanDto`; trang Lịch thường lệ (ngày vừa xếp, thẻ "Ngày bảo trì dự kiến", cột "🔧 bảo trì"), danh sách xe, form xe (chỉ xem), trang Kế hoạch cặp tuyến. `BusService.recordMaintenance` (xoá ngày khi km lần bảo trì cuối tăng), `saveBus` để trống ngày. 2026-11-23.
- [x] Test: `TripServiceMaintenancePlanTest` (9, mới); `RegularTimetableServiceTest.maintenanceCounts…` viết lại (xe C 8.800 km trước CT7d đi rồi kẹt ở bến xa; nay được đặt ngày bảo trì). `mvnw test` **303/303**.
- [x] **Thử làm hỏng 11 chỗ** (S1–S10 cho CT7d, S11 cho CT7c), mỗi lần gỡ một luật trong worktree rồi chạy 3 lớp test: bỏ quãng về (3 đỏ), không tính lại từ 0 (3), validator bỏ ngày bảo trì (1), không nạp sổ km (2), ghi nhận không xoá ngày (1), không đặt ngày (2), ngày đã qua vẫn tác dụng (1), máy xếp theo km thật (1), tạo xe nhận ngày từ form (1), giữ chỗ không cộng km (1). Khôi phục, khớp từng byte.
- [x] **`verify` trên bản sao B** (`busmanagement_cm25b`, dựng từ cùng dump với A, khớp DB thật; cổng 8098, 10 kết nối vào bản sao, 0 vào DB thật): "Tạo lịch" 27/11–03/12 → 125 mở bán + 1 chờ phân công (A: 121 + 5), 31,7 s; 3 ngày bảo trì: 29A-001.14 ngày 27/11 (4.430 km tính cả lịch), .03 ngày 29/11 (3.900), .04 ngày 30/11 (4.000); Đà Lạt 29/42 giường nằm, 12 Limousine, 1 chờ (A: 9/26/4 Ghế ngồi/3); Cần Thơ, Vũng Tàu đủ 42/42.
  - Phép kiểm độc lập `check_maint.py` (đọc DB, tính lại từ 0 sau ngày bảo trì): 0 chuyến đẩy km ≥ 90%, 0 chuyến chạm ngày bảo trì; xe kẹt ở bến xa 5 (A) → 1 (29A-001.15, từ lịch 20–26/11 tạo trước CT7d). `ct6_check.sql`: 0 vi phạm ở mọi mục trừ mục 7 = 3 — đúng ba xe có ngày bảo trì, vì script CT6 cộng km qua cả ngày bảo trì.
  - Màn Đề xuất 15/25 (A: 2/25); phép kiểm độc lập `check_cards.py` (30 lượt, 10 xe, 10 người): 0 trùng giờ, 0 đứt chuỗi, 0 ngày quá 8 giờ, 0 vượt mốc bảo trì. Cùng phép kiểm trên thẻ của bản chỉ CT7c: 12 lượt vượt mốc bảo trì — lý do 19 → 15.
  - 16 trang 200, log sạch; trang xe, form, Lịch thường lệ, Kế hoạch cặp tuyến hiện đúng ngày; màn Duyệt #51493 (27/11) không mời xe .14; duyệt tay bằng .14 bị từ chối đúng câu; ghi nhận bảo trì xe .14 qua form → ngày xoá, .03 giữ.
- [x] **`verify` chỉ đọc trên DB thật** (cổng 8099), sau sao lưu `ProjectBackup\db\busmanagement_2026-11-23_truoc-CT7d.sql` (khôi phục thử hai lần: bản sao A và B khớp ảnh chụp DB thật): 17 trang 200, log sạch; màn Đề xuất 25/25 (trước: 24/25), 48 lượt 0 vi phạm; DB thật giống hệt trước và sau, chỉ thêm cột `planned_maintenance_date` (trống ở 20 xe).
- [x] Tài liệu: QĐ-22, CM-21 (ghi chú), CM-25 (đóng), spec, summary, `database_schema.md`, `test_case.md` (TC_BUS_014, TC_TRIP_049, TC_TRIP_050, bảng thống kê); bài 01, 05, 06, 14 (nội dung) và trỏ lại 135 trích dẫn ở 8 bài (script so dòng với HEAD, 13 chỗ sửa tay), dựng lại HTML; roadmap §7, §8.
- [x] **Chủ dự án tự test trên DB thật (2026-11-25)**, có sao lưu trước (`ProjectBackup\db\busmanagement_2026-11-24_truoc-lich-27-11.sql`): "Tạo lịch" 27/11–03/12 → 125 mở bán + 1 chờ phân công, 3 ngày bảo trì như bản sao B, cặp Đà Lạt 29 giường nằm / 12 Limousine / 1 chờ, 0 chuyến chạm ngày bảo trì.
- [x] ~~**Mở, chủ dự án quyết:** xe kẹt từ lịch cũ (29A-001.15)~~ — **đóng 2026-11-25: người chọn tay đưa về**, không thêm luật "lượt về bến nhà được tới 100%". Chủ dự án duyệt chuyến chờ #51493 (Đà Lạt → Miền Đông 12:00 27/11) bằng xe .15 + tài xế 18, bỏ phụ xe của #51517 để chuỗi của tài xế 18 liền (chuyến tạo tay 06:00 cùng ngày đã xoá: khung đó có #51487, tài xế 21 đang ở Miền Đông). Sau đó phép kiểm chuỗi trên 251 chuyến từ 20/11: 0 đứt chuỗi, 0 trùng giờ, 0 ngày quá 8 giờ; `check_maint.py`: 0 xe kẹt, 1 lượt vượt mốc = #51493 do người chọn. Bản sao `busmanagement_cm25a`, `busmanagement_cm25b` giữ lại để xem; xoá cùng G3-7.
- [x] Commit `eabf985` ngày 2026-11-25 (test 303/303, `verify` DB thật chỉ đọc — kết quả ở mục 1). Đã push.

### CT8 — Tự tạo chuyến chờ duyệt mỗi đêm; sửa job 10 giây (QĐ-24)

**Mục tiêu:** dự báo tự đề xuất bằng chuyến chờ duyệt thật; job 10 giây chỉ còn lo phần bất ngờ.

**Cần chốt:** CM-10, CM-15. **Cần chủ dự án duyệt** việc sửa roadmap §4.

- [ ] Sửa câu chữ roadmap §4 "No daily cron job" (có chủ dự án duyệt). Ghi chú QĐ-17 và Phase 7 rằng "Đề xuất chỉ đọc" đã được QĐ-24 thay.
- [ ] **Job đêm** (`@Scheduled(cron = …)`) gọi một service mới:
  - lấy các chuyến thêm có lãi cả vòng > 0 cho D+3…D+7;
  - trừ đi chuyến đã có trong khung;
  - tạo chuyến `PENDING_APPROVAL` gắn với chuyến thường lệ của khung (`originalTrip`);
  - tự phân công qua đường có sẵn trong `TripService`.
- [ ] Khi dự báo giảm: tự rút chuyến tự tạo còn chờ duyệt.
- [ ] **Job 10 giây:** so vé thật với tổng ghế của cả khung; không lấy chuyến tăng cường làm chuyến gốc (P5). Giữ nguyên các điều kiện 72 giờ, 48 giờ, 95%.
- [ ] Nối việc tạo chuyến thường lệ (CT6) vào job đêm, nếu CM-14 chọn vậy.
- [ ] **Test:** `TripServiceScannerHotGateTest` cùng test mới. `verify`, lưu ý khi bật app thì cả job thật cũng chạy.
- [ ] **Tài liệu:** spec, summary, `trip_lifecycle_fsm.md` nếu luồng trạng thái đổi.

**Xong khi:** sau một đêm (hoặc một lần chạy tay), DB có đúng các chuyến chờ duyệt mong đợi, không trùng; job 10 giây không còn nối chuỗi.

### CT9 — Luật giữ–về, tiền chờ, kíp lái ở lại cùng xe (QĐ-25, QĐ-26)

**Mục tiêu:** xe tăng cường ở bến xa được giữ hay cho về theo đúng một phép so; kíp lái ở lại cùng xe.

**Cần chốt:** CM-8.

- [ ] **`CostParameters`:** thêm lương chờ (người/ngày) và phí xe nằm bến (ngày). Sửa form `cost-parameters.html`, `CostParameterService` (giá trị mặc định), `CostParameterServiceTest`.
- [ ] **Service quyết định giữ/về** cho từng xe tăng cường đang ở bến xa:
  - dự báo 35 ngày;
  - lợi ích = min(tiền vé chiều về kỳ vọng, 2 × chi phí một chiều), so với tiền chờ còn lại;
  - các bước gọi về / bỏ chuyến tăng cường;
  - xe tới trước về trước;
  - kiểm lại mỗi đêm (job của CT8).
- [ ] Giữ kíp lái ở bến xa cùng xe: tài xế bận tới khi xe về (P6).
- [ ] Màn "Kế hoạch cặp tuyến" hiện xe đang được giữ, lý do, ngày dự kiến về, và phép so tiền.
- [ ] **Cho luận văn:** mô phỏng "về ngay" so với "giữ–về" trên một dịp lễ trong lịch sử. So km rỗng, số xe phải dùng, lãi.
- [ ] Test, `verify`, tài liệu.

**Xong khi:** quyết định giữ/về của từng xe khớp phép tính tay trên vài ca; có bảng mô phỏng cho luận văn.

### CT10 — Tài liệu, bài học, chuẩn bị bảo vệ

- [ ] Rà mọi tài liệu đang tracked theo code, như GĐ7:
  - roadmap §5, §6 (#10c), §7, §9;
  - spec, summary, `02_project_context.md`, `database_schema.md`, `setup_guide.md`, `test_case.md`;
  - `docs/ai`, `README.md`;
  - hai sổ lỗi.
- [ ] Bài học: cập nhật số liệu bài 14 sau khi sinh lại (NB size, các ví dụ); bài mới về cặp tuyến và điều độ nếu chủ dự án muốn. Build HTML.
  - Ghi chú CT4: các ví dụ "DB thật" của bài 14 vẫn là số GĐ3, nay đã lưu trữ. Ví dụ: tuyến 1 lúc 06:00 ngày 13/02/2026; tự chấm 16,06% / 17,54%; 41 ô. Tuyến 1 không còn được sinh. Số mới: 16,5% / 17,9%, 25 ô.
- [ ] Ghi phần giới hạn và hướng phát triển: cặp dài + L1, M3, tăng tần suất B1/B2, tối ưu kíp lái K2.

---

## 5. Nhật ký phiên

| Ngày | Phiên | Việc | Kết quả |
|---|---|---|---|
| 2026-11-10 → 13 | `62ada296`, `66af0a5d`, `e84dbb86` | Thảo luận: 3 cặp tuyến, công thức số chuyến, tài xế tuyến dài, job và dự báo, ý thầy về vòng đi–về (M1/M2/M3), tuyến dài, luật giữ–về | Chỉ thảo luận. Chốt "tăng xe = thêm chuyến cùng khung" |
| 2026-11-16 | `d7e11adb` | Chốt các mục A và B; kiểm DB thật (4 tuyến mới); rà code, test, tài liệu; lập kế hoạch CT0–CT10 | Tạo `route_pairs_decisions.md` và file này; thêm mục roadmap §7, §8. Không đổi code. Chưa commit |
| 2026-11-16 (sau) | `d7e11adb` | Chủ dự án duyệt kế hoạch. Làm CT0 (CM-1, CM-11) và CT1 | Bỏ diff format; ghi lỗi #34 và 7 mục; sửa câu sai ở QĐ-26. CT1: code, test 217/217, thử làm hỏng, `verify` (DB giống hệt), tài liệu, bài 14. **Chưa commit**, chờ chủ dự án test |
| 2026-11-16 (sau nữa) | `d7e11adb` | *"làm CT02 luôn đi"*: làm CT2 | Sao lưu; tuyến 10, 11 sang Limousine 22 qua form (chỉ cột đó đổi); 3 cặp kiểm bằng SQL; `DataInitializer` khớp DB thật, chạy thử trên DB test; test 217/217; sửa trích dẫn lệch ở bài 02–05. **Chưa commit** |
| 2026-11-17 | `d7e11adb` | *"oke, hãy commit và push đi"* | `verify` lại trước khi commit (11 trang 200). Commit `963aa71` (CT1), `bbedfd8` (CT2), `ed38ce6` (bài học), rồi commit `docs:`; push lên `origin/temp` |
| 2026-11-17 (sau) | `d7e11adb` | Giải thích CM-2, CM-3. Chủ dự án hỏi *"sao k thêm vị trí rồi làm bộ sinh"*; đo trên DB thật: 11/20 xe sẽ "kẹt" ở bến ngoài 3 cặp | Chốt CM-2, CM-3; tách CT5 thành CT5a / CT5b. Chỉ sửa tài liệu, chưa commit |
| 2026-11-17 (CT5a) | `d7e11adb` | *"chốt CM-5 cách B, CM-12 cách (a). xong thì hãy kiểm tra lại 1 lần nữa, ổn và ok thì commit và push"* | CT5a: luật vị trí, bến nhà (code, form, danh sách), test (thử làm hỏng ok), sao lưu + đặt bến nhà trên DB thật, `verify` chỉ đọc (chọn xe giống hệt) và có ghi (bản sao), tài liệu + trỏ lại 78 trích dẫn. Kiểm lại lần cuối (test 230/230, 16 trang 200, chọn xe giống hệt), rồi commit `4c3ce51`, `d39c17e`, commit `docs:` và push |
| 2026-11-17 (CT3) | `d7e11adb` | *"hãy làm CT03 đi"* | CT3: mô phỏng theo cặp + r; bộ xếp xe chạy vòng (`Dispatcher`); test 243/243; script PART A/B/C; tổng duyệt trên bản sao `busmanagement_ct3` (PART C 0 vi phạm, DB thật không đổi); số đo ghi ở CT3 |
| 2026-11-17 (CT3, sau) | `d7e11adb` | *"cái này mới là sinh dữ liệu trên bản sau, CT04 mới áp dụng vô dữ liệu thật dk. chốt CM-16 giữ 6%, kiểm tra lại rồi commit và push"* | Xác nhận: CT3 chỉ chạy trên bản sao, CT4 mới áp vào DB thật. Chốt CM-16 (giữ 6%). Kiểm lại: test 243/243, PART C 0 vi phạm, `verify` 16 trang 200, DB thật giống hệt. Commit `7cb8fb4`, `9bb463f`, commit `docs:` và push |
| 2026-11-17 (CT4) | `d7e11adb` | 5 bước CT4, PART B *"KHÔNG XÓA, HÃY SAO LƯU LẠI ĐỂ MỞ LẠI NẾU CẦN lịch sử GĐ3 và trừ km khỏi odometer"* | Sao lưu (khôi phục thử ok) + bản riêng các dòng GĐ3. Script lưu trữ (xoá mềm) và script mở lại, tổng duyệt trên `busmanagement_test`. DB thật: PART A đúng số, lưu trữ 21.192 dòng (không xoá), bộ sinh 26.670 chuyến giống hệt tổng duyệt, PART C 0 vi phạm, `verify` 16 trang 200, Dự báo 18 chuỗi. Chưa commit |
| 2026-11-17 (CT4, sau) | `d7e11adb` | *"commit"* | Commit `6b6ecf8` (tài liệu CT4). Không push — chủ dự án không bảo |
| 2026-11-17 (CT5b) | `d7e11adb` | Bắt đầu CT5b; *"hãy dừng lại để phiên sau tiếp tục làm"* | Code + test vị trí; `verify` thấy lỗi giao một xe hai lần (thêm phía sau của luật) và câu vị trí của người chậm (API form Tạo > 2 phút). Dừng giữa chừng, chưa commit |
| 2026-11-17 (CT5b, tiếp) | `8bf87e0f` | *"sửa truy vấn chậm đi, sau đó hãy kt lại, đảm bảo CT5b đã hoàn thành và test lại app"* | Tách câu vị trí theo vai (+2 test, thử làm hỏng ok); test 259/259; `verify` DB thật (19 trang 200, Đề xuất 25 thẻ / 0 vi phạm, form Sửa và API khớp SQL) và bản sao (job, Duyệt, từ chối, xác nhận); tài liệu; lỗi #35. Chưa commit |
| 2026-11-17 (CT5b, commit) | `8bf87e0f` | *"tôi hiểu rồi, commit và push đi, sau đó sửa #35"* | `verify` ngắn trước commit; commit `5f52879`, `345c8be`, commit `docs:`; push cùng `6b6ecf8` |
| 2026-11-17 (lỗi #35) | `8bf87e0f` | *"sau đó sửa #35 đảm bảo mọi thứ đều nhất quán, logic và phù hợp chuẩn với project"* | Làm trong worktree riêng, vì app 8080 của chủ dự án chạy từ `target/classes` có devtools. Mỗi vai một câu cho bận và giờ lái; khoá sắp xếp giờ lái được nhớ; chứng minh và sửa #36 (test đỏ trên code cũ, A/B trên bản sao: cho lọt và chặn nhầm). Test 261/261; `verify` DB thật và A/B bản sao; tài liệu. Chưa commit |
| 2026-11-19 (CT6) | `dba5da64` | Giải thích lại CT6 kèm ví dụ (DB thật đọc lúc 14:47); chủ dự án *"oke, tôi đồng ý tất cả, hãy kiểm tra những chức năng, file liên quan và làm … xong thì cũng test luôn"* | Chốt CM-4/6/14, CM-17…22. Code trong worktree (#35/#36 mang theo). Test 273/273, thử làm hỏng 7 chỗ. `verify` bản sao rồi DB thật (sao lưu, khôi phục thử): đóng 5 chuyến demo, tạo lịch 20–26/11 = 125 + 1, kiểm SQL 0 vi phạm, Đề xuất 18/7 → 17/8, Dự báo không đổi. Sửa bộ dựng bài học (CRLF) và dựng lại 00b, 06, 01. Mở CM-23. Chưa commit |
| 2026-11-22 (commit #35/#36) | `2d46500c` | Chủ dự án duyệt 5 đề xuất của CT7; *"trước tiên bạn hãy commit #35/36 và push"* | Chép cả cây làm việc (có CT6) ra `ProjectBackup\patches\2026-11-22_ct6_truoc-commit-35-36\`; đặt lại bản #35/#36 của ngày 19/11 để commit. Kiểm lại đúng các file đó trong worktree: test 261/261, `verify` DB thật chỉ đọc (20 trang/API 200, API form Tạo 3,5 s, DB giống hệt). Commit `7fb50f4`, `c615164`, commit `docs:`; push. Rồi chép lại bản CT6 |
| 2026-11-22 (CT7) | `2d46500c` | *"hãy suy luận và tiến hành thực hiện code theo như đã đề xuất. hãy làm thật cẩn thận và thận trọng"* | Chụp CT6 sạch ra `ProjectBackup\patches\2026-11-22_ct6_truoc-CT7\`. CT7a: `LoopEconomics`, lãi cả vòng PA-B, cách 2a, What-if W1. CT7b: trang `/admin/analytics/pair-plan`, Dự báo gom theo cặp. Test 292/292, thử làm hỏng 8 chỗ. `verify` DB thật chỉ đọc (DB giống hệt; 19 → 22 đủ điều kiện, 7 → 3 có lãi; kiểm độc lập 41 lượt 0 vi phạm) và bản sao ("Tạo lịch" 27/11–03/12; 1/25 đủ điều kiện, bản CT6 cũng 1/25 → mở CM-24). Tài liệu, bài học. Chưa commit |
| 2026-11-23 (commit CT6) | `7b523bdf` | *"check lại đảm bảo mọi thứ hoạt động đúng và logic, sau đó commit và push"* | Chụp cả cây làm việc (CT6 + CT7) ra `ProjectBackup\patches\2026-11-23_ct6-ct7_truoc-commit\`. Rà lại code CT6. Test 273/273 trên đúng bản CT6 (worktree `wt-ct6`). Bản sao DB thật (bản CT6 + CT7): "Tạo lịch" 27/11–03/12 ra 121 mở bán + 5 chờ phân công, `ct6_check.sql` 0 vi phạm; DB thật giống hệt. Commit `5b38e71` (code + test), `8957099` (bài 01), rồi commit `docs:`; đã push |
| 2026-11-23 (CT7c, CT7d) | `74613658` | *"tôi duyệt CM24 và CM25 như đã bàn bạc. hãy xem xét kỹ thực hiện nó một cách logic, phù hợp và nhất quán với project"* | CT7c: chọn cả vòng một lần (PA-A). CT7d: ngày bảo trì trong lịch (M1) + máy cộng km đã xếp và quãng về. Test 303/303, thử làm hỏng 11 chỗ. Bản sao A (code cũ) / B (code mới): bản sao DB thật sau "Tạo lịch" 27/11–03/12, cùng dữ liệu, cùng ngày — code trước CT7c/CT7d → code mới: lịch 121 mở bán + 5 chờ phân công → 125 + 1; cặp Đà Lạt 9/42 → 29/42 chuyến giường nằm; xe kẹt ở bến xa 5 → 1; màn Đề xuất 2/25 → 15/25 thẻ đủ điều kiện (chỉ CT7c trên đúng lịch cũ: 19/25, nhưng phép kiểm thấy 12 lượt vượt mốc bảo trì, xe 29A-001.14 tới 5.990 km); DB thật (chỉ đọc) 24/25 → 25/25. Sao lưu DB thật, `verify` chỉ đọc (DB giống hệt, thêm cột). Tài liệu, bài học. Chưa commit |
| 2026-11-23 (commit CT7) | `7b523bdf` | Cùng lời dặn, sau khi commit CT6 | Rà lại code CT7. Tách CT7a khỏi CT7b: `TripRepository` chưa có truy vấn trang kế hoạch, `recommendation.html` chưa có nút sang trang đó. Test trên đúng từng bản sẽ commit: CT7a 288/288, CT7 292/292. `verify` DB thật chỉ đọc (24 lượt gọi 200; 25 thẻ, 24 đủ điều kiện; kiểm độc lập 45 lượt 0 vi phạm) và bản sao ("Tạo lịch" 27/11–03/12: 121 + 5 chuyến, `ct6_check.sql` 0 vi phạm; màn Đề xuất 2/25 → CM-24). DB thật giống hệt. Mở CM-25 (cặp Đà Lạt chỉ còn 9/42 chuyến giường nằm vì luật bảo trì nhìn trước). Commit `fae225d` (CT7a), `3e513f0` (CT7b), `deeece9` (bài học) + commit `docs:`; đã push |
| 2026-11-25 (test trên DB thật, commit CT7c/CT7d) | `8ab0b0c8` | *"hướng dẫn test CT7c và CT7d cùng với tạo lịch"*, rồi *"tôi xóa chuyến #51609 rồi, bây giờ giải quyết vđề kia sao"*, rồi *"tôi kích hoạt rồi, commit và push đi"* | Viết hướng dẫn test (sao lưu, khởi động lại app, "Tạo lịch" từ 27/11, câu SQL kiểm). Chủ dự án tự sao lưu và "Tạo lịch" trên DB thật: 125 + 1, đúng 3 ngày bảo trì, khớp bản sao B. Xe .15 kẹt ở Đà Lạt: chuyến tạo tay 06:00 27/11 bị xoá (trùng khung #51487, tài xế 21 ở Miền Đông); thay bằng duyệt #51493 bằng xe .15 + tài xế 18, bỏ phụ xe #51517; kiểm chuỗi 251 chuyến 0 vi phạm. Worktree mới `wt-precommit` (worktree `wt-cm24` cũ còn bản chép, chưa dọn): CT7c 294/294, đủ 303/303; `verify` chỉ đọc 29 lượt 200, màn Đề xuất 15/25, `check_cards.py` 0 vi phạm, DB giống hệt. Commit `f708f44` (CT7c, từ patch vào thẳng index), `eabf985` (CT7d), `afc27dd` (bài học) + commit `docs:`; đã push |

---

## 6. Quy tắc làm việc cho đợt này

- **Mỗi giai đoạn đi theo vòng:** thảo luận ngắn và chốt CM → ghi `route_pairs_decisions.md` → làm → `mvnw test` + `verify` → cập nhật file này và roadmap → chủ dự án tự test → **chỉ commit khi chủ dự án bảo**.
  - Commit không mang trailer Co-Authored-By.
  - Tách commit theo loại: feature / fix / docs / refactor.
- **Ghi vào DB thật** (CT2, CT4, CT5, CT6, CT8, CT9) luôn phải có sao lưu trước. Thay lịch sử (CT4) còn cần chủ dự án xác nhận lần cuối.
- **Khi bật app:** job 10 giây (`TripService.java:76`) chạy thật. Từ CT8 có thêm job đêm.
- **Quyết định mới hay thay đổi quyết định:** ghi vào `route_pairs_decisions.md` mục 5. Không đổi im lặng trong code.
- **Câu hỏi không phải lệnh làm.** "Nếu làm X thì sao?" nghĩa là giải thích X, không phải bắt tay làm X.
- **Số dòng** trích trong tài liệu phải được dò lại trên code hiện tại trước khi dùng.
