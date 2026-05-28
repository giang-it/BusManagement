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
                        trip.getCoDrivers().add(cd.getUser());
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
     * Hiển thị form sửa trip
     */
    @GetMapping("/trips/edit/{id}")
    public String showEditTripForm(@PathVariable Long id, Model model) {
        Trip trip = tripRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến"));

        List<Long> savedCoDriverIds = trip.getCoDrivers().stream()
                .map(User::getId)
                .toList();

        model.addAttribute("trip", trip);
        model.addAttribute("savedCoDriverIds", savedCoDriverIds);
        model.addAttribute("routes", routeRepository.findAll());
        model.addAttribute("buses", busRepository.findAllWithBusType());
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

    /**
     * Cập nhật trip
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

            // Cập nhật các trường
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
            existingTrip.setStatus(trip.getStatus());

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
                        existingTrip.getCoDrivers().add(cd.getUser());
                    }
                }
            }

            // Cập nhật thông qua Service để kích hoạt Validate
            String warning = tripService.updateManualTrip(existingTrip);

            redirectAttributes.addFlashAttribute("success", "Cập nhật chuyến xe thành công!");
            if (warning != null) {
                redirectAttributes.addFlashAttribute("warning", warning);
            }
            return "redirect:/admin/trip-management/trips";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/trip-management/trips/edit/" + trip.getId();
        }
    }

    /**
     * Hủy trip
     */
    @PostMapping("/trips/cancel/{id}")
    public String cancelTrip(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Trip trip = tripRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến"));

            trip.setStatus(TripStatus.CANCELLED);
            tripRepository.save(trip);

            redirectAttributes.addFlashAttribute("success", "Hủy chuyến thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/trip-management/trips";
    }

    /**
     * Xóa trip (chỉ nên xóa nếu chưa có vé bán)
     */
    @PostMapping("/trips/delete/{id}")
    public String deleteTrip(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Trip trip = tripRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến"));

            // Kiểm tra đã bán vé chưa
            if (trip.getTicketsSold() > 0) {
                redirectAttributes.addFlashAttribute("error",
                        "Không thể xóa chuyến đã có khách đặt vé! Hãy hủy chuyến thay vì xóa.");
                return "redirect:/admin/trip-management/trips";
            }

            tripRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa chuyến thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/trip-management/trips";
    }
}