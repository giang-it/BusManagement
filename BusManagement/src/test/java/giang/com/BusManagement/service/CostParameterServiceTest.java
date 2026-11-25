package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.CostParameters;
import giang.com.BusManagement.repository.CostParametersRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PHASE 7 (bước 3) — ghim hai bất biến của tham số chi phí mà KHÔNG có gì khác
 * canh:
 *
 *   1) getOrDefault() trả về giá trị MẶC ĐỊNH (không null) khi chưa ai lưu, để
 *      Recommendation Engine tính được chi phí ngay cả trên DB trống.
 *   2) save() giữ đúng MỘT dòng cấu hình: lưu nhiều lần vẫn chỉ một dòng, giá trị
 *      là lần lưu gần nhất. Đây là điểm dễ vỡ nhất — nếu ai đó đổi save() thành
 *      repository.save(form) trực tiếp thì mỗi lần lưu đẻ một dòng mới mà không
 *      test/màn hình nào hiện tại phát hiện.
 *
 * Chạy trên busmanagement_test, @Transactional nên mọi bản ghi được rollback.
 */
@SpringBootTest
@Transactional
class CostParameterServiceTest {

    @Autowired
    private CostParameterService costParameterService;
    @Autowired
    private CostParametersRepository repository;

    @Test
    @DisplayName("getOrDefault trả giá trị mặc định (không null) khi chưa ai lưu")
    void getOrDefault_returnsNonNullDefaults_whenEmpty() {
        repository.deleteAll();

        CostParameters d = costParameterService.getOrDefault();

        assertNotNull(d.getFuelCostPerKm(), "mặc định nhiên liệu không được null");
        assertNotNull(d.getDriverWagePerHour(), "mặc định lương không được null");
        assertTrue(d.getFuelCostPerKm().signum() > 0);
        assertTrue(d.getDriverWagePerHour().signum() > 0);
        // Không ghi-khi-đọc: đọc mặc định KHÔNG được tạo bản ghi ngầm.
        assertEquals(0, repository.count(), "getOrDefault không được ghi vào DB");
    }

    @Test
    @DisplayName("save giữ đúng MỘT dòng qua nhiều lần lưu; đọc lại đúng giá trị mới nhất")
    void save_keepsExactlyOneRow_andPersistsLatest() {
        repository.deleteAll();

        CostParameters first = new CostParameters();
        first.setFuelCostPerKm(new BigDecimal("6000"));
        first.setDriverWagePerHour(new BigDecimal("50000"));
        first.setWaitingWagePerDay(new BigDecimal("333000"));
        first.setBusParkingPerDay(new BigDecimal("330000"));
        costParameterService.save(first);
        assertEquals(1, repository.count(), "lần lưu đầu tạo đúng 1 dòng");

        CostParameters second = new CostParameters();
        second.setFuelCostPerKm(new BigDecimal("9999"));
        second.setDriverWagePerHour(new BigDecimal("77777"));
        second.setWaitingWagePerDay(new BigDecimal("250000"));
        second.setBusParkingPerDay(new BigDecimal("120000"));
        costParameterService.save(second);

        assertEquals(1, repository.count(), "lần lưu thứ hai KHÔNG được đẻ dòng mới");
        CostParameters current = costParameterService.getOrDefault();
        assertEquals(0, new BigDecimal("9999").compareTo(current.getFuelCostPerKm()));
        assertEquals(0, new BigDecimal("77777").compareTo(current.getDriverWagePerHour()));
        assertEquals(0, new BigDecimal("250000").compareTo(current.getWaitingWagePerDay()));
        assertEquals(0, new BigDecimal("120000").compareTo(current.getBusParkingPerDay()));
    }

    /**
     * Route pairs CT9 (CM-8): dòng cấu hình lưu từ trước CT9 chưa có hai cột tiền chờ — đọc ra giá trị mặc định
     * ở đúng hai ô đó, giữ nguyên hai ô đã lưu, và vẫn không ghi gì xuống DB.
     */
    @Test
    @DisplayName("CT9: dòng cũ chưa có tiền chờ → đọc ra mặc định ở hai ô đó, không ghi gì")
    void aRowSavedBeforeCt9_readsTheWaitingDefaults_withoutWriting() {
        repository.deleteAll();
        CostParameters old = new CostParameters();
        old.setFuelCostPerKm(new BigDecimal("7000"));
        old.setDriverWagePerHour(new BigDecimal("60000"));
        repository.saveAndFlush(old);

        CostParameters read = costParameterService.getOrDefault();

        assertEquals(0, new BigDecimal("7000").compareTo(read.getFuelCostPerKm()));
        assertEquals(0, new BigDecimal("60000").compareTo(read.getDriverWagePerHour()));
        assertEquals(0, CostParameterService.DEFAULT_WAITING_WAGE_PER_DAY.compareTo(read.getWaitingWagePerDay()));
        assertEquals(0, CostParameterService.DEFAULT_BUS_PARKING_PER_DAY.compareTo(read.getBusParkingPerDay()));
        CostParameters stored = repository.findAll().get(0);
        assertNull(stored.getWaitingWagePerDay(), "đọc không được ghi giá trị mặc định xuống dòng đã lưu");
        assertNull(stored.getBusParkingPerDay());
    }

