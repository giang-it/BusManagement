package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * PHASE 8 — toàn bộ nội dung màn hình "Mô Phỏng Kịch Bản (What-if)".
 *
 * Gói HAI cột kết quả (hiện trạng vs kịch bản) cùng phần "cách suy ra throughput"
 * để con số kiểm chứng được, độ tươi của dữ liệu forecast (giống các màn Pillar-2
 * khác — dữ liệu mô phỏng có mốc cố định, mỗi ngày một cũ hơn), và tham số chi
 * phí mặc định đang áp dụng để form điền sẵn.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfViewDto {

    /** false khi chưa có dữ liệu lịch sử để dựng forecast (chưa chạy backfill). */
    private boolean dataAvailable;

    /** Các cần gạt đã chuẩn hóa (kẹp biên, điền mặc định) — để form giữ trạng thái. */
    private WhatIfScenarioDto scenario;

    private WhatIfOutcomeDto baseline;
    private WhatIfOutcomeDto scenarioOutcome;

    // --- Cách suy ra throughput (hiển thị để kiểm chứng, không hardcode) ---
    private double tripsPerBusPerDay;
    private double operatingWindowHours;
    private double avgTripDurationHours;
    private double prepBufferHours;
    /** Sức chứa trung bình đội xe — dùng khi quy Δxe ra ghế và ước số khách. */
    private double avgFleetCapacity;
    private int horizonDays;

    // --- Độ tươi forecast (chuyển tiếp từ DemandForecastViewDto) ---
    private LocalDate historyEnd;
    private long historyStaleDays;
    private LocalDate forecastFrom;

    // --- Tham số chi phí đang cấu hình (mặc định khi không ghi đè) ---
    private BigDecimal defaultFuelCostPerKm;
    private BigDecimal defaultDriverWagePerHour;
}
