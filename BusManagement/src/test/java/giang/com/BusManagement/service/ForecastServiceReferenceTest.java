package giang.com.BusManagement.service;

import giang.com.BusManagement.dto.ForecastAccuracyDto.ScoreDto;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ForecastService dùng đúng mô hình đã đo và tự chấm điểm đúng cách đã chốt (GĐ5, QĐ-16), trên
 * cùng dữ liệu đóng băng của GĐ4 (src/test/resources/forecast/: lịch sử khung giờ của DB thật xuất
 * ngày 2026-10-22).
 *
 * <ul>
 * <li>Ma trận service dựng từ các khung (aggregateSlots → ModelInput.of) cho đúng dự báo của bản
 * Python ở ngày gốc 21/10/2026 (reference_forecast.csv).</li>
 * <li>Phần tự chấm điểm chọn đúng 8 ngày gốc và đúng ngày gốc Tết 25/01/2026, dự báo Tết đúng như
 * Python, và cho đúng mọi thước đo mà forecast_research/gd5/backtest_reference.py tính trên cùng dữ
 * liệu (reference_backtest.csv).</li>
 * </ul>
 *
 * JUnit thuần, không DB: dữ liệu đóng băng là có chủ đích, để test lặp lại được.
 */
class ForecastServiceReferenceTest {

    /** Route.suitableBusType trên DB thật: tuyến 1, 2, 5 là xe 22 chỗ; tuyến 3, 4 là xe 40 chỗ. */
    private static final Map<Long, Integer> STANDARD_SEATS = Map.of(1L, 22, 2L, 22, 3L, 40, 4L, 40, 5L, 22);

    private static List<ForecastService.SlotKey> keys;
    private static ForecastService.ModelInput input;
    private static int[] standardSeats;
    private static ForecastService.BacktestRun backtest;

    @BeforeAll
    static void loadAndScore() {
        Map<Long, Integer> groupOfRoute = new HashMap<>();
        for (String[] row : csv("routes.csv")) {
            groupOfRoute.put(Long.parseLong(row[0]), switch (row.length > 1 ? row[1] : "") {
                case "LEAVING_HUB" -> ForecastService.GROUP_LEAVING_HUB;
                case "TOWARD_HUB" -> ForecastService.GROUP_TOWARD_HUB;
                default -> ForecastService.GROUP_NO_FLOW;
            });
        }
        // Mỗi dòng của file là một khung đã gom; đưa vào service như một "chuyến" của khung đó.
        List<Object[]> rows = new ArrayList<>();
        for (String[] row : csv("slot_history.csv")) {
            rows.add(new Object[] { Long.parseLong(row[1]), LocalDate.parse(row[0]).atTime(Integer.parseInt(row[2]), 0),
                    Integer.parseInt(row[3]), Integer.parseInt(row[4]) });
        }
        SortedMap<ForecastService.SlotKey, SortedMap<LocalDate, int[]>> slots =
                ForecastService.aggregateSlots(rows, LocalDate.of(2026, 10, 21));
        keys = slots.entrySet().stream()
                .filter(e -> e.getValue().size() >= ForecastService.MIN_OBSERVED_DAYS)
                .map(Map.Entry::getKey)
                .toList();
        input = ForecastService.ModelInput.of(keys, slots,
                keys.stream().mapToInt(k -> groupOfRoute.get(k.routeId())).toArray());
        standardSeats = keys.stream().mapToInt(k -> STANDARD_SEATS.get(k.routeId())).toArray();

        long start = System.nanoTime();
        backtest = ForecastService.runBacktest(input);
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf("[GĐ5] tự chấm điểm: %d ngày gốc + khối Tết (9 lần khớp): %.2f giây%n",
                backtest.origins().size(), seconds);
        assertTrue(seconds < 60, "tự chấm điểm mất " + seconds + " giây");
    }

    @Test
    void slotsBuiltByTheService_giveThePythonForecast() {
        assertEquals(15, keys.size());
        assertEquals(LocalDate.of(2023, 1, 1), input.firstDay);
        assertEquals(LocalDate.of(2026, 10, 21), input.origin);

        SlotDemandModel.Fit fit = SlotDemandModel.fit(input.firstDay, input.sales, input.seats, input.groups, input.origin);
        double[][] forecast = fit.forecast(35);

        assertEquals(19_487, fit.observations());
        assertEquals(4_078, fit.censored());
        int compared = compareWithReference("2026-10-21", input.origin, forecast, null);
        assertEquals(35 * 15, compared);
    }

    @Test
    void backtest_usesOneOriginPerWeekOverTheLastEightWeeks() {
        assertEquals(8, backtest.origins().size());
        assertEquals(LocalDate.of(2026, 8, 26), backtest.origins().get(0));
        assertEquals(LocalDate.of(2026, 10, 14), backtest.origins().get(7), "7 ngày trước ngày cuối của lịch sử");
    }

