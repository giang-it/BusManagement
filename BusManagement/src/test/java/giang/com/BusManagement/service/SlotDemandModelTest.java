package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Holiday;
import giang.com.BusManagement.domain.HolidayCalendar;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Các tính chất của SlotDemandModel (GĐ4, QĐ-15) trên dữ liệu dựng tay, đủ nhỏ để biết trước
 * đáp án. Việc mô hình trùng bản Python đã đo do SlotDemandModelReferenceTest kiểm; ở đây kiểm
 * từng ý của mô hình riêng rẽ, để khi hỏng biết ngay hỏng ở đâu. JUnit thuần, không DB.
 */
class SlotDemandModelTest {

    /** Nhu cầu không đổi thì mô hình ra đúng hằng số đó, kể cả ở những ngày lễ nó chưa từng thấy. */
    @Test
    void constantDemand_isForecastAsThatConstant() {
        LocalDate first = LocalDate.of(2024, 3, 1);
        Data data = data(first, 300, 2, (d, s) -> 10.0, (d, s) -> 40.0);
        LocalDate origin = first.plusDays(299);

        double[][] forecast = SlotDemandModel.fit(first, data.sales, data.seats, new int[] { 0, 1 }, origin)
                .forecast(35); // tới 30/01/2025: Tết Dương lịch và đầu cửa sổ Tết 2025, chưa từng gặp khi học

        for (double[] day : forecast) {
            for (double value : day) {
                assertEquals(10.0, value, 1e-9);
            }
        }
    }

    @Test
    void weekdayPattern_isLearnt() {
        LocalDate first = LocalDate.of(2024, 3, 4); // thứ Hai
        Data data = data(first, 280, 1,
                (d, s) -> first.plusDays(d).getDayOfWeek() == DayOfWeek.SATURDAY ? 20.0 : 10.0, (d, s) -> 40.0);
        LocalDate origin = first.plusDays(279);

        double[][] forecast = SlotDemandModel.fit(first, data.sales, data.seats, new int[] { 0 }, origin)
                .forecast(14);

        for (int h = 1; h <= 14; h++) {
            DayOfWeek dow = origin.plusDays(h).getDayOfWeek();
            assertEquals(dow == DayOfWeek.SATURDAY ? 20.0 : 10.0, forecast[h - 1][0], 1e-3, origin.plusDays(h).toString());
        }
    }

    /** Xu hướng đóng băng tại ngày gốc: 7 ngày tới và 35 ngày tới (cùng thứ) được dự báo như nhau. */
    @Test
    void trend_isFrozenAtTheOrigin() {
        LocalDate first = LocalDate.of(2023, 6, 1);
        Data data = data(first, 120, 1, (d, s) -> 10.0 + 0.02 * d, (d, s) -> 100.0);
        LocalDate origin = first.plusDays(119); // 28/09/2023; 35 ngày sau không có dịp lễ nào

        double[][] forecast = SlotDemandModel.fit(first, data.sales, data.seats, new int[] { 0 }, origin)
                .forecast(35);

        assertEquals(forecast[6][0], forecast[34][0], 1e-12);
        assertTrue(forecast[6][0] > 11.5 && forecast[6][0] < 13.5, "mức tại ngày gốc, không phải đầu kỳ: "
                + forecast[6][0]);
    }

    /** Hệ số lễ học từ Tết 2023 được dùng cho Tết 2024; ngày cùng thứ ngoài cửa sổ thì không. */
    @Test
    void holidayEffect_learntFromHistory_isAppliedToTheForecast() {
        LocalDate first = LocalDate.of(2023, 1, 1);
        BiFunction<Integer, Integer, Double> sales = (d, s) -> {
            for (HolidayCalendar.HolidayDay h : HolidayCalendar.holidaysOn(first.plusDays(d))) {
                if (h.holiday() == Holiday.LUNAR_NEW_YEAR && h.offset() >= -3 && h.offset() <= 0) {
                    return 30.0;
                }
            }
            return 10.0;
        };
        Data data = data(first, 400, 2, sales, (d, s) -> 60.0);
        LocalDate origin = LocalDate.of(2024, 1, 20);

        double[][] forecast = SlotDemandModel.fit(first, data.sales, data.seats, new int[] { 0, 0 }, origin)
                .forecast(35);

        int tetMinus2 = (int) ChronoUnit.DAYS.between(origin, LocalDate.of(2024, 2, 8)) - 1;
        int weekEarlier = tetMinus2 - 7; // 01/02/2024, cửa sổ Tết nhưng ngoài D−3…D
        assertTrue(forecast[tetMinus2][0] > 20, "Tết D−2 phải đông hơn hẳn: " + forecast[tetMinus2][0]);
        assertEquals(10.0, forecast[weekEarlier][0], 0.5);
    }

