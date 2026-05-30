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

    @Transactional
    public void save(Station station) {
        stationRepository.save(station);
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