package com.manuelorg.cross_pesa.auth.admin.service;

import com.manuelorg.cross_pesa.auth.admin.dto.AdminUserDto;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    /**
     * Fetch a paginated list of all users for the admin dashboard.
     */
    @Transactional(readOnly = true)
    public Page<AdminUserDto.AdminUserResponse> getAllUsers(Pageable pageable) {
        // This maps the Page<User> directly to a Page<AdminUserResponse>
        return userRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional
    public void updateUserStatus(UUID userId, AdminUserDto.UpdateStatusRequest request, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Don't let admins accidentally suspend themselves or other admins
        if (user.getRole().name().equals("ADMIN")) {
            throw new SecurityException("Cannot modify another Admin's status.");
        }

        user.setStatus(request.status());
        userRepository.save(user);

        log.info("Admin {} changed User {} status to {}. Reason: {}", adminEmail, userId, request.status(), request.reason());
        // Note: You could fire an Email Notification event here telling the user their account was suspended!
    }

    @Transactional
    public void updateUserKyc(UUID userId, AdminUserDto.UpdateKycRequest request, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setKycStatus(request.kycStatus());
        if (request.kycLevel() != null) {
            user.setKycLevel(request.kycLevel());
        }

        userRepository.save(user);
        log.info("Admin {} updated KYC for User {} to Status: {}, Level: {}. Notes: {}",
                adminEmail, userId, request.kycStatus(), request.kycLevel(), request.adminNotes());
    }

    private AdminUserDto.AdminUserResponse mapToResponse(User user) {
        return new AdminUserDto.AdminUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getIdType(),
                user.getIdNumber(),
                user.getStatus(),
                user.getKycStatus(),
                user.getKycLevel(),
                user.getCreatedAt() // Assuming your base entity has this
        );
    }
}
