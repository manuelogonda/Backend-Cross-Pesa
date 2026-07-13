package com.manuelorg.cross_pesa.auth.repository;

import com.manuelorg.cross_pesa.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Crucial for the Local Login flow to find the user by their email
    Optional<User> findByEmail(String email);

    // Crucial for validating unique constraints during registration
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
}