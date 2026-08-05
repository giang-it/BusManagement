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
     *
     * Validate ở tầng service (không chỉ dựa vào `required`/`min` của form) theo
     * đúng tiền lệ RouteService.saveRoute() — xem javadoc của validate().
     */
    @Transactional
    public void save(CostParameters form) {
        validate(form);

        CostParameters target = repository.findAll().stream().findFirst().orElseGet(CostParameters::new);
        target.setFuelCostPerKm(form.getFuelCostPerKm());
        target.setDriverWagePerHour(form.getDriverWagePerHour());
        repository.save(target);
    }

    /**
     * Cả hai suất phí bắt buộc phải LỚN HƠN 0.
     *
     * ====================================================================
     * VÌ SAO 0 KHÔNG PHẢI ĐẦU VÀO HỢP LỆ
     * ====================================================================
     * 1. Chi phí nhiên liệu = {@code fuelCostPerKm × Route.distanceKm}, mà
     *    RouteService.validateRoute() ĐÃ từ chối {@code distanceKm <= 0}. Canh một
     *    thừa số của tích rồi thả thừa số kia thì guard đó bị phá bằng cửa sau —
     *    chặn ở đây là HOÀN TẤT một quyết định đã có, không phải đặt luật mới.
     * 2. Entity CostParameters sinh ra vì domain không có dữ liệu chi phí nào để
     *    suy (xem javadoc của CostParameters). Nhập 0 đưa hệ thống về đúng trạng
     *    thái "không có thông tin chi phí" nhưng nguỵ trang thành một con số đã
     *    tính, rồi biến "lợi nhuận" thành "doanh thu" trên màn Đề Xuất Tăng Cường
     *    và màn What-if mà không một cảnh báo nào.
     * 3. Số 0 không được xử lý đặc biệt ở bất kỳ đâu — nó chỉ âm thầm bị nuốt
     *    thành 0đ trong phép nhân của RecommendationService.estimateCost() và
     *    WhatIfSimulationService.computeOutcome().
     * 4. WhatIfSimulationService.positiveOrNull() đã loại 0/âm với cùng lý do
     *    ("để tránh chi phí âm") — đây chỉ là áp cùng lập trường cho nguồn cấu
     *    hình được LƯU, thay vì chỉ cho ô ghi đè tạm thời.
     *
     * Đây là ràng buộc TÍNH HỢP LỆ CỦA ĐẦU VÀO, không phải module định giá (§4
     * Non-Goals): nó không quyết định suất phí là bao nhiêu — operator vẫn nhập —
     * chỉ từ chối một giá trị không mang thông tin.
     *
     * Ném IllegalArgumentException để CostParameterController hiện flash "Lỗi: …"
     * sẵn có, không cần sửa controller (cùng khuôn AdminRouteController).
     */
    private void validate(CostParameters form) {
        requirePositive(form.getFuelCostPerKm(), "Chi phí nhiên liệu mỗi km");
        requirePositive(form.getDriverWagePerHour(), "Lương tài xế mỗi giờ");
    }

    private void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " phải lớn hơn 0!");
        }
    }
}
