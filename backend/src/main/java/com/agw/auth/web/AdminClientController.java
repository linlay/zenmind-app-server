package com.agw.auth.web;

import java.util.List;

import com.agw.auth.domain.OAuthClient;
import com.agw.auth.service.OAuthClientService;
import com.agw.auth.web.dto.ClientCreateRequest;
import com.agw.auth.web.dto.ClientResponse;
import com.agw.auth.web.dto.ClientUpdateRequest;
import com.agw.auth.web.dto.RotateSecretResponse;
import com.agw.auth.web.dto.StatusPatchRequest;
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
@RequestMapping("/admin/api/clients")
public class AdminClientController {

    private final OAuthClientService oauthClientService;

    public AdminClientController(OAuthClientService oauthClientService) {
        this.oauthClientService = oauthClientService;
    }

    @GetMapping
    public List<ClientResponse> list() {
        return oauthClientService.listClients().stream().map(AdminClientController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@Valid @RequestBody ClientCreateRequest request) {
        return toResponse(oauthClientService.createClient(request));
    }

    @GetMapping("/{clientId}")
    public ClientResponse detail(@PathVariable String clientId) {
        return oauthClientService.findClient(clientId)
            .map(AdminClientController::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("client not found"));
    }

    @PutMapping("/{clientId}")
    public ClientResponse update(@PathVariable String clientId, @Valid @RequestBody ClientUpdateRequest request) {
        return toResponse(oauthClientService.updateClient(clientId, request));
    }

    @PatchMapping("/{clientId}/status")
    public ClientResponse patchStatus(@PathVariable String clientId, @Valid @RequestBody StatusPatchRequest request) {
        return toResponse(oauthClientService.patchStatus(clientId, request.status()));
    }

    @PostMapping("/{clientId}/secret/rotate")
    public RotateSecretResponse rotateSecret(@PathVariable String clientId) {
        String newSecret = oauthClientService.rotateSecret(clientId);
        return new RotateSecretResponse(clientId, newSecret);
    }

    private static ClientResponse toResponse(OAuthClient client) {
        return new ClientResponse(
            client.id(),
            client.clientId(),
            client.clientName(),
            client.grantTypes(),
            client.redirectUris(),
            client.scopes(),
            client.requirePkce(),
            client.status(),
            client.createAt(),
            client.updateAt()
        );
    }
}
