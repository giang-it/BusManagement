package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Holiday;
import giang.com.BusManagement.domain.HolidayCalendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lõi toán của Demand Forecast mới — "G1' + EM, K=0" (QĐ-1, QĐ-3 trong
 * docs/development/forecast_decisions.md), viết tay bằng Java 17, không thư viện (Non-Goal §4).
 * Làm ở GĐ4 (QĐ-15): chưa có nơi nào trong app gọi tới; GĐ5 mới nối nó vào ForecastService.
 *
 * ====================================================================
 * MÔ HÌNH
 * ====================================================================
 * Mỗi chuỗi là một khung (tuyến × giờ khởi hành), mỗi ngày một quan sát: số vé đã bán của
 * khung (cộng cả chuyến tăng cường). Với chuỗi s, ngày d:
 *
 * <pre>
 *   log(nhu cầu kỳ vọng / mức trung bình của chuỗi)
 *       = mức của s
 *       + hệ số thứ trong tuần của s (thứ Hai là mốc)
 *       + xu hướng gấp khúc của s (đoạn mới mỗi 182 ngày)
 *       + Σ hệ số (dịp lễ, ngày lệch) GỘP theo nhóm chiều tuyến của s  — ridge 2, quy tắc (b)
 *       + Σ phần lệch lễ RIÊNG của s                                   — ridge 2
 * </pre>
 *
 * Mọi chuỗi khớp chung một lần bằng hồi quy Poisson có phạt ridge (IRLS có giảm bước), để các
 * tuyến cùng chiều học chung hình dạng dịp lễ. Mức, thứ, xu hướng gần như không bị phạt
 * (1e-6, chỉ để hệ luôn giải được).
 *
 * EM (5 vòng): khung BÁN HẾT VÉ chỉ cho biết "nhu cầu ≥ số ghế của khung". Bước E thay số vé
 * bằng E[nhu cầu | nhu cầu ≥ số ghế] theo phân phối nhị thức âm quanh nhu cầu đang khớp; bước M
 * khớp lại. Đây là EM dạng thực dụng (forecast_discussion.md §12.7) — giữ nguyên đúng bản đã đo.
 *
 * Dự báo: xu hướng ĐÓNG BĂNG tại ngày gốc (không kéo dài ra tương lai), nhân với hệ số chỉnh
 * mức = Σ thực tế / Σ mô hình trên 28 ngày không lễ gần nhất của chuỗi.
 *
 * ====================================================================
 * BẢN THAM CHIẾU
 * ====================================================================
 * Đây là bản Java của slot_glm trong docs/development/forecast_research/gd2/run_gd2.py (chính
 * code đã cho mọi con số ở bước 0 và GĐ2) với cấu hình cuối: EM 5 vòng, ridge lệch 2, ridge gộp
 * 2, K = 0. SlotDemandModelReferenceTest so từng dự báo và từng hệ số lễ với kết quả Python
 * (forecast_research/gd4/reference.py) trên dữ liệu thật đóng băng ngày 2026-10-22. Đổi bất kỳ
 * chi tiết nào ở đây là đổi mô hình đã đo: phải đo lại trước.
 *
 * Hệ phương trình mỗi vòng có khoảng 1.400 ẩn; ArrowSolver giải theo khối.
 */
final class SlotDemandModel {

    static final int EM_ROUNDS = 5;
    static final double RIDGE_DEVIATION = 2.0;
    static final double RIDGE_POOLED = 2.0;
    static final double RIDGE_FIXED = 1e-6;
    static final int KNOT_SPACING_DAYS = 182;
    /** Không đặt điểm gãy xu hướng trong 60 ngày cuối: quá ít dữ liệu để ước lượng độ dốc mới. */
    static final int KNOT_MARGIN_DAYS = 60;
    static final int LEVEL_CORRECTION_DAYS = 28;

    private static final int MAX_IRLS_ITERATIONS = 50;
    private static final double IRLS_TOLERANCE = 1e-9;
    private static final double MIN_STEP = 1e-4;
    private static final double MIN_NB_SIZE = 2.0;
    private static final double MAX_NB_SIZE = 1e4;
    /** P(nhu cầu ≥ số ghế) dưới mức này thì coi khung đầy đúng số ghế (như bản Python). */
    private static final double MIN_TAIL = 1e-12;
    private static final double DAYS_PER_YEAR = 365.25;

