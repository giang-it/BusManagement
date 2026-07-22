package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.IncidentType;
import giang.com.BusManagement.dto.VehicleReplacementDto;
import giang.com.BusManagement.dto.VehicleReplacementViewDto;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PHASE 7 (bước 1) — Đề xuất thay thế phương tiện (MVP).
 *
 * Trả lời câu hỏi: <b>"chiếc nào trong đội đáng được cân nhắc thay trước?"</b>
 *
 * ====================================================================
 * VÌ SAO KHÔNG CHẤM ĐIỂM TRÊN kmSinceLastMaintenance
 * ====================================================================
 * Đây là điểm dễ làm sai nhất của module này. `Bus.kmSinceLastMaintenance` là
 * hiệu `odometer - lastMaintenanceOdometer`, nên nó <b>reset về gần 0 sau mỗi
 * lần bảo dưỡng</b>: một chiếc chạy 800.000 km đã bảo dưỡng 50 lần vẫn đọc ra
 * một con số nhỏ. Nó trả lời "xe có tới hạn bảo dưỡng chưa?", KHÔNG phải "xe đã
 * cũ chưa?".
 *
 * Quan trọng hơn: câu hỏi thứ nhất <b>đã được trả lời rồi</b> — Dashboard có sẵn
 * dải "Cảnh báo bảo dưỡng" (`DashboardService.buildMaintenanceAlerts()`) xếp
 * hạng đúng theo `kmSinceLastMaintenance`. Chấm điểm thay xe trên cùng trường đó
 * sẽ là giao lại một tính năng đã có dưới một cái tên mới.
 *
 * Vì vậy màn hình này dùng `odometer` — con số <b>không bao giờ reset</b>, đại
 * diện trung thực cho hao mòn trọn đời — và cố ý KHÔNG hiển thị lại cột
 * kmSinceLastMaintenance, để hai câu hỏi không bị trộn vào nhau trong đầu người
 * đọc.
 *
 * ====================================================================
 * CHỈ ĐẾM SỰ CỐ NÓI LÊN TÌNH TRẠNG CHIẾC XE
 * ====================================================================
 * `IncidentType` có 5 giá trị, nhưng chỉ 2 trong số đó nói về chiếc xe:
 * VEHICLE_BREAKDOWN (hỏng hóc kỹ thuật) và ACCIDENT (tai nạn — gây hư hại thực
 * thể lên xe). ROAD_ISSUE là kẹt xe/ngập/cấm đường và STAFF_ISSUE là tài xế ốm:
 * cả hai <b>không nói gì</b> về độ tin cậy của phương tiện. Đếm cả chúng nghĩa
 * là một chiếc xe tốt bị trừ điểm chỉ vì hôm đó đường tắc.
 *
 * Tại thời điểm viết, toàn bộ 8 sự cố trong dữ liệu tình cờ đều thuộc 2 loại
 * được đếm — nên bộ lọc này <b>chưa thay đổi kết quả nào</b>. Nó vẫn phải có
 * mặt: nó là quy tắc đúng, và nó bảo vệ điểm số kể từ bản ghi ROAD_ISSUE đầu
 * tiên trở đi.
 *
 * ====================================================================
 * ĐIỂM SỐ LÀ TƯƠNG ĐỐI, KHÔNG PHẢI PHÁN QUYẾT
 * ====================================================================
 * Hai thành phần đều được chuẩn hóa theo min/max của <b>chính đội xe hiện tại</b>,
 * nên chiếc đứng đầu luôn đạt điểm cao nhất kể cả khi cả đội đều còn mới. Đây là
 * lựa chọn có chủ đích: hệ thống không có dữ liệu nào để dựng một ngưỡng tuyệt
 * đối ("trên 500.000 km thì phải thay"), và bịa ra một con số như vậy là bịa ra
 * quy tắc nghiệp vụ. Màn hình vì thế trình bày kết quả như một THỨ TỰ ƯU TIÊN
 * XEM XÉT kèm số liệu thô, không phải một mệnh lệnh thay xe.
 */
@Service
@RequiredArgsConstructor
public class VehicleReplacementService {

    private final BusRepository busRepository;
    private final IncidentRepository incidentRepository;

