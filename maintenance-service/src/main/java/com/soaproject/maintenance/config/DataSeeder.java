package com.soaproject.maintenance.config;

import com.soaproject.maintenance.entity.Role;
import com.soaproject.maintenance.entity.UserEntity;
import com.soaproject.maintenance.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    ApplicationRunner seedAdminUser(@Value("${app.seed.admin.username}") String username,
                                    @Value("${app.seed.admin.password}") String password,
                                    UserRepository userRepository,
                                    PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByUsernameIgnoreCase(username)) {
                log.info("Admin user '{}' already exists — skipping seed", username);
                return;
            }
            UserEntity user = new UserEntity();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(Role.ADMIN);
            user.setEnabled(true);
            userRepository.save(user);
            log.info("Seeded admin user '{}'", username);
        };
    }
}