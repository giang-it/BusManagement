package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả tự chấm điểm của mô hình dự báo (backtest, QĐ-16 G5-7).
 *
 * Cách làm: lùi về một ngày gốc trong quá khứ, cho mô hình học CHỈ dữ liệu tới ngày đó, bắt
 * nó dự báo những ngày sau, rồi so với số thật. Con số sai lệch vì vậy đo trên dữ liệu mô
 * hình chưa từng thấy.
 *
 * App không biết nhu cầu thật, chỉ biết số vé đã bán, và khung bán hết vé chỉ cho biết
 * "nhu cầu ≥ số ghế". Nên mọi so sánh ở đây là min(dự báo, số ghế đã chạy) với số vé — cho
 * cả mô hình lẫn mốc đối chứng. So thẳng nhu cầu dự báo với số vé sẽ phạt mô hình đúng lúc
 * nó đúng (Tết: dự báo 54 khách, xe 22 chỗ bán hết 22 vé).
 *
 * Mốc đối chứng là SN4 sạch: trung bình cùng thứ trong 4 tuần gần nhất, bỏ ngày lễ — mốc đã
 * dùng suốt phần nghiên cứu. Nó tồn tại để trả lời câu hỏi quan trọng hơn cả sai số tuyệt
 * đối: mô hình có thực sự tốt hơn một cách làm ngây thơ hay không.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastAccuracyDto {

    /** Có đủ dữ liệu để chấm ít nhất một mốc hay không. */
    private boolean available;

    /** Số ngày gốc đã chấm, mỗi tuần một ngày gốc trong những tuần cuối của lịch sử. */
    private int originCount;

    private LocalDate firstOrigin;

    private LocalDate lastOrigin;

    /** Mỗi ngày gốc dự báo bao nhiêu ngày sau nó — bằng chân trời của thẻ đề xuất. */
    private int horizonDays;

    private ScoreDto recent;

    /** Khối "Tết vừa qua"; null khi lịch sử chưa có một Tết trọn cửa sổ và một Tết trước đó để học. */
    private TetBacktestDto tet;

    /**
     * Điểm của một tập ngày-khung đã chấm — mô hình cạnh SN4 sạch trên CÙNG các ngày-khung. Các tỉ
     * lệ là null khi mẫu số bằng 0 (không có khung nào để chia), để màn hình hiện "—" thay vì 0%.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreDto {
        /** Số ngày-khung đã chấm (có số vé thật và có giá trị SN4). */
        private int testedSlotDays;
        /** WAPE (%) của min(dự báo, số ghế) so với số vé thật. Càng nhỏ càng tốt. */
        private Double modelWape;
        private Double baselineWape;
        private boolean modelBeatsBaseline;
        /** Số ngày-khung bán hết vé. */
        private int soldOutSlotDays;
        /** % ngày-khung bán hết mà dự báo đã ≥ số ghế — "báo trước được". */
        private Double modelSoldOutCaught;
        private Double baselineSoldOutCaught;
        /** Số ngày-khung thực tế đông: số vé > 90% ghế xe chuẩn. */
        private int hotSlotDays;
        /** % khung đông thật mà cờ tăng cường bắt được. */
        private Double modelFlagRecall;
        private Double baselineFlagRecall;
        /** % cờ tăng cường rơi đúng vào khung đông thật. */
        private Double modelFlagPrecision;
        private Double baselineFlagPrecision;
    }

    /**
     * Khối "Tết vừa qua": học tới Chủ nhật gần nhất cách Tết ít nhất 3 tuần, dự báo 35 ngày
     * (trùm cả Tết), so với những gì đã xảy ra. Ngày gốc cố định theo Tết nên không đổi khi lịch
     * sử được chạy bổ sung.
     *
     * Dữ liệu biểu đồ là danh sách số thuần (danh sách theo chuỗi, mỗi chuỗi theo ngày) để
     * template đưa thẳng vào Chart.js; null = ngày đó khung không có chuyến hoàn thành.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TetBacktestDto {
        private LocalDate tetDay;
        private LocalDate origin;
        private LocalDate windowFrom;
        private LocalDate windowTo;
        private ScoreDto score;
        /** Nhãn trục ngày "dd/MM", ngày Tết có thêm chữ "Tết". */
        private List<String> dayLabels;
        private List<String> seriesLabels;
        private List<List<Double>> modelDemand;
        private List<List<Double>> baselineDemand;
        private List<List<Double>> ticketsSold;
        private List<List<Double>> seatsRun;
    }
}
