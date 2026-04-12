package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.*;
import giang.com.BusManagement.repository.*;
import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;

    /**
     * AI Logic: Quét các chuyến xe có tỉ lệ lấp đầy > 90%
     * Chạy tự động mỗi 10 giây để kiểm tra
     */
    @Scheduled(fixedRate = 10000) // 10.000ms = 10 giây
    @Transactional
    public void scanAndSuggestExtraTrips() {
        // 1. Tìm các chuyến ACTIVE sắp khởi hành (trong vòng 24h tới)
        List<Trip> activeTrips = tripRepository.findByStatus(TripStatus.ACTIVE);

        for (Trip trip : activeTrips) {
            // 2. Kiểm tra tỉ lệ lấp đầy (> 90%) và chưa có chuyến tăng cường nào được tạo
            // cho nó
            if (trip.getOccupancyRate() > 0.9 && !hasAlreadySuggested(trip)) {

                System.out.println("🤖 AI Detection: Chuyến xe ID " + trip.getId() + " đạt " +
                        (trip.getOccupancyRate() * 100) + "%. Đang tạo đề xuất...");

                Trip extraTrip = new Trip();
                extraTrip.setRoute(trip.getRoute());
                extraTrip.setDepartureTime(trip.getDepartureTime().plusMinutes(30)); // Chạy sau 30p
                extraTrip.setStatus(TripStatus.PENDING_APPROVAL); // Trạng thái chờ Admin duyệt
                extraTrip.setOriginalTrip(trip); // Liên kết với chuyến gốc
                extraTrip.setTotalSeats(trip.getTotalSeats());
                extraTrip.setPrice(trip.getPrice());
                extraTrip.setExtraTrip(true);

                tripRepository.save(extraTrip);
            }
        }
    }

    private boolean hasAlreadySuggested(Trip originalTrip) {
        // Kiểm tra xem đã có chuyến nào PENDING hoặc ACTIVE mà trỏ tới originalTrip này
        // chưa

        return tripRepository.existsByOriginalTripAndStatus(originalTrip, TripStatus.PENDING_APPROVAL) ||
                tripRepository.existsByOriginalTripAndStatus(originalTrip, TripStatus.ACTIVE);
    }

    /**
     * Lấy danh sách xe SẴN SÀNG cho một chuyến xe cụ thể
     */
    public List<Bus> getAvailableBusesForTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        // 1. Lấy loại xe yêu cầu từ Route
        BusType requiredType = trip.getRoute().getSuitableBusType();

        // 2. Truy vấn tìm xe: Sẵn sàng + Đúng loại
        if (requiredType != null) {
            return busRepository.findByStatusAndBusType(BusStatus.READY, requiredType);
        } else {
            // Nếu tuyến đường không quy định loại xe, lấy tất cả xe đang READY
            return busRepository.findByStatus(BusStatus.READY);
        }
    }

    /**
     * Lấy danh sách tài xế HỢP LỆ (Không quá 8h lái/24h)
     */
    public List<Driver> getAvailableDriversForTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        LocalDateTime startTime = trip.getDepartureTime();

        System.out.println("get available driver: " + driverRepository.findAll());

        return driverRepository.findAll().stream()
                .filter(driver -> isDriverEligible(driver, startTime))
                .collect(Collectors.toList());
    }

    /**
     * Logic kiểm tra ràng buộc 8 tiếng lái xe
     */
    private boolean isDriverEligible(Driver driver, LocalDateTime newTripStart) {

        System.out.println("Checking driver: " + driver.getUser().getFullName());

        // 1. Kiểm tra tài xế có đang bận chuyến nào khác trùng giờ không
        boolean isBusy = tripRepository.existsByDriverAndStatusInAndDepartureTimeBetween(
                driver,
                List.of(TripStatus.ACTIVE, TripStatus.DEPARTED),
                newTripStart.minusHours(5), // Ước tính thời gian chuyến đi
                newTripStart.plusHours(5));

        System.out.println("isBusy: " + isBusy);

        if (isBusy)
            return false;

        // 2. Kiểm tra tổng giờ lái trong 24h qua (Requirement I.2.b)
        // Lưu ý: Trong thực tế bạn sẽ query DriverLog để SUM(duration)
        return driver.getTotalDrivingHours24h() < 8.0;
    }

    /**
     * Admin phê duyệt chuyến xe: Gán Xe + Tài xế và kích hoạt
     */
    @Transactional
    public void approveTrip(Long tripId, Long busId, Long driverId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến xe"));

        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy xe"));

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế"));

        // Cập nhật thông tin chuyến xe
        trip.setBus(bus);
        trip.setDriver(driver);
        trip.setStatus(TripStatus.ACTIVE);

        // Cập nhật trạng thái xe sang TRAVELING (hoặc giữ READY nếu chưa đến giờ chạy)
        // bus.setStatus(BusStatus.TRAVELING);

        tripRepository.save(trip);
        System.out.println("Admin: Đã phê duyệt chuyến xe " + tripId + " với xe " + bus.getLicensePlate());
    }

    /**
     * Lấy tất cả các chuyến xe đang chờ phê duyệt (do AI đề xuất)
     * Thường dùng để hiển thị danh sách cho Admin.
     */
    public List<Trip> getPendingTrips() {
        // Trả về danh sách các chuyến có status là PENDING_APPROVAL
        return tripRepository.findByStatus(TripStatus.PENDING_APPROVAL);
    }

    /**
     * Lấy thông tin chi tiết của một chuyến xe theo ID
     * 
     * @throws RuntimeException nếu không tìm thấy ID
     */
    public Trip getTripById(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến xe với ID: " + id));
    }
}