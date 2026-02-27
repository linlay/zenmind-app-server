package com.app.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuthBackendApplication {

    private static final String AUTH_DB_PATH_ENV = "AUTH_DB_PATH";
    private static final String DEFAULT_AUTH_DB_PATH = "../data/auth.db";

    public static void main(String[] args) {
        ensureAuthDbParentDirectory();
        SpringApplication.run(AuthBackendApplication.class, args);
    }

    private static void ensureAuthDbParentDirectory() {
        String dbPathValue = System.getenv(AUTH_DB_PATH_ENV);
        Path dbPath = Paths.get(
                dbPathValue == null || dbPathValue.isBlank() ? DEFAULT_AUTH_DB_PATH : dbPathValue);
        Path parent = dbPath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create auth DB directory: " + parent, ex);
        }
    }
}
