package giang.com.BusManagement.service;

/**
 * Giải hệ phương trình đối xứng, xác định dương có dạng "mũi tên" — phần đại số của
 * SlotDemandModel (forecast redesign GĐ4, QĐ-15 trong docs/development/forecast_decisions.md).
 *
 * <pre>
 *   [ A_1   0   …   B_1 ] [ x_1 ]   [ r_1 ]
 *   [  0   A_2  …   B_2 ] [ x_2 ] = [ r_2 ]
 *   [  …            …   ] [  …  ]   [  …  ]
 *   [ B_1ᵀ B_2ᵀ …    P  ] [ x_p ]   [ r_p ]
 * </pre>
 *
 * Mỗi khối A_i là các ẩn RIÊNG của một chuỗi (mức, thứ, xu hướng, phần lệch lễ — khoảng 82
 * ẩn); P là các hệ số lễ GỘP của nhóm chiều tuyến mà các chuỗi đó cùng dùng (67 ẩn). Hai chuỗi
 * không chung ẩn riêng nào, nên ngoài đường chéo khối chỉ có "cán mũi tên" B_i. Giải thẳng
 * cả ma trận khoảng 1.400 ẩn tốn cỡ một tỉ phép tính mỗi vòng lặp; khử từng khối trước
 * (bù Schur) chỉ tốn cỡ vài chục triệu, và tăng tuyến tính theo số chuyến chứ không theo lập
 * phương — kể cả khi Admin để trống chiều cho mọi tuyến, dồn chúng vào một nhóm (QĐ-8).
 *
 * <pre>
 *   S   = P   − Σ B_iᵀ A_i⁻¹ B_i
 *   x_p = S⁻¹ (r_p − Σ B_iᵀ A_i⁻¹ r_i)
 *   x_i = A_i⁻¹ (r_i − B_i x_p)
 * </pre>
 *
 * Mọi khối phân rã Cholesky. Ridge trong mô hình làm mọi A_i và S xác định dương; gặp phần
 * tử chéo không dương nghĩa là đầu vào hỏng, nên ném lỗi thay vì trả nghiệm rác.
 */
final class ArrowSolver {

    private ArrowSolver() {
    }

    /**
     * Giải một hệ mũi tên. Các mảng đầu vào bị GHI ĐÈ (phân rã tại chỗ) — người gọi dựng lại
     * chúng ở mỗi vòng lặp.
     *
     * @param a  a[i] = A_i, ma trận vuông m_i × m_i (chỉ đọc nửa dưới)
     * @param b  b[i] = B_i, m_i × n
     * @param p  P, n × n (chỉ đọc nửa dưới); n có thể bằng 0
     * @param r  r[i], độ dài m_i
     * @param rp r_p, độ dài n
     * @param x  nhận x_i, độ dài m_i
     * @param xp nhận x_p, độ dài n
     */
    static void solve(double[][][] a, double[][][] b, double[][] p, double[][] r, double[] rp,
            double[][] x, double[] xp) {
        int n = rp.length;
        double[][] schur = p;
        double[] rhs = rp;
        double[][][] ainvB = new double[a.length][][];
        double[][] ainvR = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            int m = r[i].length;
            choleskyInPlace(a[i]);
            ainvR[i] = r[i].clone();
            solveInPlace(a[i], ainvR[i]);
            // A_i⁻¹ B_i, cột theo cột: cột j của B_i là b[i][·][j].
            double[][] y = new double[n][m];
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < m; k++) {
                    y[j][k] = b[i][k][j];
                }
                solveInPlace(a[i], y[j]);
            }
            ainvB[i] = y;
            // S −= B_iᵀ (A_i⁻¹ B_i);  rhs −= B_iᵀ (A_i⁻¹ r_i). Chỉ cần nửa dưới của S.
            for (int u = 0; u < n; u++) {
                double dotR = 0;
                for (int k = 0; k < m; k++) {
                    dotR += b[i][k][u] * ainvR[i][k];
                }
                rhs[u] -= dotR;
                for (int v = 0; v <= u; v++) {
                    double dot = 0;
                    double[] yv = y[v];
                    for (int k = 0; k < m; k++) {
                        dot += b[i][k][u] * yv[k];
                    }
                    schur[u][v] -= dot;
                }
            }
        }
        if (n > 0) {
            choleskyInPlace(schur);
            System.arraycopy(rhs, 0, xp, 0, n);
            solveInPlace(schur, xp);
        }
        // x_i = A_i⁻¹ r_i − (A_i⁻¹ B_i) x_p
        for (int i = 0; i < a.length; i++) {
            int m = r[i].length;
            for (int k = 0; k < m; k++) {
                double v = ainvR[i][k];
                for (int j = 0; j < n; j++) {
                    v -= ainvB[i][j][k] * xp[j];
                }
                x[i][k] = v;
            }
        }
    }

    /**
     * Phân rã Cholesky tại chỗ: nửa dưới của m (kể cả đường chéo) thành L với m = L Lᵀ. Nửa
     * trên không được đọc và không được dùng lại.
     */
    static void choleskyInPlace(double[][] m) {
        int n = m.length;
        for (int j = 0; j < n; j++) {
            double[] rowJ = m[j];
            double d = rowJ[j];
            for (int k = 0; k < j; k++) {
                d -= rowJ[k] * rowJ[k];
            }
            if (!(d > 0)) {
                throw new IllegalStateException("Ma trận không xác định dương ở hàng " + j + " (" + d + ")");
            }
            double l = Math.sqrt(d);
            rowJ[j] = l;
            for (int i = j + 1; i < n; i++) {
                double[] rowI = m[i];
                double s = rowI[j];
                for (int k = 0; k < j; k++) {
                    s -= rowI[k] * rowJ[k];
                }
                rowI[j] = s / l;
            }
        }
    }

    /** Giải L Lᵀ x = v tại chỗ trên v, với L là nửa dưới mà choleskyInPlace để lại. */
    static void solveInPlace(double[][] l, double[] v) {
        int n = v.length;
        for (int i = 0; i < n; i++) {
            double s = v[i];
            double[] row = l[i];
            for (int k = 0; k < i; k++) {
                s -= row[k] * v[k];
            }
            v[i] = s / row[i];
        }
        for (int i = n - 1; i >= 0; i--) {
            double s = v[i];
            for (int k = i + 1; k < n; k++) {
                s -= l[k][i] * v[k];
            }
            v[i] = s / l[i][i];
        }
    }
}
