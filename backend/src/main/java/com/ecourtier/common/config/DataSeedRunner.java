package com.ecourtier.common.config;

import com.ecourtier.common.service.DemoDataSeederService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Profile("dev")
@Slf4j
@RequiredArgsConstructor
public class DataSeedRunner {

    private final DemoDataSeederService demoDataSeederService;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void seedDataOnStartup() {
        // Extra safeguard: Never execute if "dev" profile is not active
        if (!Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            log.warn("[DATA SEED] Profil 'dev' non actif. Le seed data est strictement désactivé.");
            return;
        }

        demoDataSeederService.seedDemoData();
    }
}

