package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Incident;
import giang.com.BusManagement.domain.IncidentStatus;
import giang.com.BusManagement.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Quản lý các bản ghi sự cố vận hành.
 *
 * Service này CỐ TÌNH không đụng tới trạng thái xe: ghi nhận một sự cố không tự
 * chuyển Bus sang REPAIRING, cũng không hủy/đổi chuyến nào. Đưa xe đi bảo trì
 * vẫn là quyết định thủ công của Admin ở BusService — nơi đã có sẵn ràng buộc
 * "không cho chuyển sang REPAIRING khi xe còn chuyến ACTIVE/PENDING_APPROVAL".
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;

    public List<Incident> findAllWithDetails() {
        return incidentRepository.findAllWithDetails();
    }

    public Optional<Incident> findByIdWithDetails(Long id) {
        return incidentRepository.findByIdWithDetails(id);
    }

    public long countByStatus(IncidentStatus status) {
        return incidentRepository.countByStatus(status);
    }

    @Transactional
    public void createIncident(Incident incident) {
        validate(incident);
        if (incident.getStatus() == null) {
            incident.setStatus(IncidentStatus.OPEN);
        }
        syncResolvedAt(incident);
        incidentRepository.save(incident);
    }

    /**
     * Cập nhật một sự cố.
     *
     * Chép từng field từ object của form sang bản ghi đã có trong DB thay vì lưu
     * thẳng object rời: form không gửi reportedAt/resolvedAt, nên lưu thẳng sẽ
     * ghi đè 2 mốc thời gian đó thành null.
     */
    @Transactional
    public void updateIncident(Long id, Incident form) {
        Incident existing = incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sự cố với ID: " + id));

        validate(form);

        existing.setBus(form.getBus());
        existing.setTrip(form.getTrip());
        existing.setDriver(form.getDriver());
        existing.setIncidentType(form.getIncidentType());
        existing.setDescription(form.getDescription());
        existing.setStatus(form.getStatus());

        syncResolvedAt(existing);
        incidentRepository.save(existing);
    }

    @Transactional
    public void deleteIncident(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sự cố cần xóa với ID: " + id));
        incidentRepository.delete(incident);
    }

    /**
     * Đồng bộ mốc resolvedAt theo trạng thái hiện tại.
     * RESOLVED mà chưa có mốc -> đóng dấu bây giờ; rời khỏi RESOLVED (mở lại sự
     * cố) -> gỡ mốc để không còn thời điểm xử lý xong treo lại.
     */
    private void syncResolvedAt(Incident incident) {
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            if (incident.getResolvedAt() == null) {
                incident.setResolvedAt(LocalDateTime.now());
            }
        } else {
            incident.setResolvedAt(null);
        }
    }

    private void validate(Incident incident) {
        if (incident.getBus() == null) {
            throw new IllegalArgumentException("Sự cố bắt buộc phải gắn với một xe cụ thể!");
        }
        if (incident.getIncidentType() == null) {
            throw new IllegalArgumentException("Vui lòng chọn loại sự cố!");
        }
        if (incident.getStatus() == null) {
            throw new IllegalArgumentException("Vui lòng chọn trạng thái xử lý!");
        }
    }
}
