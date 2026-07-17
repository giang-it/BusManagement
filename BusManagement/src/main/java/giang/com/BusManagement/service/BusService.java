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

    @Transactional
    public void saveBus(Bus bus) {
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

        // RÀNG BUỘC 1A: Kiểm tra nếu Admin cố tình chuyển trạng thái sang REPAIRING khi
        // đang bận chạy lịch tương lai
        if (bus.getId() != null && bus.getStatus() == BusStatus.REPAIRING) {
            boolean hasActiveTrips = tripRepository.existsByBusIdAndStatusIn(
                    bus.getId(),
                    Arrays.asList(TripStatus.ACTIVE, TripStatus.PENDING_APPROVAL));
            if (hasActiveTrips) {
                throw new RuntimeException(
                        "Không thể chuyển trạng thái xe sang bảo trì vì xe đang được phân công cho các chuyến xe đang hoạt động hoặc chờ duyệt!");
            }
        }

        busRepository.save(bus);
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