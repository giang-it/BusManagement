# Quyết định đợt "cặp tuyến và điều độ theo dự báo"

> **Sổ quyết định của đợt làm việc nối tiếp đợt thiết kế lại dự báo.**
> - Kế hoạch, tiến độ và nhật ký: [`route_pairs_progress.md`](route_pairs_progress.md).
> - Số QĐ nối tiếp `forecast_decisions.md` (QĐ-1…QĐ-17), để mỗi mã QĐ là duy nhất trong cả project.
> - Quyết định mới hay thay đổi quyết định: ghi vào mục 5 (lịch sử). Không đổi im lặng trong code.
> - Nguồn: thảo luận từ 2026-11-10 tới 2026-11-16. Các phiên đó chỉ thảo luận, không sửa code. Bản ghi gốc nằm trong lịch sử phiên Claude Code (`62ada296…`, `66af0a5d…`, `e84dbb86…`, `d7e11adb…`).
> - Quy ước tên: **MĐ = Bến xe Mỹ Đình** (Hà Nội). **"Sài Gòn" = Bến xe Miền Đông** (id 3 trên DB thật).

---

## 1. Bảng quyết định

| QĐ | Nội dung ngắn | Chốt | Làm ở |
|---|---|---|---|
| QĐ-18 | "Tăng xe" = thêm chuyến cùng khung. Số chuyến cần = max(2, ⌈nhu cầu ÷ ghế⌉) khi khung nóng | 2026-11-11, 2026-11-16 | CT1 |
| QĐ-19 | Phạm vi: 3 cặp Sài Gòn, chỉ tuyến dưới 8 giờ, bộ sinh nhận danh sách tuyến | 2026-11-16 | CT2, CT3 |
| QĐ-20 | Hai chiều một cặp cùng loại xe; Vũng Tàu dùng Limousine 22; giờ chạy 06/12/18 | 2026-11-16 | CT2 |
| QĐ-21 | Sinh lại dữ liệu: quy luật + nhiễu, chung mức theo cặp, khách về sau lễ ±15%, xe chạy vòng | 2026-11-16 | CT3, CT4 |
| QĐ-22 | Mức làm M2 + luật giữ–về; "tối ưu" = lãi cả vòng; lãi tính cả chiều về | 2026-11-16 | CT6, CT7, CT7c, CT7d |
| QĐ-23 | Xe được chọn phải đang ở bến xuất phát; xe dự phòng = xe rảnh tại bến; bến nhà Miền Đông | 2026-11-16 | CT5a (định nghĩa, bến nhà), CT5b (bật vào chọn xe) |
| QĐ-24 | Dự báo tự tạo chuyến CHỜ DUYỆT (7 điểm); job 10 giây chỉ lo phần bất ngờ | 2026-11-16; cách cài 2026-11-25 | CT8 (CT8a, CT8b, CT8c) |
| QĐ-25 | Luật giữ–về cho xe tăng cường: một phép so, nhìn trước 35 ngày | 2026-11-13, 2026-11-16 | CT9 |
| QĐ-26 | Kíp lái ở lại cùng xe, nhận lương cơ bản khi chờ; giữ mức 8 giờ lái/ngày | 2026-11-13, 2026-11-16 | CT9 |
| QĐ-27 | Để sau: cặp dài, tăng tần suất (loại B3), tối ưu kíp lái | 2026-11-16 | — |

---

## 2. Chi tiết từng quyết định

### QĐ-18. Tăng xe và số chuyến cần (chốt 2026-11-11 và 2026-11-16)

- **"Tăng xe"** = thêm một chuyến nữa trong cùng khung giờ. Không phải đổi sang xe lớn hơn. App đã có phần này ở màn Đề xuất.
- **Khung nóng** khi nhu cầu dự báo > 90% ghế xe chuẩn của tuyến. Giữ nguyên, vì đây là cùng ngưỡng với `Trip.needsReinforcement()`.
- **Số chuyến cần:**
  - Khung không nóng: 1.
  - Khung nóng: **max(2, ⌈nhu cầu ÷ ghế xe chuẩn⌉)**.
  - Tức là bỏ hệ số 0,9 ở mẫu số. Định nghĩa "nóng ⇔ cần ≥ 2 chuyến" (mục tiêu của M-4) vẫn giữ nhờ max(2, …).
- **Lý do:** 40 khách, xe 22 chỗ.
  - Công thức cũ: ⌈40 ÷ 19,8⌉ = 3 chuyến. Chuyến thêm thứ hai chỉ chở ≈ 1,5 khách, lỗ ≈ 0,64 triệu.
  - Theo phân phối của chính mô hình, 73% số ngày có ≤ 44 khách.
  - Công thức mới: 2 chuyến.
  - Hệ số 0,9 ở mẫu số được chốt ở QĐ-16 G5-6 khi app còn coi chuyến thêm luôn chở đầy (trước GĐ6). Code không sai, chỉ là quyết định đó đã hết hợp.
- **Thay một phần QĐ-16 G5-6** (phần mẫu số). Job 10 giây không bị QĐ này ảnh hưởng, vì job đọc vé thật, không đọc `tripsNeeded`.
- **Hệ quả đã biết:** khung sát ngưỡng vẫn có thẻ chuyến thêm lỗ. Ví dụ 21 khách trên xe 22 chỗ: khung nóng, cần 2 chuyến, nhưng chuyến thêm chỉ chở ≈ 1,6 khách. Thẻ vẫn hiện với nhãn "lỗ dự kiến". QĐ-24 không cho tự tạo các chuyến lỗ này.

### QĐ-19. Phạm vi tuyến (chốt 2026-11-16)

- **Bộ sinh chỉ sinh 6 tuyến, tức 3 cặp, đều xuất phát hoặc kết thúc ở Bến xe Miền Đông:**

  | Cặp | Đi (id) | Về (id) | Km | Thời gian |
  |---|---|---|---|---|
  | Sài Gòn ↔ Cần Thơ | 13 | 5 | 180 | 210 phút |
  | Sài Gòn ↔ Đà Lạt | 2 | 12 | 300 | 360 phút |
  | Sài Gòn ↔ Vũng Tàu | 10 | 11 | 100 | 120 phút |

  Chủ dự án tạo các tuyến 10–13 trên DB thật ngày 2026-11-16. Đã kiểm: cùng km, cùng thời gian, hướng dòng khách đảo đúng (rời bến lớn / về bến lớn).
- **Không sinh:** MĐ↔Niệm Nghĩa (1, 4), Đà Nẵng→Huế (3), MĐ→Miền Đông 30 giờ (6), MĐ→Đà Nẵng→Cần Thơ 50 giờ (8), tuyến thử A→b→c→d (9).
  - **Không xoá các tuyến này khỏi DB.** Bộ sinh nhận **danh sách tuyến cụ thể** thay vì lấy mọi tuyến ≤ 8 giờ (`HistoricalDataBackfill.eligibleRoutes()`).
  - Tuyến không còn lịch sử sẽ tự rời màn Dự báo, vì mỗi chuỗi cần ≥ 28 ngày quan sát.
- **Chỉ tuyến dưới 8 giờ.** Cặp dài để sau (QĐ-27).
- **Lý do:**
  - Nếu lấy mọi tuyến ≤ 8 giờ như hiện nay thì có 9 tuyến × 3 giờ = 27 chuyến thường/ngày, nhiều hơn 20 xe.
  - 3 cặp chung bến Miền Đông, đúng mẫu "A↔B, A↔C" thầy hướng dẫn nêu.
  - Cặp MĐ↔NN đang lệch loại xe: MĐ→NN Limousine 22, NN→MĐ Giường nằm 40.

### QĐ-20. Loại xe theo cặp và giờ chạy (chốt 2026-11-16)

- **Hai chiều của một cặp dùng cùng loại xe gợi ý:**
  - Vũng Tàu: **Limousine 22**. Đổi từ Ghế ngồi 50 (cách B).
  - Cần Thơ: Ghế ngồi 50.
  - Đà Lạt: Giường nằm 40.
