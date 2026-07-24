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
        costParameterService.save(first);
        assertEquals(1, repository.count(), "lần lưu đầu tạo đúng 1 dòng");

        CostParameters second = new CostParameters();
        second.setFuelCostPerKm(new BigDecimal("9999"));
        second.setDriverWagePerHour(new BigDecimal("77777"));
        costParameterService.save(second);

        assertEquals(1, repository.count(), "lần lưu thứ hai KHÔNG được đẻ dòng mới");
        CostParameters current = costParameterService.getOrDefault();
        assertEquals(0, new BigDecimal("9999").compareTo(current.getFuelCostPerKm()));
        assertEquals(0, new BigDecimal("77777").compareTo(current.getDriverWagePerHour()));
    }
}
