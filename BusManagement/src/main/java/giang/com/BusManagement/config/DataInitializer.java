package giang.com.BusManagement.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.repository.TripRepository;

import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private TripRepository tripRepository;

    @Override
    public void run(String... args) throws Exception {
        // Xóa dữ liệu cũ để mỗi lần restart không bị trùng (Tùy chọn)
        tripRepository.deleteAll();

        // Tạo 1 chuyến xe sắp đầy để test "AI"
        Trip t1 = new Trip();
        t1.setRoute("Hà Nội - Đà Nẵng");
        t1.setDepartureTime(LocalDateTime.now().plusHours(10));
        t1.setTotalSeats(40);
        t1.setTicketsSold(38); // 95% lấp đầy -> Thỏa mãn Rule AI
        t1.setPrice(500000);
        t1.setStatus("ACTIVE");
        tripRepository.save(t1);

        // Tạo 1 chuyến xe vắng khách để xem AI có bỏ qua không
        Trip t2 = new Trip();
        t2.setRoute("Sài Gòn - Vũng Tàu");
        t2.setDepartureTime(LocalDateTime.now().plusHours(12));
        t2.setTotalSeats(30);
        t2.setTicketsSold(5); // Chỉ có 5 vé -> AI sẽ không đề xuất tăng cường
        t2.setPrice(150000);
        t2.setStatus("ACTIVE");
        tripRepository.save(t2);

        Trip t3 = new Trip();
        t3.setRoute("BD-Vũng Tàu");
        t3.setDepartureTime(LocalDateTime.now().plusHours(15));
        t3.setTotalSeats(30);
        t3.setTicketsSold(29); // Chỉ có 5 vé -> AI sẽ không đề xuất tăng cường
        t3.setPrice(10000);
        t3.setStatus("ACTIVE");
        tripRepository.save(t3);

        Trip t4 = new Trip();
        t4.setRoute("HN- Noinh Thuan");
        t4.setDepartureTime(LocalDateTime.now().plusHours(12));
        t4.setTotalSeats(30);
        t4.setTicketsSold(28); // Chỉ có 5 vé -> AI sẽ không đề xuất tăng cường
        t4.setPrice(20000);
        t4.setStatus("ACTIVE");
        tripRepository.save(t4);

        System.out.println(">>>>> Đã khởi tạo dữ liệu mẫu thành công! <<<<<");
    }
}
