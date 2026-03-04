package giang.com.BusManagement.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.TripRepository;

@Service
public class TripService {

    @Autowired
    private TripRepository tripRepository;

    @Scheduled(fixedRate = 60000) // 1 phút quét 1 lần cho thực tế
    public void autoDetectHighDemand() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusHours(24);

        // Tìm các chuyến ACTIVE trong 24h tới
        List<Trip> activeTrips = tripRepository.findByDepartureTimeBetweenAndStatus(now, threshold, TripStatus.ACTIVE);

        for (Trip trip : activeTrips) {
            // Sử dụng helper method trong Entity
            if (trip.getOccupancyRate() > 0.9) {
                // Kiểm tra xem đã có đề xuất nào cho chuyến này chưa (dựa trên originalTrip)
                if (!tripRepository.existsByOriginalTripAndStatus(trip, TripStatus.PENDING_APPROVAL)) {
                    suggestExtraTrip(trip);
                }
            }
        }
    }

    private void suggestExtraTrip(Trip originalTrip) {
        Trip extraTrip = new Trip();

        // Copy thông tin từ chuyến gốc
        extraTrip.setRoute(originalTrip.getRoute());
        extraTrip.setDepartureTime(originalTrip.getDepartureTime());
        extraTrip.setTotalSeats(originalTrip.getTotalSeats());
        extraTrip.setPrice(originalTrip.getPrice());

        extraTrip.setTicketsSold(0);
        extraTrip.setStatus(TripStatus.PENDING_APPROVAL);
        extraTrip.setExtraTrip(true);
        extraTrip.setOriginalTrip(originalTrip); // Gán cả object thay vì chỉ ID

        /*
         * LƯU Ý: Ở bước này AI chưa gán Bus và Driver.
         * Admin khi Approve sẽ phải chọn Xe và Tài xế còn trống (hoặc AI gợi ý dựa trên
         * lịch rảnh).
         */

        tripRepository.save(extraTrip);
        System.out.println("AI: Đề xuất tăng cường cho tuyến: " + originalTrip.getRoute().getDeparturePoint());
    }
}