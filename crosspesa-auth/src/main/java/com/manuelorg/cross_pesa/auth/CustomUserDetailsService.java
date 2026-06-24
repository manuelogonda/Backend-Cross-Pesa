package com.manuelorg.cross_pesa.auth;

import com.manuelorg.cross_pesa.user.User;
import com.manuelorg.cross_pesa.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // 🟢 No import needed! UserRepository lives in this exact same package folder.
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
        // 🟢 Cleaned: Changed from com.manuelorg.cross_pesa.user.User to just User
        User domainUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // 2. Safely transform your domain user role into a GrantedAuthority Spring understands
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + domainUser.getRole().name());

        // 3. Return Spring's native UserDetails implementation
        return new org.springframework.security.core.userdetails.User(
                domainUser.getEmail(),
                domainUser.getPasswordHash(),
                domainUser.getStatus().name().equals("ACTIVE"), // enabled
                true, // accountNonExpired
                true, // credentialsNonExpired
                !domainUser.getStatus().name().equals("LOCKED"), // accountNonLocked
                Collections.singletonList(authority)
        );
    }
}
