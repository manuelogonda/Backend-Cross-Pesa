package com.manuelorg.cross_pesa.config;

import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.auth.service.OAuth2LoginCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuth2LoginCodeService oauth2LoginCodeService;

    /** Frontend base URL the browser is redirected to after OAuth2 login. */
    @Value("${app.oauth2.redirect-base-url:http://localhost:5173}")
    private String redirectBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not synced correctly after OAuth authentication"));

        String code = oauth2LoginCodeService.issueCode(user);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectBaseUrl)
                .path("/oauth2/redirect")
                .queryParam("code", code)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
