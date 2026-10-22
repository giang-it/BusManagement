package giang.com.BusManagement.service;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Bộ giải hệ "mũi tên" của SlotDemandModel (GĐ4, QĐ-15): nghiệm phải trùng nghiệm của phép khử
 * Gauss thông thường trên CÙNG ma trận đầy đủ, dựng lại từ các khối. JUnit thuần, không DB.
 */
class ArrowSolverTest {

    @Test
    void solution_equalsPlainGaussianElimination_onRandomArrowSystems() {
        Random random = new Random(20261022L);
        int[][] shapes = { { 3, 4, 5 }, { 82, 82, 82, 82, 82 }, { 7 }, { 2, 9 } };
        int[] shared = { 4, 67, 0, 3 };
        for (int t = 0; t < shapes.length; t++) {
            int[] sizes = shapes[t];
            int n = shared[t];
            int total = n;
            for (int m : sizes) {
                total += m;
            }
            // Ma trận đầy đủ đối xứng xác định dương có dạng mũi tên: X^T X + I, với X thưa theo khối.
            double[][] full = new double[total][total];
            int offset = 0;
            for (int m : sizes) {
                // Mỗi dòng chỉ chạm khối của chính nó và phần chung: cộng tích ngoài trên đúng các chỉ số đó.
                int[] idx = new int[m + n];
                for (int k = 0; k < m; k++) {
                    idx[k] = offset + k;
                }
                for (int k = 0; k < n; k++) {
                    idx[m + k] = total - n + k;
                }
                for (int row = 0; row < 2 * (m + n); row++) {
                    double[] x = new double[m + n];
                    for (int k = 0; k < m; k++) {
                        x[k] = random.nextGaussian();
                    }
                    for (int k = 0; k < n; k++) {
                        x[m + k] = random.nextDouble() < 0.3 ? random.nextGaussian() : 0;
                    }
                    for (int i = 0; i < m + n; i++) {
                        for (int j = 0; j < m + n; j++) {
                            full[idx[i]][idx[j]] += x[i] * x[j];
                        }
                    }
                }
                offset += m;
            }
            double[] rhs = new double[total];
            for (int i = 0; i < total; i++) {
                full[i][i] += 1.0;
                rhs[i] = random.nextGaussian();
            }
            double[] expected = gauss(full, rhs);

            double[][][] a = new double[sizes.length][][];
            double[][][] b = new double[sizes.length][][];
            double[][] r = new double[sizes.length][];
            double[][] x = new double[sizes.length][];
            double[][] p = new double[n][n];
            double[] rp = new double[n];
            offset = 0;
            for (int i = 0; i < sizes.length; i++) {
                int m = sizes[i];
                a[i] = new double[m][m];
                b[i] = new double[m][n];
                r[i] = new double[m];
                x[i] = new double[m];
                for (int u = 0; u < m; u++) {
                    for (int v = 0; v < m; v++) {
                        a[i][u][v] = full[offset + u][offset + v];
                    }
                    for (int k = 0; k < n; k++) {
                        b[i][u][k] = full[offset + u][total - n + k];
                    }
                    r[i][u] = rhs[offset + u];
                }
                offset += m;
            }
            for (int u = 0; u < n; u++) {
                for (int v = 0; v < n; v++) {
                    p[u][v] = full[total - n + u][total - n + v];
                }
                rp[u] = rhs[total - n + u];
            }
            double[] xp = new double[n];

            ArrowSolver.solve(a, b, p, r, rp, x, xp);

            offset = 0;
            for (int i = 0; i < sizes.length; i++) {
                for (int u = 0; u < sizes[i]; u++) {
                    assertEquals(expected[offset + u], x[i][u], 1e-9 * (1 + Math.abs(expected[offset + u])));
                }
                offset += sizes[i];
            }
            for (int k = 0; k < n; k++) {
                assertEquals(expected[total - n + k], xp[k], 1e-9 * (1 + Math.abs(expected[total - n + k])));
            }
        }
    }

    @Test
    void notPositiveDefinite_isRefused_notSolvedIntoGarbage() {
        double[][] m = { { 1, 0 }, { 2, 1 } }; // nửa dưới của [[1, 2], [2, 1]]: trị riêng −1
        assertThrows(IllegalStateException.class, () -> ArrowSolver.choleskyInPlace(m));
    }

    /** Khử Gauss có chọn trụ, độc lập với ArrowSolver. */
    private static double[] gauss(double[][] matrix, double[] rhs) {
        int n = rhs.length;
        double[][] m = new double[n][];
        double[] v = rhs.clone();
        for (int i = 0; i < n; i++) {
            m[i] = matrix[i].clone();
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) {
                    pivot = row;
                }
            }
            double[] tmp = m[col];
            m[col] = m[pivot];
            m[pivot] = tmp;
            double t = v[col];
            v[col] = v[pivot];
            v[pivot] = t;
            for (int row = col + 1; row < n; row++) {
                double f = m[row][col] / m[col][col];
                for (int k = col; k < n; k++) {
                    m[row][k] -= f * m[col][k];
                }
                v[row] -= f * v[col];
            }
        }
        double[] x = new double[n];
        for (int row = n - 1; row >= 0; row--) {
            double s = v[row];
            for (int k = row + 1; k < n; k++) {
                s -= m[row][k] * x[k];
            }
            x[row] = s / m[row][row];
        }
        return x;
    }
}
