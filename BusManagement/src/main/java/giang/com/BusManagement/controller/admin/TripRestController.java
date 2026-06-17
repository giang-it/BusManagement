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
 * danh sách xe và tài xế THỰC SỰ RẢNH trong khoảng thời gian đó.
 */
@RestController
@RequestMapping("/api/admin/trips")
@RequiredArgsConstructor
public class TripRestController {

    private final TripService tripService;

    /**
     * Trả về danh sách xe và tài xế rảnh trong khoảng [departure, arrival].
     *
     * JavaScript sẽ gọi endpoint này sau khi người dùng chọn đủ Tuyến đường
     * và Thời gian khởi hành (arrival được tính tự động ở frontend).
     *
     * @param departure Thời gian khởi hành (ISO format: "2025-06-15T07:30")
     * @param arrival   Thời gian đến dự kiến (ISO format: "2025-06-15T14:00")
     * @return JSON: { buses: [...], drivers: [...] }
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

        // Lấy tài xế rảnh
        List<Map<String, Object>> drivers = tripService
                .getAvailableDriversForTimeRange(departure, arrival)
                .stream()
                .map(this::toDriverDto)
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("buses", buses);
        response.put("drivers", drivers);

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