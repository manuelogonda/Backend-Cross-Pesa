package com.manuelorg.cross_pesa.config;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.auth.service.OAuth2LoginCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2SuccessHandler Tests")
class OAuth2SuccessHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuth2LoginCodeService oauth2LoginCodeService;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oauth2User;

    private OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(userRepository, oauth2LoginCodeService);
        ReflectionTestUtils.setField(handler, "redirectBaseUrl", "https://frontend.example.com");
    }

    @Test
    void onAuthenticationSuccess_redirectsWithOneTimeCodeOnly() throws Exception {
        User user = User.builder()
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Tester")
                .build();

        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttribute("email")).thenReturn("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(oauth2LoginCodeService.issueCode(user)).thenReturn("code123");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://frontend.example.com/oauth2/redirect?code=code123");
        assertThat(response.getRedirectedUrl()).doesNotContain("#");
    }
}
