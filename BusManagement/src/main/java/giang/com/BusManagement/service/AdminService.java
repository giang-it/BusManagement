package giang.com.BusManagement.service;

import java.util.List;

import org.springframework.stereotype.Service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

// UserRepository.java & BusRepository.java (Tương tự như code cũ bạn đã có)

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final BusRepository busRepository;
    private final BusTypeRepository busTypeRepository;

    @Transactional
    public void createNewUser(User user) {
        // Thực tế nên mã hóa password tại đây:
        // user.setPassword(passwordEncoder.encode(...));
        userRepository.save(user);
    }

    @Transactional
    public void createNewBus(Bus bus) {
        busRepository.save(bus);
    }

    public List<BusType> getAllBusTypes() {
        return busTypeRepository.findAll();
    }
}