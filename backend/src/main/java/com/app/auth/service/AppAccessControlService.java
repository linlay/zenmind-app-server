package com.app.auth.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

@Service
public class AppAccessControlService {

    private final AtomicBoolean newDeviceLoginAllowed = new AtomicBoolean(false);

    public boolean isNewDeviceLoginAllowed() {
        return newDeviceLoginAllowed.get();
    }

    public boolean setNewDeviceLoginAllowed(boolean enabled) {
        newDeviceLoginAllowed.set(enabled);
        return newDeviceLoginAllowed.get();
    }
}

