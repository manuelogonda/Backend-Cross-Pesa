package com.manuelorg.cross_pesa.core.security;


import com.manuelorg.cross_pesa.features.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Fetch the user from the database or crash safely with an execution block
        com.manuelorg.cross_pesa.features.user.User domainUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // 2. Safely transform your domain user role into a GrantedAuthority Spring understands
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + domainUser.getRole().name());

        // 3. Return Spring's native UserDetails implementation
        return new org.springframework.security.core.userdetails.User(
                domainUser.getEmail(),
                domainUser.getPasswordHash(),
                domainUser.getStatus().name().equals("ACTIVE"),
                true,
                true,
                !domainUser.getStatus().name().equals("LOCKED"),
                Collections.singletonList(authority)
        );
    }
}
