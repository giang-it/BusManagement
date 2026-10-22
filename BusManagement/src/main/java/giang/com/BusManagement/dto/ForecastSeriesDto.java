package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dự báo cho MỘT chuỗi = một tuyến ở một khung giờ khởi hành cố định.
 *
 * Chuỗi là đơn vị dự báo vì nhu cầu của tuyến A lúc 06:00 và lúc 18:00 là hai câu chuyện
 * khác nhau. Mỗi ngày của chuỗi là một KHUNG: mọi chuyến của tuyến khởi hành trong giờ đó,
 * kể cả chuyến tăng cường (QĐ-16, G5-2).
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

    /** Nhóm học hệ số lễ: "Rời TP lớn" / "Về TP lớn" / "Không theo chiều" (Route.holidayFlow). */
    private String flowLabel;

    /** Số ghế xe chuẩn: loại xe phù hợp của tuyến, hoặc sức chứa trung bình đội xe nếu tuyến chưa có. */
    private int standardSeats;

    /** false khi standardSeats là sức chứa trung bình đội xe vì tuyến chưa có loại xe phù hợp. */
    private boolean standardSeatsFromRoute;

    /** Số ngày có ít nhất một chuyến COMPLETED của khung — số quan sát mô hình học được. */
    private int observedDays;

    /** Trong số đó, số ngày bán hết vé: chỉ biết "nhu cầu ≥ số ghế", EM phải đoán lại. */
    private int soldOutDays;

    /** 7 ngày từ ngày mai — chân trời của thẻ Recommendation và What-if (QĐ-5). */
    private List<ForecastPointDto> points;

    /** Tới 35 ngày từ ngày mai — chỉ để xem trước trên màn Dự báo. */
    private List<ForecastPointDto> preview;

    /** demandRatio cao nhất trong 7 ngày — dùng để xếp chuỗi cần chú ý lên trước. */
    private double peakDemandRatio;

    /** Có ít nhất một ngày trong 7 ngày cần tăng cường. */
    private boolean anyReinforcement;
}