    /**
     * Những loại sự cố được tính là tín hiệu về tình trạng xe.
     *
     * Danh sách này sống ở tầng service chứ không nằm trong câu truy vấn, vì nó
     * là một quyết định nghiệp vụ ("loại nào nói lên tình trạng chiếc xe"), không
     * phải chi tiết truy cập dữ liệu.
     */
    static final List<IncidentType> VEHICLE_RELATED_TYPES =
            List.of(IncidentType.VEHICLE_BREAKDOWN, IncidentType.ACCIDENT);

    /**
     * Trọng số hao mòn (số km trọn đời) so với độ tin cậy (số sự cố).
     *
     * Nghiêng về km không phải vì km quan trọng hơn về bản chất, mà vì <b>chất
     * lượng tín hiệu</b>: odometer là số đo liên tục, có mặt trên mọi xe và phân
     * biệt được từng chiếc; số sự cố thì rời rạc, thưa, và hiện phần lớn đội xe
     * đang hòa nhau ở mức 0. Trọng số phản ánh mức độ tin được của từng tín hiệu.
     * Nếu về sau dữ liệu sự cố dày lên, đây là con số đầu tiên nên xem lại.
     */
    static final double WEAR_WEIGHT = 0.7;
    static final double RELIABILITY_WEIGHT = 0.3;

    /** "Còn nhiều km hơn phần lớn đội" — chỉ dùng để chọn câu lý do, không phải ngưỡng nghiệp vụ. */
    private static final double HIGH_WEAR_SCORE = 0.66;

    @Transactional(readOnly = true)
    public VehicleReplacementViewDto rankFleet() {
        // findAllWithBusType() chứ không phải findAll(): busType là LAZY, và
        // template hiển thị tên loại xe — gọi findAll() sẽ ném
        // LazyInitializationException hoặc sinh N+1 query.
        List<Bus> buses = busRepository.findAllWithBusType();
        if (buses.isEmpty()) {
            return new VehicleReplacementViewDto(List.of(), 0, 0.0, 0.0, 0, 0, false,
                    percent(WEAR_WEIGHT), percent(RELIABILITY_WEIGHT));
        }

        Map<Long, Map<IncidentType, Integer>> incidentsByBus = loadVehicleIncidentCounts();

        double minOdometer = buses.stream().mapToDouble(this::odometerOf).min().orElse(0.0);
        double maxOdometer = buses.stream().mapToDouble(this::odometerOf).max().orElse(0.0);
        int maxIncidents = buses.stream().mapToInt(b -> totalIncidents(incidentsByBus, b.getId())).max().orElse(0);

        List<VehicleReplacementDto> ranking = new ArrayList<>();
        for (Bus bus : buses) {
            ranking.add(toDto(bus, incidentsByBus, minOdometer, maxOdometer, maxIncidents));
        }

        ranking.sort(Comparator.comparingDouble(VehicleReplacementDto::getReplacementScore).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (VehicleReplacementDto d) -> d.getOdometer() != null ? d.getOdometer() : 0.0).reversed())
                .thenComparing(VehicleReplacementDto::getLicensePlate,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        ranking.forEach(dto -> dto.setReason(buildReason(dto)));

        int totalIncidents = ranking.stream().mapToInt(VehicleReplacementDto::getVehicleIncidentCount).sum();
        int withoutIncidents = (int) ranking.stream()
                .filter(d -> d.getVehicleIncidentCount() == 0)
                .count();

        return new VehicleReplacementViewDto(
                ranking,
                buses.size(),
                minOdometer,
                maxOdometer,
                totalIncidents,
                withoutIncidents,
                // Tín hiệu coi là quá mỏng khi PHẦN LỚN đội xe chưa có sự cố nào:
                // lúc đó nửa "độ tin cậy" không phân biệt được đa số với nhau và
                // thứ tự thực chất do số km quyết định.
                withoutIncidents * 2 >= buses.size(),
                percent(WEAR_WEIGHT),
                percent(RELIABILITY_WEIGHT));
    }

