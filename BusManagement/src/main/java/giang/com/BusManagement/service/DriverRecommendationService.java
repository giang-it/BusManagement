package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.dto.DriverRecommendationDto;
import giang.com.BusManagement.dto.DriverRecommendationViewDto;
import giang.com.BusManagement.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Decision Support — trả lời câu hỏi "ngày này ai còn nhận thêm chuyến được?".
 *
 * KHÔNG phải analytics và KHÔNG phải monitoring: Dashboard đã có bảng "Tài xế
 * có tải cao nhất hôm nay" (DashboardService.buildDriverStats() →
 * topLoadedDrivers) trả lời "ai đang làm nhiều nhất". Service này cố ý làm
 * đúng chiều ngược lại — ai còn dư năng lực — và trên MỘT NGÀY DO ADMIN CHỌN
 * chứ không chỉ thời điểm hiện tại. Không đọc, không sửa, không gói lại bảng
 * kia.
 *
 * Toàn bộ số giờ lái đều do TripService.getDrivingHoursForDate() tính. Service
 * này tuyệt đối không tự cộng giờ: quy tắc chia giờ cho tài xế phụ, quy ước phụ
 * xe tính 0 giờ và phần giờ nền mock totalDrivingHours24h đều nằm trong đó, và
 * chỉ được phép có một cách tính duy nhất trong hệ thống.
 *
 * Đây là ĐỀ XUẤT hỗ trợ quyết định, không phải phân công tự động: service chỉ
 * đọc dữ liệu và xếp hạng, không tạo/sửa chuyến và không gán ai vào đâu.
 */
@Service
@RequiredArgsConstructor
public class DriverRecommendationService {

    private final DriverRepository driverRepository;
    private final TripService tripService;

    /**
     * Hạn mức giờ lái tối đa một tài xế được nhận trong một ngày.
     *
     * Khai báo lại tại đây thay vì lấy từ TripService, theo đúng tiền lệ
     * DispatchController.UPCOMING_WINDOW_HOURS (nhắc lại ngưỡng 48h của
     * Dashboard kèm chú thích thay vì mở rộng visibility cho một con số).
     *
     * Lý do quan trọng hơn: trong TripService, số 8.0 xuất hiện 14 lần với BA ý
     * nghĩa khác nhau — hạn mức giờ/ngày (dòng 357, 833, 856, 1024, 1120), mức
     * trần quy đổi giờ cho MỘT chuyến (343, 502, 817, 1016, 1112), và ngưỡng
     * "chuyến dài" bắt buộc có phụ xe / thêm tài xế (215, 253, 751, 775). Gom
     * tất cả vào một hằng số chung sẽ SAI về nghiệp vụ dù giá trị giống nhau.
     * Hằng số dưới đây chỉ phản chiếu ý nghĩa THỨ NHẤT — đúng quy tắc mà
     * validateStaffForTrip() dùng khi chặn "vượt 8h/ngày".
     *
     * Nếu hạn mức ngày trong TripService đổi, phải đổi ở đây.
     */
    private static final double MAX_DAILY_DRIVING_HOURS = 8.0;

    /** "Còn nhiều giờ" = còn ít nhất một nửa hạn mức ngày. Chỉ dùng để chọn câu lý do. */
    private static final double LARGE_CAPACITY_HOURS = MAX_DAILY_DRIVING_HOURS / 2;

    /**
     * Xếp hạng tài xế còn năng lực nhận thêm chuyến trong ngày đã chọn.
     *
     * Bộ lọc dùng đúng các ràng buộc mà validateStaffForTrip() sẽ áp khi thực
     * sự gán người, nên danh sách này không bao giờ đề xuất một tài xế mà hệ
     * thống sẽ từ chối ngay sau đó:
     * - đang hoạt động (isActive) — validator chặn "đã bị khóa";
     * - bằng lái còn hạn (isLicenseValid) — validator chặn "bằng lái hết hạn";
     * - còn hạn mức giờ trong ngày — validator chặn "vượt 8h/ngày".
     *
     * @param date ngày cần xét (Admin chọn; không giới hạn quá khứ/tương lai)
     */
    @Transactional(readOnly = true)
    public DriverRecommendationViewDto recommendAvailableDrivers(LocalDate date) {
        // getDrivingHoursForDate() chỉ dùng phần NGÀY của tham số để khoanh vùng
        // truy vấn, nên đầu ngày là mốc trung lập và không làm lệch kết quả.
        LocalDateTime reference = date.atStartOfDay();

        List<Driver> activeDrivers = driverRepository.findAllWithUser().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .toList();

        long licenseBlockedCount = activeDrivers.stream()
                .filter(d -> !d.isLicenseValid())
                .count();

        List<DriverRecommendationDto> assignable = activeDrivers.stream()
                .filter(Driver::isLicenseValid)
                .map(d -> toDto(d, tripService.getDrivingHoursForDate(d, reference)))
                .toList();

        List<DriverRecommendationDto> withCapacity = assignable.stream()
                .filter(dto -> dto.getRemainingHours() > 0)
                .sorted(Comparator.comparingDouble(DriverRecommendationDto::getDrivingHours)
                        .thenComparing(DriverRecommendationDto::getFullName))
                .collect(Collectors.toList());

        // "Tải thấp nhất" chỉ có nghĩa khi so với chính danh sách được đề xuất.
        double lowestLoad = withCapacity.stream()
                .mapToDouble(DriverRecommendationDto::getDrivingHours)
                .min()
                .orElse(0.0);
        withCapacity.forEach(dto -> dto.setReason(buildReason(dto, lowestLoad)));

        return new DriverRecommendationViewDto(
                date,
                withCapacity,
                activeDrivers.size(),
                assignable.size() - withCapacity.size(),
                licenseBlockedCount);
    }

    private DriverRecommendationDto toDto(Driver driver, double drivingHours) {
        double remaining = MAX_DAILY_DRIVING_HOURS - drivingHours;
        return new DriverRecommendationDto(
                driver.getUserId(),
                driver.getUser() != null ? driver.getUser().getFullName() : "Tài xế #" + driver.getUserId(),
                drivingHours,
                remaining,
                driver.isLicenseValid(),
                driver.getLicenseExpiryDate(),
                driver.getExperienceYears(),
                drivingHours <= 0.0 ? "Trống cả ngày" : "Đã phân công một phần",
                null); // reason được điền sau, khi đã biết tải thấp nhất của cả danh sách
    }

    /**
     * Sinh câu giải thích vì sao tài xế được đề xuất. Chỉ diễn đạt lại dữ liệu
     * đã tính ở trên — không có ngưỡng nghiệp vụ mới nào được đặt ra ở đây.
     */
    private String buildReason(DriverRecommendationDto dto, double lowestLoad) {
        if (dto.getDrivingHours() <= 0.0) {
            return "Trống cả ngày — chưa có chuyến nào được phân công";
        }
        if (dto.getDrivingHours() <= lowestLoad) {
            return String.format("Tải thấp nhất trong ngày (đã phân công %.1fh)", dto.getDrivingHours());
        }
        if (dto.getRemainingHours() >= LARGE_CAPACITY_HOURS) {
            return String.format("Còn nhiều giờ lái khả dụng (%.1fh)", dto.getRemainingHours());
        }
        return String.format("Còn %.1fh trong hạn mức %.0fh/ngày",
                dto.getRemainingHours(), MAX_DAILY_DRIVING_HOURS);
    }
}
