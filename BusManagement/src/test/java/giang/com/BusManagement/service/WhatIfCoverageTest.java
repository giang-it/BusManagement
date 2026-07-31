package giang.com.BusManagement.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Chốt số học độ phủ của màn What-if — regression cho lỗi #7 trong
 * docs/todo/current_bugs_found.md.
 *
 * Trước khi sửa, độ phủ là {@code min(hotSlots, servablePerDay × số ngày)}: một
 * phép GỘP cho năng lực nhàn rỗi của ngày vắng "cho vay" sang ngày cao điểm. Vì
 * nhu cầu dự báo dồn cục (hệ số cuối tuần ~1,19 của ForecastService), công thức đó
 * báo "phủ 100% — đủ năng lực" ngay cả khi đội xe bị hạ xuống MỘT tài xế.
 *
 * Test này là JUnit thuần, KHÔNG @SpringBootTest — khác các test khác trong dự án
 * là có chủ đích: {@link WhatIfSimulationService#coverableSlots} là hàm thuần,
 * không cần database, và điều cần chốt là số học chứ không phải tầng truy cập dữ
 * liệu. Nạp cả Spring context cho nó sẽ chỉ làm test chậm mà không chứng minh thêm
 * điều gì.
 *
 * Hai nhóm dữ liệu bên dưới đều là số ĐO ĐƯỢC trên bộ dữ liệu thật ngày
 * 2026-07-30 (drive app trên :8099), không phải số bịa:
 * <ul>
 *   <li>Hiện trạng: 17 khung đông phân bố T6=1, T7=6, CN=6, T2..T5=1 mỗi ngày —
 *       12/17 dồn vào cuối tuần.</li>
 *   <li>Nhu cầu 200%: 105 khung, đều 15/ngày — phân bố ĐỀU, và đây chính là lý do
 *       việc kiểm chứng Phase 8 ban đầu không phát hiện lỗi: khi nhu cầu đều thì
 *       gộp và tính-theo-ngày cho CÙNG một số.</li>
 * </ul>
 */
class WhatIfCoverageTest {

    private static final int HORIZON_DAYS = 7;
    private static final LocalDate DAY_1 = LocalDate.of(2026, 7, 31); // T6

    /** Throughput đo được: cửa sổ 15,0h ÷ (3,0h + 1,0h) = 3,75 chuyến/xe/ngày. */
    private static final double PER_BUS_PER_DAY = 3.75;

    /** Trần theo tài xế: 8h/ngày ÷ 3,0h mỗi chuyến. */
    private static double driverThroughput(int drivers) {
        return drivers * 8.0 / 3.0;
    }

    /** 17 khung đông của hiện trạng, đúng phân bố ngày đo được trên app. */
    private static List<LocalDate> baselineHotDates() {
        return hotDates(1, 6, 6, 1, 1, 1, 1);
    }

    /** 105 khung ở nhu cầu 200% — đều 15 khung mỗi ngày. */
    private static List<LocalDate> uniformHotDates() {
        return hotDates(15, 15, 15, 15, 15, 15, 15);
    }

    private static List<LocalDate> hotDates(int... countPerDay) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < countPerDay.length; i++) {
            dates.addAll(Collections.nCopies(countPerDay[i], DAY_1.plusDays(i)));
        }
        return dates;
    }

    /** Công thức GỘP đã bị thay thế — chỉ dùng để chứng minh sự khác biệt. */
    private static int pooledFormula(List<LocalDate> hotDates, double servablePerDay) {
        return (int) Math.min(hotDates.size(), Math.floor(servablePerDay * HORIZON_DAYS));
    }

    // =========================================================================
    // Hiện trạng: nhu cầu DỒN CỤC — đây là nơi lỗi #7 sống
    // =========================================================================

    /** Đủ năng lực thật thì vẫn phải phủ 100% — bản sửa không được bi quan hoá. */
    @Test
    void baselineFleet_coversEverything() {
        assertEquals(17, WhatIfSimulationService.coverableSlots(
                baselineHotDates(), 13 * PER_BUS_PER_DAY));
    }

    /**
     * Regression #7: một tài xế KHÔNG thể phủ 17 chuyến tăng cường trong 7 ngày.
     * Ngày cao điểm có 6 khung nhưng một tài xế chỉ kham 2,67 chuyến/ngày.
     */
    @Test
    void oneDriver_cannotCoverTheWeekendPeak() {
        assertEquals(10, WhatIfSimulationService.coverableSlots(
                baselineHotDates(), driverThroughput(1)));
    }

    @Test
    void twoDrivers_stillShortOnThePeakDays() {
        assertEquals(15, WhatIfSimulationService.coverableSlots(
                baselineHotDates(), driverThroughput(2)));
    }

    @Test
    void oneBus_cannotCoverTheWeekendPeak() {
        assertEquals(12, WhatIfSimulationService.coverableSlots(
                baselineHotDates(), 1 * PER_BUS_PER_DAY));
    }

    /**
     * Biên: 2 xe = 7,5 chuyến/ngày, vẫn trên đỉnh 6 khung/ngày, nên phủ đủ. Đây là
     * ca mà công thức cũ và mới ĐỒNG Ý — giữ lại để bản sửa không siết quá tay.
     */
    @Test
    void twoBuses_areAlreadyEnough_bothFormulasAgree() {
        double spd = 2 * PER_BUS_PER_DAY;
        assertEquals(17, WhatIfSimulationService.coverableSlots(baselineHotDates(), spd));
        assertEquals(17, pooledFormula(baselineHotDates(), spd));
    }

    /**
     * Ghi lại chính xác cái sai mà bản sửa loại bỏ: với 1 tài xế, công thức gộp cho
     * 17/17 (báo "đủ năng lực, không bị bó"), còn tính theo ngày cho 10/17 (58,8%,
     * nút thắt DRIVER). Nếu ai đó "đơn giản hoá" trở lại thành một phép nhân với số
     * ngày, test này đỏ.
     */
    @Test
    void perDayAccounting_differsFromThePooledFormulaItReplaced() {
        double spd = driverThroughput(1);

        assertEquals(17, pooledFormula(baselineHotDates(), spd),
                "công thức gộp cũ báo phủ trọn 17 khung");
        assertEquals(10, WhatIfSimulationService.coverableSlots(baselineHotDates(), spd),
                "tính theo ngày chỉ phủ được 10 khung");
        assertNotEquals(pooledFormula(baselineHotDates(), spd),
                WhatIfSimulationService.coverableSlots(baselineHotDates(), spd));
    }

    // =========================================================================
    // Nhu cầu ĐỀU: các con số roadmap đã ghi nhận PHẢI không đổi
    // =========================================================================

    /**
     * Nhu cầu 200% + 2 xe: roadmap (Phase 8, "As delivered" 2026-07-25) ghi
     * 49,5% coverage = 52/105. Phân bố đều nên hai công thức trùng nhau — bản sửa
     * không được làm sai lệch con số đã kiểm chứng.
     */
    @Test
    void uniformDemand_twoBuses_matchesTheFigureRecordedInTheRoadmap() {
        double spd = 2 * PER_BUS_PER_DAY;
        assertEquals(52, WhatIfSimulationService.coverableSlots(uniformHotDates(), spd));
        assertEquals(52, pooledFormula(uniformHotDates(), spd));
    }

    /**
     * Nhu cầu 200% + 4 tài xế: roadmap ghi 70,5% = 74/105. Cũng phải không đổi.
     */
    @Test
    void uniformDemand_fourDrivers_matchesTheFigureRecordedInTheRoadmap() {
        double spd = driverThroughput(4);
        assertEquals(74, WhatIfSimulationService.coverableSlots(uniformHotDates(), spd));
        assertEquals(74, pooledFormula(uniformHotDates(), spd));
    }

    /**
     * Làm tròn xuống MỘT LẦN ở cuối, không từng ngày: 7,5 chuyến/ngày × 7 ngày phải
     * cho 52, không phải 49 (= 7 mỗi ngày). servablePerDay là tỉ lệ thực của mô hình
     * cycle-time, chặt xuống từng ngày sẽ vứt bỏ năng lực có thật.
     */
    @Test
    void fractionalCapacityIsFlooredOnceAtTheEnd_notPerDay() {
        assertEquals(52, WhatIfSimulationService.coverableSlots(uniformHotDates(), 7.5));
        assertNotEquals(49, WhatIfSimulationService.coverableSlots(uniformHotDates(), 7.5));
    }

    // =========================================================================
    // Biên
    // =========================================================================

    @Test
    void noHotSlots_coversNothing() {
        assertEquals(0, WhatIfSimulationService.coverableSlots(List.of(), 48.75));
    }

    @Test
    void noCapacity_coversNothing() {
        assertEquals(0, WhatIfSimulationService.coverableSlots(baselineHotDates(), 0.0));
    }
}
