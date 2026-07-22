package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả tự chấm điểm độ chính xác của mô hình dự báo (backtest).
 *
 * Cách làm: giấu đi phần đuôi của lịch sử, huấn luyện mô hình trên phần còn
 * lại, bắt nó dự báo đúng quãng đã giấu, rồi so với số thật. Nhờ vậy con số sai
 * lệch dưới đây được đo trên dữ liệu mà mô hình CHƯA TỪNG NHÌN THẤY — nếu đo
 * trên chính dữ liệu đã học thì con số sẽ đẹp một cách vô nghĩa.
 *
 * baselineMape là mức sai của cách làm ngây thơ nhất ("tuần tới giống trung
 * bình 7 ngày gần đây"). Nó tồn tại để trả lời câu hỏi quan trọng hơn cả sai số
 * tuyệt đối: mô hình có thực sự tốt hơn việc không làm gì cả hay không.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastAccuracyDto {

    /** Số ngày cuối bị giấu đi để kiểm tra. */
    private int testDays;

    /** Số điểm dữ liệu thực tế đã được dùng để chấm điểm. */
    private int testedPoints;

    /** Sai số phần trăm tuyệt đối trung bình của mô hình (%). Càng nhỏ càng tốt. */
    private double modelMape;

    /** Sai số của baseline trung bình trượt (%), để đối chiếu. */
    private double baselineMape;

    /** Mô hình có thắng baseline không. Nếu không, đừng tô hồng — phải nói thẳng. */
    private boolean modelBeatsBaseline;

    /** Có đủ dữ liệu để chạy backtest hay không. */
    private boolean available;
}
