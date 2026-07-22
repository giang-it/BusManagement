package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Toàn bộ nội dung màn hình "Dự Báo Nhu Cầu".
 *
 * Ngoài các chuỗi dự báo, DTO còn mang theo phạm vi và độ tươi của dữ liệu lịch
 * sử. Đây không phải trang trí: dự báo chỉ đáng tin ngang với dữ liệu sinh ra
 * nó, và bộ dữ liệu hiện tại là MÔ PHỎNG, có mốc kết thúc cố định và mỗi ngày
 * lại cũ thêm. Che giấu điều đó sẽ khiến người xem tưởng đây là số liệu thật.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemandForecastViewDto {

    /** Ngày khởi hành sớm nhất có trong lịch sử được dùng. */
    private LocalDate historyStart;

    /** Ngày khởi hành muộn nhất có trong lịch sử được dùng. */
    private LocalDate historyEnd;

    /** Số ngày từ historyEnd tới hôm nay — dữ liệu đã cũ bao nhiêu. */
    private long historyStaleDays;

    /** Tổng số chuyến COMPLETED đã đưa vào tính toán. */
    private int totalObservations;

    /** Ngày đầu tiên được dự báo. */
    private LocalDate forecastFrom;

    /** Số ngày dự báo. */
    private int horizonDays;

    /** Các chuỗi dự báo, xếp theo mức lấp đầy đỉnh GIẢM DẦN (cần chú ý nhất lên đầu). */
    private List<ForecastSeriesDto> series;

    /** Số nhóm (tuyến × khung giờ) bị loại vì không đủ số quan sát tối thiểu. */
    private int skippedSeriesCount;

    /** Ngưỡng số quan sát tối thiểu đang áp dụng — hiển thị để giải thích skippedSeriesCount. */
    private int minObservations;

    /** Hệ số thứ trong tuần, đã Việt hoá nhãn, dùng chung cho mọi chuỗi. */
    private List<DayOfWeekFactorDto> dayOfWeekFactors;

    private ForecastAccuracyDto accuracy;

    /** Tổng số ngày-chuỗi được dự báo là vượt ngưỡng tăng cường. */
    private long reinforcementSignalCount;

    /**
     * Một hệ số mùa vụ theo thứ trong tuần.
     *
     * 1.0 = đúng mức trung bình; 1.20 = thứ đó đông hơn trung bình 20%.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayOfWeekFactorDto {
        private String dayLabel;
        private boolean weekend;
        private double factor;
        private int observationCount;
    }
}
