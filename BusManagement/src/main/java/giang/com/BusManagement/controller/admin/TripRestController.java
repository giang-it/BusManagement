package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller cung cấp dữ liệu động cho Wizard Form tạo chuyến xe.
 *
 * Endpoint duy nhất: GET /api/admin/trips/available-resources
 * Nhận 2 tham số thời gian (departure, arrival), trả về JSON chứa
 * danh sách xe, tài xế và phụ xe THỰC SỰ RẢNH trong khoảng thời gian đó.
 *
 * Tài xế và phụ xe là HAI danh sách riêng vì hai vai trò chịu hai bộ ràng buộc
 * khác nhau — cùng khuôn với màn Phê Duyệt, nơi AdminTripController đã đưa
 * availableDrivers và availableAssistants vào model một cách tách bạch.
 */
@RestController
@RequestMapping("/api/admin/trips")
@RequiredArgsConstructor
public class TripRestController {

    private final TripService tripService;

    /**
     * Trả về danh sách xe, tài xế và phụ xe rảnh trong khoảng [departure, arrival].
     *
     * JavaScript sẽ gọi endpoint này sau khi người dùng chọn đủ Tuyến đường
     * và Thời gian khởi hành (arrival được tính tự động ở frontend).
     *
     * {@code assistants} luôn là siêu tập của {@code drivers} — nó thiếu đúng bộ
     * lọc giờ lái mà vai trò phụ xe không phải chịu.
     *
     * @param departure Thời gian khởi hành (ISO format: "2025-06-15T07:30")
     * @param arrival   Thời gian đến dự kiến (ISO format: "2025-06-15T14:00")
     * @return JSON: { buses: [...], drivers: [...], assistants: [...] }
     */
    @GetMapping("/available-resources")
    public ResponseEntity<Map<String, Object>> getAvailableResources(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departure,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrival) {

        if (arrival.isBefore(departure) || arrival.isEqual(departure)) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Thời gian đến phải sau thời gian khởi hành"));
        }

        // Lấy xe rảnh (trả về AvailableBusDto để frontend dùng data-capacity)
        List<Map<String, Object>> buses = tripService
                .getAvailableBusesForTimeRange(departure, arrival)
                .stream()
                .map(this::toBusDto)
                .toList();

        // Lấy tài xế rảnh (dropdown Tài xế chính + Tài xế phụ — có trần 8h/ngày)
        List<Map<String, Object>> drivers = tripService
                .getAvailableDriversForTimeRange(departure, arrival)
                .stream()
                .map(this::toDriverDto)
                .toList();

        // Lấy phụ xe khả dụng — danh sách RIÊNG, không áp trần giờ lái vì phụ xe
        // không cầm lái (xem TripService.getAvailableAssistantsForTimeRange).
        // Dùng chung một danh sách cho cả hai vai trò chính là lỗi #13.
        List<Map<String, Object>> assistants = tripService
                .getAvailableAssistantsForTimeRange(departure, arrival)
                .stream()
                .map(this::toDriverDto)
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("buses", buses);
        response.put("drivers", drivers);
        response.put("assistants", assistants);

        return ResponseEntity.ok(response);
    }

    // ── DTO helpers ──────────────────────────────────────────────────────────

    /**
     * Chuyển Bus entity thành Map đơn giản để serialize thành JSON.
     * Bao gồm 'capacity' để frontend điền totalSeats tự động khi chọn xe.
     */
    private Map<String, Object> toBusDto(Bus bus) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", bus.getId());
        dto.put("licensePlate", bus.getLicensePlate());
        // typeId để form Tạo so với loại xe gợi ý của tuyến (data-type) — Group C(a)
        dto.put("typeId", bus.getBusType() != null ? bus.getBusType().getId() : null);
        dto.put("typeName", bus.getBusType() != null ? bus.getBusType().getTypeName() : "Không rõ");
        dto.put("capacity", bus.getBusType() != null ? bus.getBusType().getCapacity() : 0);
        dto.put("brand", bus.getBrand());
        return dto;
    }

    /**
     * Chuyển Driver entity thành Map đơn giản để serialize thành JSON.
     */
    private Map<String, Object> toDriverDto(Driver driver) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("userId", driver.getUserId());
        dto.put("fullName", driver.getUser() != null
                ? driver.getUser().getFullName()
                : "Tài xế #" + driver.getUserId());
        dto.put("licenseNumber", driver.getLicenseNumber());
        dto.put("experienceYears", driver.getExperienceYears());
        return dto;
    }
}