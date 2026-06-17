package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.*;
import giang.com.BusManagement.repository.*;
import giang.com.BusManagement.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/trip-management")
@RequiredArgsConstructor
public class AdminTripManagementController {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final TripService tripService;

    // ==================== DANH SÁCH TẤT CẢ TRIPS ====================

    /**
     * Xem tất cả các chuyến xe (tất cả trạng thái)
     */
    @GetMapping("/trips")
    public String listAllTrips(Model model) {
        // SỬA: Dùng findAllWithDetails() thay vì findAll()
        model.addAttribute("trips", tripRepository.findAllWithDetails());
        model.addAttribute("statuses", TripStatus.values());
        return "admin/trip-list";
    }

    /**
     * Lọc trips theo trạng thái
     */
    @GetMapping("/trips/filter")
    public String filterTrips(@RequestParam(required = false) String status, Model model) {
        if (status != null && !status.isEmpty()) {
            TripStatus tripStatus = TripStatus.valueOf(status);
            // SỬA: Dùng findByStatusWithDetails() thay vì findByStatus()
            model.addAttribute("trips", tripRepository.findByStatusWithDetails(tripStatus));
        } else {
            // SỬA: Dùng findAllWithDetails() thay vì findAll()
            model.addAttribute("trips", tripRepository.findAllWithDetails());
        }
        model.addAttribute("statuses", TripStatus.values());
        model.addAttribute("selectedStatus", status);
        return "admin/trip-list";
    }

    // ==================== TẠO TRIP THỦ CÔNG ====================

    /**
     * Hiển thị form tạo trip thủ công
     */
    @GetMapping("/trips/create")
    public String showCreateTripForm(Model model) {
        model.addAttribute("trip", new Trip());
        model.addAttribute("routes", routeRepository.findAll());
        model.addAttribute("buses", busRepository.findAll());
        model.addAttribute("drivers", driverRepository.findAll());
        return "admin/trip-create-form";
    }

