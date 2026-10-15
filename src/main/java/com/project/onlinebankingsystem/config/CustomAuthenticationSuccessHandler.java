package com.project.onlinebankingsystem.config;

import com.project.onlinebankingsystem.enums.Role;
import com.project.onlinebankingsystem.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public CustomAuthenticationSuccessHandler(@Lazy UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {
        String username = authentication.getName();
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        try {
            userService.recordLoginSuccess(username, ipAddress, userAgent);
        } catch (Exception ignored) {}

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> Role.ROLE_SUPER_ADMIN.name().equals(a.getAuthority()));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> Role.ROLE_ADMIN.name().equals(a.getAuthority()));

        if (isSuperAdmin) {
            redirectStrategy.sendRedirect(request, response, "/super-admin");
        } else if (isAdmin) {
            redirectStrategy.sendRedirect(request, response, "/admin");
        } else {
            redirectStrategy.sendRedirect(request, response, "/dashboard");
        }
    }
}