    @Test
    void recentScore_equalsThePythonReference() {
        assertScore("recent", ForecastService.score(backtest.recent(), standardSeats));
    }

    @Test
    void tetBlock_learnsUntilThreeWeeksBeforeTet_andForecastsLikePython() {
        ForecastService.TetRun tet = backtest.tet();
        assertNotNull(tet);
        assertEquals(LocalDate.of(2026, 2, 17), tet.tetDay());
        assertEquals(LocalDate.of(2026, 1, 25), tet.origin(), "Chủ nhật gần nhất cách Tết ít nhất 21 ngày");
        assertEquals(15, tet.series().length, "mọi chuỗi đã đủ lịch sử từ trước Tết 2026");

        int compared = compareWithReference("2026-01-25", tet.origin(), tet.model(), tet.series());
        assertEquals(35 * 15, compared);
        assertScore("tet", ForecastService.score(tet.scored(), standardSeats));
    }

    // =====================================================================

    /** So từng dự báo của một ngày gốc với reference_forecast.csv; columns = chỉ số chuỗi của từng cột (null = trùng). */
    private static int compareWithReference(String origin, LocalDate originDay, double[][] forecast, int[] columns) {
        int compared = 0;
        double worst = 0;
        for (String[] row : csv("reference_forecast.csv")) {
            if (!row[0].equals(origin)) {
                continue;
            }
            int h = (int) ChronoUnit.DAYS.between(originDay, LocalDate.parse(row[1]));
            int s = keys.indexOf(new ForecastService.SlotKey(Long.parseLong(row[2]), Integer.parseInt(row[3])));
            int column = s;
            if (columns != null) {
                column = -1;
                for (int j = 0; j < columns.length; j++) {
                    if (columns[j] == s) {
                        column = j;
                    }
                }
            }
            double expected = Double.parseDouble(row[4]);
            double actual = forecast[h - 1][column];
            worst = Math.max(worst, Math.abs(actual - expected) / expected);
            assertEquals(expected, actual, 1e-6 * expected, row[1] + " tuyến " + row[2] + " " + row[3] + "h");
            compared++;
        }
        System.out.printf("[GĐ5] ngày gốc %s: %d dự báo khớp Python, sai lệch tương đối lớn nhất %.2e%n", origin,
                compared, worst);
        return compared;
    }

    private static void assertScore(String block, ScoreDto score) {
        Map<String, Double> expected = new HashMap<>();
        for (String[] row : csv("reference_backtest.csv")) {
            if (row[0].equals(block)) {
                expected.put(row[1], Double.parseDouble(row[2]));
            }
        }
        assertEquals(expected.get("testedSlotDays").intValue(), score.getTestedSlotDays(), block);
        assertEquals(expected.get("soldOutSlotDays").intValue(), score.getSoldOutSlotDays(), block);
        assertEquals(expected.get("hotSlotDays").intValue(), score.getHotSlotDays(), block);
        assertEquals(expected.get("modelWape"), score.getModelWape(), 1e-6, block + " WAPE mô hình");
        assertEquals(expected.get("baselineWape"), score.getBaselineWape(), 1e-6, block + " WAPE SN4");
        assertEquals(expected.get("modelSoldOutCaught"), score.getModelSoldOutCaught(), 1e-6, block);
        assertEquals(expected.get("baselineSoldOutCaught"), score.getBaselineSoldOutCaught(), 1e-6, block);
        assertEquals(expected.get("modelFlagRecall"), score.getModelFlagRecall(), 1e-6, block);
        assertEquals(expected.get("baselineFlagRecall"), score.getBaselineFlagRecall(), 1e-6, block);
        assertEquals(expected.get("modelFlagPrecision"), score.getModelFlagPrecision(), 1e-6, block);
        assertEquals(expected.get("baselineFlagPrecision"), score.getBaselineFlagPrecision(), 1e-6, block);
        assertTrue(score.isModelBeatsBaseline(), block);
        System.out.printf("[GĐ5] %s: %d ngày-khung, sai số số vé %.2f%% (SN4 %.2f%%), báo trước %.1f%% khung bán hết "
                + "(SN4 %.1f%%)%n", block, score.getTestedSlotDays(), score.getModelWape(), score.getBaselineWape(),
                score.getModelSoldOutCaught(), score.getBaselineSoldOutCaught());
    }

    /** Đọc một CSV trong src/test/resources/forecast/, bỏ dòng tiêu đề. */
    private static List<String[]> csv(String name) {
        try (InputStream in = ForecastServiceReferenceTest.class.getResourceAsStream("/forecast/" + name)) {
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