    /** Cột cố định đầu mỗi khối chuỗi: mức (0), thứ Ba … Chủ nhật (1–6), độ dốc xu hướng (7). */
    private static final int FIRST_KNOT_COLUMN = 8;

    /** Một hệ số lễ: dịp lễ và số ngày lệch so với ngày neo, ví dụ (Tết, −3). */
    record HolidayKey(Holiday holiday, int offset) {
    }

    /** Hệ số lễ gộp của một nhóm chiều tuyến, dạng nhân: ×1 là không khác ngày thường. */
    record HolidayEffect(Holiday holiday, int offset, int group, double multiplier) {
    }

    private SlotDemandModel() {
    }

    /**
     * Khớp mô hình trên lịch sử tới hết ngày gốc.
     *
     * @param firstDay ngày của hàng 0 trong sales và seats — cũng là gốc của xu hướng
     * @param sales    sales[ngày][chuỗi]: số vé bán của khung; NaN = không quan sát (khung bị hủy)
     * @param seats    seats[ngày][chuỗi]: tổng số ghế đã chạy của khung; đọc ở nơi sales có số
     * @param groups   groups[chuỗi]: nhóm chiều tuyến, đánh số từ 0
     * @param origin   ngày gốc: chỉ dùng dữ liệu tới hết ngày này; dự báo bắt đầu từ ngày sau
     * @throws IllegalArgumentException nếu kích thước lệch, ngày gốc nằm ngoài dữ liệu, một chuỗi
     *                                  không có quan sát nào, hoặc ngày nằm ngoài HolidayCalendar
     */
    static Fit fit(LocalDate firstDay, double[][] sales, double[][] seats, int[] groups, LocalDate origin) {
        int seriesCount = groups.length;
        int originIndex = (int) ChronoUnit.DAYS.between(firstDay, origin);
        if (sales.length != seats.length || originIndex < 0 || originIndex >= sales.length) {
            throw new IllegalArgumentException("Ngày gốc " + origin + " phải nằm trong dữ liệu từ " + firstDay
                    + " (" + sales.length + " ngày), và sales/seats phải cùng số ngày");
        }
        int groupCount = 0;
        for (int g : groups) {
            if (g < 0) {
                throw new IllegalArgumentException("Nhóm chiều tuyến phải ≥ 0");
            }
            groupCount = Math.max(groupCount, g + 1);
        }

        // Điểm gãy xu hướng: firstDay + 182, + 364, … không muộn hơn ngày gốc − 60.
        List<Integer> knots = new ArrayList<>();
        for (int k = KNOT_SPACING_DAYS; k <= originIndex - KNOT_MARGIN_DAYS; k += KNOT_SPACING_DAYS) {
            knots.add(k);
        }
        int knotCount = knots.size();

        // Các hệ số lễ: mọi cặp (dịp, ngày lệch) xuất hiện trong dữ liệu học, theo thứ tự gặp.
        Map<HolidayKey, Integer> keyIndex = new LinkedHashMap<>();
        int[][] dayKeys = new int[originIndex + 1][];
        for (int d = 0; d <= originIndex; d++) {
            List<HolidayCalendar.HolidayDay> days = HolidayCalendar.holidaysOn(firstDay.plusDays(d));
            dayKeys[d] = new int[days.size()];
            for (int i = 0; i < days.size(); i++) {
                HolidayKey key = new HolidayKey(days.get(i).holiday(), days.get(i).offset());
                Integer index = keyIndex.get(key);
                if (index == null) {
                    index = keyIndex.size();
                    keyIndex.put(key, index);
                }
                dayKeys[d][i] = index;
            }
        }
        int keyCount = keyIndex.size();
        int columns = FIRST_KNOT_COLUMN + knotCount + keyCount;

        // Mức trung bình của từng chuỗi trên dữ liệu học: số đếm được chia cho nó trước khi khớp.
        double[] scale = new double[seriesCount];
        int observations = 0;
        for (int s = 0; s < seriesCount; s++) {
            double sum = 0;
            int count = 0;
            for (int d = 0; d <= originIndex; d++) {
                if (sales[d].length != seriesCount || seats[d].length != seriesCount) {
                    throw new IllegalArgumentException("Ngày " + firstDay.plusDays(d) + ": số chuỗi không khớp groups");
                }
                if (Double.isFinite(sales[d][s])) {
                    sum += sales[d][s];
                    count++;
                }
            }
            if (count == 0) {
                throw new IllegalArgumentException("Chuỗi " + s + " không có quan sát nào tới " + origin);
            }
            scale[s] = sum / count;
            observations += count;
        }

        // Dòng dữ liệu: mọi chuỗi trong cùng một ngày dùng chung chỉ số cột (cột là cục bộ trong
        // khối của chuỗi), nên mỗi ngày dựng một lần.
        int[][] dayColumns = new int[originIndex + 1][];
        double[][] dayValues = new double[originIndex + 1][];
        for (int d = 0; d <= originIndex; d++) {
            buildRow(firstDay.plusDays(d), d, knots, dayKeys[d], dayColumns, dayValues, d);
        }
        Rows rows = new Rows(observations);
        for (int d = 0; d <= originIndex; d++) {
            for (int s = 0; s < seriesCount; s++) {
                if (Double.isFinite(sales[d][s])) {
                    double cap = seats[d][s];
                    if (!(cap > 0)) {
                        throw new IllegalArgumentException("Ngày " + firstDay.plusDays(d) + ", chuỗi " + s
                                + ": có số vé nhưng không có số ghế dương");
                    }
                    rows.add(d, s, sales[d][s], cap);
                }
            }
        }

        Coefficients beta = new Coefficients(seriesCount, groupCount, columns, keyCount);
        double[] target = new double[observations];
        for (int i = 0; i < observations; i++) {
            target[i] = rows.sales[i];
        }
        int iterations = 0;
        double nbSize = Double.NaN;
        int censored = 0;
        for (int i = 0; i < observations; i++) {
            if (rows.sales[i] >= rows.cap[i]) {
                censored++;
            }
        }
        for (int round = 0; round <= EM_ROUNDS; round++) {
            double[] scaled = new double[observations];
            for (int i = 0; i < observations; i++) {
                scaled[i] = target[i] / scale[rows.series[i]];
            }
            iterations += irls(rows, dayColumns, dayValues, dayKeys, groups, scaled, beta, knotCount);
            if (round == EM_ROUNDS) {
                break;
            }
            nbSize = expectationStep(rows, dayColumns, dayValues, dayKeys, groups, scale, beta, target);
        }

        // Hệ số chỉnh mức: 28 ngày gần nhất, bỏ ngày thuộc cửa sổ lễ.
        double[] correction = new double[seriesCount];
        double[] actual = new double[seriesCount];
        double[] modelled = new double[seriesCount];
        int calmFrom = originIndex - (LEVEL_CORRECTION_DAYS - 1);
        for (int i = 0; i < observations; i++) {
            int d = rows.day[i];
            if (d >= calmFrom && dayKeys[d].length == 0) {
                int s = rows.series[i];
                actual[s] += target[i];
                modelled[s] += Math.exp(eta(beta, rows.series[i], groups[rows.series[i]], dayColumns[d], dayValues[d],
                        dayKeys[d])) * scale[s];
            }
        }
        for (int s = 0; s < seriesCount; s++) {
            correction[s] = modelled[s] > 0 ? actual[s] / modelled[s] : 1.0;
        }
        return new Fit(firstDay, origin, groups.clone(), knots, keyIndex, beta, scale, correction, iterations,
                nbSize, observations, censored);
    }

