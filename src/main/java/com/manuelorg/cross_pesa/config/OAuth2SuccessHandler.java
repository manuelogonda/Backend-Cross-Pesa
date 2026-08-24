package com.manuelorg.cross_pesa.config;

import com.manuelorg.cross_pesa.auth.repository.UserRepository;
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

    private final JwtService jwtService;
    private final UserRepository userRepository;

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

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Tokens go in the URL FRAGMENT (#), not the query string (?):
        // fragments are never sent to servers, so they stay out of access
        // logs and proxy logs.
        String targetUrl = UriComponentsBuilder.fromUriString(redirectBaseUrl)
                .path("/oauth2/redirect")
                .fragment("token=" + accessToken + "&refresh_token=" + refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
