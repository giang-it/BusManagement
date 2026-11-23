package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Route pairs CT7 (QĐ-22, M2; CM-9) — trang "Kế hoạch cặp tuyến": mỗi cặp một bảng cho một ngày, hai
 * chiều cạnh nhau, mỗi xe một hàng theo giờ chạy thường lệ; chuyến thêm của màn Đề xuất có dấu ★ cùng
 * lượt đi kèm của nó. Chỉ đọc, tính lại mỗi lần xem — cùng kết quả màn Đề xuất tính ra.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PairPlanViewDto {

    // --- Bối cảnh dự báo (như màn Đề xuất) ---
    private LocalDate historyEnd;
    private long historyStaleDays;
    private LocalDate forecastFrom;
    private int horizonDays;

    /** Ngày đang xem, và các ngày chọn được (chân trời dự báo). */
    private LocalDate selectedDate;
    private List<LocalDate> days = new ArrayList<>();

    /** Giờ chạy thường lệ — các cột của bảng (RegularTimetable.DEPARTURE_HOURS). */
    private List<Integer> hours = new ArrayList<>();

    /** Mỗi cặp: số chuyến thêm ★ từng ngày của chân trời — để thấy ngày nào, cặp nào được tăng cường. */
    private List<PairWeek> overview = new ArrayList<>();

    /** Bảng của từng cặp cho ngày đang xem. */
    private List<PairDay> pairs = new ArrayList<>();

    /** Lý do không xếp được cặp (thiếu chiều ngược…), như trang Lịch thường lệ. */
    private List<String> problems = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PairWeek {
        private String pairLabel;
        private List<DayCount> days = new ArrayList<>();
    }

    /** Một ngày của một cặp trong phần tổng quan. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayCount {
        private LocalDate date;
        /** Chuyến thêm ★ đủ điều kiện (xe + tài xế đã giữ chỗ). */
        private int extras;
        /** Chuyến thêm cần nhưng chưa xếp được (thiếu tài nguyên, cổng từ chối). */
        private int unserved;
        /** Tổng lãi cả vòng của các chuyến thêm ★ (null khi không có). */
        private BigDecimal loopProfit;
    }

    /** Bảng một cặp, một ngày. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PairDay {
        private String pairLabel;
        /** Chiều đi: rời bến nhà của đội xe. */
        private String outLabel;
        private String backLabel;
        private Double distanceKm;
        private Integer durationMinutes;
        private String busTypeLabel;

        /** false khi ngày này cặp chưa có chuyến nào trong hệ thống (chưa bấm "Tạo lịch"). */
        private boolean scheduled;

        private List<SlotPair> slots = new ArrayList<>();
        private List<BusRow> rows = new ArrayList<>();
        /** Chuyến thêm cần nhưng chưa xếp được, kèm lý do của màn Đề xuất. */
        private List<String> unserved = new ArrayList<>();

        // --- Dòng tóm tắt ---
        /** Số xe khác nhau chạy cặp này trong ngày (chuyến đã có + chuyến thêm ★ + lượt đi kèm). */
        private int busesUsed;
        private int extras;
        /** Km của các lượt đi kèm (về ngay / đưa xe tới / về rỗng) của chuyến thêm ★ trong ngày. */
        private double companionKm;
        private BigDecimal loopProfit;
    }

    /** Một giờ chạy thường lệ, hai chiều. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotPair {
        private int hour;
        private SlotInfo out;
        private SlotInfo back;
    }

    /** Một khung (tuyến, ngày, giờ): dự báo, chuyến đã có, số chuyến thêm ★. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotInfo {
        /** Nhu cầu dự báo (khách); null khi khung không có dự báo. */
        private Double demand;
        private int standardSeats;
        private boolean hot;
        /** Số chuyến khung cần (QĐ-18). */
        private int tripsNeeded;
        /** Chuyến đã có (chưa hủy) và tổng ghế của chúng. */
        private int trips;
        private int seats;
        private int extras;
    }

    /** Một xe trong ngày (hay một chuyến chưa có xe). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusRow {
        /** null = chuyến chưa có xe (chờ Admin phân công). */
        private String busPlate;
        /** Hàng có chuyến thêm ★ hay lượt đi kèm của nó. */
        private boolean extra;
        private List<HourCell> cells = new ArrayList<>();
        /** Bến của lượt cuối trong ngày — tối nay xe ngủ ở đó. */
        private String sleepsAt;
        /** Ghi chú cho hàng ★: chuyến thêm nào, vì sao, lãi cả vòng. */
        private List<String> notes = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourCell {
        private int hour;
        private List<LegCell> legs = new ArrayList<>();
    }

    /** Một lượt chạy trong ô. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegCell {
        /** REGULAR (chuyến đã có), EXTRA (chuyến tăng cường đã có), STAR (chuyến thêm ★), RETURN, BRING. */
        private String kind;
        /** true = chiều đi (rời bến nhà). */
        private boolean outbound;
        private String routeLabel;
        private LocalDateTime departure;
        /** Trạng thái của chuyến đã có; null với lượt ★. */
        private String status;
        /** Khách kỳ vọng của lượt ★ / lượt đi kèm; null với chuyến đã có. */
        private Double passengers;
    }
}
