package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kết quả một lần bấm "Tạo lịch" (route pairs CT6): bao nhiêu chuyến mở bán, bao nhiêu chuyến chờ
 * Admin chọn xe (kèm lý do từng chuyến), bao nhiêu khung bỏ qua và vì sao.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegularTimetableRunDto {

    private LocalDate from;
    /** Ngày cuối, tính cả ngày đó. */
    private LocalDate to;
    /** Chuyến ACTIVE: máy xếp được xe và kíp, qua cổng kiểm tra, mở bán ngay. */
    private int created;
    /** Chuyến PENDING_APPROVAL chưa có xe: không có xe hay kíp hợp lệ ở bến xuất phát (CM-17). */
    private int pending;
    /** Khung đã có chuyến chính (thường lệ hay Admin tự tạo, kể cả đã hủy) — CM-18. */
    private int skippedExisting;
    /** Khung có giờ khởi hành đã qua lúc bấm. */
    private int skippedPast;
    /** Khung chưa có giá vé lịch sử (CM-20) — không tạo, vì không biết bán giá nào. */
    private int skippedNoPrice;
    private long elapsedMillis;
    private List<PendingRow> pendingTrips;
    /** Tuyến trong cấu hình nhưng không xếp lịch được, mỗi dòng một lý do. */
    private List<String> problems;
    /** Ngày bảo trì lần bấm này vừa đặt cho các xe sắp chạm mốc 90% theo lịch (CT7d, CM-25). */
    private List<MaintenancePlanDto> maintenancePlanned;

    /** Một chuyến chờ Admin chọn xe: để mở thẳng màn Duyệt. */
    @Data
    @AllArgsConstructor
    public static class PendingRow {
        private Long tripId;
        private String routeLabel;
        private LocalDateTime departure;
        private String reason;
    }
}
