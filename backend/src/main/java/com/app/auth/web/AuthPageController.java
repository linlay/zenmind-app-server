package com.app.auth.web;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthPageController {

    private final RegisteredClientRepository registeredClientRepository;

    public AuthPageController(RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @GetMapping("/openid/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/openid/consent")
    public String consentPage(
        Principal principal,
        @RequestParam("client_id") String clientId,
        @RequestParam("state") String state,
        @RequestParam(value = "scope", required = false) String scope,
        Model model
    ) {
        if (principal == null) {
            return "redirect:/openid/login";
        }

        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            throw new IllegalArgumentException("client not found");
        }

        Set<String> scopes = new LinkedHashSet<>();
        if (StringUtils.hasText(scope)) {
            scopes.addAll(Arrays.asList(scope.split("\\s+")));
        }

        if (scopes.isEmpty()) {
            scopes.addAll(client.getScopes());
        }

        model.addAttribute("principalName", principal.getName());
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client.getClientName());
        model.addAttribute("state", state);
        model.addAttribute("scopes", scopes);
        return "consent";
    }

    @PostMapping("/openid/consent")
    public void consentSubmit(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/oauth2/authorize");
        dispatcher.forward(request, response);
    }
}
