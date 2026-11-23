package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Trang "Lịch thường lệ" (route pairs CT6): các cặp tuyến có lịch, giờ chạy, giá vé sẽ dùng,
 * những tuyến trong cấu hình mà không xếp được (kèm lý do), và mỗi ngày tới đã có bao nhiêu khung
 * có chuyến chính. Chỉ đọc — RegularTimetableService.overview().
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegularTimetableViewDto {

    /** Giờ khởi hành thường lệ (RegularTimetable.DEPARTURE_HOURS). */
    private List<Integer> hours;
    /** Hôm nay — mặc định của ô "từ ngày". */
    private LocalDate today;
    private int defaultDays;
    private int maxDays;
    /** Số khung mỗi ngày = số tuyến xếp được × số giờ chạy. */
    private int slotsPerDay;
    private List<PairRow> pairs;
    /** Tuyến trong cấu hình nhưng không xếp lịch được, mỗi dòng một lý do. */
    private List<String> problems;
    private List<DayRow> coverage;

    /** Một cặp tuyến: chiều đi và chiều về, cùng km, thời lượng, loại xe gợi ý. */
    @Data
    @AllArgsConstructor
    public static class PairRow {
        private Long outboundId;
        private String outboundLabel;
        private Long inboundId;
        private String inboundLabel;
        private Double distanceKm;
        private Integer durationMinutes;
        /** Loại xe gợi ý của chiều đi (QĐ-20: hai chiều cùng loại); null nếu tuyến không quy định. */
        private String busTypeName;
        private Integer busTypeCapacity;
        /** Giá vé theo từng giờ chạy, cùng thứ tự với hours; phần tử null = khung chưa có giá. */
        private List<BigDecimal> outboundPrices;
        private List<BigDecimal> inboundPrices;
    }

    /** Một ngày: đã có bao nhiêu khung có chuyến chính, trên tổng số khung của ngày. */
    @Data
    @AllArgsConstructor
    public static class DayRow {
        private LocalDate date;
        private int mainTrips;
        private int expected;
    }
}
