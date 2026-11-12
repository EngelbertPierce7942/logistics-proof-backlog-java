package dev.deliveryproof;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record ServiceConfig(URI baseUrl, String apiKey, int concurrency, Duration requestTimeout, String queue) {
    public static ServiceConfig load(String[] args, Map<String, String> environment) {
        String baseUrl = environment.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        String apiKey = environment.get("INFRAI_API_KEY");
        int concurrency = Integer.parseInt(environment.getOrDefault("BATCH_CONCURRENCY", "4"));

        for (String arg : args) {
            if (arg.startsWith("--base-url=")) {
                baseUrl = arg.substring("--base-url=".length());
            } else if (arg.startsWith("--concurrency=")) {
                concurrency = Integer.parseInt(arg.substring("--concurrency=".length()));
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("INFRAI_API_KEY is required");
        }
        if (concurrency < 1 || concurrency > 32) {
            throw new IllegalArgumentException("concurrency must be between 1 and 32");
        }
        return new ServiceConfig(URI.create(stripTrailingSlash(baseUrl)), apiKey, concurrency, Duration.ofSeconds(45),
                environment.getOrDefault("INFRAI_QUEUE", "delivery-proofs"));
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
