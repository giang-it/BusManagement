package giang.com.BusManagement.dto;

/**
 * PHASE 7 (bước 2) — kết cục của MỘT thẻ đề xuất tăng cường.
 *
 * Là từ vựng của RIÊNG Recommendation Engine, suy ra từ kết quả pass/fail của
 * cổng Business Rule Validation (Phase 3) — KHÔNG bao giờ parse từ câu lý do
 * tiếng Việt của validator (xem THESIS_ROADMAP.md, Developer Notes: "What Phase
 * 7 CANNOT do with a ValidationResult"). Cổng là nhị phân; enum này chỉ thêm
 * trạng thái "không đủ tài nguyên" cho nhánh không chọn được xe/tài xế.
 *
 * Dùng enum thay cho chuỗi "OK"/"STALE" để đồng nhất với mọi status khác trong
 * hệ thống (TripStatus, BusStatus, IncidentStatus) và để template so khớp an
 * toàn thay vì đối chiếu chuỗi.
 */
public enum RecommendationStatus {

    /** Chọn được xe + tài xế và cổng validation xác nhận vẫn hợp lệ. */
    RECOMMENDED,

    /**
     * Chọn được xe + tài xế nhưng cổng validation cuối lại từ chối — tài nguyên
     * vừa hết hợp lệ. Trong mô hình tính-lại-mỗi-lần-xem, chọn và kiểm tra diễn
     * ra trong cùng một transaction đọc nên trạng thái này gần như không xuất
     * hiện; nó vẫn tồn tại để (a) trung thực khi cổng thật sự trượt, và (b) làm
     * lưới an toàn nếu logic chọn và logic validate về sau lệch nhau.
     */
    STALE,

    /** Không tìm được xe và/hoặc tài xế rảnh & hợp lệ cho khung giờ này. */
    NO_RESOURCE
}
