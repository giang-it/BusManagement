package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dự báo cho MỘT chuỗi = một tuyến ở một khung giờ khởi hành cố định.
 *
 * Chuỗi là đơn vị dự báo vì nhu cầu của tuyến A lúc 06:00 và lúc 18:00 là hai
 * câu chuyện khác nhau (bộ sinh dữ liệu Phase 5 gán hệ số khung giờ riêng cho
 * từng mốc, và số liệu thực tế xác nhận khung giữa trưa luôn yếu nhất).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastSeriesDto {

    private Long routeId;

    /** "Hà Nội → Hải Phòng", dựng từ Route.getDeparturePointDisplay() như DashboardService. */
    private String routeLabel;

    /** Giờ khởi hành của khung, 0..23. */
    private int departureHour;

    /** "06:00" — nhãn hiển thị của khung giờ. */
    private String slotLabel;

    /** Số chuyến COMPLETED có thật trong lịch sử của chuỗi này. */
    private int observationCount;

    /** Tỉ lệ lấp đầy trung bình trong toàn bộ lịch sử của chuỗi. */
    private double averageOccupancy;

    /** Trung bình trượt 7 ngày cuối — mức nền hiện tại, cũng là baseline để so sánh. */
    private double movingAverage;

    /**
     * Độ dốc xu hướng quy về MỘT TUẦN, tính bằng điểm phần trăm lấp đầy.
     * Dương = nhu cầu đang tăng. Hiển thị để Admin thấy vì sao dự báo cao/thấp
     * hơn mức nền.
     */
    private double trendPerWeek;

    /** Dự báo từng ngày trong chân trời dự báo. */
    private List<ForecastPointDto> points;

    /** Giá trị dự báo cao nhất trong chân trời — dùng để xếp hạng chuỗi cần chú ý trước. */
    private double peakOccupancy;

    /** Có ít nhất một ngày vượt ngưỡng tăng cường. */
    private boolean anyReinforcement;
}