    /** (busId -> (loại sự cố -> số lượng)), chỉ gồm các loại liên quan tới xe. */
    private Map<Long, Map<IncidentType, Integer>> loadVehicleIncidentCounts() {
        Map<Long, Map<IncidentType, Integer>> result = new HashMap<>();
        for (Object[] row : incidentRepository.countIncidentsPerBusAndType(VEHICLE_RELATED_TYPES)) {
            if (row[0] == null || row[1] == null || row[2] == null) {
                continue;
            }
            Long busId = ((Number) row[0]).longValue();
            IncidentType type = (IncidentType) row[1];
            int count = ((Number) row[2]).intValue();
            result.computeIfAbsent(busId, k -> new EnumMap<>(IncidentType.class)).put(type, count);
        }
        return result;
    }

    private VehicleReplacementDto toDto(Bus bus, Map<Long, Map<IncidentType, Integer>> incidentsByBus,
            double minOdometer, double maxOdometer, int maxIncidents) {

        Map<IncidentType, Integer> byType = incidentsByBus.getOrDefault(bus.getId(), Map.of());
        int breakdowns = byType.getOrDefault(IncidentType.VEHICLE_BREAKDOWN, 0);
        int accidents = byType.getOrDefault(IncidentType.ACCIDENT, 0);
        int incidents = breakdowns + accidents;

        // Chuẩn hóa theo min/max của đội xe. Khi cả đội bằng nhau (hoặc chỉ có 1
        // xe) thì mẫu số bằng 0 — trả 0 thay vì chia cho 0; lúc đó thành phần này
        // không phân biệt được ai với ai, và đúng ra là nó không nên phân biệt.
        double span = maxOdometer - minOdometer;
        double wear = span > 0 ? (odometerOf(bus) - minOdometer) / span : 0.0;
        double reliability = maxIncidents > 0 ? (double) incidents / maxIncidents : 0.0;

        double score = (WEAR_WEIGHT * wear + RELIABILITY_WEIGHT * reliability) * 100;

        return new VehicleReplacementDto(
                bus.getId(),
                bus.getLicensePlate(),
                bus.getBrand(),
                bus.getBusType() != null ? bus.getBusType().getTypeName() : null,
                statusLabel(bus),
                bus.getOdometer(),
                incidents,
                breakdowns,
                accidents,
                wear,
                reliability,
                score,
                null); // reason điền sau khi đã xếp hạng xong
    }

    /**
     * Câu giải thích vì sao xe nằm ở vị trí đó. Chỉ diễn đạt lại các con số đã
     * tính ở trên — không có ngưỡng nghiệp vụ mới nào được đặt ra ở đây.
     */
    private String buildReason(VehicleReplacementDto dto) {
        double wearPart = WEAR_WEIGHT * dto.getWearScore();
        double reliabilityPart = RELIABILITY_WEIGHT * dto.getReliabilityScore();

        if (wearPart <= 0 && reliabilityPart <= 0) {
            return "Km thấp nhất đội xe và chưa ghi nhận sự cố nào";
        }
        if (reliabilityPart > wearPart) {
            return String.format("Sự cố lặp lại (%d lần) là yếu tố chính", dto.getVehicleIncidentCount());
        }
        if (dto.getVehicleIncidentCount() > 0) {
            return String.format("Km trọn đời cao, kèm %d sự cố xe", dto.getVehicleIncidentCount());
        }
        if (dto.getWearScore() >= HIGH_WEAR_SCORE) {
            return "Km trọn đời thuộc nhóm cao nhất đội xe, chưa có sự cố";
        }
        return "Chủ yếu do km trọn đời, chưa có sự cố";
    }

    private double odometerOf(Bus bus) {
        // Xe thiếu odometer được coi như 0 km khi chấm điểm (không có thông tin
        // hao mòn thì không có cơ sở để xếp nó lên cao). DTO vẫn giữ giá trị gốc
        // là null để giao diện hiển thị "—" thay vì con số 0 gây hiểu nhầm.
        return bus.getOdometer() != null ? bus.getOdometer() : 0.0;
    }

    private String statusLabel(Bus bus) {
        if (bus.getStatus() == null) {
            return "—";
        }
        return switch (bus.getStatus()) {
            case READY -> "Sẵn sàng";
            case TRAVELING -> "Đang chạy";
            case REPAIRING -> "Đang sửa chữa";
        };
    }

    private int totalIncidents(Map<Long, Map<IncidentType, Integer>> incidentsByBus, Long busId) {
        return incidentsByBus.getOrDefault(busId, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }

    private int percent(double weight) {
        return (int) Math.round(weight * 100);
    }
}
