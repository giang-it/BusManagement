package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Toàn bộ nội dung màn hình "Dự Báo Nhu Cầu".
 *
 * Ngoài các chuỗi dự báo, DTO còn mang theo phạm vi và độ tươi của dữ liệu lịch sử. Đây
 * không phải trang trí: dự báo chỉ đáng tin ngang với dữ liệu sinh ra nó, và bộ dữ liệu hiện
 * tại là MÔ PHỎNG, mỗi ngày lại cũ thêm. Che giấu điều đó sẽ khiến người xem tưởng đây là số
 * liệu thật.
 *
 * Recommendation và What-if dùng lại DTO này qua ForecastService.buildForecast(); khi đó
 * accuracy = null — chỉ ForecastService.buildForecastPage() mới tự chấm điểm (QĐ-16, G5-4).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemandForecastViewDto {

    /** Ngày đầu của lịch sử mô hình học. */
    private LocalDate historyStart;

    /** Ngày cuối có dữ liệu — cũng là ngày gốc của dự báo. */
    private LocalDate historyEnd;

    /** Số ngày từ historyEnd tới hôm nay — dữ liệu đã cũ bao nhiêu. */
    private long historyStaleDays;

    /** Ngày đầu tiên được dự báo (ngày mai). */
    private LocalDate forecastFrom;

    /** Số ngày của thẻ đề xuất (7). */
    private int horizonDays;

    /** Số ngày xem trước thật sự có (35, ít hơn khi chạm cuối lịch ngày lễ). */
    private int previewDays;

    /** Ngày cuối mà lịch ngày lễ trả lời được; dự báo không vượt quá ngày này. */
    private LocalDate calendarEnd;

    /** Các chuỗi dự báo, xếp theo demandRatio đỉnh GIẢM DẦN (cần chú ý nhất lên đầu). */
    private List<ForecastSeriesDto> series;

    /** Số nhóm (tuyến × khung giờ) bị loại vì không đủ số ngày quan sát tối thiểu. */
    private int skippedSeriesCount;

    /** Ngưỡng số ngày quan sát tối thiểu — hiển thị để giải thích skippedSeriesCount. */
    private int minObservedDays;

    /** Tổng số ô (ngày × chuỗi) trong 7 ngày được dự báo là cần tăng cường. */
    private long reinforcementSignalCount;

    /** Thông tin lần khớp mô hình; null khi chưa có chuỗi nào để khớp. */
    private ModelInfoDto model;

    /** Hệ số lễ gộp theo chiều tuyến mà mô hình học được, mỗi dịp một bảng. */
    private List<HolidayEffectTableDto> holidayEffects;

    /** Dữ liệu biểu đồ xem trước 35 ngày. */
    private PreviewChartDto previewChart;

    /** Tự chấm điểm — chỉ buildForecastPage() điền, buildForecast() để null. */
    private ForecastAccuracyDto accuracy;

    /** Lần khớp mô hình: học bao nhiêu, mất bao lâu, có dùng lại kết quả cũ không. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfoDto {
        /** Số ngày-khung đã học (khung có ít nhất một chuyến hoàn thành). */
        private int observedSlotDays;
        /** Trong số đó, số khung bán hết vé — những khung EM phải đoán lại. */
        private int soldOutSlotDays;
        private int emRounds;
        /** Tổng số vòng IRLS của lần đầu và các vòng EM. */
        private int irlsIterations;
        /** Tham số size của nhị thức âm ở bước E cuối — càng nhỏ thì nhu cầu càng dao động. */
        private double negativeBinomialSize;
        private long fitMillis;
        private LocalDateTime computedAt;
        /** true khi lịch sử y hệt lần trước nên dùng lại kết quả đã khớp (QĐ-16, G5-4). */
        private boolean reused;
    }

    /** Hệ số lễ gộp của một dịp: mỗi nhóm chiều một dòng, mỗi ngày lệch một cột. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HolidayEffectTableDto {
        /** "Tết Nguyên đán". */
        private String holidayLabel;
        /** Nhãn cột: "D−14" … "D" … "D+21". */
        private List<String> offsetLabels;
        private List<HolidayEffectRowDto> rows;
    }

    /** Một nhóm chiều trong bảng hệ số lễ. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HolidayEffectRowDto {
        /** "Rời TP lớn". */
        private String groupLabel;
        /** Hệ số nhân theo từng cột; null khi mô hình chưa gặp ngày lệch đó. 1,00 = như ngày thường. */
        private List<Double> multipliers;
        private double peakMultiplier;
        private String peakOffsetLabel;
        private double lowMultiplier;
        private String lowOffsetLabel;
    }

    /**
     * Dữ liệu biểu đồ xem trước 35 ngày, ở dạng danh sách số thuần để template đưa thẳng vào
     * Chart.js — cùng cách dashboard-analytics.html đưa dữ liệu biểu đồ.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewChartDto {
        /** "dd/MM" cho trục ngày. */
        private List<String> dayLabels;
        /** Dịp lễ của từng ngày; chuỗi rỗng là ngày thường. */
        private List<String> holidayLabels;
        /** "Tuyến … — 06:00", cùng thứ tự với series. */
        private List<String> seriesLabels;
        /** demand[chuỗi][ngày]. */
        private List<List<Double>> demand;
        private List<Integer> standardSeats;
    }
}