- **"Loại xe phù hợp" vẫn chỉ là GỢI Ý**, như đã chốt 2026-09-23 (Group C(a)) và 2026-09-24 (#26). Hết xe đúng loại thì app vẫn lấy loại khác, và tính theo số ghế thật của xe được chọn.
- **Giờ chạy thường lệ:** 06:00, 12:00, 18:00 cho cả 6 tuyến, cả hai chiều. Chuyến Đà Lạt 18:00 tới nơi lúc 24:00.
- **Lý do chọn cách B:**
  - Đội xe trên DB thật: Limousine 8, Giường nằm 7 (1 xe đang sửa), Ghế ngồi 5.
  - Nếu Vũng Tàu giữ Ghế ngồi thì không cặp nào dùng Limousine. Chuyến tăng cường khi đó phải chạy xe 22 chỗ, trong khi dự báo và màn Đề xuất tính theo xe chuẩn 50 chỗ.
  - Sau khi đổi, tính nhẩm theo vòng xe:

    | Loại xe | Có | Cần cho chuyến thường | Dư |
    |---|---|---|---|
    | Limousine 22 | 8 | 2 | 6 |
    | Ghế ngồi 50 | 5 | 2 | 3 |
    | Giường nằm 40 | 6 (không tính xe đang sửa) | 6 | 0 |

### QĐ-21. Sinh lại dữ liệu (chốt 2026-11-16)

- **Xoá lịch sử do bộ sinh GĐ3 tạo, rồi sinh lại.** Không dùng bộ lọc tuyến. Làm theo đúng cách GĐ3 (QĐ-14): sao lưu, tổng duyệt trên bản sao, chủ dự án xác nhận lần cuối, xuất file nhu cầu thật.
  - **Sửa ngày 2026-11-17 khi làm CT4**, chủ dự án: *"PART B: KHÔNG XÓA, HÃY SAO LƯU LẠI ĐỂ MỞ LẠI NẾU CẦN lịch sử GĐ3 và trừ km khỏi odometer"*.
  - Trên DB thật, lịch sử GĐ3 được **lưu trữ chứ không xoá**: xoá mềm (`is_deleted = 1`, đúng cách app tự xoá một chuyến). Dòng còn nguyên trong bảng; mọi truy vấn của app, bộ sinh và dự báo không thấy nó.
  - Km của nó vẫn được trừ khỏi cả hai mốc odometer.
  - Mở lại bằng `route_pairs_research/ct4_restore_old_history.sql`. Ngoài ra có thêm bản sao lưu riêng các dòng đó.
- **Thứ tự:** tạo đủ cặp tuyến trước (đã xong trên DB thật), chỉnh loại xe (QĐ-20), rồi mới sinh.
- **Giữ nguyên mọi quy luật hiện có của `HistorySimulation`:**
  - giờ trong ngày;
  - thứ trong tuần theo chiều;
  - tăng trưởng, trôi ngẫu nhiên, nhảy bậc;
  - mùa;
  - lễ theo chiều;
  - bão và huỷ chuyến — **vẫn 6% và vẫn rút theo từng chiều, kể cả khi xe chạy vòng** (CM-16, chốt 2026-11-17). Hệ quả đo ở CT3: khung chiều về không có xe khi chuyến chiều đi đưa xe lên bị hủy, và xe về rỗng khi chuyến thường ở bến xa bị hủy;
  - nhiễu nhị thức âm;
  - vé bị chặn bởi số ghế.

  Cũng giữ: tính tất định theo seed và EPOCH, và nguyên tắc "bộ sinh khác cấu trúc mô hình" (để mô hình không tự chứng minh chính nó).
- **Thêm (a). Chung mức theo cặp:** mức tuyến, độ trôi, lần nhảy bậc và độ lớn lễ từng năm được rút ngẫu nhiên theo **cặp**. Chỉ hình dạng theo chiều (thứ trong tuần, trước hay sau lễ) là để riêng.
  - Hiện nay mỗi tuyến tự rút theo id tuyến, nên có thể xảy ra chuyện chiều đi bị nhảy bậc −15% mà chiều về thì không.
- **Thêm (b). Khách quay về sau lễ:** phần khách tăng thêm của cả đợt chiều về = phần tăng thêm của cả đợt chiều đi × **r**. r rút ngẫu nhiên đều trong [0,85; 1,15], mỗi bộ (cặp, dịp lễ, năm) một lần.
  - **Tâm 100%, đã chốt 2026-11-17 (CM-3).** Trung bình bao nhiêu người đi thì bấy nhiêu người về. Không có số liệu thật để nói hơn hay kém, và mọi dao động đã nằm trong ±15%.
  - r áp cho **mọi dịp lễ**, không riêng Tết. Không áp cho cuối tuần thường; nhịp cuối tuần do hệ số thứ trong tuần lo.
  - r được rải theo hình dạng đợt về sẵn có, đông nhất quanh ngày nghỉ cuối.
  - **Ảnh hưởng tới dự báo:**
    - Mô hình vẫn học đúng mức trung bình, vì hệ số lễ của nó chung cho mọi năm.
    - Từng năm, đợt về lệch đi một mức không đoán được. Ví dụ tính nhẩm: ngày đông nhất Đà Lạt→Sài Gòn khoảng 84 khách có thể thành 76–92 khách.
    - Độ lệch chuẩn của đợt về tăng từ khoảng 9% lên khoảng 12%, nhưng đợt về bám theo đợt đi của cùng cặp.
- **Thêm (c). Xe và tài xế chạy nối tiếp (vòng xe):**
  - Chuyến sau của một xe phải xuất phát từ bến mà chuyến trước của nó vừa tới.
  - Xe nghỉ ≥ 1 giờ giữa hai chuyến. Tài xế nghỉ ≥ 30 phút và lái ≤ 8 giờ trong một ngày lịch.
  - Đầu lịch sử, mọi xe và tài xế ở Miền Đông (QĐ-23).
  - Thay bất biến "mỗi xe, mỗi tài xế một chuyến/ngày" của QĐ-10.
  - **Lý do:** 6 tuyến × 3 giờ = 18 chuyến thường/ngày. Nếu mỗi xe chỉ 1 chuyến/ngày thì chỉ còn 2 xe cho tăng cường. Cặp Vũng Tàu chạy theo vòng thì chỉ cần 2 xe cho cả 6 lượt/ngày.
- **Thêm (d). Xe tăng cường trong lịch sử "về ngay" (CM-2, chốt 2026-11-17, cách H1):**
  - Xe tăng cường tới bến xa thì quay về bằng chuyến thường lệ chiều về sớm nhất mà cả xe (nghỉ ≥ 1 giờ) lẫn tài xế (≤ 8 giờ trong ngày lịch) đều kịp. Nó chạy như chuyến tăng cường của khung đó và chở khách tràn của khung, thường là 0 vé.
  - Ngược lại, chiều về cần thêm xe ở bến xa mà ở đó không có xe rảnh: nhà xe trong quá khứ gửi xe từ bến nhà lên trước, đi cùng một chuyến thường lệ chiều đi, cũng chở khách tràn của khung đó.
  - Lượt chạy rỗng được ghi là chuyến tăng cường `COMPLETED` có 0 vé. Nhu cầu của khung không đổi, còn khung có thêm ghế trống nên không bị coi là bán hết, đúng sự thật.
  - Ví dụ: Sài Gòn→Vũng Tàu chạy thêm 06:30, về cùng khung Vũng Tàu→Sài Gòn 12:00 trong ngày. Sài Gòn→Đà Lạt chạy thêm 06:30, về 06:30 sáng hôm sau, vì tài xế đã lái 6 giờ.
  - **Lý do:** lịch sử là "nhà xe trong quá khứ", chưa có luật giữ–về. Chính lịch sử "về ngay" này là **mốc để so** với QĐ-25 ở CT9 (số lượt chạy rỗng, số xe, tiền lãi).
  - **Không chọn H2** (áp luật giữ–về vào lịch sử): vòng lặp, vì luật cần dự báo mà dự báo học từ lịch sử; và làm mất mốc so sánh.
  - **Không chọn H3** (xe "bay" về như bộ sinh cũ): trái (c) và QĐ-23.
- **Cách hiểu khi cài ở CT3 (2026-11-17), chủ dự án chưa duyệt từng điểm:**
  - Chọn xe, tài xế cho chuyến thường: ở đúng bến, đủ nghỉ, đúng loại xe trước, **ai chờ lâu nhất đi trước**.
  - "Xe rảnh" cho chuyến tăng cường = xe đang đứng ở **bến nhà** của nó, chưa có lượt. Xe của lịch thường lệ đang ở bến xa không bị lấy đi, vì lấy đi thì chuyến thường kế tiếp ở đó thiếu xe.
  - Xe gửi lên trước đi cùng chuyến chiều đi **muộn nhất còn kịp**; mỗi khung tối đa 3 chuyến thêm (phút 30/40/50).
  - **Chuyến thường ở bến xa bị hủy thì xe và tài xế cũng về ngay**, như xe tăng cường. Lên n xe mà chỉ về n − 1 thì một xe phải về rỗng. Không có luật này, đội xe trôi dần ra bến xa (test đo được).
  - Chuyến có sẵn của chủ dự án giữ xe và người cả ngày (#33), nhưng **không đổi vị trí** của ai: 19 chuyến đó là chuyến thử tay, và tự chúng không nối nhau.
  - Chạy bổ sung đọc vị trí từ DB. Chỉ lượt tăng cường vắt qua ranh giới có thể lệch so với chạy liền.

### QĐ-22. Mức làm M2, thước đo "tối ưu", lãi cả vòng (chốt 2026-11-16)

- **M2:**
  - Mỗi cặp có lịch thường lệ. Mỗi xe chạy theo vòng đi–về, và vòng cho biết tối nay xe ngủ ở bến nào.
  - Tuyến ≤ 4 giờ chạy vòng 1 ngày (Vũng Tàu, Cần Thơ). Tuyến 4–8 giờ chạy vòng 2 ngày, xe ngủ ở bến xa (Đà Lạt).
  - Tăng cường = thêm một vòng, bằng xe rảnh đang ở bến xuất phát.
  - Màn hình gom theo cặp: hai chiều đặt cạnh nhau, chuyến thêm được đánh dấu. Phần này bao gồm luôn M1.
- **Xe tăng cường không bắt buộc về theo vòng.** Nó theo luật giữ–về (QĐ-25).
- **M3** (thuật toán tối ưu cho toàn đội xe) đưa vào phần hướng phát triển.
- **"Tối ưu" = lãi cả vòng** = tiền vé − nhiên liệu − lương − tiền chờ, tính cả chiều về. Số km chạy rỗng và số xe phải dùng thì hiển thị kèm, không làm mục tiêu chính.
  - Lý do: luật giữ–về đã quyết bằng tiền, và màn Đề xuất đã xếp thẻ theo lãi kỳ vọng (G6-3, G6-4).
- **Lãi của chuyến thêm tính cả chiều về.** Lúc chốt, `RecommendationService.estimateCost` (dòng 407) chỉ tính một chiều. *(CT7, 2026-11-22: đã cài — phép tính chi phí một lượt và lãi cả vòng nay ở `service/LoopEconomics`, dùng chung cho màn Đề xuất và What-if; xem "cách cài lãi cả vòng ở CT7" bên dưới.)*
  - Ví dụ, tuyến Vũng Tàu, xe 22 chỗ, 100 km, 2 giờ, vé 100.000đ. Một chiều tốn 100 × 6.000 + 2 × 50.000 = 0,7 triệu.
  - Chuyến thêm chở 16,5 khách thì lãi một chiều là +0,95 triệu. Nếu chiều về chạy rỗng thì lãi cả vòng chỉ còn **+0,25 triệu**.
- **"Mỗi tuyến 2 xe" chỉ là ví dụ** của thầy. Số xe mỗi cặp được tính từ lịch chạy.
- **Cách cài lịch thường lệ ở CT6 (2026-11-19).** Chủ dự án duyệt cả CM-4, CM-6, CM-14 và sáu điểm CM-17…CM-22 tìm ra khi đối chiếu code và DB: *"oke, tôi đồng ý tất cả"*.
  - **Một nguồn giờ chạy và danh sách tuyến** (CM-4): `domain/RegularTimetable`. Bộ sinh lịch sử (`HistorySimulation.DEPARTURE_HOURS`, `backfill.route-ids`) và app cùng đọc một chỗ.
  - **Cặp tuyến** suy bằng đảo bến (CM-6). Lịch là theo cặp: một chiều không xếp được thì chiều kia cũng không chạy một mình.
  - **Nút "Tạo lịch"** ở trang mới `/admin/timetable` (CM-14). Chuyến xếp được thì `ACTIVE`, lưu qua `createManualTrip`, lối của form Tạo.
  - **Vòng xe không viết sẵn** (CM-19). Máy xếp từng khung theo giờ, cả 6 tuyến chung một trục, bằng `autoAssignResources` của job 10 giây; chuyến vừa xếp được giữ chỗ trong context, nên vòng đi–về tự hình thành.
  - **Đà Lạt chạy khác câu "vòng 2 ngày, xe ngủ ở bến xa" ở trên.** Câu đó là cách hiểu lúc lập kế hoạch. Theo CM-19, xe tới Đà Lạt lúc 12:00 được quay về lúc 18:00 với một kíp khác đang ở Đà Lạt, nên cặp này cần tối thiểu 4 xe thay vì 6. Lịch sử (bộ sinh CT3) thì mỗi xe một lượt/ngày: đo trên DB thật 12–16/11, 6 chuyến Đà Lạt mỗi ngày dùng 6 xe khác nhau.
- **Cách cài lãi cả vòng ở CT7 (2026-11-22).** Chủ dự án duyệt cả 5 đề xuất của vòng thảo luận đầu CT7: *"tôi đồng ý với những đề xuất của bạn (từ 1-5)"*.
  1. **PA-B — mỗi thẻ của màn Đề xuất là một vòng:** chuyến thêm + **lượt đi kèm**, đi cùng giờ một chuyến thường lệ của chiều ngược (06/12/18), chở phần khách còn tràn của khung đó.
     - **"Về ngay"** khi chuyến thêm làm xe rời bến nhà: khung chiều ngược **sớm nhất** mà xe (nghỉ ≥ 1 giờ) và một tài xế (≤ 8 giờ/ngày, đứng đúng bến) đều kịp, trong 2 ngày — đúng luật H1 của lịch sử (QĐ-21 (d)).
     - **"Đưa xe tới trước"** khi bến đi không có xe rảnh: khung chiều ngược **muộn nhất** còn kịp, trong 2 ngày trước, không trước lúc xem.
     - Lãi = tiền vé hai lượt − chi phí hai lượt. Mọi lượt của vòng được chọn bằng `selectBestAvailableBus/Driver` (luật vị trí CT5b), qua cổng kiểm tra, và giữ chỗ cùng nhau.
     - **Không ghép** hai thẻ ngược chiều thành một vòng. Đo trên lịch sử (2023 → 16/11/2026), ngày cả hai chiều của một cặp cùng có chuyến tăng cường có khách chỉ là 10/282 ngày; ghép khác ngày chính là giữ xe, để CT9.
  2. **Cách 2a — trừ chuyến đã có:** số thẻ = số chuyến cần (QĐ-18) − số chuyến **chưa hủy** đã có trong khung (thường lệ, tăng cường, tạo tay; 06:30 thuộc khung 06:00 như CM-18). Khung chưa có chuyến nào coi như có 1 xe chuẩn, như trước CT7. Khách của chuyến thêm tính trên **ghế thật** đã có. Khác CM-18: chuyến đã hủy không tính, vì nó không chở ai.
  3. **S1 — trang mới "Kế hoạch cặp tuyến"** (`/admin/analytics/pair-plan`, CM-9).
  4. **W1 — What-if tính tiền cả vòng**, năng lực giữ nguyên (CM-7).
  5. **Màn Dự báo gom theo cặp** (phần M1); làm thành CT7a (con số) rồi CT7b (màn hình).
- **Cách hiểu khi cài ở CT7, chủ dự án chưa duyệt từng điểm:**
  - **Có thêm loại "không cần lượt đi kèm".** Xe đang đứng ở bến xa (bến nhà Miền Đông) mà chuyến thêm đưa đúng nó về bến nhà thì cả vòng chỉ là một lượt. Đề xuất ban đầu chỉ nêu "về ngay" và "đưa xe tới"; bắt chiếc xe này quay lại bến xa là trả tiền để đưa xe về đúng chỗ nó đang bị kẹt.
  - **Không xếp được lượt cụ thể** (tuyến không có đúng một tuyến chiều ngược, hay trong 2 ngày không có khung nào xe và tài xế kịp) → tính như **một lượt về rỗng**: chi phí một lượt, 0 tiền vé. Không bao giờ nói lãi quá tay.
  - **Lượt đi kèm chở phần khách còn tràn SAU khi khung đó đã có đủ số chuyến nó cần**, chứ không chen trước chuyến thêm của chính khung đó. Nhờ vậy tiền của nó không phụ thuộc thứ tự xét thẻ, và What-if tính ra đúng số đó mà không chọn xe. Thường gần 0 khách.
  - **Xe của lượt về do máy chọn**, như CM-19 (vòng xe không viết sẵn), không ép đúng chiếc xe của chuyến thêm. Thường đó chính là chiếc xe vừa tới; nếu là một xe rảnh khác đang ở bến xa thì đội xe vẫn kết thúc ở cùng chỗ.
  - **Thứ tự xét thẻ:** lãi cả vòng **theo kế hoạch** giảm dần (thay cho lãi một chiều của G6-4). Kế hoạch giả định xe dự phòng đứng ở bến nhà: chuyến thêm rời bến nhà thì về ngay, tới bến nhà thì đưa xe tới. Thẻ thiếu tài nguyên hiện tiền theo kế hoạch đó.
  - **What-if cũng trừ chuyến đã có** (cách 2a), để giữ bất biến đã có từ GĐ6: ở 100% nhu cầu, What-if và màn Đề xuất cùng số chuyến thêm. What-if chỉ có kế hoạch (không chọn xe), nên tiền của nó khớp tổng các thẻ khi xe thật đi đúng kế hoạch; khi xe rảnh đang ở bến xa, hay tài xế chưa kịp nghỉ, hai màn khác nhau. Màn ghi rõ giới hạn đó.
  - **Màn Dự báo:** chiều "đi" của một cặp là chiều rời bến nhà của đội xe; cặp có mức đỉnh cao nhất lên trước; tuyến không có cặp ở nhóm cuối. Chỉ đổi thứ tự hiển thị của bảng 7 ngày.
  - **Trang kế hoạch** không tính gì mới: chuyến đã có (DB), kết quả của màn Đề xuất (gọi đúng một lần), dự báo của từng khung. Cặp lấy theo danh sách tuyến có lịch (CM-4).
- **Cách cài cả vòng một lần ở CT7c (2026-11-23, CM-24 cách PA-A).** Chủ dự án: *"tôi duyệt CM24 và CM25 như đã bàn bạc"*.
  - **Một vòng = một khối bến nhà → bến nhà**, chọn MỘT LẦN cho MỘT xe và MỘT tài xế chính (`TripService.selectLoop`): "về ngay" = chuyến thêm + lượt về; "đưa xe tới" = lượt đưa xe tới + chuyến thêm. Đúng luật H1 của lịch sử (`HistoricalDataBackfill.addOuting` / `findReturn`) và "kíp ở lại cùng xe" (QĐ-26) trong phạm vi của vòng.
  - **Cùng các luật của hàm chọn một chuyến, áp cho cả khối:** xe không bận trong [đầu khối − 1 giờ, cuối khối + 1 giờ], tài xế trong [đầu − 30 phút, cuối + 30 phút] (xe và kíp chờ nhau ở bến xa); vị trí = `LocationRule.fits` với cả khối coi như một lượt (CM-5 không đổi: máy vẫn cứng); giờ lái mỗi lượt cộng vào ngày khởi hành của nó, mỗi ngày ≤ 8 giờ; bảo trì tính km mọi lượt; bằng lái còn hạn vào ngày của mọi lượt.
  - **Không xếp được cả vòng thì thẻ "thiếu tài nguyên"**, kèm lý do (không có xe, hay có xe mà không có tài xế cho cả vòng). Loại "không cần" (xe kẹt ở bến xa, chuyến thêm đưa nó về) giữ nguyên, chọn như một chuyến. "Tính như về rỗng" chỉ còn cho tuyến không có đúng một tuyến chiều ngược.
  - **Thay "cách hiểu khi cài" ở CT7** về xe của lượt về ("do máy chọn, không ép đúng chiếc xe của chuyến thêm"): nay lượt về luôn là chính xe và chính tài xế đó.
  - **Cách hiểu khi cài:** chuyến thêm rời bến nhà (theo kế hoạch: RETURN) chỉ thử "về ngay"; trước CT7c, khi bến nhà không có xe, máy từng thử đưa một xe từ bến xa về rồi chạy chuyến thêm (xe kết thúc ở bến xa). Vòng đó không phải bến nhà → bến nhà, nên không còn. Bỏ `AvailabilityContext.release` (giữ chỗ thử), vì cả vòng được kiểm một lần trước khi giữ chỗ.
- **Cách cài bảo trì trong lịch ở CT7d (2026-11-23, CM-25 cách M1).** Cùng lời duyệt trên. Ghi ở mục "BẢO TRÌ TRONG LỊCH" cuối `TripService`.
  - **Dữ liệu:** cột mới `buses.planned_maintenance_date` (`Bus.plannedMaintenanceDate`, cho phép null). Chỉ bộ lập lịch thường lệ đặt; trang xe và trang Lịch thường lệ chỉ hiển thị. Admin bảo trì xong thì ghi nhận như trước (km lần bảo trì cuối = odometer), `BusService.updateBus` thấy số đó tăng thì tự xoá ngày dự kiến. Lối tạo xe luôn để trống (POST tự chế không đặt được).
  - **R1 — ngày bảo trì:** ngày M xe nằm ở bến nhà; không chuyến nào của xe chạm [M 00:00, M+1 00:00) — máy, ô chọn và validator như nhau. Với luật vị trí, máy chỉ đặt M vào ngày xe nằm trọn ở bến nhà.
  - **R2 — km tính lại từ 0:** chuyến khởi hành từ M+1 00:00 tính km từ 0 — máy, ô chọn và validator như nhau. **Ngày M đã qua mà chưa ghi nhận bảo trì thì R1, R2 hết tác dụng** (`Bus.hasPlannedMaintenance`): số liệu nói xe chưa được bảo trì; trang Lịch thường lệ và danh sách xe gắn "đã qua, chưa ghi nhận".
  - **R3 — máy cộng km đã xếp** (thay phần "chỉ áp trong bộ lập lịch" của CM-21): mọi bộ chọn của máy — lịch, màn Đề xuất, job 10 giây — tính km từ lần bảo trì (R2) + km mọi chuyến chưa xong của xe trong cùng đoạn (trước M hay sau M), kể cả chuyến giữ chỗ. Validator và ô chọn của người không cộng (vẫn chỉ xét chuyến đang lưu): máy chặt hơn cổng.
  - **R4 — phải về được bến nhà (cách hiểu khi cài, bắt buộc để M1 làm được):** chuyến hay vòng kết thúc xa bến nhà thì máy cộng thêm quãng về. Bảo trì làm ở bến nhà và mốc 90% chặn mọi chuyến, nên xe chỉ được rời bến nhà khi đi rồi về vẫn dưới mốc. Đo trên bản sao DB thật bằng code trước CT7d, sau "Tạo lịch" 27/11–03/12: 5 xe kết thúc ở bến xa mà quay về là ≥ 90% (ví dụ 29A-001.15 tối 26/11 ở Đà Lạt, 4.420 km tính cả lịch, về thêm 300 km là 4.720 ≥ 4.500).
  - **R5 — thứ tự của máy** (đề xuất "nên" của CM-25): đúng loại trước, rồi km tính cả lịch ít nhất, nên xe vừa bảo trì theo lịch được dùng trước. Ô chọn của người vẫn xếp theo km thật.
  - **Ai đặt M, khi nào (cách hiểu khi cài):** trước khi xếp mỗi khung, `TripService.planDueMaintenance` xét các xe (1) đúng loại gợi ý của tuyến, (2) đang ở bến nhà = bến đi của khung, (3) chưa có ngày dự kiến còn hiệu lực — **mỗi xe một ngày**, (4) km thật chưa vào vùng cảnh báo, (5) mọi luật khác cho chạy, chỉ luật km chặn. M = ngày đầu tiên từ ngày của khung tới hết lần bấm mà xe không chạy chuyến nào và nằm trọn ở bến nhà. Bảo trì mất **1 ngày, ở bến nhà** (giả định đã nêu khi đề xuất, chủ dự án duyệt cùng).
  - **Xe sát / quá hạn NGAY LÚC NÀY theo km thật** (điều kiện 4, ví dụ các xe mẫu 51B-SAT.BT, 51B-QUA.BT, CM-22) vẫn như cũ: Admin bảo trì rồi ghi nhận; lịch không tự đặt ngày cho chúng.
  - **Giới hạn đã biết:** một xe chỉ có một ngày dự kiến. Cần lần thứ hai trong cùng lần bấm (14 ngày) thì xe bị loại như trước tới khi lần trước được ghi nhận. Không giới hạn số xe bảo trì cùng ngày (không có số liệu xưởng).

### QĐ-23. Vị trí xe và xe dự phòng (chốt 2026-11-16)

- **Xe được lấy để chạy** (tăng cường, tự phân công) phải là xe không bị gán, không đang sử dụng, **và đang đứng ở bến xuất phát của chuyến**.
- **Vị trí suy ra từ chuyến cuối của xe:** chuyến cuối tới bến nào thì xe đang ở bến đó. Xe chưa chạy chuyến nào thì ở bến nhà. Không dùng GPS.
- **Xe dự phòng** của một bến = các xe rảnh đang đứng ở bến đó. Không có danh sách xe dự phòng cố định.
- **Bến nhà ban đầu** của mọi xe và tài xế: Miền Đông.
- **Hiện trạng:** app chưa biết vị trí. `TripService.findBestAvailableBus` (dòng 308) chọn từ mọi xe READY trong cả công ty.
- **Cách áp (CM-5, chốt 2026-11-17, cách B):**
  - **Máy chọn** (job 10 giây, màn Đề xuất, tự tạo chuyến sau này): **cứng**. Chỉ xét xe và người đang ở bến xuất phát.
  - **Người chọn** (ô chọn ở các màn Duyệt, Sửa, Tạo): **mềm**. Hiện mọi xe hợp lệ; xe cùng bến xếp đầu, xe ở bến khác có ghi chú.
  - `validateBusForTrip` **không** chặn theo vị trí.
  - Đúng tiền lệ "loại xe phù hợp là gợi ý" (2026-09-23, 2026-09-24).
  - **Áp cả cho tài xế, phụ xe và tài xế phụ, cùng một luật.**
  - Lý do không chọn cứng ở mọi nơi: khi mọi xe có bến nhà Miền Đông, không ai tạo được chuyến xuất phát ở Mỹ Đình. Và app chưa có lối "chạy rỗng để điều xe".
- **Bến nhà (CM-12, chốt 2026-11-17, cách (a)):**
  - Cột riêng `home_station_id` trên `buses` và `drivers`.
  - Ô bắt buộc trên form; service cũng từ chối khi thiếu. DB cho phép trống, vì cột được thêm vào bảng đã có dữ liệu.
  - Xe hay người chưa có bến nhà và chưa chạy chuyến nào thì coi là "không biết ở đâu": máy không chọn.
  - Không chọn (b), một cấu hình chung: mọi xe sẽ cùng một bến nhà. Không chọn (c), suy từ chuyến đầu: xe mới chưa có chuyến thì không biết ở đâu.
- **Luật vị trí viết ở đúng một chỗ:** `domain/LocationRule`, hàm thuần, CT5a. Vị trí lúc t = bến cuối của chuyến gần nhất (không huỷ) có giờ đến ≤ t; chưa có chuyến thì là bến nhà. Bộ sinh (CT3), script kiểm và app (CT5b) cùng gọi.
- **Cách cài ở CT5b (2026-11-17), đúng CM-5 cách B:**
  - **Hai phía** (`LocationRule.fits`). Nhận thêm một chuyến thì lúc khởi hành xe/người phải đứng ở bến đi, **và** chuyến kế tiếp đã cam kết, nếu có, phải xuất phát từ bến đến.
    - Phía sau được thêm khi `verify` trên DB thật thấy màn Đề xuất giao xe 29A-001.04 cả chuyến Đà Lạt 06:00 lẫn 18:00 ngày 22/11. Thẻ được xét theo lãi, không theo giờ, nên chỉ xét phía trước là không đủ.
    - Đây là cách hiểu khi cài, nằm trong chữ "đang ở bến xuất phát" của QĐ-23, không phải quyết định mới.
  - **Máy:** `findBestAvailableBus` / `findBestAvailableDriver`, ở cả bản hỏi DB lẫn bản `AvailabilityContext`. Context nạp sẵn lượt gần nhất **trước** và lượt sớm nhất **sau** khoảng đã nạp, cho từng xe và từng người.
  - **Người:** dropdown Duyệt, Sửa, Tạo xếp ai vừa chuỗi lên đầu; những người khác ghi "⚠ đang ở …" hay "chuyến kế tiếp đi từ …". Form Tạo gửi `routeId` cho API.
  - **Không chặn:** validator không kiểm vị trí.
  - **Mọi vai trên xe:** vị trí của một người tính theo chuyến muộn nhất ở bất kỳ vai nào. DB được hỏi riêng từng vai, vì một câu `OR` quét cả bảng (0,46 s một lần).
  - **Tuyến chưa có bến** (chỉ có trong dữ liệu test) thì không lọc. Thời gian nghỉ vẫn do cửa sổ "bận" sẵn có lo.

### QĐ-24. Dự báo tự tạo chuyến chờ duyệt (chốt 2026-11-16)

1. **Chỉ tạo chuyến `PENDING_APPROVAL`.** Admin vẫn duyệt, giữ nguyên tắc "máy đề xuất, người duyệt".
2. **Chỉ tạo chuyến có lãi kỳ vọng > 0, tính cả chiều về.** Chuyến lỗ vẫn hiện thẻ trên màn Đề xuất nhưng không được tự tạo. Nhờ vậy QĐ-18 trả lời "khung cần mấy chuyến", còn lãi trả lời "có chạy không".
3. **Chạy mỗi đêm một lần**, xét các ngày từ D+3 tới D+7:
   - từ D+3: còn ≥ 72 giờ trước giờ đi, như `MIN_HOURS_BEFORE_DEPARTURE` của job, để khách kịp mua vé;
   - tới D+7: bằng `FORECAST_HORIZON_DAYS`.
4. **Đếm mọi chuyến đã có trong khung** (thường lệ, tăng cường, tạo tay) và chỉ tạo phần còn thiếu.
5. **Dự báo giảm:** chuyến tự tạo còn chờ duyệt thì tự rút; chuyến đã mở bán thì giữ.
6. **Job 10 giây chỉ lo phần bất ngờ:**
   - chỉ thêm chuyến khi vé thật > 90% **tổng ghế của mọi chuyến trong khung**;
   - không lấy chuyến tăng cường làm chuyến gốc, để hết chuyện nối chuỗi +30 phút;
   - giữ nguyên các điều kiện 72 giờ, 48 giờ, 95%.
7. **Thay hai quyết định cũ:**
   - "màn Đề xuất chỉ đọc, không tạo chuyến" (`RecommendationService.java:84`, Phase 7, QĐ-17);
   - Non-Goal §4 "No daily cron job" (`THESIS_ROADMAP.md:53`). Dòng đó tự ghi rằng thêm bộ hẹn giờ là quyết định riêng của chủ dự án. Sửa câu chữ §4 khi làm CT8, có chủ dự án duyệt.
   - *(2026-11-25, CT8: §4 đã sửa theo câu chữ chủ dự án duyệt. Trang Đề xuất vẫn chỉ đọc. Job đêm là nơi ghi, và nó dùng chung bộ lập kế hoạch với trang (`RecommendationService.buildPlan`). Ghi chú cho QĐ-17 trong `forecast_decisions.md` chưa thêm, để đợt tài liệu sau.)*

- **Ví dụ** (khung Sài Gòn→Vũng Tàu 06:00 thứ Sáu 27/11, xe Limousine 22):
  - Đêm 20/11: dự báo cần 2 chuyến, đã có 1 → tạo 1 chuyến chờ duyệt.
  - 21/11: Admin duyệt.
  - Các đêm sau: khung đã đủ, không tạo thêm.
  - 23/11 (còn > 72 giờ): vé thật 41/44 = 93% → job 10 giây thêm 1 chuyến. Đây là phần bất ngờ mà dự báo không thấy.
- **Cách cài ở CT8 (2026-11-25).** Chủ dự án duyệt mọi đề xuất của vòng thảo luận đầu CT8: *"tôi đồng ý tất cả, hãy thực hiện như những gì đã bàn"*. Gồm CM-10, CM-14, CM-15, CM-23, CM-26, CM-27, CM-28, mục 6, câu chữ §4, và cách chia CT8a / CT8b / CT8c.
  - **Commit:** `fb6bc9b` (CT8c), `b34aba3` (CT8b), `ad75bc6` (CT8a). Thứ tự commit theo phụ thuộc, vì job của CT8a gọi phần vòng của CT8b.
  - **Job đêm** (`NightlyDispatchService`, CM-10 cách T2):
    - chạy lúc 01:00 (`dispatch.nightly.cron`), chạy bù một lần khi app khởi động, và chạy khi bấm nút "Chạy ngay" trên màn Đề xuất;
    - mỗi lần chạy có ba bước, mỗi bước một transaction riêng; bước nào lỗi thì tự bắt lỗi đó, ghi log và hiện trên khung "Job đêm", không ném ra ngoài:
      1. vận hành mô phỏng (CM-23 cách V4, `config/SimulatedOperations`), khi bật `dispatch.nightly.simulate-operations`;
      2. lịch thường lệ tới D+7 (CM-14): `RegularTimetableService.generate(hôm nay, 8 ngày)`; khung đã có chuyến thì bỏ qua;
      3. vòng dự báo (`ForecastLoopService.reconcile`): rút, giữ, tạo;
    - bước "dự báo học lại" trong đề xuất không thành bước riêng: `reconcile` gọi `buildForecast`, nên dự báo đọc luôn các chuyến mà bước 1 vừa đóng;
    - mỗi lúc chỉ có một lần chạy; kết quả lần gần nhất giữ trong bộ nhớ, khởi động lại thì mất;
    - công tắc `dispatch.nightly.enabled` bật trong `application.properties`, tắt trong cấu hình test. Chạy app để kiểm chỉ đọc trên DB thật thì phải truyền `-Ddispatch.nightly.enabled=false`.
  - **Vận hành mô phỏng (V4):** xét từng khung (tuyến × ngày × giờ khung) của các tuyến lịch thường lệ, khi **mọi** chuyến đang bán hay đang chạy của khung đã tới nơi.
    - Số khách = `HistorySimulation.demand` của khung: cùng mô hình, cùng hạt giống 20261021 với lịch sử, tất định theo ngày.
    - Trừ trước khách của chuyến đã `COMPLETED` trong khung. Lấp chuyến chính trước, rồi chuyến tăng cường theo giờ đi; mỗi chuyến tối đa số ghế của nó, và không thấp hơn số vé đã gõ tay.
    - Rồi đi `ACTIVE → DEPARTED → COMPLETED` qua `updateTripStatus`: xe về READY, odometer cộng km.
    - Chuyến chờ duyệt đã qua giờ khởi hành → `CANCELLED`, qua FSM.
    - Không mô phỏng bão hay hủy. Tuyến khác và giờ ngoài khung không bị đụng tới. Số khách là **mô phỏng**, phải nói rõ khi bảo vệ.
  - **Vòng dự báo** (CM-26 cách K1, CM-15):
    - **Điều kiện tạo:** thẻ "Đủ điều kiện", lãi cả vòng > 0, chuyến thêm còn ≥ 72 giờ (`TripService.MIN_HOURS_BEFORE_DEPARTURE`).
    - **Mỗi vòng** gồm chuyến thêm + lượt đi kèm, `PENDING_APPROVAL`, đi **đúng giờ khung**. Xe và tài xế là đúng cặp thẻ đã chọn và đã qua cổng dry-run; số ghế theo xe; giá lấy từ `loadLatestPrices`.
    - **Cột mới** `trips.forecast_loop_id` (cho phép null, thêm bằng `ddl-auto=update`): chuyến thêm trỏ vào chính nó, lượt đi kèm trỏ vào chuyến thêm.
    - **Chuyến gốc** của mỗi lượt là chuyến chính của khung nó (`TripRepository.findMainTripsOfSlot`).
    - Trang Đề xuất vẫn chỉ đọc: `buildRecommendations()` trả phần hiển thị của `buildPlan()`, và chỉ job lưu chuyến.
  - **Rút vòng chờ duyệt** (CM-28) trong hai trường hợp:
    - **(a) dự báo giảm:** máy tạm coi các vòng chờ duyệt của chính nó là chưa có, rồi dựng lại kế hoạch. Mỗi khung giữ tối đa số vòng mà kế hoạch còn muốn (vòng cũ nhất trước, không đổi xe), phần dư bị rút. "Tạm coi là chưa có" nghĩa là đổi tạm các lượt sang `CANCELLED` ngay trong transaction, rồi trả lại `PENDING_APPROVAL` trước khi làm gì khác;
    - **(b) quá hạn duyệt:** chuyến thêm còn dưới 72 giờ mà chưa ai duyệt.
    - Vòng đã duyệt là chuyến thật, luôn được giữ.
  - **Duyệt cả vòng** (CM-27):
    - Xác nhận một lượt (`confirmAutoAssignedTrip`) thì các lượt còn chờ của vòng cũng được kích hoạt, trong cùng transaction; lượt nào cũng qua cổng xe và kíp của nó.
    - Từ chối một lượt thì cả vòng bị hủy, qua FSM.
    - Đường duyệt tay (`approveTrip`, chọn xe khác) không nhận lượt của vòng.
  - **Khi thiếu dữ liệu** (CM-10): chưa có chuỗi dự báo nào, hay lịch sử cũ hơn 7 ngày, thì job không tạo và không rút theo dự báo, nhưng vẫn rút theo hạn duyệt. Khung "Job đêm" ghi lý do.
  - **Job 10 giây** (mục 6, CT8c):
    - gom các chuyến `ACTIVE` theo khung; 06:30 thuộc khung 06:00, như CM-18;
    - tỉ lệ = tổng vé ÷ tổng ghế của cả khung, tính qua `Trip.exceedsReinforcementThreshold`, định nghĩa duy nhất của "> 90 %";
    - các cổng 72 giờ / 48 giờ / 95 % hỏi chuyến chính, với tỉ lệ của cả khung;
    - chuyến gốc luôn là chuyến chính;
    - khung đang có chuyến tăng cường chờ duyệt, do ai tạo cũng vậy, thì không tạo thêm. Chuyến tăng cường đã duyệt không còn chặn mà góp ghế vào khung; chuyến đã hủy thì vẫn không chặn (TC_AI_009).
- **Cách hiểu khi cài ở CT8, chủ dự án chưa duyệt từng điểm:**
  - **Admin đã từ chối vòng của một khung thì job không đề xuất lại khung đó.**
    - Job tự rút vòng bằng **xoá mềm** (`TripService.deleteTrip`), không chuyển `CANCELLED`, nên khi dự báo tăng lại thì khung vẫn được đề xuất lại.
    - `CANCELLED` của một vòng dự báo chỉ có một nghĩa: "Admin đã từ chối". Job bỏ qua khung đó từ đó về sau, khác job 10 giây, vốn đề xuất lại theo vé thật (TC_AI_009).
    - Không có luật này thì đêm sau job lập lại đúng vòng mà Admin vừa từ chối. Khung "Job đêm" đếm số thẻ bị bỏ qua vì lý do này.
  - **Ngày bảo trì dự kiến đã qua thì vận hành mô phỏng ghi nhận là đã bảo trì** (`BusService.recordPlannedService`: km lần bảo trì cuối = odometer, ngày dự kiến được bỏ). Lần bảo trì được xếp đúng thứ tự thời gian giữa các chuyến của xe.
    - Lý do: xe đã đứng ở bến nhà cả ngày đó đúng như lịch xếp.
    - Nếu không ghi nhận, R1 và R2 của CT7d hết tác dụng, trong khi các chuyến sau ngày đó đã được xếp với km tính lại từ 0.
  - **Một khung chỉ được đóng khi mọi chuyến đang chạy của nó đã tới nơi.** Không đóng nửa khung, vì nhu cầu là của cả khung.
  - **Vòng dự báo không có phụ xe**, vì tuyến ≤ 8 giờ không bắt buộc phụ xe, như CT7c.
  - **Hàng chờ duyệt không gom hai lượt thành một nhóm "🤖 Vòng"** như đã đề xuất. Thay vào đó:
    - mỗi lượt có nhãn "🤖 Vòng dự báo #id · chuyến thêm / lượt đi kèm" và một dòng nhắc rằng xác nhận hay từ chối một lượt là áp cho cả vòng;
    - nút "Từ chối" của lượt thuộc vòng đổi thành "Từ chối cả vòng";
    - thông báo sau khi bấm kể tên mọi lượt.
  - **Lượt lẻ** (lượt đi kèm còn chờ duyệt mà chuyến thêm của nó thì không) bị rút theo hạn duyệt.
  - **Ba câu "chạy lại profile `backfill`"** trên màn Dự báo, What-if và Đề xuất đổi thành "job đêm bổ sung mỗi ngày".
- **Ví dụ trên bản sao DB thật** (`busmanagement_ct8`, 2026-11-25):
  - **Lần khởi động đầu:** đóng 89 chuyến của 20–24/11 (2.489 vé mô phỏng) và tạo 4 vòng #51610, #51612, #51614, #51616.
  - **Lần chạy thứ hai:** giữ 3 vòng. Rút #51614 (Đà Lạt → Miền Đông 18:00 29/11, đưa xe lên bằng chuyến 18:00 28/11), vì lãi 20.810đ của nó đã xuống ≤ 0. Vòng này bị xoá mềm.
  - **Admin thao tác:** xác nhận #51612 (cả hai lượt thành `ACTIVE`) và từ chối #51616 (cả hai lượt thành `CANCELLED`). Lượt đưa xe lên của #51616 cũng đi lúc 18:00 28/11.
  - **Bấm "Chạy ngay":** khung của #51616 không được đề xuất lại. Khung của #51614 thì được đề xuất lại thành #51618, lãi 1.053.981đ: lượt 18:00 28/11 nay không phải chia khách tràn cho #51616 nữa.

### QĐ-25. Luật giữ–về cho xe tăng cường (chốt 2026-11-13, sửa 2026-11-16)

- **Áp cho xe tăng cường**, không áp cho xe thường lệ (xe thường lệ chạy theo vòng).
- **Giữ xe ở bến xa nếu tiền chờ còn lại < lợi ích của việc chờ.**
  - Lợi ích = tiền vé chiều về mà chính xe đó chở. Số khách lấy theo khách kỳ vọng của chuyến thêm, `ForecastService.extraTripPassengers`.
  - Lợi ích tối đa bằng **2 × chi phí một chiều**. Vì nếu chờ đắt hơn mức đó, cho xe về rỗng ngay rồi sau này gửi một xe rỗng lên đón sẽ rẻ hơn.
- **Nhìn trước 35 ngày** (`PREVIEW_DAYS`), áp dụng cho cả ngày thường lẫn dịp lễ. Bỏ trần 7 ngày đã bàn hôm 13/11.
- **Kiểm lại mỗi ngày**, và chỉ tính số ngày chờ còn lại. Tiền đã chờ là khoản đã mất, không đưa lên cân.
- **Giữ từ luật 13/11:**
  - Bến nhà thiếu xe cho **chuyến thường lệ** → gọi xe về nếu còn kịp (≥ thời gian chạy + 1 giờ), cho xe đi cùng giờ một chuyến thường lệ.
  - Chỉ thiếu xe cho **chuyến tăng cường** → bỏ chuyến tăng cường đó.
  - Xe tới trước về trước.
  - Tính theo số ghế thật của xe.
- **Lý do sửa:** hai phép so cũ ("phí chờ > phí về", "phí chờ > lợi nhuận chuyến về") đều trừ chi phí chạy về, trong khi xe đằng nào cũng phải về.
  - Ví dụ tuyến 30 giờ, dùng để giải thích: luật cũ cho xe về rỗng, tổng −16,2 triệu.
  - Nếu chờ 8 ngày rồi chở 15 khách về: tổng −6,7 triệu, tức lợi hơn 9,5 triệu.
- **Với 3 cặp ngắn**, lợi ích tối đa (2 × chi phí một chiều) chỉ khoảng:
  - Vũng Tàu: 1,4 triệu.
  - Cần Thơ: 2,5 triệu.
  - Đà Lạt: 4,2 triệu.

  Luật này hiện rõ nhất ở Đà Lạt vào cuối tuần.

### QĐ-26. Kíp lái và giờ lái (chốt 2026-11-13 và 2026-11-16)

- **Kíp lái ở lại cùng xe** khi xe chờ ở bến xa. Không đi nhờ chuyến khác về.
- **Kíp lái chờ vẫn nhận lương cơ bản:**
  - Cần thêm tham số chi phí: lương chờ (mỗi người mỗi ngày), và phí xe nằm bến (mỗi ngày).
  - `CostParameters` hiện chỉ có nhiên liệu/km và lương/giờ lái. Giá trị cụ thể chờ CM-8.
- **App phải giữ kíp lái ở bến xa cùng xe.** Hiện tài xế chỉ bị coi là bận ±30 phút quanh chuyến.
- **Giữ mức 8 giờ lái mỗi ngày.**
  - Chuyến **thường lệ** của 3 cặp (06/12/18, dài nhất 6 giờ) không có chuyến nào lái qua nửa đêm. Chuyến Đà Lạt 18:00 tới đúng 24:00.
  - Tuy vậy, lỗi #34 (giờ lái chỉ tính vào ngày khởi hành) **vẫn phát sinh** với chuyến tăng cường của job 10 giây. Ví dụ Đà Lạt 18:30 tới 00:30: có 0,5 giờ rơi sang ngày sau. Trong lịch sử hiện có 37 chuyến như vậy.
  - Chuyến tự tạo theo CM-15 (khởi hành đúng giờ khung) thì không dính.
  - L1 (đếm giờ lái theo ngày lịch, sửa luôn #34) làm khi làm cặp dài, hoặc sớm hơn nếu chủ dự án muốn sửa riêng #34.
  - *(Sửa 2026-11-16: bản đầu ghi "lỗi không phát sinh", sai. Đo trên DB thật thấy 41 chuyến qua nửa đêm.)*

### QĐ-27. Để sau (chốt 2026-11-16)

- **Cặp dài MĐ↔Miền Đông**, kèm L1, bộ sinh chuyến nhiều ngày, thêm xe và tài xế.
- **Tăng tần suất:** chọn sau giữa B1 (gom theo buổi) và B2 (chuyến thêm ở giờ giữa). **Loại B3**, để bộ sinh không phải tạo lịch sử có những lần đổi giờ chạy.
- **Tối ưu kíp lái (K2):** kíp lái đang chờ ở bến xa thì lái tuyến ngắn của bến đó.
- **Thứ tự so với Phase 9** (M-12): chủ dự án chưa nói.

---

## 3. Chưa chốt — quyết ở vòng thảo luận đầu mỗi giai đoạn

| Mã | Câu hỏi | Đề xuất | Giai đoạn |
|---|---|---|---|
| CM-1 | **ĐÃ CHỐT 2026-11-16: bỏ (đã làm).** `ForecastService.java` đang có một diff chưa commit, chỉ là IDE format lại (+197/−91, không đổi logic). Giữ hay bỏ? | **Bỏ** (`git checkout -- <file>`). Bài học trích số dòng theo HEAD (ví dụ `FS:482-514` ở bài 14); giữ diff này thì các trích dẫn lệch hết. Phải quyết trước CT1, vì CT1 sửa đúng file này | CT0 |
| CM-2 | **ĐÃ CHỐT 2026-11-17: H1 "về ngay" (ghi ở QĐ-21 (d)).** Trong lịch sử, xe tăng cường về bằng cách nào? | **H1:** đi cùng chuyến thường lệ chiều về sớm nhất, chở khách tràn của khung đó. Không áp luật giữ–về vào lịch sử: lịch sử là "nhà xe trong quá khứ", và chính nó là mốc để so với QĐ-25 | CT3 |
| CM-3 | **ĐÃ CHỐT 2026-11-17: 100% (ghi ở QĐ-21 (b)).** Tâm của r (khách về sau lễ) | 100%, tức r đều trong [0,85; 1,15] | CT3 |
| CM-4 | **ĐÃ CHỐT 2026-11-19: một hằng số dùng chung (`domain/RegularTimetable`), đúng 6 tuyến của bộ sinh.** Lịch thường lệ lưu ở đâu? | Một hằng số dùng chung cho bộ sinh và app (06/12/18). Chưa thêm bảng mới. Danh sách tuyến có lịch = danh sách của bộ sinh (`backfill.route-ids`, mặc định `2,12,5,13,10,11`), cùng một khoá cấu hình | CT6 |
| CM-5 | **ĐÃ CHỐT 2026-11-17: cách B (cứng cho máy, mềm cho người), áp cả cho tài xế (ghi ở QĐ-23).** Vị trí áp cứng hay mềm? Có áp cho tài xế không? | Áp **cứng** cho AI (tự phân công, Đề xuất, tự tạo chuyến). Luồng thủ công thì **mềm**: xếp xe cùng bến lên đầu và ghi chú, giống cách làm với loại xe. **Áp cả cho tài xế**, cùng quy tắc | CT5 |
| CM-6 | **ĐÃ CHỐT 2026-11-19: đảo bến, đúng một tuyến khớp, không đổi schema (`RouteService.reverseRoutesOf`).** Liên kết cặp tuyến | Suy ra bằng cách đảo bến đầu/cuối (`Route.getDepartureStation()`/`getDestinationStation()`), phải có đúng một tuyến khớp. Không đổi schema của `Route` | CT6 |
| CM-7 | **ĐÃ CHỐT 2026-11-22: W1 — tiền tính cả vòng theo kế hoạch, năng lực giữ nguyên (ghi ở QĐ-22, "cách cài lãi cả vòng ở CT7").** What-if có tính lãi cả vòng không? | Có, để chỉ có một định nghĩa chi phí | CT7 |
| CM-8 | Giá trị tiền chờ | Chưa có số. Lúc bàn đã dùng tạm: lương chờ 333.000đ/người/ngày, xe nằm bến 330.000đ/ngày | CT9 |
| CM-9 | **ĐÃ CHỐT 2026-11-22: S1 — trang mới `/admin/analytics/pair-plan` (ghi ở QĐ-22).** Màn M2 đặt ở đâu? | Trang mới "Kế hoạch cặp tuyến" trong nhóm phân tích. Tránh nhầm với "Bảng điều hành" (`/admin/dispatch`), vốn là màn chuyển trạng thái chuyến | CT7 |
| CM-10 | **ĐÃ CHỐT 2026-11-25: T2 — 01:00 + một lần chạy bù khi app khởi động + nút "Chạy ngay"; chưa có chuỗi dự báo hay lịch sử cũ hơn 7 ngày thì không tự tạo; công tắc `dispatch.nightly.enabled` (ghi ở QĐ-24, "cách cài ở CT8"). Đã làm ở CT8a, CT8b.** Job đêm chạy lúc mấy giờ? Làm gì khi dự báo thiếu dữ liệu? | **T2.** App chạy trên laptop, ban đêm thường tắt: chỉ hẹn giờ (T1) thì job gần như không chạy; chỉ có nút (T3) thì không còn là "tự tạo" như QĐ-24. Chọn 01:00 vì đã sang ngày mới mà còn trước chuyến 06:00. Mọi bước chạy lại đều an toàn. Có công tắc, vì không có nó thì chỉ việc khởi động app để kiểm tra cũng ghi vào DB thật | CT8 |
| CM-11 | **ĐÃ CHỐT 2026-11-16 (đã ghi: lỗi #34 và 7 mục có hướng xử lý).** 8 phát hiện phụ ghi vào sổ nào? | `docs/todo/current_bugs_found.md`, sau phép thử ba ngả | CT0 |
| CM-12 | **ĐÃ CHỐT 2026-11-17: cách (a), cột riêng trên từng xe và từng tài xế (ghi ở QĐ-23).** Bến nhà lưu ở đâu? | Cột mới `Bus.homeStation` (và cột tương tự cho tài xế), cho phép null, thêm bằng `ddl-auto=update` | CT5 |
| CM-13 | **ĐÃ CHỐT 2026-11-16: có (đã làm ở CT2).** Seed vốn đã có Ghế ngồi 50; chỉ đổi loại xe 2 tuyến, thêm bến Vũng Tàu và 4 tuyến chiều ngược; giữ 6 tuyến cũ. Dữ liệu mẫu (`DataInitializer`) có đổi theo DB thật không? | Có: thêm bến Vũng Tàu, loại Ghế ngồi 50, 3 cặp Sài Gòn với loại xe theo QĐ-20 | CT2 |
| CM-14 | **ĐÃ CHỐT 2026-11-19: nút ở trang `/admin/timetable`; `ACTIVE` qua `createManualTrip`.** *(CT8, 2026-11-25: đã nối vào job đêm. Bước 2 giữ lịch luôn đủ tới D+7, bình thường chỉ thêm một ngày; nút vẫn giữ.)* Chuyến thường lệ tương lai: tạo bằng nút hay bằng job đêm? Trạng thái gì? | Nút cho Admin ở CT6, nối vào job đêm ở CT8. Trạng thái `ACTIVE` (vì lịch thường lệ đã được chủ dự án chốt), vẫn đi qua cổng validate | CT6 |
| CM-15 | **ĐÃ CHỐT 2026-11-25: đúng giờ khung (06/12/18). Đã làm ở CT8b.** Chuyến tự tạo khởi hành đúng giờ khung hay +30 phút? | Đúng giờ khung, như thẻ Đề xuất, vì ba lý do. (1) Bộ lập kế hoạch kiểm xe, tài xế và vị trí đúng tại giờ khung, nên lùi 30 phút là chạy một kế hoạch chưa được kiểm. (2) Tránh lỗi #34: chuyến Đà Lạt 18:30 tới nơi 00:30. (3) Nhìn giờ là biết nguồn: +30 phút là chuyến "bất ngờ" của job 10 giây | CT8 |
| CM-16 | **ĐÃ CHỐT 2026-11-17: giữ 6% (ghi ở QĐ-21).** Bão và 6% hủy ngẫu nhiên vẫn rút theo **từng chiều** (giữ từ QĐ-10). Với xe chạy vòng, tổng duyệt CT3 cho thấy 791 khung chiều về không có xe (chuyến chiều đi đưa xe lên đã bị hủy) và 736 lượt về rỗng (chuyến thường ở bến xa bị hủy). Giữ nguyên hay đổi? | **Giữ 6%**, như QĐ-21 đã chốt ("giữ bão và huỷ chuyến"). Bộ dự báo chỉ đọc chuyến COMPLETED, nên khung thiếu xe cũng như khung bị hủy. Có thể cân nhắc cho **bão đóng cả cặp** (cùng một con đường), nhưng phần do bão rất nhỏ so với 6% hủy | Trước CT4 |
| CM-17 | **ĐÃ CHỐT 2026-11-19 (điểm A).** Ngày đầu, bến xa chưa có xe: lúc kiểm 17/11, Vũng Tàu và Cần Thơ không có xe nào chọn được, nên máy không xếp được chuyến xuất phát từ đó lúc 06:00 | Vẫn tạo chuyến, nhưng `PENDING_APPROVAL` chưa có xe, như job 10 giây khi không phân công được; Admin chọn tay ở màn Duyệt (dropdown mời mọi xe hợp lệ, kèm ghi chú vị trí). Không thêm "chuyến điều xe rỗng". Số ghế tạm = xe chuẩn của tuyến; `approveTrip` đặt lại theo xe được chọn (#26) | CT6 |
| CM-18 | **ĐÃ CHỐT 2026-11-19 (điểm B).** Chuyến Admin tạo tay có tính là chuyến thường lệ của khung không? `Trip` chỉ có `isExtraTrip` và `originalTrip` | Có: mọi chuyến **không** phải tăng cường, cùng tuyến, cùng ngày, cùng giờ (06:xx) giữ khung đó. Không đổi schema. **Cách hiểu khi cài:** tính cả chuyến đã hủy, để chuyến Admin đã hủy không bị bấm lại dựng lên; chuyến xóa mềm thì không tính (app không thấy nó) | CT6 |
| CM-19 | **ĐÃ CHỐT 2026-11-19 (điểm C).** Máy chọn từng chuyến hay viết sẵn vòng cố định? | Máy chọn từng chuyến bằng `autoAssignResources` có sẵn, xếp theo giờ, cả 6 tuyến xen kẽ trên một trục. Đà Lạt được quay đầu trong ngày với kíp khác (tối thiểu 4 xe thay vì 6), khác lịch sử (mỗi xe một lượt/ngày) | CT6 |
| CM-20 | **ĐÃ CHỐT 2026-11-19 (điểm D).** Giá vé của chuyến thường lệ lấy ở đâu? Bảng `routes` không có cột giá | Giá của chuyến `COMPLETED` gần nhất cùng tuyến, cùng giờ — **cùng hàm** `RecommendationService.loadLatestPrices` của màn Đề xuất (mở từ `private` thành package-private, không chép bản thứ ba như What-if đã chép). Khung chưa có giá thì bỏ qua, có đếm | CT6 |
| CM-21 | **ĐÃ CHỐT 2026-11-19 (điểm E).** Tạo trước bao nhiêu ngày, và hai cái bẫy của hàm chọn xe khi lập lịch tương lai: km bảo trì không cộng dồn; "READY" là trạng thái lúc bấm nút | Mặc định 7 ngày (= `FORECAST_HORIZON_DAYS`). **Cách hiểu khi cài:** trần 14 ngày một lần bấm. Context lập lịch cộng km mọi chuyến chưa xong của xe vào luật bảo trì, và coi xe `TRAVELING` có chuyến `DEPARTED` giải thích là xếp được (luật #19 của validator); `REPAIRING` không. Chỉ áp trong bộ lập lịch; job 10 giây và màn Đề xuất không đổi. *(Thay một phần 2026-11-23, CM-25 / CT7d: mọi bộ chọn của máy cộng km đã xếp — R3 ở QĐ-22, "cách cài bảo trì trong lịch ở CT7d".)* | CT6 |
| CM-22 | **ĐÃ CHỐT 2026-11-19 (điểm F).** 9/20 xe trên DB thật không được máy chọn: 5 xe `TRAVELING` vì 5 chuyến demo `DEPARTED` từ tháng 7 chưa bao giờ kết thúc (#3, #6, #11, #12, #13), 1 xe thử `TRAVELING` không chuyến nào giải thích (19), 1 quá bảo trì (17), 1 sát bảo trì (16), 1 đang sửa (18) | Đóng 5 chuyến demo `DEPARTED` bằng nút "Hoàn thành" của Bảng điều hành, có sao lưu trước. Xe 16–19 là xe thử cố ý, giữ nguyên | CT6 |
| CM-23 | **ĐÃ CHỐT 2026-11-25: V4 — vận hành mô phỏng (ghi ở QĐ-24, "cách cài ở CT8"). Đã làm ở CT8a.** *(Phát hiện 2026-11-19, CT6.)* App không có ai vận hành chuyến thật. Chuyến thường lệ qua giờ khởi hành mà không ai bấm "Xuất phát/Hoàn thành" vẫn `ACTIVE`: (a) nó nằm ở nhóm "Trễ giờ" của Bảng điều hành; (b) lần chạy bổ sung lịch sử bỏ qua cả ngày đó, vì `HistoricalDataBackfill.existingGeneratorDays` (`:1229-1241`) coi mọi chuyến lúc 06/12/18:00 là "dạng bộ sinh" — nên dự báo không có quan sát cho những ngày có lịch thật. Làm gì với chuyến thường lệ đã qua? | **Đã chọn V4** (đề xuất 2026-11-25). Mỗi đêm, khung đã chạy xong được đóng với số khách lấy từ chính mô hình đã sinh lịch sử, rồi đi qua FSM. Nhờ vậy vòng "dự báo → đề xuất → duyệt → xe chạy → có khách (mô phỏng) → dự báo học lại" khép được, và xe thật sự tăng km nên luồng bảo trì của CT7d chạy thật. Các cách không chọn: V1 đóng với 0 vé (dự báo sẽ học rằng khung có 0 khách); V2 hủy (vị trí xe quay về 16/11, nên lịch đã dựng bị đứt chuỗi); V3 để nguyên (dữ liệu cũ dần, và sau khoảng một tuần job thôi tự tạo). **Còn để ngỏ:** chuyến tương lai vẫn 0 vé, nên job 10 giây chỉ chạy khi Admin gõ số vé; mô phỏng "vé bán dần" không thuộc CT8. Ba ngày trống 17–19/11 vẫn trống. Chuyến cũ tháng 7–9 trên tuyến 1 và 6 không bị đụng tới. M-16 chưa được xét lại | CT8 |
| CM-24 | **ĐÃ CHỐT 2026-11-23: PA-A — chọn cả vòng một lần, một xe + một tài xế; không xếp được cả vòng thì "thiếu tài nguyên" (ghi ở QĐ-22, "cách cài cả vòng một lần ở CT7c"). Đã làm ở CT7c.** Phát hiện 2026-11-22, `verify` CT7. Khi lịch thường lệ phủ cả tuần, màn Đề xuất gần như không còn tài xế cho chuyến thêm. Đo trên bản sao DB thật sau khi "Tạo lịch" 27/11–03/12: **1/25** thẻ đủ điều kiện, 24 "có xe nhưng không có tài xế" — **bản CT6 (chưa có CT7) trên cùng bản sao cũng 1/25**, nên không do CT7. Nguyên nhân: (a) lịch thường lệ gán cả phụ xe cho mọi chuyến (121/121; mỗi ngày dùng 22–26 trên 33 tài xế), nên ai cũng có chuyến kế tiếp; (b) luật vị trí hai phía (CT5b) xét RIÊNG chuyến thêm: lúc 18:00 ngày 27/11 có 15 tài xế rảnh ở Miền Đông nhưng chuyến kế tiếp của họ lại xuất phát từ Miền Đông, nên bị loại khỏi chuyến Miền Đông → Cần Thơ — dù lượt về của chính vòng đó sẽ đưa họ về kịp. Làm gì? | **Đã chọn PA-A** (đề xuất 2026-11-23; PA-B bỏ phụ xe của lịch thường lệ chỉ nâng 2/25 → 7/25, PA-C nới luật vị trí trái CM-5). Lúc đề xuất: **chọn cả vòng một lần** — một xe và một kíp cho cả chuyến thêm lẫn lượt đi kèm (đúng luật H1 của lịch sử, và "kíp ở lại cùng xe" của QĐ-26); luật vị trí xét cả vòng như một lượt "bến nhà → bến nhà", bận và giờ lái xét từng lượt. Cần thêm một lối chọn mới (cộng thêm) trong `TripService`. Hướng khác: lịch thường lệ không gán phụ xe cho tuyến ≤ 8 giờ (phụ xe vốn tùy chọn) — chỉ nới số người, không gỡ được (b). *Đo lại 2026-11-23, trước khi commit CT7, trên bản sao mới của DB thật: "Tạo lịch" 27/11–03/12 rồi mở màn Đề xuất → **2/25** đủ điều kiện, 16 "có xe nhưng không có tài xế", 7 "không có xe".* | CT7c |
| CM-25 | **ĐÃ CHỐT 2026-11-23: M1 — lịch có ngày bảo trì ở bến nhà (1 ngày), cột mới trên xe, mọi bộ chọn của máy cộng km đã xếp (ghi ở QĐ-22, "cách cài bảo trì trong lịch ở CT7d"). Đã làm ở CT7d.** Phát hiện 2026-11-23, kiểm lại CT6 trước khi commit. Lịch nhìn trước không có bước bảo trì. Luật bảo trì của context lập lịch (CM-21) cộng km của mọi chuyến chưa xong; ngưỡng mỗi xe 5.000 km, loại khi tới 90 % (4.500 km); một xe giường nằm chạy vòng Đà Lạt đi ~600 km/ngày. Đo trên bản sao DB thật sau "Tạo lịch" 27/11–03/12: cặp Đà Lạt (xe chuẩn Giường nằm 40) có **42/42** chuyến giường nằm ở 20–26/11, nhưng ở 27/11–03/12 chỉ **9** chuyến giường nằm, **26** chuyến Limousine 22, 4 chuyến Ghế ngồi 50, 3 chờ phân công. Ví dụ xe 29A-001.14: 230 km từ lần bảo trì + 4.200 km cam kết (14 chuyến Đà Lạt 20–26/11) + 300 km = 4.730 km ≥ 4.500 → bị loại. Luật làm đúng như CM-21; 53 chuyến 20–22/11 vẫn `ACTIVE` (CM-23) cũng được cộng, nhưng nếu chúng đã hoàn thành thì km vẫn vào odometer như vậy. Hệ quả: khung Đà Lạt bán xe 22 chỗ cho nhu cầu ~37–58 khách. Làm gì? | **Đã chọn M1** (đề xuất 2026-11-23: M0 chỉ cảnh báo và thống nhất một định nghĩa; M1 lịch có ngày bảo trì; M2 = M1 + thêm một xe giường nằm qua form). Không chọn M2: thêm xe là quyết định dữ liệu, không phải code | CT7d |
| CM-26 | **ĐÃ CHỐT 2026-11-25: K1 — một cột `trips.forecast_loop_id` (ghi ở QĐ-24, "cách cài ở CT8"). Đã làm ở CT8b.** Mở ở vòng thảo luận CT8. `Trip` không có cách nào biết chuyến do job tạo, cũng không nối được hai lượt của một vòng. Đánh dấu và nối thế nào? | **K1:** thêm một cột cho phép null. Cột có giá trị = chuyến do job đêm tạo; cùng giá trị = cùng một vòng (chuyến thêm trỏ vào chính nó, lượt đi kèm trỏ vào chuyến thêm); chuyến cũ để trống, như cách CT7d thêm cột. Chuyến gốc của mỗi lượt là chuyến chính của khung nó, không bao giờ là một chuyến tăng cường. Không chọn K0 (suy từ "chuyến thêm + phút 00": mong manh, và không biết lượt nào đi với lượt nào), K2 (hai cột, nguồn tạo + lượt cặp: nhiều hơn mức cần) | CT8b |
| CM-27 | **ĐÃ CHỐT 2026-11-25: duyệt / từ chối cả vòng một lần (ghi ở QĐ-24, "cách cài ở CT8"). Đã làm ở CT8b.** Mở ở vòng thảo luận CT8. Admin duyệt chuyến thêm Đà Lạt → Miền Đông 06:00 30/11 nhưng từ chối lượt đưa xe lên 18:00 29/11 thì xe không có mặt ở Đà Lạt để chạy, đúng kiểu xe .15. Làm gì? | Duyệt một lượt là duyệt cả vòng, trong một giao dịch, lượt nào cũng qua cổng kiểm tra; từ chối một lượt là hủy cả vòng. Xe và tài xế đã gán sẵn từ kế hoạch, nên màn Duyệt dùng chế độ "✅ Xác nhận" một nút; đường duyệt tay (đổi xe) không nhận lượt của vòng | CT8b |
| CM-28 | **ĐÃ CHỐT 2026-11-25: rút khi dự báo giảm, hoặc khi còn dưới 72 giờ mà chưa duyệt (ghi ở QĐ-24, "cách cài ở CT8"). Đã làm ở CT8b.** Mở ở vòng thảo luận CT8. QĐ-24 mục 5 đã chốt "dự báo giảm thì rút". Còn khi nào nữa, và rút thế nào? | (a) Dự báo giảm: máy tạm coi các vòng chờ duyệt của chính nó là chưa có rồi dự báo lại. Khung vẫn cần thì giữ vòng cũ (không tạo thêm, không đổi xe), khung hết cần thì rút; vòng đã duyệt thì giữ. (b) Quá hạn duyệt: còn dưới 72 giờ mà chưa duyệt thì rút, vì không kịp bán vé; cùng mốc 72 giờ của job 10 giây. Ví dụ vòng 06:00 ngày 30/11: lúc 01:00 ngày 27/11 còn 77 giờ thì giữ, lúc 01:00 ngày 28/11 còn 53 giờ thì rút. Rút xong, xe và tài xế được trả lại | CT8b |

---

## 4. Đã cân nhắc nhưng không chọn

- **Số chuyến:**
  - cách 2: chỉ thêm chuyến đủ hoà vốn, vì sẽ có khung "nóng" mà không có chuyến thêm nào;
  - cách 3: giữ nguyên công thức.
- **Tuyến dài:** L2 / P2 (đổi kíp lái giữa đường). DB thiếu bến giữa đường và giờ tới từng trạm.
- **Mức làm:** M1 đơn thuần (không biết vị trí xe); M3 lúc này ("tối ưu" chưa đo được, rủi ro thời gian).
- **Dữ liệu:** chỉ thêm bộ lọc tuyến thay vì sinh lại.
- **Luật giữ–về:** hai phép so "phí chờ > phí về" và "phí chờ > lợi nhuận chuyến về"; trần cứng 7 ngày.
- **Kíp lái:** đi nhờ chuyến khác về (K3). Phải lo vé và thay đổi nhiều thứ.
- **Xe dự phòng:** chia riêng theo từng cặp, vì 20 xe là quá mỏng.
- **Tự tạo chuyến mở bán thẳng**, không qua duyệt.
- **Cách A:** giữ Ghế ngồi 50 cho Vũng Tàu.
- **Tăng tần suất B3:** mô phỏng khách chọn giờ.
- **Chuyến đã qua giờ mà không ai vận hành (CM-23, 2026-11-25):**
  - V1: đóng với 0 vé, nên dự báo học rằng khung có 0 khách;
  - V2: hủy, nên vị trí xe quay về 16/11 và lịch đã dựng bị đứt chuỗi;
  - V3: để nguyên, nên dự báo cũ dần.
- **Đánh dấu vòng dự báo (CM-26):**
  - K0: suy từ "chuyến thêm + phút 00";
  - K2: hai cột.
- **Giờ chạy job đêm (CM-10):**
  - T1: chỉ hẹn giờ (laptop tắt đêm thì không chạy);
  - T3: chỉ có nút (không còn là "tự tạo").
- **Chuyến tự tạo khởi hành +30 phút (CM-15).**

---

## 5. Lịch sử thay đổi

- **2026-11-16:** tạo file. Ghi QĐ-18…QĐ-27 và CM-1…CM-15 từ các phiên thảo luận 10/11 → 16/11. Chưa có dòng code nào đổi.
- **2026-11-16 (sau):** chủ dự án duyệt kế hoạch và các đề xuất (*"oke. chốt hãy thực hiện đi"*).
  - Đóng CM-1 (bỏ diff format) và CM-11 (lỗi #34 cùng 7 mục, ghi ở `current_bugs_found.md`).
  - Sửa QĐ-26: lỗi giờ lái theo ngày khởi hành **có** phát sinh với chuyến tăng cường +30 phút trên tuyến Đà Lạt (đo được 41 chuyến qua nửa đêm trên DB thật).
  - QĐ-18 đã cài ở CT1, chưa commit.
- **2026-11-16 (CT2):** chủ dự án bảo *"làm CT02 luôn đi"*.
  - QĐ-20 đã áp lên DB thật: tuyến 10, 11 dùng Limousine 22.
  - CM-13 đóng theo đề xuất, và giữ 6 tuyến cũ trong seed cho khớp QĐ-19.
  - Sửa một sự thật sai trong `route_pairs_progress.md` mục 3: dữ liệu mẫu **có** loại Ghế ngồi.
- **2026-11-17:** QĐ-18 (`963aa71`) và phần seed của QĐ-19/QĐ-20 (`bbedfd8`) đã commit và push. Không có quyết định mới.
- **2026-11-17 (sau):** chủ dự án chốt *"đồng ý tách CT5, chốt CM-2 và CM-3"*.
  - **CM-2:** H1 "về ngay", ghi vào QĐ-21 (d).
  - **CM-3:** tâm 100%, ghi vào QĐ-21 (b).
  - **Tách CT5** thành CT5a (định nghĩa luật vị trí dùng chung, bến nhà; chưa bật vào chọn xe; làm **trước** CT3) và CT5b (bật vị trí vào chọn xe, tài xế; làm **sau** CT4).
  - **Lý do tách, đo trên DB thật:** nếu bật luật vị trí trên lịch sử cũ thì chỉ 6 xe ở Miền Đông, 3 ở Đà Lạt, 0 ở Cần Thơ và Vũng Tàu, còn 11/20 xe "kẹt" ở Mỹ Đình (4), Niệm Nghĩa (4), Huế (3), là các bến ngoài 3 cặp.
- **2026-11-17 (CT5a):** chủ dự án chốt CM-5 cách B và CM-12 cách (a), ghi vào QĐ-23.
  - CT5a đã cài: cột bến nhà trên xe và tài xế (bắt buộc ở form và service); luật `domain/LocationRule`, chưa bật vào việc chọn xe.
  - DB thật: cả 20 xe và 36 tài xế có bến nhà Miền Đông.
  - Không có quyết định mới ngoài hai mục trên.
- **2026-11-17 (CT3):** cài QĐ-21 (a)–(d), tổng duyệt trên bản sao.
  - Ghi "cách hiểu khi cài" vào QĐ-21.
  - Mở **CM-16**: bão và hủy vẫn theo từng chiều trong lịch sử vòng xe.
- **2026-11-17 (CT3, sau):** chủ dự án chốt *"chốt CM-16 giữ 6%"*. Bão và hủy giữ nguyên như QĐ-10, ghi vào QĐ-21. Code không đổi. Sau báo cáo CT3, chủ dự án bảo *"kiểm tra lại rồi commit và push"*, nên code được commit cùng các "cách hiểu khi cài" như đã ghi ở QĐ-21.
- **2026-11-17 (CT4):** chủ dự án giao 5 bước của CT4 và sửa bước PART B: *"KHÔNG XÓA, HÃY SAO LƯU LẠI ĐỂ MỞ LẠI NẾU CẦN lịch sử GĐ3"*.
  - Ghi thành sửa đổi của QĐ-21: lưu trữ bằng xoá mềm thay cho xoá, có script mở lại.
  - Đã làm trên DB thật. Không có quyết định mới nào khác.
- **2026-11-17 (CT5b):** cài QĐ-23 vào việc chọn xe và tài xế theo CM-5 cách B; ghi "cách cài ở CT5b" vào QĐ-23 (luật hai phía, mọi vai, hỏi DB theo từng vai). Không có quyết định mới. Phát hiện lỗi #35 (hai câu kiểm lịch có sẵn quét cả bảng); ghi ở `current_bugs_found.md`, chưa sửa.
- **2026-11-19 (CT6):** chủ dự án nghe giải thích lại CT6 cùng sáu điểm tìm ra khi đối chiếu code và DB, rồi chốt *"oke, tôi đồng ý tất cả"*.
  - Đóng **CM-4** (hằng số dùng chung `domain/RegularTimetable`, cùng danh sách tuyến với bộ sinh), **CM-6** (đảo bến, đúng một tuyến khớp), **CM-14** (nút, `ACTIVE` qua `createManualTrip`).
  - Thêm và đóng **CM-17…CM-22** (điểm A–F): chuyến chờ duyệt không xe; chuyến chính ở mọi trạng thái giữ khung; máy chọn từng chuyến theo giờ; giá của màn Đề xuất; 7 ngày, km cam kết, xe `TRAVELING` có chuyến giải thích; đóng 5 chuyến demo.
  - Ghi "cách cài lịch thường lệ ở CT6" vào QĐ-22. **Đà Lạt khác câu "vòng 2 ngày" của QĐ-22**, do CM-19: xe quay đầu trong ngày với kíp khác, 4 xe thay vì 6.
  - Mở **CM-23**: chuyến thường lệ đã qua giờ mà không ai vận hành.
- **2026-11-22 (CT7):** chủ dự án nghe giải thích hiện trạng và năm câu hỏi của CT7, rồi duyệt cả năm đề xuất: *"tôi đồng ý với những đề xuất của bạn (từ 1-5)"*; CM-23 *"bàn sau"*; chuyến #51360 chủ dự án tự hủy.
  - Đóng **CM-7** (W1) và **CM-9** (S1). Ghi PA-B, cách 2a, S1, W1 và việc chia CT7a / CT7b vào QĐ-22, "cách cài lãi cả vòng ở CT7".
  - Ghi các "cách hiểu khi cài" vào QĐ-22. Đáng chú ý nhất: thêm loại lượt đi kèm "không cần" (xe ở bến xa đi về bến nhà); What-if cũng trừ chuyến đã có để giữ bất biến "ở 100% cùng số chuyến thêm".
  - **Thay một phần G6-4** (thứ tự xét thẻ): nay là lãi CẢ VÒNG theo kế hoạch, không còn lãi một chiều.
  - Mở **CM-24**: lịch thường lệ phủ cả tuần thì màn Đề xuất gần như hết tài xế cho chuyến thêm (1/25 trên bản sao, cả bản CT6 lẫn CT7), vì luật vị trí hai phía xét riêng chuyến thêm. Đề xuất "chọn cả vòng một lần", chưa làm.
- **2026-11-23 (commit CT6, CT7):** chủ dự án: *"check lại đảm bảo mọi thứ hoạt động đúng và logic, sau đó commit và push"*. Không đổi quyết định nào.
  - CM-24 đo lại trên bản sao mới: 2/25 thẻ đủ điều kiện.
  - Mở **CM-25**: lịch nhìn trước không có bước bảo trì, nên từ 27/11 cặp Đà Lạt chạy chủ yếu bằng Limousine 22 (26/42 chuyến).
- **2026-11-23 (CT7c, CT7d):** chủ dự án: *"tôi duyệt CM24 và CM25 như đã bàn bạc"* — tức các đề xuất của phiên `7b523bdf`: CM-24 cách PA-A, CM-25 cách M1 (bảo trì 1 ngày, ở bến nhà).
  - Đóng **CM-24** và **CM-25**; ghi "cách cài cả vòng một lần ở CT7c" và "cách cài bảo trì trong lịch ở CT7d" vào QĐ-22.
  - **Thay một phần CM-21:** "chỉ áp trong bộ lập lịch; job 10 giây và màn Đề xuất không đổi" → mọi bộ chọn của máy cộng km đã xếp (R3).
  - **Thay một "cách hiểu khi cài" của CT7:** xe của lượt về nay luôn là chính xe của chuyến thêm.
  - **Cách hiểu khi cài mới, cần chủ dự án biết:** R4 (máy chỉ cho xe rời bến nhà khi đi rồi về vẫn dưới mốc 90%) — bắt buộc để bảo trì ở bến nhà làm được; R5 (máy xếp xe theo km tính cả lịch); mỗi xe một ngày dự kiến; xe sát / quá hạn theo km thật không được lịch tự đặt; ngày dự kiến chỉ để xem, không sửa tay.
- **2026-11-25 (sau CT7d, xe kẹt từ lịch cũ):** xe 29A-001.15 kẹt ở Đà Lạt từ lịch 20–26/11 tạo trước CT7d (tính cả lịch 4.420 km, về thêm 300 km là 4.720 ≥ 4.500). Chủ dự án chọn **người chọn tay đưa về**, không thêm luật "lượt về bến nhà được tới 100%": duyệt chuyến chờ #51493 (Đà Lạt → Miền Đông 12:00 27/11) bằng xe .15 + tài xế 18, và bỏ phụ xe của #51517 để chuỗi vị trí của tài xế 18 liền. Không đổi luật nào: R4 vẫn chỉ áp cho máy, cổng kiểm tra vẫn xét km thật (CM-5 cách B, "mềm cho người").
- **2026-11-25 (CT8):** chủ dự án nghe vòng thảo luận đầu CT8, rồi trả lời: *"tôi đồng ý tất cả, hãy thực hiện như những gì đã bàn … còn việc tài liệu thì để sau đi"*.
  - **Đóng:** CM-10 (T2), CM-15 (đúng giờ khung), CM-23 (V4). CM-14 thêm phần nối vào job đêm.
  - **Mở và đóng ngay:** CM-26 (K1), CM-27 (duyệt / từ chối cả vòng), CM-28 (rút khi dự báo giảm hoặc còn dưới 72 giờ).
  - Mục 6 của QĐ-24 (job 10 giây theo khung) cài đúng như đã ghi. Câu chữ mới của roadmap §4 được duyệt.
  - Ghi "cách cài ở CT8" và các "cách hiểu khi cài" vào QĐ-24. Điểm cần chủ dự án biết nhất: Admin đã từ chối vòng của một khung thì job không đề xuất lại khung đó; job tự rút thì chỉ xoá mềm bản nháp.
  - Sau khi tự test, chủ dự án bảo *"commit và push đi"*. Commit `fb6bc9b` (CT8c), `b34aba3` (CT8b), `ad75bc6` (CT8a), đã push.
  - Khi chủ dự án khởi động lại app, lần chạy bù đầu tiên của job đã ghi vào DB thật. Trước đó đã có bản sao lưu `ProjectBackup\db\busmanagement_2026-11-25_truoc-CT8.sql`.
  - Tài liệu của CT8 làm theo hai đợt. Đợt nhỏ (file này, `route_pairs_progress.md`, roadmap, `test_case.md`, hướng dẫn `verify`, hai sổ lỗi) làm trước CT9, theo lời chủ dự án *"làm đợt tài liệu nhỏ đó đi"*. Phần còn lại để sau.
