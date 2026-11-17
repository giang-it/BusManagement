package giang.com.BusManagement.config;

import giang.com.BusManagement.config.HistorySimulation.OfficialBreak;
import giang.com.BusManagement.config.HistorySimulation.RouteProfile;
import giang.com.BusManagement.domain.Holiday;
import giang.com.BusManagement.domain.HolidayCalendar;
import giang.com.BusManagement.domain.HolidayFlow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phần mô phỏng của bộ sinh dữ liệu lịch sử (QĐ-10, docs/development/forecast_decisions.md).
 *
 * JUnit thuần, không @SpringBootTest — HistorySimulation không đụng DB.
 *
 * Test quan trọng nhất là aLaterRun_reproducesEveryEarlierDay: nó chốt rằng chạy bổ sung
 * không làm đổi những ngày đã sinh. Bộ sinh Phase 5 tính xu hướng từ "hôm nay − 84" nên
 * mỗi lần chạy bổ sung lại reset (lỗi #31) — test này đỏ với kiểu tính đó.
 */
class HistorySimulationTest {

    private static final LocalDate LAST_DAY = LocalDate.of(2026, 10, 20);
    private static final RouteProfile OUT = new RouteProfile(1, HolidayFlow.LEAVING_HUB, 22);
    private static final RouteProfile IN = new RouteProfile(4, HolidayFlow.TOWARD_HUB, 40);
    private static final RouteProfile CROSS = new RouteProfile(3, null, 40);
    private static final List<RouteProfile> ROUTES = List.of(OUT, IN, CROSS);
    /** Một cặp đi / về (QĐ-21): Miền Đông → Vũng Tàu và chiều ngược. */
    private static final RouteProfile PAIR_OUT = new RouteProfile(10, HolidayFlow.LEAVING_HUB, 22, 11L);
    private static final RouteProfile PAIR_IN = new RouteProfile(11, HolidayFlow.TOWARD_HUB, 22, 10L);
    private static final List<RouteProfile> PAIRED = List.of(PAIR_OUT, PAIR_IN);

    private static HistorySimulation simulation(LocalDate lastDay) {
        return new HistorySimulation(HistoricalDataBackfill.RANDOM_SEED, ROUTES, lastDay);
    }

    @Test
    void aLaterRun_reproducesEveryEarlierDay_soTopUpsDoNotReset() {
        LocalDate earlierEnd = LocalDate.of(2024, 6, 30);
        HistorySimulation earlier = simulation(earlierEnd);
        HistorySimulation later = simulation(LAST_DAY);

        for (LocalDate d = HistorySimulation.EPOCH; !d.isAfter(earlierEnd); d = d.plusDays(1)) {
            for (RouteProfile route : ROUTES) {
                assertEquals(earlier.isClosed(route.routeId(), d), later.isClosed(route.routeId(), d), d.toString());
                assertEquals(earlier.wishedExtraTrips(route.routeId(), d),
                        later.wishedExtraTrips(route.routeId(), d), d.toString());
                for (int h = 0; h < HistorySimulation.DEPARTURE_HOURS.size(); h++) {
                    assertEquals(earlier.mean(route, d, h), later.mean(route, d, h), route + " " + d + " " + h);
                    assertEquals(earlier.demand(route, d, h), later.demand(route, d, h), route + " " + d + " " + h);
                    assertEquals(earlier.isRandomlyCancelled(route.routeId(), d, h),
                            later.isRandomlyCancelled(route.routeId(), d, h));
                }
            }
        }
    }

    /** Cùng phép thử cho một cặp: đợt về được cân theo CẢ đợt đi, mà vẫn không đổi khi chạy bổ sung. */
    @Test
    void aLaterRun_reproducesEveryEarlierDay_forAPairToo() {
        LocalDate earlierEnd = LocalDate.of(2024, 6, 30);
        HistorySimulation earlier = new HistorySimulation(HistoricalDataBackfill.RANDOM_SEED, PAIRED, earlierEnd);
        HistorySimulation later = new HistorySimulation(HistoricalDataBackfill.RANDOM_SEED, PAIRED, LAST_DAY);
        for (LocalDate d = HistorySimulation.EPOCH; !d.isAfter(earlierEnd); d = d.plusDays(1)) {
            for (RouteProfile route : PAIRED) {
                for (int h = 0; h < HistorySimulation.DEPARTURE_HOURS.size(); h++) {
                    assertEquals(earlier.mean(route, d, h), later.mean(route, d, h), route + " " + d + " " + h);
                    assertEquals(earlier.demand(route, d, h), later.demand(route, d, h), route + " " + d + " " + h);
                }
            }
        }
    }

    /**
     * QĐ-21 (a): mức tuyến, độ trôi và nhảy bậc rút theo CẶP — hai chiều của một cặp có cùng
     * "mức khách" mọi ngày; chỉ hình dạng theo chiều (mùa, thứ, lễ) là khác. Hai tuyến không ghép
     * cặp thì mỗi tuyến một mức như trước.
     */
    @Test
    void pairedRoutes_shareTheirLevel_whileUnpairedRoutesDoNot() {
        HistorySimulation paired = new HistorySimulation(HistoricalDataBackfill.RANDOM_SEED, PAIRED, LAST_DAY);
        HistorySimulation unpaired = simulation(LAST_DAY);
        double largestGap = 0;
        for (LocalDate d = HistorySimulation.EPOCH; !d.isAfter(LAST_DAY); d = d.plusDays(1)) {
            assertEquals(paired.levelLog(PAIR_OUT, d), paired.levelLog(PAIR_IN, d), 1e-9, d.toString());
            largestGap = Math.max(largestGap, Math.abs(unpaired.levelLog(OUT, d) - unpaired.levelLog(IN, d)));
        }
        assertTrue(largestGap > 0.02, "tuyến lẻ vẫn tự rút mức riêng: lệch lớn nhất " + largestGap);
    }

    /**
     * QĐ-21 (b), CM-3: tổng khách dư của đợt VỀ = r × tổng khách dư của đợt ĐI của cùng cặp, mỗi
     * (dịp lễ, năm) một r, r đều trong [0,85; 1,15], tâm 100% — có năm về ít hơn, có năm về nhiều hơn.
     */
    @Test
    void returnWave_isTheOutboundWaveTimesR_withRCentredOnOneHundredPercent() {
        HistorySimulation simulation = new HistorySimulation(HistoricalDataBackfill.RANDOM_SEED, PAIRED, LAST_DAY);
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = 0;
        int n = 0;
        for (Holiday holiday : Holiday.values()) {
            for (int year = 2023; year <= 2027; year++) {
                double r = simulation.returnRatio(PAIR_OUT.pairKey(), holiday, year);
                double out = simulation.holidayExcess(PAIR_OUT.routeId(), holiday, year);
                double back = simulation.holidayExcess(PAIR_IN.routeId(), holiday, year);
                assertTrue(out > 0, holiday + " " + year + ": đợt đi có khách dư");
                assertEquals(r, back / out, 1e-6, holiday + " " + year);
                assertTrue(r >= HistorySimulation.RETURN_RATIO_MIN && r <= HistorySimulation.RETURN_RATIO_MAX);
                sum += r;
                min = Math.min(min, r);
                max = Math.max(max, r);
                n++;
            }
        }
        assertEquals(1.0, sum / n, 0.05, "tâm của r");
        assertTrue(min < 0.95 && max > 1.05, "r dao động hai phía: " + min + " … " + max);
    }

    @Test
    void tetBreak_startsTwoToFourDaysBeforeTet_andLastsSevenToNineDays() {
        HistorySimulation simulation = simulation(LAST_DAY);
        for (int year = HolidayCalendar.FIRST_COVERED_YEAR; year <= HolidayCalendar.LAST_COVERED_YEAR; year++) {
            LocalDate tet = HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, year);
            OfficialBreak b = simulation.officialBreak(Holiday.LUNAR_NEW_YEAR, year);
            long lead = ChronoUnit.DAYS.between(b.firstDayOff(), tet);
            long length = ChronoUnit.DAYS.between(b.firstDayOff(), b.lastDayOff()) + 1;
            assertTrue(lead >= 2 && lead <= 4, "Tết " + year + ": nghỉ trước " + lead + " ngày");
            assertTrue(length >= 7 && length <= 9, "Tết " + year + ": nghỉ " + length + " ngày");
        }
    }

    /** Ngày lễ theo luật luôn nằm trong kỳ nghỉ; dài nhất 2 + 1 + 1 + 2 + 2 = 8 ngày. */
    @Test
    void everyOtherBreak_containsTheLegalHoliday() {
        HistorySimulation simulation = simulation(LAST_DAY);
        for (Holiday holiday : Holiday.values()) {
            if (holiday == Holiday.LUNAR_NEW_YEAR) {
                continue;
            }
            for (int year = HolidayCalendar.FIRST_COVERED_YEAR; year <= HolidayCalendar.LAST_COVERED_YEAR; year++) {
                LocalDate anchor = HolidayCalendar.anchorOf(holiday, year);
                OfficialBreak b = simulation.officialBreak(holiday, year);
                assertFalse(anchor.isBefore(b.firstDayOff()) || anchor.isAfter(b.lastDayOff()),
                        holiday + " " + year + ": " + b);
                assertTrue(ChronoUnit.DAYS.between(b.firstDayOff(), b.lastDayOff()) + 1 <= 8, holiday + " " + year);
            }
        }
    }

    /**
     * Ngay trước kỳ nghỉ Tết, tuyến "Rời TP lớn" đông vọt còn tuyến "Về TP lớn" vắng đi —
     * nhãn Route.holidayFlow quyết định hình dạng lễ. So với một ngày thường cùng thứ 10
     * tuần trước, để mức nền và hệ số thứ triệt tiêu nhau.
     */
    @Test
    void holidayFlow_decidesWhichRoutePeaksBeforeTet() {
        HistorySimulation simulation = simulation(LAST_DAY);
        for (int year = 2024; year <= 2026; year++) {
            LocalDate eve = simulation.officialBreak(Holiday.LUNAR_NEW_YEAR, year).firstDayOff().minusDays(1);
            LocalDate ordinary = eve.minusWeeks(10);
            double peak = simulation.mean(OUT, eve, 2) / simulation.mean(IN, eve, 2);
            double normal = simulation.mean(OUT, ordinary, 2) / simulation.mean(IN, ordinary, 2);
            assertTrue(peak / normal > 2.5, "Tết " + year + ": " + peak / normal);
        }
    }

    @Test
    void stormClosures_lastAtMostThreeDaysAYear() {
        HistorySimulation simulation = simulation(LAST_DAY);
        for (RouteProfile route : ROUTES) {
            for (int year = 2023; year <= 2026; year++) {
                int closed = 0;
                for (LocalDate d = LocalDate.of(year, 1, 1); d.getYear() == year && !d.isAfter(LAST_DAY); d = d.plusDays(1)) {
                    closed += simulation.isClosed(route.routeId(), d) ? 1 : 0;
                }
                assertTrue(closed <= 3, route.routeId() + " " + year + ": " + closed + " ngày đóng tuyến");
            }
        }
    }

    /** Nhị thức âm size 40, trung bình 30: phương sai = 30 + 30² / 40 = 52,5. */
    @Test
    void negativeBinomial_hasTheIntendedMeanAndVariance() {
        Random random = new Random(42);
        int n = 20_000;
        double sum = 0;
        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            int x = HistorySimulation.negativeBinomial(30.0, random);
            sum += x;
            sumSq += (double) x * x;
        }
        double mean = sum / n;
        double variance = sumSq / n - mean * mean;
        assertEquals(30.0, mean, 0.5);
        assertEquals(52.5, variance, 6.0);
    }

    /**
     * Mức nhu cầu được đặt theo XE CHUẨN của tuyến (QĐ-10, G2-2): trung bình cả năm 2023
     * của mỗi khung khoảng 0,5–0,95 số ghế xe chuẩn (ngày thường 0,6–0,85, cộng các dịp lễ).
     */
    @Test
    void averageLoad_isSetByTheStandardBus() {
        HistorySimulation simulation = simulation(LAST_DAY);
        for (RouteProfile route : ROUTES) {
            for (int h = 0; h < HistorySimulation.DEPARTURE_HOURS.size(); h++) {
                double total = 0;
                int days = 0;
                for (LocalDate d = HistorySimulation.EPOCH; d.getYear() == 2023; d = d.plusDays(1)) {
                    total += simulation.mean(route, d, h);
                    days++;
                }
                double load = total / days / route.standardSeats();
                assertTrue(load > 0.5 && load < 0.95, route.routeId() + " khung " + h + ": " + load);
            }
        }
    }
}
