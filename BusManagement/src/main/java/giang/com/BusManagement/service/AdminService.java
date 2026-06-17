package giang.com.BusManagement.service;

import org.springframework.stereotype.Service;

import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;

    @Transactional
    public void createNewUser(User user) {
        // Thực tế nên mã hóa password tại đây:
        // user.setPassword(passwordEncoder.encode(...));
        userRepository.save(user);
    }
}