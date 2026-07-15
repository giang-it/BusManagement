package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload dùng chung cho mọi biểu đồ Chart.js (Bar/Line/Doughnut) trên Dashboard.
 * Tránh mỗi tab tự định nghĩa một shape JSON riêng cho chart.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartSeriesDto {
    private List<String> labels;
    private List<Number> values;
}