    // =========================================================================
    // Validate đầu vào — regression cho lỗi #11 trong docs/todo/current_bugs_found.md
    // =========================================================================
    //
    // Trước khi sửa, save() chép thẳng form xuống DB: guard duy nhất của toàn hệ
    // thống là `required min="0"` phía client, mà min="0" cho phép chính số 0.
    // Nhập 0 ⇒ chi phí = 0 ⇒ lợi nhuận = doanh thu trên màn Đề Xuất Tăng Cường và
    // cột hiện trạng màn What-if, tô xanh, không một cảnh báo.
    //
    // Cặp test cốt lõi là "chặn 0" + "giá trị dương vẫn lưu bình thường" — cùng
    // khuôn cặp test của bản sửa lỗi #6, để bản sửa không thể sửa quá tay thành
    // chặn nhầm cả đầu vào hợp lệ.

    private static CostParameters form(String fuel, String wage) {
        return form(fuel, wage, "333000", "330000");
    }

    private static CostParameters form(String fuel, String wage, String waiting, String parking) {
        CostParameters p = new CostParameters();
        p.setFuelCostPerKm(fuel == null ? null : new BigDecimal(fuel));
        p.setDriverWagePerHour(wage == null ? null : new BigDecimal(wage));
        p.setWaitingWagePerDay(waiting == null ? null : new BigDecimal(waiting));
        p.setBusParkingPerDay(parking == null ? null : new BigDecimal(parking));
        return p;
    }

    @Test
    @DisplayName("CT9: hai suất tiền chờ cũng phải > 0 — chặn 0, âm, trống")
    void save_rejectsAWaitingRateThatIsNotPositive() {
        repository.deleteAll();
        for (String bad : new String[] { "0", "-1", null }) {
            assertThrows(IllegalArgumentException.class,
                    () -> costParameterService.save(form("6000", "50000", bad, "330000")));
            assertThrows(IllegalArgumentException.class,
                    () -> costParameterService.save(form("6000", "50000", "333000", bad)));
        }
        assertEquals(0, repository.count());
    }

    @Test
    @DisplayName("save chặn số 0 — 0 không phải suất phí, nó biến lợi nhuận thành doanh thu")
    void save_rejectsZero() {
        repository.deleteAll();

        assertThrows(IllegalArgumentException.class, () -> costParameterService.save(form("0", "50000")));
        assertThrows(IllegalArgumentException.class, () -> costParameterService.save(form("6000", "0")));
        assertEquals(0, repository.count(), "đầu vào bị từ chối thì KHÔNG được ghi gì xuống DB");
    }

    @Test
    @DisplayName("save chặn số âm — chi phí âm sẽ thổi phồng lợi nhuận")
    void save_rejectsNegative() {
        repository.deleteAll();

        assertThrows(IllegalArgumentException.class, () -> costParameterService.save(form("-1", "50000")));
        assertThrows(IllegalArgumentException.class, () -> costParameterService.save(form("6000", "-1")));
        assertEquals(0, repository.count());
    }

    /**
     * null nguy hiểm nhất trong ba ca: nếu một dòng NULL lọt vào bảng thì
     * getOrDefault() trả về CHÍNH dòng đó (findFirst) chứ không trả mặc định, nên
     * DEFAULT_FUEL_COST_PER_KM / DEFAULT_DRIVER_WAGE_PER_HOUR bị vô hiệu vĩnh viễn.
     */
    @Test
    @DisplayName("save chặn null — nếu không, một dòng NULL sẽ vô hiệu hoá giá trị mặc định vĩnh viễn")
    void save_rejectsNull() {
        repository.deleteAll();

        assertThrows(IllegalArgumentException.class, () -> costParameterService.save(form(null, "50000")));
        assertThrows(IllegalArgumentException.class, () -> costParameterService.save(form("6000", null)));
        assertEquals(0, repository.count());
    }

    @Test
    @DisplayName("ĐỐI TRỌNG: giá trị dương vẫn lưu bình thường — bản sửa không được chặn quá tay")
    void save_stillAcceptsPositiveValues() {
        repository.deleteAll();

        costParameterService.save(form("6000", "50000"));

        assertEquals(1, repository.count());
        CostParameters current = costParameterService.getOrDefault();
        assertEquals(0, new BigDecimal("6000").compareTo(current.getFuelCostPerKm()));
        assertEquals(0, new BigDecimal("50000").compareTo(current.getDriverWagePerHour()));

        // Giá trị nhỏ nhất còn hợp lệ (1đ) không được coi là "gần bằng 0" rồi chặn nhầm.
        costParameterService.save(form("1", "1"));
        assertEquals(0, new BigDecimal("1").compareTo(costParameterService.getOrDefault().getFuelCostPerKm()));
    }
}