    /**
     * Các cột cục bộ và giá trị của một ngày: mức, thứ (trừ thứ Hai), xu hướng tính tới
     * trendDay, các điểm gãy đã qua, phần lệch lễ của các khóa lễ trong ngày. trendDay khác
     * ngày thật khi dự báo: xu hướng đóng băng tại ngày gốc.
     */
    private static void buildRow(LocalDate date, int trendDay, List<Integer> knots, int[] keys,
            int[][] columnsOut, double[][] valuesOut, int slot) {
        List<Integer> cols = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        cols.add(0);
        vals.add(1.0);
        DayOfWeek dow = date.getDayOfWeek();
        if (dow != DayOfWeek.MONDAY) {
            cols.add(dow.getValue() - 1);
            vals.add(1.0);
        }
        cols.add(FIRST_KNOT_COLUMN - 1);
        vals.add(trendDay / DAYS_PER_YEAR);
        for (int j = 0; j < knots.size(); j++) {
            int past = trendDay - knots.get(j);
            if (past > 0) {
                cols.add(FIRST_KNOT_COLUMN + j);
                vals.add(past / DAYS_PER_YEAR);
            }
        }
        int firstKeyColumn = FIRST_KNOT_COLUMN + knots.size();
        for (int key : keys) {
            cols.add(firstKeyColumn + key);
            vals.add(1.0);
        }
        columnsOut[slot] = cols.stream().mapToInt(Integer::intValue).toArray();
        valuesOut[slot] = vals.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** η = X β của một dòng: phần cục bộ của chuỗi cộng các hệ số lễ gộp của nhóm. */
    private static double eta(Coefficients beta, int series, int group, int[] cols, double[] vals, int[] keys) {
        double[] local = beta.local[series];
        double sum = 0;
        for (int c = 0; c < cols.length; c++) {
            sum += local[cols[c]] * vals[c];
        }
        double[] pooled = beta.pooled[group];
        for (int key : keys) {
            sum += pooled[key];
        }
        return sum;
    }

    /**
     * Hồi quy Poisson có phạt ridge bằng IRLS có giảm bước, khởi đầu từ beta hiện có (beta được
     * cập nhật tại chỗ). Cực đại hóa Σ (y·η − e^η) − ½ Σ ridge·β². Giống hệt poisson_irls trong
     * forecast_research/step0/methods.py: tối đa 50 vòng, dừng khi mục tiêu đổi không quá 1e-9
     * tương đối, giảm nửa bước khi mục tiêu tụt, chấp nhận bước khi nó nhỏ hơn 1e-4.
     *
     * @return số vòng lặp đã chạy
     */
    private static int irls(Rows rows, int[][] dayColumns, double[][] dayValues, int[][] dayKeys, int[] groups,
            double[] y, Coefficients beta, int knotCount) {
        int seriesCount = beta.local.length;
        int groupCount = beta.pooled.length;
        int columns = beta.columns;
        int keyCount = beta.keyCount;
        int firstKeyColumn = FIRST_KNOT_COLUMN + knotCount;
        double current = objective(rows, dayColumns, dayValues, dayKeys, groups, y, beta, firstKeyColumn);
        int iteration = 0;
        while (iteration < MAX_IRLS_ITERATIONS) {
            iteration++;
            double[][][] a = new double[seriesCount][columns][columns];
            double[][][] b = new double[seriesCount][columns][keyCount];
            double[][][] p = new double[groupCount][keyCount][keyCount];
            double[][] r = new double[seriesCount][columns];
            double[][] rp = new double[groupCount][keyCount];
            for (int i = 0; i < rows.size; i++) {
                int s = rows.series[i];
                int g = groups[s];
                int d = rows.day[i];
                int[] cols = dayColumns[d];
                double[] vals = dayValues[d];
                int[] keys = dayKeys[d];
                double e = eta(beta, s, g, cols, vals, keys);
                double mu = Math.exp(e);
                double t = mu * e + (y[i] - mu); // w·z với w = μ, z = η + (y − μ)/μ
                double[][] as = a[s];
                double[] rs = r[s];
                for (int u = 0; u < cols.length; u++) {
                    double wu = mu * vals[u];
                    rs[cols[u]] += t * vals[u];
                    for (int v = 0; v <= u; v++) {
                        int hi = Math.max(cols[u], cols[v]);
                        int lo = Math.min(cols[u], cols[v]);
                        as[hi][lo] += wu * vals[v];
                    }
                    for (int key : keys) {
                        b[s][cols[u]][key] += wu;
                    }
                }
                double[][] pg = p[g];
                for (int u = 0; u < keys.length; u++) {
                    rp[g][keys[u]] += t;
                    for (int v = 0; v <= u; v++) {
                        pg[Math.max(keys[u], keys[v])][Math.min(keys[u], keys[v])] += mu;
                    }
                }
            }
            for (int s = 0; s < seriesCount; s++) {
                for (int c = 0; c < columns; c++) {
                    a[s][c][c] += c >= firstKeyColumn ? RIDGE_DEVIATION : RIDGE_FIXED;
                }
            }
            Coefficients next = new Coefficients(seriesCount, groupCount, columns, keyCount);
            for (int g = 0; g < groupCount; g++) {
                for (int k = 0; k < keyCount; k++) {
                    p[g][k][k] += RIDGE_POOLED;
                }
                List<Integer> members = new ArrayList<>();
                for (int s = 0; s < seriesCount; s++) {
                    if (groups[s] == g) {
                        members.add(s);
                    }
                }
                if (members.isEmpty()) {
                    continue; // nhóm không có chuỗi: chỉ có ridge, nghiệm là 0
                }
                int size = members.size();
                double[][][] ag = new double[size][][];
                double[][][] bg = new double[size][][];
                double[][] rg = new double[size][];
                double[][] xg = new double[size][];
                for (int m = 0; m < size; m++) {
                    int s = members.get(m);
                    ag[m] = a[s];
                    bg[m] = b[s];
                    rg[m] = r[s];
                    xg[m] = next.local[s];
                }
                ArrowSolver.solve(ag, bg, p[g], rg, rp[g], xg, next.pooled[g]);
            }

            double step = 1.0;
            Coefficients candidate;
            double value;
            while (true) {
                candidate = beta.towards(next, step);
                value = objective(rows, dayColumns, dayValues, dayKeys, groups, y, candidate, firstKeyColumn);
                if (value >= current - 1e-12 || step < MIN_STEP) {
                    break;
                }
                step /= 2;
            }
            boolean done = Math.abs(value - current) <= IRLS_TOLERANCE * (Math.abs(current) + 1);
            beta.copyFrom(candidate);
            current = value;
            if (done) {
                break;
            }
        }
        return iteration;
    }

    /** Mục tiêu có phạt: Σ (y·η − e^η) − ½ Σ ridge·β². */
    private static double objective(Rows rows, int[][] dayColumns, double[][] dayValues, int[][] dayKeys,
            int[] groups, double[] y, Coefficients beta, int firstKeyColumn) {
        double sum = 0;
        for (int i = 0; i < rows.size; i++) {
            int s = rows.series[i];
            int d = rows.day[i];
            double e = eta(beta, s, groups[s], dayColumns[d], dayValues[d], dayKeys[d]);
            sum += y[i] * e - Math.exp(e);
        }
        double penalty = 0;
        for (double[] local : beta.local) {
            for (int c = 0; c < local.length; c++) {
                penalty += (c >= firstKeyColumn ? RIDGE_DEVIATION : RIDGE_FIXED) * local[c] * local[c];
            }
        }
        for (double[] pooled : beta.pooled) {
            for (double v : pooled) {
                penalty += RIDGE_POOLED * v * v;
            }
        }
        return sum - 0.5 * penalty;
    }

    /**
     * Bước E: ước lượng độ phân tán nhị thức âm từ các khung KHÔNG bán hết (phương pháp mô-men),
     * rồi thay số vé của các khung bán hết bằng E[nhu cầu | nhu cầu ≥ số ghế]. Ghi vào target.
     *
     * @return tham số size đã dùng
     */
    private static double expectationStep(Rows rows, int[][] dayColumns, double[][] dayValues, int[][] dayKeys,
            int[] groups, double[] scale, Coefficients beta, double[] target) {
        double[] mu = new double[rows.size];
        double excess = 0;
        double squares = 0;
        for (int i = 0; i < rows.size; i++) {
            int s = rows.series[i];
            int d = rows.day[i];
            mu[i] = Math.exp(eta(beta, s, groups[s], dayColumns[d], dayValues[d], dayKeys[d])) * scale[s];
            if (rows.sales[i] < rows.cap[i]) {
                double diff = rows.sales[i] - mu[i];
                excess += diff * diff - mu[i];
                squares += mu[i] * mu[i];
            }
        }
        // Không còn khung nào chưa bán hết thì không ước lượng được: coi như gần Poisson. Bản
        // Python ra NaN ở trường hợp này; dữ liệu thật không bao giờ chạm tới nó.
        double size = squares > 0
                ? Math.min(Math.max(1.0 / Math.max(excess / squares, 1e-4), MIN_NB_SIZE), MAX_NB_SIZE)
                : MAX_NB_SIZE;
        for (int i = 0; i < rows.size; i++) {
            if (rows.sales[i] >= rows.cap[i]) {
                target[i] = meanAtLeast(mu[i], rows.cap[i], size);
            }
        }
        return size;
    }

    /**
     * E[D | D ≥ c] với D ~ nhị thức âm (trung bình mu, tham số size), dùng
     * d·NB(d; r, p) = mu·NB(d − 1; r + 1, p):  E = mu · P(D' ≥ c − 1) / P(D ≥ c), D' ~ NB(r + 1, p).
     * Hai xác suất đuôi được CỘNG TRỰC TIẾP từ c trở lên chứ không lấy 1 − (phần dưới): khi khung
     * hiếm khi bán hết, phần đuôi rất nhỏ và phép trừ sẽ ra số rác. Đuôi dưới 1e-12 thì trả về c,
     * như bản Python (scipy).
     */
    static double meanAtLeast(double mu, double c, double size) {
        double p = size / (size + mu);
        double tail = upperTail((int) Math.ceil(c), size, p);
        if (!(tail > MIN_TAIL)) {
            return c;
        }
        double shifted = upperTail((int) Math.ceil(c) - 1, size + 1, p);
        return Math.max(mu * shifted / tail, c);
    }

    /**
     * E[(D − c)⁺] với D ~ nhị thức âm (trung bình mu, tham số size): số khách kỳ vọng KHÔNG có chỗ khi
     * khung chỉ có c ghế. Thêm ở GĐ6 (QĐ-17, G6-1) cho doanh thu của chuyến tăng cường; không đổi gì
     * của mô hình đã đo. Cùng hằng đẳng thức với meanAtLeast:
     * Σ_{d ≥ c} d·P(D = d) = mu · P(D' ≥ c − 1), D' ~ NB(r + 1, p), nên
     * E[(D − c)⁺] = mu · P(D' ≥ c − 1) − c · P(D ≥ c). Hai đuôi cộng trực tiếp như ở meanAtLeast;
     * kết quả âm vì làm tròn (chỉ khi cả hai đuôi gần bằng 0) được đưa về 0.
     */
    static double expectedOverflow(double mu, int c, double size) {
        if (!(mu > 0)) {
            return 0.0;
        }
        if (c <= 0) {
            return mu;
        }
        double p = size / (size + mu);
        return Math.max(0.0, mu * upperTail(c - 1, size + 1, p) - c * upperTail(c, size, p));
    }

    /**
     * P(X ≥ k) với X ~ nhị thức âm(r, p), pmf(j) = C(j + r − 1, j)·p^r·(1 − p)^j. pmf(k) tính trong
     * thang log bằng truy hồi từ pmf(0) = p^r, rồi cộng các số hạng từ k trở lên tới khi chúng
     * không còn đáng kể.
     */
    static double upperTail(int k, double r, double p) {
        if (k <= 0) {
            return 1.0;
        }
        double logQ = Math.log1p(-p);
        double logTerm = r * Math.log(p);
        for (int j = 0; j < k; j++) {
            logTerm += Math.log((j + r) / (j + 1)) + logQ;
        }
        double term = Math.exp(logTerm);
        double sum = 0;
        double q = Math.exp(logQ);
        for (int j = k; j < k + 1_000_000; j++) {
            sum += term;
            double ratio = (j + r) / (j + 1) * q;
            term *= ratio;
            if (ratio < 1 && term <= sum * 1e-17) {
                break;
            }
        }
        return sum;
    }

    // =========================================================================
    // Dữ liệu nội bộ
    // =========================================================================

    /** Các quan sát dùng để học: ngày, chuỗi, số vé gốc, số ghế của khung. */
    private static final class Rows {
        final int[] day;
        final int[] series;
        final double[] sales;
        final double[] cap;
        int size;

        Rows(int capacity) {
            day = new int[capacity];
            series = new int[capacity];
            sales = new double[capacity];
            cap = new double[capacity];
        }

        void add(int d, int s, double y, double seats) {
            day[size] = d;
            series[size] = s;
            sales[size] = y;
            cap[size] = seats;
            size++;
        }
    }

    /** Hệ số: local[chuỗi][cột cục bộ] và pooled[nhóm][khóa lễ]. */
    private static final class Coefficients {
        final double[][] local;
        final double[][] pooled;
        final int columns;
        final int keyCount;

        Coefficients(int seriesCount, int groupCount, int columns, int keyCount) {
            this.local = new double[seriesCount][columns];
            this.pooled = new double[groupCount][keyCount];
            this.columns = columns;
            this.keyCount = keyCount;
        }

        /** this + step·(target − this). */
        Coefficients towards(Coefficients target, double step) {
            Coefficients out = new Coefficients(local.length, pooled.length, columns, keyCount);
            for (int s = 0; s < local.length; s++) {
                for (int c = 0; c < columns; c++) {
                    out.local[s][c] = local[s][c] + step * (target.local[s][c] - local[s][c]);
                }
            }
            for (int g = 0; g < pooled.length; g++) {
                for (int k = 0; k < keyCount; k++) {
                    out.pooled[g][k] = pooled[g][k] + step * (target.pooled[g][k] - pooled[g][k]);
                }
            }
            return out;
        }

        void copyFrom(Coefficients other) {
            for (int s = 0; s < local.length; s++) {
                System.arraycopy(other.local[s], 0, local[s], 0, columns);
            }
            for (int g = 0; g < pooled.length; g++) {
                System.arraycopy(other.pooled[g], 0, pooled[g], 0, keyCount);
            }
        }
    }

    // =========================================================================
    // Kết quả
    // =========================================================================

    /** Mô hình đã khớp tại một ngày gốc. Bất biến, không giữ dữ liệu đầu vào. */
    static final class Fit {
        private final LocalDate firstDay;
        private final LocalDate origin;
        private final int[] groups;
        private final List<Integer> knots;
        private final Map<HolidayKey, Integer> keyIndex;
        private final Coefficients beta;
        private final double[] scale;
        private final double[] correction;
        private final int iterations;
        private final double nbSize;
        private final int observations;
        private final int censored;

        private Fit(LocalDate firstDay, LocalDate origin, int[] groups, List<Integer> knots,
                Map<HolidayKey, Integer> keyIndex, Coefficients beta, double[] scale, double[] correction,
                int iterations, double nbSize, int observations, int censored) {
            this.firstDay = firstDay;
            this.origin = origin;
            this.groups = groups;
            this.knots = List.copyOf(knots);
            this.keyIndex = keyIndex;
            this.beta = beta;
            this.scale = scale;
            this.correction = correction;
            this.iterations = iterations;
            this.nbSize = nbSize;
            this.observations = observations;
            this.censored = censored;
        }

        /**
         * Nhu cầu kỳ vọng cho các ngày origin + 1 … origin + horizon: result[ngày − 1][chuỗi]. Xu
         * hướng đóng băng tại ngày gốc; hệ số lễ chỉ tính cho cặp (dịp, ngày lệch) đã gặp khi học.
         *
         * @throws IllegalArgumentException nếu một ngày nằm ngoài HolidayCalendar
         */
        double[][] forecast(int horizon) {
            int originIndex = (int) ChronoUnit.DAYS.between(firstDay, origin);
            int seriesCount = groups.length;
            double[][] out = new double[horizon][seriesCount];
            int[][] cols = new int[1][];
            double[][] vals = new double[1][];
            for (int h = 1; h <= horizon; h++) {
                LocalDate date = origin.plusDays(h);
                List<Integer> keys = new ArrayList<>();
                for (HolidayCalendar.HolidayDay day : HolidayCalendar.holidaysOn(date)) {
                    Integer index = keyIndex.get(new HolidayKey(day.holiday(), day.offset()));
                    if (index != null) {
                        keys.add(index);
                    }
                }
                int[] keyArray = keys.stream().mapToInt(Integer::intValue).toArray();
                buildRow(date, originIndex, knots, keyArray, cols, vals, 0);
                for (int s = 0; s < seriesCount; s++) {
                    out[h - 1][s] = Math.exp(eta(beta, s, groups[s], cols[0], vals[0], keyArray))
                            * correction[s] * scale[s];
                }
            }
            return out;
        }

        /** Hệ số lễ gộp của mọi nhóm chiều, theo thứ tự các khóa lễ gặp khi học. */
        List<HolidayEffect> holidayEffects() {
            List<HolidayEffect> effects = new ArrayList<>();
            for (Map.Entry<HolidayKey, Integer> entry : keyIndex.entrySet()) {
                for (int g = 0; g < beta.pooled.length; g++) {
                    effects.add(new HolidayEffect(entry.getKey().holiday(), entry.getKey().offset(), g,
                            Math.exp(beta.pooled[g][entry.getValue()])));
                }
            }
            return effects;
        }

        double levelCorrection(int series) {
            return correction[series];
        }

        double scale(int series) {
            return scale[series];
        }

        /** Tổng số vòng IRLS của cả 6 lần khớp (lần đầu và 5 vòng EM). */
        int iterations() {
            return iterations;
        }

        /** Tham số size của nhị thức âm ở bước E cuối. */
        double negativeBinomialSize() {
            return nbSize;
        }

        int observations() {
            return observations;
        }

        /** Số khung bán hết vé trong dữ liệu học — những khung EM phải đoán lại. */
        int censored() {
            return censored;
        }
    }
}
