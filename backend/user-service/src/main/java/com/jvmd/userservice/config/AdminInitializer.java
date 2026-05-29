package com.jvmd.userservice.config;

import com.jvmd.userservice.model.User;
import com.jvmd.userservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    public void initAdmin() {
        if (userRepository.existsByUsername("jvmdevelop")) {
            return;
        }
        User admin = new User();
        admin.setUsername("jvmdevelop");
        admin.setEmail("admin@legis-entropy.local");
        admin.setPassword(passwordEncoder.encode("deltaq123"));
        admin.setFirstName("Admin");
        admin.setLastName("System");
        admin.setActive(true);
        admin.setRole("ROLE_ADMIN");
        admin.setPlanType("PRO");
        userRepository.save(admin);
        log.info("Admin user 'jvmdevelop' created.");
    }
}
