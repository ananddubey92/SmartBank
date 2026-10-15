package com.project.onlinebankingsystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.onlinebankingsystem.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        boolean isApi = uri.startsWith("/api/") || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE));

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        boolean isAccountCreationReq = uri.contains("account/create") || uri.contains("accounts/create") || uri.contains("account/open") || uri.contains("accounts/open") || uri.contains("open-account") || uri.contains("create-account");
        boolean isSuperAdminReq = uri.contains("super-admin") || isAccountCreationReq;
        String requiredRole = isSuperAdminReq ? "ROLE_SUPER_ADMIN" : "ROLE_ADMIN";
        
        String accessDeniedMsg;
        if (isAccountCreationReq) {
            accessDeniedMsg = "Access Denied: Only Super Admin can open a new bank account.";
        } else if (isSuperAdminReq) {
            accessDeniedMsg = "Access Denied: You do not have permission to access Super Admin resources. ROLE_SUPER_ADMIN required.";
        } else {
            accessDeniedMsg = "Access Denied: You do not have permission to access this admin resource. ROLE_ADMIN required.";
        }

        if (isApi) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ErrorResponse error = new ErrorResponse(
                    LocalDateTime.now(),
                    HttpStatus.FORBIDDEN.value(),
                    "Forbidden",
                    accessDeniedMsg,
                    uri
            );
            response.getWriter().write(objectMapper.writeValueAsString(error));
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, accessDeniedMsg);
        }
    }
}
