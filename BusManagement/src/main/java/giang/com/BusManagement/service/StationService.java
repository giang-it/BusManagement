package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;

    public List<Station> findAll() {
        return stationRepository.findAll();
    }

    public Optional<Station> findById(Long id) {
        return stationRepository.findById(id);
    }

    /**
     * TẠO MỚI một bến xe. CHỈ dùng cho việc tạo — cập nhật phải đi qua
     * {@link #updateStation(Long, Station)}.
     *
     * Trước đây một hàm save() làm cả hai việc, tức là một upsert: controller
     * bind Station bằng @ModelAttribute không có @InitBinder, nên một POST tự chế
     * tới /stations/create mang id có sẵn khiến save() thành MERGE và ghi đè bến
     * đó (lỗi #21, đã tái hiện thật). Tripwire dưới đây cùng khuôn
     * BusService.saveBus() (lỗi #16).
     */
    @Transactional
    public void createStation(Station station) {
        if (station.getId() != null) {
            throw new IllegalArgumentException(
                    "createStation() chỉ dùng để tạo bến xe mới (id phải trống). Bến #" + station.getId()
                            + " đã tồn tại — muốn sửa thì dùng chức năng Sửa bến xe.");
        }
        stationRepository.save(station);
    }

    /**
     * CẬP NHẬT một bến xe: nạp bản ghi theo id từ URL rồi chép hai field của form
     * — khuôn BusService.updateBus() / IncidentService.updateIncident(). Không
     * merge nguyên object form, để routeStations (form không gửi) không bị đụng.
     */
    @Transactional
    public void updateStation(Long id, Station form) {
        Station existing = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bến xe với ID: " + id));
        existing.setStationName(form.getStationName());
        existing.setAddress(form.getAddress());
        stationRepository.save(existing);
    }

    @Transactional
    public void deleteById(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bến xe cần xóa với ID: " + id));

        // RÀNG BUỘC 3A: Chặn xóa nếu trạm đang nằm trong lộ trình của bất kỳ tuyến
        // đường nào
        if (station.getRouteStations() != null && !station.getRouteStations().isEmpty()) {
            throw new RuntimeException(
                    "Không thể xóa bến xe này vì đang thuộc lộ trình của một số tuyến đường hiện hành!");
        }

        stationRepository.delete(station);
    }
}