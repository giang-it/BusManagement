package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.HolidayFlow;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.dto.ForecastAccuracyDto.ScoreDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Các luật của ForecastService mới (GĐ5, QĐ-16) mà không cần DB: gom khung (G5-2), cờ tăng cường
 * và số chuyến cần (G5-6), mốc SN4 sạch và cách chấm điểm (G5-7), khóa nhớ (G5-4). JUnit thuần —
 * cùng lý do WhatIfCoverageTest chốt thẳng số học của What-if.
 */
class ForecastServiceRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void tripsOfTheSameRouteHourAndDay_formOneSlot() {
        List<Object[]> rows = List.of(
                row(1, "2026-10-20T06:00", 22, 22), // chuyến gốc, bán hết
                row(1, "2026-10-20T06:30", 15, 40), // chuyến tăng cường: cùng khung
                row(1, "2026-10-20T07:00", 5, 22), // 07h là chuỗi khác
                row(2, "2026-10-20T06:00", 10, 40), // tuyến khác
                row(1, "2026-10-21T06:00", 18, 22),
                row(1, "2026-10-23T06:00", 9, 22), // sau hôm nay: chưa phải lịch sử
                row(1, "2022-12-31T06:00", 9, 22), // trước lịch ngày lễ: bỏ, nếu không mô hình ném lỗi
                row(1, "2026-10-19T06:00", 9, 0), // không có ghế
                new Object[] { 1L, null, 9, 22 }); // thiếu giờ khởi hành

        SortedMap<ForecastService.SlotKey, SortedMap<LocalDate, int[]>> slots =
                ForecastService.aggregateSlots(rows, TODAY);

