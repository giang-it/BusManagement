package giang.com.BusManagement.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lịch ngày lễ dùng chung, dành cho bộ sinh dữ liệu lịch sử (GĐ2) và bộ dự báo nhu
 * cầu (GĐ5) của đợt thiết kế lại Demand Forecast (QĐ-9,
 * docs/development/forecast_decisions.md); tới GĐ1 chưa nơi nào gọi. Chỉ trả lời câu
 * hỏi "ngày này nằm quanh dịp lễ nào, lệch mấy ngày so với ngày lễ"; độ lớn hiệu ứng
 * của từng dịp là việc của mô hình học từ dữ liệu (QĐ-3), không nằm ở đây.
 *
 * Ngày lễ là SỰ THẬT khách quan như thứ trong tuần, không phải tham số nghiệp vụ, nên
 * lịch nằm trong code chứ không do Admin cấu hình — khác CostParameters, nơi con số
 * là quyết định của nhà xe.
 *
 * ====================================================================
 * PHẠM VI: 01/01/2023 → 31/12/2032
 * ====================================================================
 * Ngoài khoảng này mọi câu hỏi đều bị TỪ CHỐI (IllegalArgumentException) thay vì trả
 * lời thiếu: một ngày Tết năm 2033 bị coi như ngày thường là lỗi âm thầm làm hỏng dự
 * báo đúng vào lúc cần nó nhất. Nơi gọi phải hỏi covers() trước — màn dự báo mới
 * (GĐ5) sẽ dùng nó để cảnh báo khi lịch không phủ tới ngày cần dự báo; hết phạm vi
 * thì bổ sung bảng âm lịch bên dưới.
 *
 * ====================================================================
 * NGÀY ÂM LỊCH: BẢNG TRA, KHÔNG TỰ TÍNH
 * ====================================================================
 * Java không có lịch âm Việt Nam, và tự cài thuật toán thiên văn vào app là rủi ro
 * khó bảo vệ, nên ngày Tết và Giỗ Tổ lấy từ bảng tra. Bảng có thêm hai năm biên 2022
 * và 2033 để một ngày sát đầu hoặc cuối phạm vi vẫn biết được cửa sổ của năm bên
 * cạnh, kể cả khi cửa sổ được nới rộng.
 *
 * Nguồn (đối chiếu ngày 2026-10-21, đủ 24 ngày, khớp hoàn toàn):
 *  1. Thuật toán âm lịch Việt Nam của Hồ Ngọc Đức (informatik.uni-leipzig.de/~duc/amlich),
 *     tính theo múi giờ GMT+7 — cài lại độc lập, chỉ để kiểm tra, ở
 *     docs/development/forecast_research/holiday_calendar_check.py.
 *  2. Công cụ lịch âm của Báo Hà Tĩnh, tra từng ngày tại
 *     https://baohatinh.vn/cong-cu/lich-am/{dd-MM-yyyy}.
 * Múi giờ là chi tiết quan trọng: theo lịch Trung Quốc (GMT+8) Tết 2030 là 03/02,
 * theo lịch Việt Nam là 02/02 — vì vậy không dùng thư viện lịch âm Trung Quốc.
 */
public final class HolidayCalendar {

    /** Năm đầu tiên lịch phủ trọn (từ 01/01). */
    public static final int FIRST_COVERED_YEAR = 2023;

    /** Năm cuối cùng lịch phủ trọn (tới 31/12). */
    public static final int LAST_COVERED_YEAR = 2032;

    /**
     * Một ngày nằm trong cửa sổ của một dịp lễ.
     *
     * @param holiday dịp lễ
     * @param anchor  ngày neo của lần lễ chứa ngày đang hỏi (có thể thuộc năm bên cạnh:
     *                30/12/2025 thuộc cửa sổ Tết Dương lịch có ngày neo 01/01/2026)
     * @param offset  số ngày lệch so với ngày neo: âm là trước lễ ("Tết D−3" → −3),
     *                0 là đúng ngày lễ, dương là sau lễ
     */
    public record HolidayDay(Holiday holiday, LocalDate anchor, int offset) {
    }

    /** Hai ngày neo âm lịch của một năm dương lịch. */
    private record LunarAnchors(LocalDate lunarNewYear, LocalDate hungKingsCommemoration) {
    }