    /** Khung bán hết vé chỉ cho biết "ít nhất bằng số ghế"; EM kéo dự báo lên gần nhu cầu thật. */
    @Test
    void soldOutSlots_areEstimatedAboveTheirSeats() {
        LocalDate first = LocalDate.of(2024, 3, 4);
        // Nhu cầu thật luôn là 30. Ngày chẵn chỉ chạy xe 22 chỗ nên DB ghi 22 (bán hết); ngày lẻ xe 50 chỗ, ghi 30.
        Data data = data(first, 200, 1, (d, s) -> d % 2 == 0 ? 22.0 : 30.0, (d, s) -> d % 2 == 0 ? 22.0 : 50.0);
        LocalDate origin = first.plusDays(199);

        SlotDemandModel.Fit fit = SlotDemandModel.fit(first, data.sales, data.seats, new int[] { 0 }, origin);
        double forecast = fit.forecast(1)[0][0];

        assertEquals(100, fit.censored());
        assertTrue(forecast > 29.5 && forecast < 32,
                "phải gần 30 chứ không phải trung bình 26 của số vé ghi được: " + forecast);
    }

    /** Dữ liệu sau ngày gốc không được lọt vào lần khớp — điều backtest ở GĐ5 dựa vào. */
    @Test
    void dataAfterTheOrigin_isIgnored() {
        LocalDate first = LocalDate.of(2024, 3, 1);
        Data clean = data(first, 200, 1, (d, s) -> 10.0 + (d % 7), (d, s) -> 40.0);
        Data spoiled = data(first, 200, 1, (d, s) -> d > 150 ? 999.0 : 10.0 + (d % 7), (d, s) -> 40.0);
        LocalDate origin = first.plusDays(150);

        double[][] a = SlotDemandModel.fit(first, clean.sales, clean.seats, new int[] { 0 }, origin).forecast(10);
        double[][] b = SlotDemandModel.fit(first, spoiled.sales, spoiled.seats, new int[] { 0 }, origin).forecast(10);

        for (int h = 0; h < 10; h++) {
            assertEquals(a[h][0], b[h][0], 0.0);
        }
    }

    @Test
    void tailMean_behaves() {
        assertEquals(15.0, SlotDemandModel.meanAtLeast(15.0, 0, 40.0), 1e-12, "c = 0: chính là trung bình");
        double previous = 0;
        for (int c = 1; c <= 60; c++) {
            double value = SlotDemandModel.meanAtLeast(15.0, c, 40.0);
            assertTrue(value >= c, "không nhỏ hơn số ghế");
            assertTrue(value > previous, "tăng theo số ghế");
            previous = value;
        }
        assertEquals(172.0, SlotDemandModel.meanAtLeast(3.0, 172, 40.0), 0.0, "đuôi gần như bằng 0: lấy số ghế");
        // size rất lớn thì gần Poisson: so với E[X | X ≥ 20], X ~ Poisson(20), tính thẳng ở đây.
        double pmf = Math.exp(-20.0);
        double below = 0;
        double weightedBelow = 0;
        for (int x = 0; x < 20; x++) {
            below += pmf;
            weightedBelow += x * pmf;
            pmf *= 20.0 / (x + 1);
        }
        double poisson = (20.0 - weightedBelow) / (1 - below);
        assertEquals(poisson, SlotDemandModel.meanAtLeast(20.0, 20, 1e9), 1e-6);
    }

    @Test
    void badInput_isRefused() {
        LocalDate first = LocalDate.of(2024, 3, 1);
        Data data = data(first, 50, 2, (d, s) -> s == 1 ? Double.NaN : 10.0, (d, s) -> 40.0);
        int[] groups = { 0, 1 };
        assertThrows(IllegalArgumentException.class,
                () -> SlotDemandModel.fit(first, data.sales, data.seats, groups, first.plusDays(10)),
                "chuỗi 1 không có quan sát nào");
        Data ok = data(first, 50, 1, (d, s) -> 10.0, (d, s) -> 40.0);
        assertThrows(IllegalArgumentException.class,
                () -> SlotDemandModel.fit(first, ok.sales, ok.seats, new int[] { 0 }, first.minusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> SlotDemandModel.fit(first, ok.sales, ok.seats, new int[] { 0 }, first.plusDays(50)));
        assertThrows(IllegalArgumentException.class,
                () -> SlotDemandModel.fit(first, ok.sales, new double[49][1], new int[] { 0 }, first.plusDays(10)));
    }

    // =====================================================================

    private record Data(double[][] sales, double[][] seats) {
    }

    private static Data data(LocalDate first, int days, int series, BiFunction<Integer, Integer, Double> sales,
            BiFunction<Integer, Integer, Double> seats) {
        double[][] y = new double[days][series];
        double[][] c = new double[days][series];
        for (int d = 0; d < days; d++) {
            for (int s = 0; s < series; s++) {
                y[d][s] = sales.apply(d, s);
                c[d][s] = seats.apply(d, s);
            }
        }
        return new Data(y, c);
    }
}
