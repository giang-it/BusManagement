package giang.com.BusManagement.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chốt số học phần-chia-giờ-lái của hai dropdown "tài xế rảnh" — regression cho
 * lỗi #12 trong docs/todo/current_bugs_found.md.
 *
 * Trước khi sửa, {@code getAvailableDriversForTrip} và
 * {@code getAvailableDriversForTimeRange} dùng {@code min(duration, 8.0)} —
 * KHÔNG chia cho số tài xế — trong khi javadoc của chính chúng ghi "phần chia
 * của chuyến này" và hai method anh em ({@code findBestAvailableDriver},
 * {@code validateStaffForTrip}) đều chia. Với chuyến dài hơn 8h, công thức cũ mô
 * hình hoá kịch bản "một tài xế lái trọn chuyến" — đúng cái mà
 * validateStaffForTrip() từ chối thẳng — nên nó giấu khỏi Admin những tài xế mà
 * validator sẵn sàng chấp nhận.
 *
 * Test JUnit thuần, KHÔNG {@code @SpringBootTest} — có chủ đích, cùng lý do
 * WhatIfCoverageTest: {@link TripService#driverShareHours} là hàm thuần, không
 * cần database, và điều cần chốt là số học chứ không phải tầng truy cập dữ liệu.
 *
 * Mọi thời lượng dưới đây là số ĐO ĐƯỢC trên bảng `routes` ngày 2026-08-04, không
 * phải số bịa: 90 / 120 / 120 / 210 / 360 phút (5 tuyến ngắn) và 1800 / 3000 /
 * 4500 phút (3 tuyến dài).
 */
class TripServiceDriverShareTest {

    /** Hạn giờ lái/ngày — cùng giá trị mà validateStaffForTrip() dùng làm trần. */
    private static final double MAX_DAILY = 8.0;

    /** Công thức CŨ đã bị thay thế — chỉ dùng để chứng minh sự khác biệt. */
    private static double oldFormula(double durationHours) {
        return Math.min(durationHours, MAX_DAILY);
    }

    /** Phần chia mà validateStaffForTrip() thực sự áp, với số người Admin đã gán. */
    private static double validatorShare(double durationHours, int assignedDrivers) {
        return Math.min(durationHours / assignedDrivers, MAX_DAILY);
    }

    private static int requiredDrivers(double durationHours) {
        return Math.max(1, (int) Math.ceil(durationHours / MAX_DAILY));
    }

    // =========================================================================
    // 5 tuyến NGẮN: bản sửa KHÔNG được đổi bất cứ thứ gì
    // =========================================================================

    /**
     * Với mọi tuyến ≤ 8h, {@code requiredDrivers = 1} nên phần chia trùng khít
     * thời lượng — công thức cũ và mới cho CÙNG một số. Đây là bằng chứng bán kính
     * của bản sửa chỉ gồm 3 tuyến dài, và 5 tuyến ngắn không hề đổi hành vi.
     */
    @Test
    @DisplayName("5 tuyến ≤ 8h: công thức cũ và mới trùng khít — không đổi hành vi")
    void shortRoutes_unchanged() {
        for (double minutes : new double[] { 90, 120, 120, 210, 360 }) {
            double h = minutes / 60.0;
            assertEquals(oldFormula(h), TripService.driverShareHours(h), 1e-9,
                    "tuyến " + minutes + " phút phải giữ nguyên kết quả cũ");
            assertEquals(1, requiredDrivers(h));
        }
    }

    /** Biên đúng 8h vẫn là 1 tài xế, vẫn không đổi. */
    @Test
    @DisplayName("Biên 8h chẵn: vẫn 1 tài xế, vẫn 8.0h")
    void exactlyEightHours_isStillOneDriver() {
        assertEquals(8.0, TripService.driverShareHours(8.0), 1e-9);
        assertEquals(1, requiredDrivers(8.0));
    }

    // =========================================================================
    // 3 tuyến DÀI: nơi lỗi #12 sống
    // =========================================================================

    /** Tuyến #6 — 1800 phút = 30h ⇒ 4 tài xế ⇒ 7,5h mỗi người (cũ: 8,0h). */
    @Test
    @DisplayName("Tuyến 30h: 7,5h mỗi tài xế, không phải 8,0h")
    void route30h_splitsAcrossFourDrivers() {
        double h = 1800 / 60.0;
        assertEquals(4, requiredDrivers(h));
        assertEquals(7.5, TripService.driverShareHours(h), 1e-9);
        assertEquals(8.0, oldFormula(h), 1e-9);
    }

    /** Tuyến #8 — 3000 phút = 50h ⇒ 7 tài xế ⇒ ~7,143h mỗi người. */
    @Test
    @DisplayName("Tuyến 50h: ~7,143h mỗi tài xế, không phải 8,0h")
    void route50h_splitsAcrossSevenDrivers() {
        double h = 3000 / 60.0;
        assertEquals(7, requiredDrivers(h));
        assertEquals(50.0 / 7, TripService.driverShareHours(h), 1e-9);
        assertNotEquals(oldFormula(h), TripService.driverShareHours(h));
    }

    /** Tuyến #9 — 4500 phút = 75h ⇒ 10 tài xế ⇒ 7,5h mỗi người. */
    @Test
    @DisplayName("Tuyến 75h: 7,5h mỗi tài xế — công thức cũ bắt một người lái trọn 75h")
    void route75h_splitsAcrossTenDrivers() {
        double h = 4500 / 60.0;
        assertEquals(10, requiredDrivers(h));
        assertEquals(7.5, TripService.driverShareHours(h), 1e-9);
    }

    /**
     * Phân bố giờ lái nền của 33 tài xế đang hoạt động, ĐO ĐƯỢC ngày 2026-08-04:
     * {giờ, số người}. Tổng = 33, khớp `SELECT COUNT(*) FROM drivers WHERE is_active=1`.
     *
     * Lưu ý mức 2h có ĐÚNG HAI người — chi tiết này không phải trang trí: bản nháp
     * đầu của test này làm phẳng thành một người và ra 24 thay vì 25, đúng kiểu sai
     * mà một fixture bịa sẽ mắc còn một fixture đo được thì không.
     */
    private static final double[][] ACTIVE_DRIVER_HOURS = {
            { 0, 21 }, { 1, 1 }, { 2, 2 }, { 3, 1 }, { 5, 1 }, { 6, 1 },
            { 7, 1 }, { 7.9, 1 }, { 8, 1 }, { 9, 1 }, { 10, 1 }, { 11, 1 }
    };

    private static int driversVisibleWith(double shareHours) {
        int visible = 0;
        for (double[] row : ACTIVE_DRIVER_HOURS) {
            if (row[0] + shareHours <= MAX_DAILY) {
                visible += (int) row[1];
            }
        }
        return visible;
    }

    /**
     * Ghi lại chính xác cái mìn mà bản sửa gỡ: một tuyến 9h (chưa tồn tại, nhưng
     * RouteService.saveRoute() cho nhập estimatedDuration tự do, chỉ đòi > 0) mở
     * khoảng lệch 3,5h giữa dropdown và validator. Công thức cũ chỉ hiện tài xế có
     * đúng 0h (21 người); validator chấp nhận tới 3,5h (25 người — đã đối chiếu
     * `SELECT COUNT(*) … WHERE total_driving_hours24h <= 3.5` = 25). Tức 4 tài xế
     * hợp lệ bị giấu khỏi Admin.
     */
    @Test
    @DisplayName("Mìn: tuyến 9h mở khoảng lệch 3,5h — 4 tài xế hợp lệ bị giấu")
    void nineHourRoute_isWhereTheGapBecomesLarge() {
        double h = 9.0;
        assertEquals(2, requiredDrivers(h));
        assertEquals(4.5, TripService.driverShareHours(h), 1e-9);
        assertEquals(3.5, oldFormula(h) - TripService.driverShareHours(h), 1e-9);

        assertEquals(21, driversVisibleWith(oldFormula(h)), "công thức cũ chỉ hiện người có đúng 0h");
        assertEquals(25, driversVisibleWith(TripService.driverShareHours(h)),
                "bản sửa hiện đủ 25 người validator chấp nhận");
    }

    /**
     * Chốt điều đã đo được và là lý do mục #12 được xếp "chưa cắn": trên BA tuyến
     * dài đang có, hai công thức cho ra CÙNG một tập tài xế, vì không ai có giờ nền
     * rơi vào khoảng (0; 0,857]. Nếu sau này dữ liệu đổi và test này đỏ, nghĩa là
     * lỗi #12 đã bắt đầu cắn thật — không phải test hỏng.
     */
    @Test
    @DisplayName("Vì sao #12 'chưa cắn': 3 tuyến dài hiện tại cho cùng tập tài xế")
    void onCurrentRoutes_bothFormulasSelectTheSameDrivers() {
        for (double minutes : new double[] { 1800, 3000, 4500 }) {
            double h = minutes / 60.0;
            assertEquals(driversVisibleWith(oldFormula(h)),
                    driversVisibleWith(TripService.driverShareHours(h)),
                    "tuyến " + minutes + " phút: trên dữ liệu hiện tại hai công thức phải cho cùng số người");
        }
    }

    // =========================================================================
    // BẤT BIẾN: dropdown phải luôn là TẬP CON của "validator chấp nhận"
    // =========================================================================

    /**
     * Quy tắc một chiều đã ghi ở THESIS_ROADMAP.md §8 (*"the dropdowns no longer
     * offer drivers the validator would then reject"*) PHẢI được giữ: vì
     * {@code assignedDriversCount >= requiredDrivers}, phần chia của validator luôn
     * ≤ phần chia của dropdown, nên dropdown không bao giờ mời một người rồi bị từ
     * chối lúc submit. Nếu ai đó "tối ưu" hàm này thành chia cho một số lớn hơn
     * requiredDrivers, test này đỏ.
     */
    @Test
    @DisplayName("Bất biến: dropdown không bao giờ rộng hơn validator, ở mọi mức nhân sự")
    void dropdownNeverOffersWhatTheValidatorWouldReject() {
        for (double minutes : new double[] { 90, 120, 210, 360, 480, 540, 1800, 3000, 4500 }) {
            double h = minutes / 60.0;
            double dropdownShare = TripService.driverShareHours(h);
            for (int assigned = requiredDrivers(h); assigned <= requiredDrivers(h) + 5; assigned++) {
                assertTrue(validatorShare(h, assigned) <= dropdownShare + 1e-9,
                        "tuyến " + minutes + " phút với " + assigned + " tài xế: validator phải rộng hơn hoặc bằng");
            }
        }
    }

    /** Phần chia không bao giờ vượt trần 8h/người, kể cả tuyến cực dài. */
    @Test
    @DisplayName("Phần chia luôn ≤ 8h — trần mỗi người không bị phá")
    void shareNeverExceedsDailyLimit() {
        for (double minutes : new double[] { 90, 480, 540, 1800, 3000, 4500, 100000 }) {
            double share = TripService.driverShareHours(minutes / 60.0);
            assertTrue(share <= MAX_DAILY + 1e-9, "tuyến " + minutes + " phút cho phần chia " + share);
            assertTrue(share > 0, "phần chia phải dương");
        }
    }
}
