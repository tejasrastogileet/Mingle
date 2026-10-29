package com.meetnow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class AllowedOrigins {

    private final List<String> origins;

    public AllowedOrigins(@Value("${app.cors.allowed-origins}") String configuredOrigins) {
        this.origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
    }

    public List<String> getOrigins() {
        return origins;
    }
}
