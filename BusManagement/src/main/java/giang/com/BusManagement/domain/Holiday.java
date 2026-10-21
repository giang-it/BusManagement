package giang.com.BusManagement.domain;

import lombok.Getter;

/**
 * Các dịp lễ mà Demand Forecast xét tới — đúng các ngày nghỉ lễ, tết theo Điều 112
 * Bộ luật Lao động 2019, trong đó 30/4 và 1/5 gộp thành một dịp (QĐ-9,
 * docs/development/forecast_decisions.md). Không có Trung thu, Noel...: chưa có căn
 * cứ rằng chúng làm tăng khách xe khách, mà mỗi dịp lại thêm hệ số phải học.
 *
 * Mỗi dịp có một NGÀY NEO — ngày lễ theo luật hoặc theo âm lịch, KHÔNG phải kỳ nghỉ
 * chính thức (kỳ nghỉ đổi theo từng năm và chỉ được công bố hằng năm) — và một CỬA
 * SỔ quanh ngày đó. Mô hình học một hệ số cho từng cặp (dịp, số ngày lệch so với ngày
 * neo), ví dụ "Tết D−3"; ngày neo của từng năm do HolidayCalendar trả về.
 *
 * Cửa sổ là THAM SỐ MÔ HÌNH, không phải sự thật lịch: số hiện tại lấy nguyên từ thí
 * nghiệm bước 0 (forecast_research/step0/run_synth.py, mục WINDOWS) và sẽ được chốt
 * lại bằng số đo ở GĐ2. Cửa sổ càng rộng thì càng nhiều hệ số phải học. Mỗi phía tối
 * đa 365 ngày — HolidayCalendar chỉ xét ngày neo của năm trước, năm nay và năm sau.
 */
@Getter
public enum Holiday {
    /** Tết Dương lịch — neo 1/1. */
    NEW_YEAR(3, 4),
    /** Tết Âm lịch (Tết Nguyên đán) — neo mùng 1 tháng Giêng âm lịch. */
    LUNAR_NEW_YEAR(14, 21),
    /** Giỗ Tổ Hùng Vương — neo 10/3 âm lịch. */
    HUNG_KINGS_COMMEMORATION(3, 2),
    /** Ngày Chiến thắng (30/4) và Quốc tế Lao động (1/5), gộp một dịp — neo 30/4. */
    REUNIFICATION_AND_LABOUR_DAY(4, 5),
    /** Quốc khánh — neo 2/9. */
    NATIONAL_DAY(4, 2);

    /** Số ngày TRƯỚC ngày neo vẫn thuộc dịp: cửa sổ bắt đầu ở D − daysBefore. */
    private final int daysBefore;
    /** Số ngày SAU ngày neo vẫn thuộc dịp: cửa sổ kết thúc ở D + daysAfter. */
    private final int daysAfter;

    Holiday(int daysBefore, int daysAfter) {
        this.daysBefore = daysBefore;
        this.daysAfter = daysAfter;
    }
}
