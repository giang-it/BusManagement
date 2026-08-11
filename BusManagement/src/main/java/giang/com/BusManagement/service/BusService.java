package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.IncidentRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BusService {

    private final BusRepository busRepository;
    private final BusTypeRepository busTypeRepository;
    private final TripRepository tripRepository;
    private final IncidentRepository incidentRepository;

    public List<Bus> findAllWithBusType() {
        return busRepository.findAllWithBusType();
    }

    public Optional<Bus> findById(Long id) {
        return busRepository.findById(id);
    }

    public List<BusType> findAllBusTypes() {
        return busTypeRepository.findAll();
    }

    /**
     * TẠO MỚI một xe. CHỈ dùng cho việc tạo — cập nhật phải đi qua
     * {@link #updateBus(Long, Bus)}.
     *
     * Ba giá trị odometer được điền mặc định khi null, vì form tạo xe không bắt
     * buộc nhập chúng: một chiếc xe vừa nhập đội hợp lệ khi chưa có số km nào.
     * Mặc định đó CHỈ đúng ở đây — áp nó cho luồng sửa thì "ô để trống" biến
     * thành "đặt về 0", tức xoá mất số km trọn đời. Đó chính là lỗi #16 trong
     * docs/todo/current_bugs_found.md.
     */
    @Transactional
    public void saveBus(Bus bus) {
        // Tripwire: entity đã có id nghĩa là người gọi đang muốn CẬP NHẬT. Nếu để
        // lọt xuống dưới, JPA sẽ merge và ghi đè mọi cột của bản ghi cũ bằng nội
        // dung của form — kể cả các mặc định điền ở ngay bên dưới, tức lỗi #16 quay
        // lại — đồng thời đi vòng qua ràng buộc REPAIRING nay nằm trong updateBus().
        if (bus.getId() != null) {
            throw new IllegalArgumentException(
                    "saveBus() chỉ dùng để tạo xe mới. Cập nhật phải gọi updateBus(id, form).");
        }

        // Khởi tạo các giá trị Odometer an toàn tránh lỗi NullPointerException
        if (bus.getMaintenanceThreshold() == null) {
            bus.setMaintenanceThreshold(5000.0);
        }
        if (bus.getLastMaintenanceOdometer() == null) {
            bus.setLastMaintenanceOdometer(0.0);
        }
        if (bus.getOdometer() == null) {
            bus.setOdometer(0.0);
        }

        validate(bus);
        busRepository.save(bus);
    }

    /**
     * CẬP NHẬT một xe: chép từng field từ object của form sang bản ghi đã có
     * trong DB, thay vì lưu thẳng object rời.
     *
     * ====================================================================
     * VÌ SAO Ô SỐ ĐỂ TRỐNG NGHĨA LÀ "GIỮ NGUYÊN", KHÔNG PHẢI "ĐẶT VỀ 0"
     * ====================================================================
     * AdminBusController dựng một Bus MỚI từ form, nên field vắng mặt hoặc ô để
     * trống đều bind thành null. Trước đây null rơi vào nhánh mặc-định-lúc-tạo và
     * biến thành 0.0: chỉ cần xoá trắng ô "Odometer Hiện Tại" rồi bấm Lưu là mất
     * sạch số km trọn đời, kèm thông báo THÀNH CÔNG. Hậu quả không dừng ở con số
     * hiển thị — kmSinceLastMaintenance thành âm nên xe KHÔNG BAO GIỜ tới hạn bảo
     * trì nữa, và màn Đề Xuất Thay Xe (70% điểm theo odometer) xếp sai. Giá trị cũ
     * không còn ở đâu để khôi phục.
     *
     * Đây đúng khuôn IncidentService.updateIncident(): "form không gửi X, nên lưu
     * thẳng sẽ ghi đè X thành null". Muốn đặt odometer bằng 0 (xe mới tinh) thì
     * GÕ SỐ 0 — validate() cho phép 0, nên không lựa chọn nào bị mất.
     *
     * Chỉ BA Ô SỐ áp quy tắc này. Biển số / hãng / loại xe / trạng thái vẫn chép
     * nguyên như form gửi: cả bốn đều là input `required` nên form không bao giờ
     * gửi rỗng, và chúng không tham gia phép tính nào — mất một trong số đó là lỗi
     * hiển thị thấy ngay, không phải một con số sai âm thầm rồi lan sang bộ lọc
     * bảo trì như odometer. Giữ nguyên khuôn IncidentService.updateIncident(),
     * nơi các FK bắt buộc cũng được chép thẳng.
     */
    @Transactional
    public void updateBus(Long id, Bus form) {
        Bus existing = busRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy xe với ID: " + id));

        // RÀNG BUỘC 1A: Kiểm tra nếu Admin cố tình chuyển trạng thái sang REPAIRING khi
        // đang bận chạy lịch tương lai. Dời nguyên văn từ saveBus() sang đây vì luồng
        // sửa nay đi qua method này; DEPARTED cố ý CHƯA thêm — đó là lỗi #15, một
        // quyết định riêng.
        if (form.getStatus() == BusStatus.REPAIRING) {
            boolean hasActiveTrips = tripRepository.existsByBusIdAndStatusIn(
                    id,
                    Arrays.asList(TripStatus.ACTIVE, TripStatus.PENDING_APPROVAL));
            if (hasActiveTrips) {
                throw new RuntimeException(
                        "Không thể chuyển trạng thái xe sang bảo trì vì xe đang được phân công cho các chuyến xe đang hoạt động hoặc chờ duyệt!");
            }
        }

        existing.setLicensePlate(form.getLicensePlate());
        existing.setBrand(form.getBrand());
        existing.setBusType(form.getBusType());
        existing.setStatus(form.getStatus());

        if (form.getOdometer() != null) {
            existing.setOdometer(form.getOdometer());
        }
        if (form.getLastMaintenanceOdometer() != null) {
            existing.setLastMaintenanceOdometer(form.getLastMaintenanceOdometer());
        }
        if (form.getMaintenanceThreshold() != null) {
            existing.setMaintenanceThreshold(form.getMaintenanceThreshold());
        }

        validate(existing);
        busRepository.save(existing);
    }

    /**
     * Ràng buộc tính hợp lệ của ba con số bảo trì, dùng chung cho cả tạo lẫn sửa.
     *
     * Validate ở tầng service theo đúng tiền lệ RouteService.validateRoute() và
     * CostParameterService.validate() (lỗi #11): form chỉ là hàng rào phía client.
     * Ba ca dưới đây đều đã tái hiện được trên app thật — xem lỗi #16.
     *
     * - Số âm: odometer/km bảo trì âm không có nghĩa vật lý.
     * - odometer < lastMaintenanceOdometer: kmSinceLastMaintenance (Bus.java:42-46)
     *   thành ÂM, nên needsMaintenance()/isNearMaintenance() vĩnh viễn false — xe
     *   không bao giờ được đưa đi bảo trì nữa.
     * - maintenanceThreshold <= 0: needsMaintenance() là {@code km >= threshold},
     *   mà {@code 0 >= 0} luôn đúng ⇒ xe bị coi là QUÁ HẠN vĩnh viễn và bị
     *   validateBusForTrip() loại khỏi mọi chuyến. Ngay cả thao tác nghiệp vụ
     *   "vừa bảo trì xong" (đặt lastMaintenanceOdometer = odometer) cũng không gỡ
     *   được — chỉ sửa lại chính ô ngưỡng mới thoát.
     */
    private void validate(Bus bus) {
        if (bus.getOdometer() == null || bus.getOdometer() < 0) {
            throw new IllegalArgumentException("Odometer không được âm!");
        }
        if (bus.getLastMaintenanceOdometer() == null || bus.getLastMaintenanceOdometer() < 0) {
            throw new IllegalArgumentException("Km lần bảo trì cuối không được âm!");
        }
        if (bus.getOdometer() < bus.getLastMaintenanceOdometer()) {
            throw new IllegalArgumentException(String.format(
                    "Odometer (%.1f km) không được nhỏ hơn km lần bảo trì cuối (%.1f km)!",
                    bus.getOdometer(), bus.getLastMaintenanceOdometer()));
        }
        if (bus.getMaintenanceThreshold() == null || bus.getMaintenanceThreshold() <= 0) {
            throw new IllegalArgumentException("Ngưỡng cảnh báo bảo trì phải lớn hơn 0 km!");
        }
    }

    @Transactional
    public void deleteBus(Long id) {
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy xe cần xóa với ID: " + id));

        // RÀNG BUỘC 1A: Chặn xóa tuyệt đối xe đã nằm trong hệ thống chuyến đi để giữ
        // lịch sử giao dịch/vé
        boolean hasAnyTrip = tripRepository.existsByBusId(id);
        if (hasAnyTrip) {
            throw new RuntimeException(
                    "Không thể xóa xe này vì xe đã có dữ liệu lịch sử vận hành hoặc phân công. Hãy đổi trạng thái sang bảo trì thay vì xóa cứng!");
        }

        // RÀNG BUỘC: Chặn xóa xe còn bản ghi sự cố trỏ tới nó — cùng nguyên tắc giữ
        // lịch sử vận hành với ràng buộc chuyến ở trên.
        // Kiểm tra ở tầng service là bắt buộc, KHÔNG thể dựa vào FK: JDBC URL đặt
        // sessionVariables=foreign_key_checks=0 nên MySQL không chặn, xóa sẽ âm thầm
        // để lại incidents.bus_id trỏ vào xe không còn tồn tại — trong khi Incident
        // khai báo bus là optional=false/nullable=false.
        // Incident.bus BẮT BUỘC nên Admin không thể gỡ liên kết; lối thoát duy nhất
        // là xóa bản ghi sự cố trước (Incident cho xóa tự do, không guard).
        if (incidentRepository.existsByBusId(id)) {
            throw new RuntimeException(
                    "Không thể xóa xe này vì đang có bản ghi sự cố gắn với nó. Hãy xóa các bản ghi sự cố của xe ở màn hình Quản Lý Sự Cố trước, hoặc đổi trạng thái xe sang bảo trì thay vì xóa cứng!");
        }

        busRepository.delete(bus);
    }
}