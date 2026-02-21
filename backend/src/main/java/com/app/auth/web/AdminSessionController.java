package com.app.auth.web;

import com.app.auth.security.AdminApiInterceptor;
import com.app.auth.service.AdminSessionService;
import com.app.auth.service.AdminSessionService.AdminSession;
import com.app.auth.web.dto.AdminLoginRequest;
import com.app.auth.web.dto.AdminSessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/session")
public class AdminSessionController {

    private final AdminSessionService adminSessionService;

    public AdminSessionController(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminSessionResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminSession session = adminSessionService.login(request.username(), request.password())
            .orElseThrow(() -> new IllegalArgumentException("invalid admin credentials"));

        ResponseCookie cookie = ResponseCookie.from(AdminSessionService.COOKIE_NAME, session.sessionId())
            .httpOnly(true)
            .secure(false)
            .path("/")
            .sameSite("Lax")
            .maxAge(8 * 60 * 60)
            .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(new AdminSessionResponse(session.username(), session.issuedAt()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        AdminSession session = (AdminSession) request.getAttribute(AdminApiInterceptor.ADMIN_SESSION_ATTR);
        if (session != null) {
            adminSessionService.logout(session.sessionId());
        }

        ResponseCookie cookie = ResponseCookie.from(AdminSessionService.COOKIE_NAME, "")
            .httpOnly(true)
            .secure(false)
            .path("/")
            .sameSite("Lax")
            .maxAge(0)
            .build();

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .build();
    }

    @GetMapping("/me")
    public AdminSessionResponse me(HttpServletRequest request) {
        AdminSession session = (AdminSession) request.getAttribute(AdminApiInterceptor.ADMIN_SESSION_ATTR);
        if (session == null) {
            throw new IllegalArgumentException("session not found");
        }
        return new AdminSessionResponse(session.username(), session.issuedAt());
    }
}
