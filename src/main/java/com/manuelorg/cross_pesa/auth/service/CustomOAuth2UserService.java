package com.manuelorg.cross_pesa.auth.service;

import com.manuelorg.cross_pesa.auth.dto.GoogleUserResponse;
import com.manuelorg.cross_pesa.auth.entity.AuthProvider;
import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.Role;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        GoogleUserResponse googleUser = new GoogleUserResponse(oAuth2User.getAttributes());

        // Sync or register user in PostgreSQL database
        userRepository.findByEmail(googleUser.getEmail())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .firstName(googleUser.getFirstName())
                                .lastName(googleUser.getLastName())
                                .email(googleUser.getEmail())
                                .role(Role.USER)
                                .authProvider(AuthProvider.GOOGLE)
                                .kycStatus(KycStatus.PENDING)
                                .build()
                ));

        return oAuth2User;
    }
}