    /**
     * Lưu trip mới
     */
    @PostMapping("/trips/create")
    public String createTrip(@ModelAttribute Trip trip,
            @RequestParam Long routeId,
            @RequestParam Long busId,
            @RequestParam Long driverId,
            @RequestParam(required = false) Long assistantId,
            @RequestParam(required = false) List<Long> coDriverIds,
            RedirectAttributes redirectAttributes) {
        try {
            // Gán route, bus, driver
            Route route = routeRepository.findById(routeId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tuyến"));
            Bus bus = busRepository.findById(busId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy xe"));
            Driver driver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế"));

            trip.setRoute(route);
            trip.setBus(bus);
            trip.setDriver(driver);

            // Gán phụ xe nếu có
            if (assistantId != null && assistantId > 0) {
                Driver assistant = driverRepository.findById(assistantId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy phụ xe"));
                trip.setAssistant(assistant);
            }

            // Gán tài xế phụ nếu có
            trip.getCoDrivers().clear();
            if (coDriverIds != null) {
                for (Long cdId : coDriverIds) {
                    if (cdId != null && cdId > 0) {
                        Driver cd = driverRepository.findById(cdId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế phụ #" + cdId));
                        trip.getCoDrivers().add(cd);
                    }
                }
            }

            // Đặt trạng thái mặc định
            trip.setStatus(TripStatus.ACTIVE);
            trip.setExtraTrip(false);

            // Lưu trip thông qua Service để kích hoạt Validate
            String warning = tripService.createManualTrip(trip);

            redirectAttributes.addFlashAttribute("success", "Tạo chuyến xe thành công!");
            if (warning != null) {
                redirectAttributes.addFlashAttribute("warning", warning);
            }
            return "redirect:/admin/trip-management/trips";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/trip-management/trips/create";
        }
    }

    // ==================== SỬA & XÓA TRIP ====================

    /**
     * Hiển thị form sửa trip.
     *
     * Danh sách xe đưa vào dropdown được lọc qua
     * tripService.getAvailableBusesForTrip(id)
     * — CÙNG logic với form tạo trip (READY, không bận, chưa quá hạn/sắp đến hạn
     * bảo trì) — để Create và Edit nhất quán, không cho Admin chọn lại một xe mà
     * validateBusForTrip() sẽ từ chối khi submit.
     *
     * Xe ĐANG được gán cho chính chuyến này luôn được thêm vào danh sách (nếu
     * chưa có) để dropdown hiển thị đúng lựa chọn hiện tại, dù xe đó vừa rơi vào
     * trạng thái không hợp lệ sau khi trip đã được tạo (ví dụ vừa quá hạn bảo
     * trì) — Admin vẫn cần thấy xe nào đang được gán để chủ động đổi sang xe khác.
     */
    @GetMapping("/trips/edit/{id}")
    public String showEditTripForm(@PathVariable Long id, Model model) {
        Trip trip = tripRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến"));

        List<Long> savedCoDriverIds = trip.getCoDrivers().stream()
                .map(Driver::getUserId)
                .toList();

        List<Bus> availableBuses = new java.util.ArrayList<>(tripService.getAvailableBusesForTrip(id));
        Bus currentBus = trip.getBus();
        if (currentBus != null && availableBuses.stream().noneMatch(b -> b.getId().equals(currentBus.getId()))) {
            availableBuses.add(currentBus);
        }

        model.addAttribute("trip", trip);
        model.addAttribute("savedCoDriverIds", savedCoDriverIds);
        model.addAttribute("routes", routeRepository.findAll());
        model.addAttribute("buses", availableBuses);
        List<Driver> drivers = driverRepository.findAllWithUser();
        model.addAttribute("drivers", drivers);
        model.addAttribute("statuses", TripStatus.values());

        List<Map<String, Object>> driversForJs = drivers.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", d.getUserId());
            map.put("fullName", d.getUser() != null ? d.getUser().getFullName() : "ID: " + d.getUserId());
            return map;
        }).toList();

        // Đẩy danh sách rút gọn này sang View
        model.addAttribute("driversForJs", driversForJs);

        return "admin/trip-edit-form";
    }

    // ===== AFTER =====
    /**
     * Cập nhật trip.
     *
     * Tách biệt 2 luồng:
     * 1. Cập nhật thông tin chuyến (route, bus, driver, thời gian, giá...) qua
     * updateManualTrip().
     * 2. Chuyển trạng thái (nếu có thay đổi) qua updateTripStatus() để FSM được
     * thực thi.
     *
     * Thứ tự quan trọng: updateManualTrip() phải chạy TRƯỚC để lưu thông tin mới
     * xuống DB,
     * sau đó updateTripStatus() re-fetch trip từ DB và apply FSM transition trên
     * trạng thái
     * hiện tại — đảm bảo BusStatus synchronization cũng chạy đúng trên bus mới (nếu
     * bus bị thay).
     */
    @PostMapping("/trips/update")
    public String updateTrip(@ModelAttribute Trip trip,
            @RequestParam Long routeId,
            @RequestParam Long busId,
            @RequestParam Long driverId,
            @RequestParam(required = false) Long assistantId,
            @RequestParam(required = false) List<Long> coDriverIds,
            RedirectAttributes redirectAttributes) {
        try {
            // Lấy trip cũ từ DB
            Trip existingTrip = tripRepository.findByIdWithDetails(trip.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến"));

            // Capture requested status TRƯỚC khi ghi đè existingTrip
            TripStatus newStatus = trip.getStatus();

            // Cập nhật các trường thông tin (KHÔNG setStatus)
            Route route = routeRepository.findById(routeId).orElseThrow();
            Bus bus = busRepository.findById(busId).orElseThrow();
            Driver driver = driverRepository.findById(driverId).orElseThrow();

            existingTrip.setRoute(route);
            existingTrip.setBus(bus);
            existingTrip.setDriver(driver);
            existingTrip.setDepartureTime(trip.getDepartureTime());
            existingTrip.setArrivalTimeExpected(trip.getArrivalTimeExpected());
            existingTrip.setTotalSeats(trip.getTotalSeats());
            existingTrip.setPrice(trip.getPrice());
            // existingTrip.setStatus() KHÔNG được gọi ở đây — status chỉ thay đổi qua FSM
            // bên dưới

            // Cập nhật phụ xe
            if (assistantId != null && assistantId > 0) {
                Driver assistant = driverRepository.findById(assistantId).orElseThrow();
                existingTrip.setAssistant(assistant);
            } else {
                existingTrip.setAssistant(null);
            }

            // Cập nhật tài xế phụ
            existingTrip.getCoDrivers().clear();
            if (coDriverIds != null) {
                for (Long cdId : coDriverIds) {
                    if (cdId != null && cdId > 0) {
                        Driver cd = driverRepository.findById(cdId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế phụ #" + cdId));
                        existingTrip.getCoDrivers().add(cd);
                    }
                }
            }

            // Bước 1: Lưu thông tin chuyến (status không đổi ở bước này)
            String warning = tripService.updateManualTrip(existingTrip);

            // Bước 2: Nếu admin muốn đổi trạng thái → delegate qua FSM
            // updateTripStatus() re-fetch trip từ DB, chạy canTransition(), đồng bộ
            // BusStatus
            if (existingTrip.getStatus() != newStatus) {
                tripService.updateTripStatus(existingTrip.getId(), newStatus);
            }

            redirectAttributes.addFlashAttribute("success", "Cập nhật chuyến xe thành công!");
            if (warning != null) {
                redirectAttributes.addFlashAttribute("warning", warning);
            }
            return "redirect:/admin/trip-management/trips";

        } catch (IllegalStateException e) {
            // FSM từ chối transition không hợp lệ (ví dụ: COMPLETED → ACTIVE)
            redirectAttributes.addFlashAttribute("error", "Không thể đổi trạng thái: " + e.getMessage());
            return "redirect:/admin/trip-management/trips/edit/" + trip.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/trip-management/trips/edit/" + trip.getId();
        }
    }

    /**
     * Hủy trip.
     * Delegate sang tripService.updateTripStatus() để FSM canTransition() được thực
     * thi.
     * Các trạng thái hợp lệ để hủy: PENDING_APPROVAL, ACTIVE.
     * COMPLETED và CANCELLED là terminal states — FSM sẽ reject và ném
     * IllegalStateException.
     */
    @PostMapping("/trips/cancel/{id}")
    public String cancelTrip(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            tripService.updateTripStatus(id, TripStatus.CANCELLED);
            redirectAttributes.addFlashAttribute("success", "Hủy chuyến thành công!");
        } catch (IllegalStateException e) {
            // FSM từ chối transition không hợp lệ (ví dụ: COMPLETED → CANCELLED)
            redirectAttributes.addFlashAttribute("error", "Không thể hủy: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/trip-management/trips";
    }

    /**
     * Xóa mềm chuyến xe — toàn bộ kiểm tra nghiệp vụ nằm trong
     * TripService.deleteTrip().
     *
     * Các trường hợp lỗi được phân biệt rõ ràng:
     * IllegalStateException → Vi phạm quy tắc nghiệp vụ (chuyến đang chạy, đã bán
     * vé...)
     * EntityNotFoundException → Không tìm thấy chuyến trong DB
     * Exception → Lỗi hệ thống không mong đợi
     */
    @PostMapping("/trips/delete/{id}")
    public String deleteTrip(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            tripService.deleteTrip(id);
            redirectAttributes.addFlashAttribute("success",
                    "✅ Đã xóa chuyến xe #" + id + " thành công.");
        } catch (IllegalStateException e) {
            // Vi phạm ràng buộc nghiệp vụ → thông báo rõ ràng cho Admin
            redirectAttributes.addFlashAttribute("error", "⛔ Không thể xóa: " + e.getMessage());
        } catch (jakarta.persistence.EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "❌ Không tìm thấy chuyến xe #" + id);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "❌ Lỗi hệ thống: " + e.getMessage());
        }
        return "redirect:/admin/trip-management/trips";
    }
}