    /** Năm → (mùng 1 Tết, 10/3 âm lịch). Hai năm biên 2022 và 2033 xem javadoc của lớp. */
    private static final Map<Integer, LunarAnchors> LUNAR_ANCHORS = Map.ofEntries(
            lunarYear(2022, LocalDate.of(2022, 2, 1), LocalDate.of(2022, 4, 10)),
            lunarYear(2023, LocalDate.of(2023, 1, 22), LocalDate.of(2023, 4, 29)), // nhuận tháng 2
            lunarYear(2024, LocalDate.of(2024, 2, 10), LocalDate.of(2024, 4, 18)),
            lunarYear(2025, LocalDate.of(2025, 1, 29), LocalDate.of(2025, 4, 7)),
            lunarYear(2026, LocalDate.of(2026, 2, 17), LocalDate.of(2026, 4, 26)),
            lunarYear(2027, LocalDate.of(2027, 2, 6), LocalDate.of(2027, 4, 16)),
            lunarYear(2028, LocalDate.of(2028, 1, 26), LocalDate.of(2028, 4, 4)),
            lunarYear(2029, LocalDate.of(2029, 2, 13), LocalDate.of(2029, 4, 23)),
            lunarYear(2030, LocalDate.of(2030, 2, 2), LocalDate.of(2030, 4, 12)), // Trung Quốc: 03/02
            lunarYear(2031, LocalDate.of(2031, 1, 23), LocalDate.of(2031, 4, 1)), // nhuận tháng 3, Giỗ Tổ ở tháng 3 thường
            lunarYear(2032, LocalDate.of(2032, 2, 11), LocalDate.of(2032, 4, 19)),
            lunarYear(2033, LocalDate.of(2033, 1, 31), LocalDate.of(2033, 4, 9)));

    private HolidayCalendar() {
    }

    /** Lịch có trả lời được cho ngày này không (01/01/2023 → 31/12/2032). */
    public static boolean covers(LocalDate date) {
        return date.getYear() >= FIRST_COVERED_YEAR && date.getYear() <= LAST_COVERED_YEAR;
    }

    /**
     * Ngày neo của một dịp lễ trong một năm dương lịch.
     *
     * @throws IllegalArgumentException nếu năm nằm ngoài phạm vi lịch
     */
    public static LocalDate anchorOf(Holiday holiday, int year) {
        if (year < FIRST_COVERED_YEAR || year > LAST_COVERED_YEAR) {
            throw new IllegalArgumentException("Lịch ngày lễ chỉ phủ các năm " + FIRST_COVERED_YEAR + "–"
                    + LAST_COVERED_YEAR + "; năm " + year + " nằm ngoài phạm vi — phải bổ sung bảng âm lịch "
                    + "trong HolidayCalendar trước.");
        }
        return anchor(holiday, year);
    }

    /**
     * Mọi dịp lễ có cửa sổ chứa ngày này, xếp theo thứ tự khai báo của Holiday. Rỗng
     * nghĩa là ngày thường. Một ngày có thể thuộc HAI dịp khi cửa sổ chồng nhau — ví
     * dụ năm 2026 Giỗ Tổ rơi vào 26/4, nằm trong cửa sổ của 30/4.
     *
     * @throws IllegalArgumentException nếu ngày nằm ngoài phạm vi lịch (xem covers())
     */
    public static List<HolidayDay> holidaysOn(LocalDate date) {
        if (!covers(date)) {
            throw new IllegalArgumentException("Lịch ngày lễ chỉ phủ 01/01/" + FIRST_COVERED_YEAR + " → 31/12/"
                    + LAST_COVERED_YEAR + "; ngày " + date + " nằm ngoài phạm vi — phải bổ sung bảng âm lịch "
                    + "trong HolidayCalendar trước.");
        }
        List<HolidayDay> result = new ArrayList<>();
        for (Holiday holiday : Holiday.values()) {
            // Cửa sổ mỗi phía tối đa 365 ngày (xem Holiday), nên lần lễ chứa ngày này chỉ
            // có thể thuộc năm trước, năm nay hoặc năm sau — luôn nằm trong bảng 2022–2033.
            for (int year = date.getYear() - 1; year <= date.getYear() + 1; year++) {
                LocalDate anchor = anchor(holiday, year);
                long offset = ChronoUnit.DAYS.between(anchor, date);
                if (offset >= -holiday.getDaysBefore() && offset <= holiday.getDaysAfter()) {
                    result.add(new HolidayDay(holiday, anchor, (int) offset));
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * Ngày neo, không kiểm phạm vi — dùng nội bộ cho cả hai năm biên. Switch không có
     * nhánh default: thêm một dịp vào Holiday mà quên khai ngày neo thì không biên dịch
     * được.
     */
    private static LocalDate anchor(Holiday holiday, int year) {
        return switch (holiday) {
            case NEW_YEAR -> LocalDate.of(year, 1, 1);
            case LUNAR_NEW_YEAR -> lunarAnchors(year).lunarNewYear();
            case HUNG_KINGS_COMMEMORATION -> lunarAnchors(year).hungKingsCommemoration();
            case REUNIFICATION_AND_LABOUR_DAY -> LocalDate.of(year, 4, 30);
            case NATIONAL_DAY -> LocalDate.of(year, 9, 2);
        };
    }

    private static LunarAnchors lunarAnchors(int year) {
        LunarAnchors anchors = LUNAR_ANCHORS.get(year);
        if (anchors == null) {
            // Không xảy ra với ngày trong phạm vi: phạm vi 2023–2032 cộng hai năm biên.
            throw new IllegalStateException("Bảng âm lịch của HolidayCalendar thiếu năm " + year);
        }
        return anchors;
    }

    private static Map.Entry<Integer, LunarAnchors> lunarYear(int year, LocalDate lunarNewYear,
            LocalDate hungKingsCommemoration) {
        return Map.entry(year, new LunarAnchors(lunarNewYear, hungKingsCommemoration));
    }
}
