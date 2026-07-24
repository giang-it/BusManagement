package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.CostParameters;
import giang.com.BusManagement.repository.CostParametersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Nguồn duy nhất của tham số chi phí vận hành (Phase 7, bước 3).
 *
 * Đảm bảo quy ước "một dòng cấu hình duy nhất": mọi thao tác đọc/ghi đều nhắm
 * vào dòng ĐẦU TIÊN trong bảng, nên dù bảng có sẵn nhiều dòng vì lý do nào đó,
 * hệ thống vẫn nhất quán và {@link #save} không bao giờ đẻ thêm dòng thứ hai.
 */
@Service
@RequiredArgsConstructor
public class CostParameterService {

    private final CostParametersRepository repository;

    /**
     * Mặc định tham chiếu thị trường XẤP XỈ (đồng) — đơn vị vận hành PHẢI chỉnh
     * theo thực tế; khi bảo vệ nên trích nguồn hiện hành. Đặt ở đây, không hardcode
     * rải rác trong engine.
     *
     * - Nhiên liệu ~6.000đ/km: diesel ~22.000đ/L × ~0,27 L/km (xe khách ~27 L/100km).
     * - Lương ~50.000đ/giờ: ~10 triệu đồng/tháng ÷ ~200 giờ công.
     */
    static final BigDecimal DEFAULT_FUEL_COST_PER_KM = new BigDecimal("6000");
    static final BigDecimal DEFAULT_DRIVER_WAGE_PER_HOUR = new BigDecimal("50000");

    /**
     * Dòng cấu hình hiện hành, hoặc một object mặc định (CHƯA lưu) nếu Admin chưa
     * từng đặt. KHÔNG ghi-khi-đọc: giá trị mặc định chỉ nằm trong bộ nhớ cho tới
     * khi Admin bấm lưu, nên trang đề xuất vẫn tính được chi phí ngay từ đầu mà
     * không tạo bản ghi ngầm.
     */
    @Transactional(readOnly = true)
    public CostParameters getOrDefault() {
        return repository.findAll().stream().findFirst().orElseGet(() -> {
            CostParameters d = new CostParameters();
            d.setFuelCostPerKm(DEFAULT_FUEL_COST_PER_KM);
            d.setDriverWagePerHour(DEFAULT_DRIVER_WAGE_PER_HOUR);
            return d;
        });
    }

    /**
     * Lưu dòng cấu hình duy nhất. Nếu đã có dòng thì ghi đè chính dòng đó (giữ id,
     * đóng dấu lại updatedAt) thay vì tạo dòng mới — đây là chỗ thực thi quy ước
     * một-dòng-duy-nhất.
     */
    @Transactional
    public void save(CostParameters form) {
        CostParameters target = repository.findAll().stream().findFirst().orElseGet(CostParameters::new);
        target.setFuelCostPerKm(form.getFuelCostPerKm());
        target.setDriverWagePerHour(form.getDriverWagePerHour());
        repository.save(target);
    }
}
