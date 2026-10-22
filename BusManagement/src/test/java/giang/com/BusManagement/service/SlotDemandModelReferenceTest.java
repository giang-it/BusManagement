package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Holiday;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java phải ra ĐÚNG mô hình đã đo (GĐ4, QĐ-15): mọi con số ở bước 0 và GĐ2 là của bản Python
 * (forecast_research/gd2/run_gd2.py, slot_glm), nên bản Java chỉ được coi là "mô hình đó" khi nó
 * tái hiện kết quả Python trên cùng dữ liệu.
 *
 * Dữ liệu: src/test/resources/forecast/ — lịch sử khung giờ của DB thật xuất ngày 2026-10-22,
 * ngay sau GĐ3 (19.487 khung có chuyến hoàn thành, 15 chuỗi, 01/01/2023 → 21/10/2026), và kết
 * quả mà forecast_research/gd4/reference.py tính trên chính file đó với cấu hình cuối (EM 5 vòng,
 * ridge lệch 2, ridge gộp 2, K = 0). Hai ngày gốc: 25/01/2026 (tầm dự báo trùm Tết 2026) và
 * 21/10/2026.
 *
 * JUnit thuần, không DB: dữ liệu đóng băng là có chủ đích, để test lặp lại được.
 */
class SlotDemandModelReferenceTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2023, 1, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 10, 21);
    private static final int HORIZON = 35;

    private static final List<long[]> SERIES = new ArrayList<>(); // {route, hour}
    private static int[] groups;
    private static double[][] sales;
    private static double[][] seats;

    @BeforeAll
    static void loadHistory() {
        Map<Long, Integer> groupOfRoute = new HashMap<>();
        for (String[] row : csv("routes.csv")) {
            groupOfRoute.put(Long.parseLong(row[0]), switch (row.length > 1 ? row[1] : "") {
                case "LEAVING_HUB" -> 0;
                case "TOWARD_HUB" -> 1;
                default -> 2;
            });
        }
        List<String[]> history = csv("slot_history.csv");
        TreeSet<String> keys = new TreeSet<>();
        for (String[] row : history) {
            keys.add(String.format("%05d-%02d", Long.parseLong(row[1]), Integer.parseInt(row[2])));
        }
        Map<String, Integer> column = new HashMap<>();
        for (String key : keys) {
            column.put(key, SERIES.size());
            SERIES.add(new long[] { Long.parseLong(key.substring(0, 5)), Long.parseLong(key.substring(6)) });
        }
        groups = new int[SERIES.size()];
        for (int s = 0; s < SERIES.size(); s++) {
            groups[s] = groupOfRoute.get(SERIES.get(s)[0]);
        }
        int days = (int) ChronoUnit.DAYS.between(FIRST_DAY, LAST_DAY) + 1;
        sales = new double[days][SERIES.size()];
        seats = new double[days][SERIES.size()];
        for (double[] row : sales) {
            Arrays.fill(row, Double.NaN);
        }
        for (double[] row : seats) {
            Arrays.fill(row, Double.NaN);
        }
        for (String[] row : history) {
            int d = (int) ChronoUnit.DAYS.between(FIRST_DAY, LocalDate.parse(row[0]));
            int s = column.get(String.format("%05d-%02d", Long.parseLong(row[1]), Integer.parseInt(row[2])));
            sales[d][s] = Double.parseDouble(row[3]);
            seats[d][s] = Double.parseDouble(row[4]);
        }
    }

    @Test
    void forecasts_equalThePythonReference_toOnePartInAMillion() {
        Map<LocalDate, double[][]> forecasts = new HashMap<>();
        int compared = 0;
        double worst = 0;
        for (String[] row : csv("reference_forecast.csv")) {
            LocalDate origin = LocalDate.parse(row[0]);
            double[][] forecast = forecasts.computeIfAbsent(origin,
                    o -> SlotDemandModel.fit(FIRST_DAY, sales, seats, groups, o).forecast(HORIZON));
            int h = (int) ChronoUnit.DAYS.between(origin, LocalDate.parse(row[1]));
            int s = seriesOf(Long.parseLong(row[2]), Integer.parseInt(row[3]));
            double expected = Double.parseDouble(row[4]);
            double actual = forecast[h - 1][s];
            worst = Math.max(worst, Math.abs(actual - expected) / expected);
            assertEquals(expected, actual, 1e-6 * expected, row[0] + " → " + row[1] + ", tuyến " + row[2] + " " + row[3] + "h");
            compared++;
        }
        assertEquals(2 * HORIZON * SERIES.size(), compared);
        System.out.printf("[GĐ4] %d dự báo khớp Python, sai lệch tương đối lớn nhất %.2e%n", compared, worst);
    }

    @Test
    void pooledHolidayEffects_equalThePythonReference() {
        Map<LocalDate, Map<String, Double>> javaBeta = new HashMap<>();
        int compared = 0;
        for (String[] row : csv("reference_holiday_effects.csv")) {
            LocalDate origin = LocalDate.parse(row[0]);
            Map<String, Double> beta = javaBeta.computeIfAbsent(origin, o -> {
                Map<String, Double> out = new HashMap<>();
                for (SlotDemandModel.HolidayEffect e : SlotDemandModel.fit(FIRST_DAY, sales, seats, groups, o)
                        .holidayEffects()) {
                    out.put(e.holiday() + "/" + e.offset() + "/" + e.group(), Math.log(e.multiplier()));
                }
                return out;
            });
            String key = Holiday.valueOf(row[1]) + "/" + row[2] + "/" + row[3];
            assertTrue(beta.containsKey(key), "Java thiếu hệ số " + key + " tại " + origin);
            assertEquals(Double.parseDouble(row[4]), beta.get(key), 1e-6, key + " tại " + origin);
            compared++;
        }
        for (Map<String, Double> beta : javaBeta.values()) {
            compared -= beta.size();
        }
        assertEquals(0, compared, "Java và Python có cùng số hệ số lễ");
    }

    @Test
    void negativeBinomialTailMean_equalsScipy() {
        for (String[] row : csv("reference_nb.csv")) {
            double expected = Double.parseDouble(row[3]);
            double actual = SlotDemandModel.meanAtLeast(Double.parseDouble(row[0]), Double.parseDouble(row[1]),
                    Double.parseDouble(row[2]));
            assertEquals(expected, actual, 1e-9 * expected, "mu=" + row[0] + " c=" + row[1] + " size=" + row[2]);
        }
    }

    /**
     * GĐ6 (QĐ-17, G6-1): khách tràn kỳ vọng E[(D − c)⁺] — doanh thu của chuyến tăng cường — so với
     * scipy cộng trực tiếp (forecast_research/gd6/overflow_reference.py), một đường tính khác hẳn
     * hằng đẳng thức mà Java dùng. Sai số tuyệt đối 1e-15 cho những đuôi gần như bằng 0.
     */
    @Test
    void negativeBinomialExpectedOverflow_equalsScipy() {
        List<String[]> rows = csv("reference_overflow.csv");
        assertEquals(288, rows.size());
        for (String[] row : rows) {
            double expected = Double.parseDouble(row[3]);
            double actual = SlotDemandModel.expectedOverflow(Double.parseDouble(row[0]), Integer.parseInt(row[1]),
                    Double.parseDouble(row[2]));
            assertEquals(expected, actual, 1e-9 * expected + 1e-15,
                    "mu=" + row[0] + " c=" + row[1] + " size=" + row[2]);
        }
    }

    /** G4-7: mục tiêu ≤ 2 giây cho một lần khớp đầy đủ; chặn lỏng 15 giây để test không chập chờn. */
    @Test
    void fullSizeFit_isFastEnough() {
        SlotDemandModel.fit(FIRST_DAY, sales, seats, groups, LocalDate.of(2026, 1, 25)); // làm nóng JIT
        long start = System.nanoTime();
        SlotDemandModel.Fit fit = SlotDemandModel.fit(FIRST_DAY, sales, seats, groups, LAST_DAY);
        fit.forecast(HORIZON);
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf("[GĐ4] khớp + dự báo trên %d quan sát (%d khung bán hết): %.2f giây, %d vòng IRLS, "
                + "size nhị thức âm %.1f%n", fit.observations(), fit.censored(), seconds, fit.iterations(),
                fit.negativeBinomialSize());
        assertTrue(seconds < 15, "khớp mất " + seconds + " giây");
    }

    private static int seriesOf(long route, int hour) {
        for (int s = 0; s < SERIES.size(); s++) {
            if (SERIES.get(s)[0] == route && SERIES.get(s)[1] == hour) {
                return s;
            }
        }
        throw new IllegalArgumentException("Không có chuỗi tuyến " + route + " " + hour + "h");
    }

    /** Đọc một CSV trong src/test/resources/forecast/, bỏ dòng tiêu đề. */
    private static List<String[]> csv(String name) {
        try (InputStream in = SlotDemandModelReferenceTest.class.getResourceAsStream("/forecast/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Thiếu file test /forecast/" + name);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String[]> rows = new ArrayList<>();
            reader.readLine();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank()) {
                    rows.add(line.split(",", -1));
                }
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
