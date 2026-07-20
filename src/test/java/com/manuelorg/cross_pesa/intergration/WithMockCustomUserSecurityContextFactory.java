package com.manuelorg.cross_pesa.intergration;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContext;

import java.util.Collections;

@Component
public class WithMockCustomUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockCustomUser> {

    @Autowired
    private UserRepository userRepository;

    @Override
    public SecurityContext createSecurityContext(WithMockCustomUser customUser) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        // Fetch the REAL user we saved in the @BeforeEach setup!
        User realAfriPayUser = userRepository.findByEmail(customUser.email())
                .orElseThrow(() -> new RuntimeException("Test user not found in DB"));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(realAfriPayUser, "password", Collections.emptyList());

        context.setAuthentication(auth);
        return context;
    }
}