        assertEquals(Set.of(key(1, 6), key(1, 7), key(2, 6)), slots.keySet());
        SortedMap<LocalDate, int[]> slot = slots.get(key(1, 6));
        assertEquals(2, slot.size(), "chỉ 20/10 và 21/10");
        assertArrayEquals(new int[] { 37, 62 }, slot.get(LocalDate.of(2026, 10, 20)), "22 + 15 vé, 22 + 40 ghế");
        assertArrayEquals(new int[] { 18, 22 }, slot.get(LocalDate.of(2026, 10, 21)));
        assertEquals(List.of(key(1, 6), key(1, 7), key(2, 6)), List.copyOf(slots.keySet()), "sắp theo tuyến rồi giờ");
    }

    /** Một định nghĩa "chuyến nóng": cần ≥ 2 chuyến đúng khi nhu cầu vượt 90% xe chuẩn, ở mọi mức nhu cầu. */
    @Test
    void reinforcementFlag_andTripsNeeded_areOneDefinition() {
        for (int seats : new int[] { 22, 40, 50 }) {
            for (int c = 0; c <= 30_000; c++) {
                double demand = c / 100.0;
                boolean hot = ForecastService.isHot(demand, seats);
                int trips = ForecastService.tripsNeeded(demand, seats);
                assertEquals(hot, demand / seats > 0.90);
                assertEquals(hot, trips >= 2, demand + " khách / " + seats + " chỗ");
                if (hot) {
                    assertEquals(Math.max(2, (int) Math.ceil(demand / (0.9 * seats))), trips);
                }
            }
        }
        // Sát ngưỡng, hai phép chia làm tròn khác nhau: với xe 21 chỗ, 0,9 × 21 = 18,900000000000002 thì
        // 18,900000000000002 / 21 > 0,9 nhưng ⌈18,900000000000002 / (0,9 × 21)⌉ = 1. Tính số chuyến QUA cờ
        // nên "cần tăng cường" và "cần ≥ 2 chuyến" không bao giờ lệch nhau.
        for (int seats : new int[] { 13, 21, 26, 37, 42, 47, 52 }) {
            double demand = 0.9 * seats;
            assertTrue(ForecastService.isHot(demand, seats), seats + " chỗ: phép chia làm tròn lên trên 0,9");
            assertEquals(2, ForecastService.tripsNeeded(demand, seats), seats + " chỗ");
        }
        // Ví dụ của vòng thảo luận GĐ5.
        assertEquals(2, ForecastService.tripsNeeded(57.6, 40), "tuyến 4, 18:00, 25/10/2026");
        assertEquals(3, ForecastService.tripsNeeded(53.8, 22), "tuyến 1, 06:00, 13/02/2026");
        assertEquals(1, ForecastService.tripsNeeded(36.0, 40), "đúng 90% là chưa vượt ngưỡng");
        assertFalse(ForecastService.isHot(30, 0), "chưa rõ xe chuẩn thì không gắn cờ");
    }

    /** Đúng cách bước 0 đo: 4 NGÀY cùng thứ gần nhất, bỏ ngày lễ; ngày không quan sát vẫn chiếm chỗ. */
    @Test
    void seasonalNaive_averagesTheLastFourSameWeekdays_skippingHolidays() {
        double[][] sales = new double[50][1];
        for (int d = 0; d < sales.length; d++) {
            sales[d][0] = d; // số vé = chỉ số ngày, dễ kiểm
        }
        boolean[] holiday = new boolean[50];
        holiday[30] = true;
        sales[23][0] = Double.NaN;

        // Ngày gốc 41, ngày cần dự báo 44: cùng thứ là 37, 30 (lễ, bỏ), 23 (không quan sát), 16, 9.
        assertEquals((37 + 16 + 9) / 3.0, ForecastService.seasonalNaive(sales, holiday, 0, 41, 44), 1e-12);
        // Ngày cần dự báo cùng thứ với ngày gốc: bắt đầu từ chính ngày gốc.
        assertEquals((41 + 34 + 27 + 20) / 4.0, ForecastService.seasonalNaive(sales, holiday, 0, 41, 48), 1e-12);

        for (double[] day : sales) {
            day[0] = Double.NaN;
        }
        assertTrue(Double.isNaN(ForecastService.seasonalNaive(sales, holiday, 0, 41, 44)), "không có gì để lấy trung bình");
    }

    /**
     * Chấm min(dự báo; số ghế) với số vé: 13/02/2026, tuyến 1 06:00 — mô hình 53,8 khách, SN4 17,8;
     * xe 22 chỗ bán hết 22 vé. So thẳng nhu cầu với số vé sẽ ghi mô hình sai 31,8 khách.
     */
    @Test
    void score_capsForecastsAtTheSeatsThatRan() {
        ScoreDto s = ForecastService.score(List.of(new ForecastService.Scored(0, 53.8, 17.8, 22, 22)), new int[] { 22 });

        assertEquals(1, s.getTestedSlotDays());
        assertEquals(0.0, s.getModelWape(), 1e-12);
        assertEquals(100 * 4.2 / 22, s.getBaselineWape(), 1e-9);
        assertTrue(s.isModelBeatsBaseline());
        assertEquals(1, s.getSoldOutSlotDays());
        assertEquals(100.0, s.getModelSoldOutCaught());
        assertEquals(0.0, s.getBaselineSoldOutCaught());
        assertEquals(1, s.getHotSlotDays(), "22 vé > 90% của 22 chỗ");
        assertEquals(100.0, s.getModelFlagRecall());
        assertEquals(0.0, s.getBaselineFlagRecall());
        assertEquals(100.0, s.getModelFlagPrecision());
        assertNull(s.getBaselineFlagPrecision(), "SN4 không bật cờ nào: không có gì để chia");
    }

    @Test
    void score_ofNothing_isUndefined_notZero() {
        ScoreDto s = ForecastService.score(List.of(), new int[0]);
        assertEquals(0, s.getTestedSlotDays());
        assertNull(s.getModelWape());
        assertNull(s.getModelSoldOutCaught());
        assertFalse(s.isModelBeatsBaseline());
    }

    @Test
    void holidayLabels_nameTheDayRelativeToTheHoliday() {
        assertEquals("Tết D−4", ForecastService.holidayLabel(LocalDate.of(2026, 2, 13)));
        assertEquals("Tết D", ForecastService.holidayLabel(LocalDate.of(2026, 2, 17)));
        assertEquals("2/9 D+1", ForecastService.holidayLabel(LocalDate.of(2026, 9, 3)));
        assertEquals("Giỗ Tổ D · 30/4 D−4", ForecastService.holidayLabel(LocalDate.of(2026, 4, 26)),
                "hai cửa sổ chồng nhau");
        assertNull(ForecastService.holidayLabel(LocalDate.of(2026, 10, 22)));
        assertNull(ForecastService.holidayLabel(LocalDate.of(2033, 1, 1)), "ngoài lịch: không ném lỗi");
    }

    @Test
    void groups_followTheRouteFlow_numberedAsInThePythonReference() {
        assertEquals(ForecastService.GROUP_NO_FLOW, ForecastService.groupOf(null));
        Route route = new Route();
        assertEquals(2, ForecastService.groupOf(route));
        route.setHolidayFlow(HolidayFlow.LEAVING_HUB);
        assertEquals(0, ForecastService.groupOf(route));
        route.setHolidayFlow(HolidayFlow.TOWARD_HUB);
        assertEquals(1, ForecastService.groupOf(route));
    }

    /** Khóa nhớ (G5-4): giống hệt thì dùng lại, khác dù một vé hay một nhóm chiều thì không. */
    @Test
    void modelInput_isTheSameOnlyWhenEverySlotIsTheSame() {
        List<Object[]> rows = new ArrayList<>();
        for (int d = 1; d <= 30; d++) {
            rows.add(row(1, TODAY.minusDays(d) + "T06:00", 20, 40));
        }
        ForecastService.ModelInput a = input(rows, new int[] { 0 });
        ForecastService.ModelInput b = input(rows, new int[] { 0 });
        assertTrue(a.sameAs(b));
        assertFalse(a.sameAs(input(rows, new int[] { 2 })), "đổi chiều tuyến");

        List<Object[]> oneTicketMore = new ArrayList<>(rows);
        oneTicketMore.set(5, row(1, TODAY.minusDays(6) + "T06:00", 21, 40));
        assertFalse(a.sameAs(input(oneTicketMore, new int[] { 0 })), "thêm một vé");
        assertFalse(a.sameAs(null));
    }

    // =====================================================================

    private static ForecastService.ModelInput input(List<Object[]> rows, int[] groups) {
        SortedMap<ForecastService.SlotKey, SortedMap<LocalDate, int[]>> slots = ForecastService.aggregateSlots(rows, TODAY);
        return ForecastService.ModelInput.of(List.copyOf(slots.keySet()), slots, groups);
    }

    private static Object[] row(long route, String departure, int sold, int seats) {
        return new Object[] { route, LocalDateTime.parse(departure), sold, seats };
    }

    private static ForecastService.SlotKey key(long route, int hour) {
        return new ForecastService.SlotKey(route, hour);
    }
}
