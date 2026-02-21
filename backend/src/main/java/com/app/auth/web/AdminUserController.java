package com.app.auth.web;

import java.util.List;
import java.util.UUID;

import com.app.auth.domain.AppUser;
import com.app.auth.service.AppUserService;
import com.app.auth.web.dto.StatusPatchRequest;
import com.app.auth.web.dto.UserCreateRequest;
import com.app.auth.web.dto.UserPasswordResetRequest;
import com.app.auth.web.dto.UserResponse;
import com.app.auth.web.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/users")
public class AdminUserController {

    private final AppUserService appUserService;

    public AdminUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return appUserService.listUsers().stream().map(AdminUserController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return toResponse(appUserService.createUser(request));
    }

    @GetMapping("/{userId}")
    public UserResponse detail(@PathVariable UUID userId) {
        return appUserService.findByUserId(userId)
            .map(AdminUserController::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("user not found"));
    }

    @PutMapping("/{userId}")
    public UserResponse update(@PathVariable UUID userId, @Valid @RequestBody UserUpdateRequest request) {
        return toResponse(appUserService.updateUser(userId, request));
    }

    @PatchMapping("/{userId}/status")
    public UserResponse patchStatus(@PathVariable UUID userId, @Valid @RequestBody StatusPatchRequest request) {
        return toResponse(appUserService.patchStatus(userId, request.status()));
    }

    @PostMapping("/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID userId, @Valid @RequestBody UserPasswordResetRequest request) {
        appUserService.resetPassword(userId, request.password());
    }

    private static UserResponse toResponse(AppUser appUser) {
        return new UserResponse(
            appUser.userId(),
            appUser.username(),
            appUser.displayName(),
            appUser.status(),
            appUser.createAt(),
            appUser.updateAt()
        );
    }
}
