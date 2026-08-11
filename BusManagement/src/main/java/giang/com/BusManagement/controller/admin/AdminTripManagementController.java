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
        model.addAttribute("routes", routeRepository.findAllWithStations());
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
    /**
     * Chính sách SỬA chuyến theo trạng thái — soi gương chính sách XOÁ đã viết
     * thành văn ở TripService.deleteTrip():1485-1507. Xem lỗi #18.
     *
     * Trước bản sửa này, updateTrip() không xét trạng thái ở bất kỳ đâu, nên
     * deleteTrip cấm XOÁ một chuyến COMPLETED (thao tác ồn ào, có xác nhận) trong
     * khi SỬA MỌI TRƯỜNG của chính chuyến đó (thao tác im lặng, không xác nhận,
     * không log) thì trót lọt và báo thành công. Cấm cái ồn ào, thả cái im lặng.
     *
     * Luật phát biểu bằng MỘT câu: chuyến sửa được cho tới khi xuất phát; sau khi
     * xuất phát nó là BẢN GHI, không còn là kế hoạch. Đó đúng là tập trạng thái
     * mà deleteTrip nêu tên, nên hai lối vào nay khớp nhau từng trạng thái một.
     *
     * DEPARTED cũng là thứ giữ bất biến ghép đôi của FSM (trip_lifecycle_fsm.md
     * §8.1): xe được đặt TRAVELING lúc vào DEPARTED phải chính là xe được trả
     * READY lúc vào COMPLETED, mà FSM không nhớ nó đã đánh dấu xe nào — nó đọc
     * lại trip.getBus() ở cả hai đầu. Khoá cả chuyến là điều kiện MẠNH HƠN "khoá
     * mỗi ô xe" của bản sửa #14, nên #14 được gộp vào đây thay vì giữ song song;
     * nó còn đóng thêm kẽ mà #14 không với tới: đổi TUYẾN của chuyến đang chạy
     * làm đổi luôn số km cộng vào odometer lúc hoàn thành (TripService:621-627).
     *
     * KHÔNG chặn CANCELLED: deleteTrip cũng cho xoá CANCELLED ("đã kết thúc vòng
     * đời"), nên code khớp chính sách từng dòng chứ không khớp đại khái.
     *
     * Việc này KHÔNG lấy mất năng lực nào, đã kiểm trước khi sửa:
     * - Hoàn thành một chuyến DEPARTED vẫn làm ở Bảng Điều Hành, một endpoint
     *   khác hẳn (POST /admin/dispatch/status). Query của bảng
     *   (findDispatchBoardTrips) KHÔNG có cận dưới thời gian, nên mọi chuyến
     *   DEPARTED dù cũ tới đâu vẫn hiện — đo ngày 2026-08-11: đủ 5/5, 0 chuyến
     *   bị bỏ sót.
     * - "Ghi nhận xe chạy trễ" vốn KHÔNG làm được: Trip chỉ có departureTime và
     *   arrivalTimeExpected, không có trường giờ đến thực tế. Sửa
     *   arrivalTimeExpected của chuyến đang chạy là sửa lại KẾ HOẠCH cho khớp
     *   thực tế, tức đúng thứ mục #18 đang cấm.
     *
     * @return câu từ chối, hoặc null nếu chuyến còn sửa được
     */
    private String editRefusalReason(Trip trip) {
        return switch (trip.getStatus()) {
            case DEPARTED -> "Chuyến #" + trip.getId() + " đang trên đường (DEPARTED). "
                    + "Không thể sửa chuyến đang vận hành — thông tin chuyến phải khớp với hành trình thực tế. "
                    + "Dùng Bảng Điều Hành để đánh dấu hoàn thành.";

            case COMPLETED -> "Chuyến #" + trip.getId() + " đã hoàn thành (COMPLETED). "
                    + "Dữ liệu lịch sử và báo cáo tài chính phải được giữ nguyên, không thể sửa.";

            // PENDING_APPROVAL, ACTIVE, CANCELLED: chuyến chưa lăn bánh hoặc đã bị
            // huỷ — sửa được. Switch cố ý KHÔNG có default: thêm một TripStatus mới
            // sẽ làm vỡ biên dịch, buộc người thêm phải quyết định, y như deleteTrip.
            case PENDING_APPROVAL, ACTIVE, CANCELLED -> null;
        };
    }

    @GetMapping("/trips/edit/{id}")
    public String showEditTripForm(@PathVariable Long id, Model model,
            RedirectAttributes redirectAttributes) {
        Trip trip = tripRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến"));

        // Không MỞ form cho chuyến mà mọi submit đều bị từ chối. Nút Sửa ở
        // trip-list.html đã ẩn cho hai trạng thái này; đây là chặn cho URL gõ tay.
        String refusal = editRefusalReason(trip);
        if (refusal != null) {
            redirectAttributes.addFlashAttribute("error", refusal);
            return "redirect:/admin/trip-management/trips";
        }

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
        model.addAttribute("routes", routeRepository.findAllWithStations());
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
     * xuống DB, sau đó updateTripStatus() re-fetch trip từ DB và apply FSM
     * transition trên trạng thái hiện tại.
     *
     * ⚠️ ĐỒNG BỘ BusStatus CHỈ CHẠY KHI TRẠNG THÁI ĐỔI. Khối ở cuối method gọi
     * updateTripStatus() dưới điều kiện {@code status != newStatus}, nên sửa một
     * chuyến mà KHÔNG đổi trạng thái sẽ không chạy đồng bộ BusStatus nào cả —
     * updateManualTrip() chỉ validate và save(), nó không đụng Bus.status.
     *
     * Câu trước đây ở chỗ này khẳng định ngược lại ("đảm bảo BusStatus
     * synchronization cũng chạy đúng trên bus mới nếu bus bị thay") — mô tả một
     * hành vi mà điều kiện bên dưới không cho xảy ra, và chính câu sai đó đã che
     * lỗi #14 khỏi các lần rà trước.
     *
     * Hệ quả của việc đồng bộ chỉ chạy khi trạng thái đổi nay đã VÔ HẠI, vì
     * editRefusalReason() chặn mọi thao tác sửa trên chuyến DEPARTED/COMPLETED —
     * tức trip.bus không thể đổi trong khoảng giữa hai đầu của cặp bất biến FSM.
     * Trạng thái chỉ còn đi tiếp qua Bảng Điều Hành (POST /admin/dispatch/status),
     * nơi duy nhất còn phơi ra transition, đúng như bản sửa #10 đã siết.
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

            // RÀNG BUỘC: chuyến đã XUẤT PHÁT hoặc đã HOÀN THÀNH thì không sửa được
            // trường nào. Luật và toàn bộ lý do nằm ở editRefusalReason() — cùng một
            // chỗ với lối GET, để hai lối vào không thể trôi ra khác nhau.
            //
            // Đây là lớp CHẶN THẬT. Nút Sửa ẩn ở trip-list.html và form GET bị từ
            // chối chỉ là lớp KHÔNG-MỜI: chúng không cản được một POST tự chế, còn
            // dòng này thì có. Cùng phân vai với allow-list BOARD_ACTIONS của bản
            // sửa #10 — TripService không bị đụng tới.
            String refusal = editRefusalReason(existingTrip);
            if (refusal != null) {
                redirectAttributes.addFlashAttribute("error", refusal);
                return "redirect:/admin/trip-management/trips";
            }

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