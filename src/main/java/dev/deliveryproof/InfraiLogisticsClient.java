package dev.deliveryproof;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public final class InfraiLogisticsClient {
    private final ServiceConfig config;
    private final HttpClient http;

    public InfraiLogisticsClient(ServiceConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Map<String, Object> publish(ShipmentEvent event, String idempotencyKey) throws IOException, InterruptedException {
        String payload = "{\"queue\":" + Json.quote(config.queue()) + ",\"payload\":{" +
                "\"event_id\":" + Json.quote(event.eventId()) + "," +
                "\"shipment_id\":" + Json.quote(event.shipmentId()) + "," +
                "\"event_type\":" + Json.quote(event.type().name()) + "}}";
        HttpRequest request = request("/v1/queue/publish", idempotencyKey)
                .header("Content-Type", "application/json")
                .method("POST", HttpRequest.BodyPublishers.ofString(payload))
                .build();
        return send(request);
    }

    public Map<String, Object> resizeProof(Path image, String idempotencyKey) throws IOException, InterruptedException {
        String boundary = "infrai-" + UUID.randomUUID();
        byte[] body = multipart(boundary, image);
        HttpRequest request = request("/v1/image/resize", idempotencyKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method("POST", HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return send(request);
    }

    private HttpRequest.Builder request(String path, String idempotencyKey) {
        return HttpRequest.newBuilder(config.baseUrl().resolve(path))
                .timeout(config.requestTimeout())
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Idempotency-Key", idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(HttpRequest request) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Object decoded;
            try { decoded = Json.parse(response.body()); }
            catch (IllegalArgumentException e) {
                throw new IOException("Infrai returned a non-JSON response with HTTP " + response.statusCode(), e);
            }
            if (!(decoded instanceof Map<?, ?> raw)) throw new IOException("Infrai envelope must be an object");
            Map<String, Object> envelope = (Map<String, Object>) raw;

            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport response: HTTP " + response.statusCode());
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> value
                        ? (Map<String, Object>) value : Map.of();
                String code = String.valueOf(error.getOrDefault("code", ""));
                String message = String.valueOf(error.getOrDefault("message", "Infrai request rejected"));
                throw new InfraiException(code, message, response.statusCode());
            }
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of("value", data);
        }
        throw new IOException("retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        try { return Math.max(0, Long.parseLong(retryAfter) * 1000L); }
        catch (NumberFormatException ignored) { return 250L * (1L << attempt); }
    }

    private static byte[] multipart(String boundary, Path image) throws IOException {
        String filename = image.getFileName().toString();
        StringBuilder fields = new StringBuilder();
        addField(fields, boundary, "width", "1600");
        addField(fields, boundary, "height", "1200");
        addField(fields, boundary, "fit", "inside");
        addField(fields, boundary, "enlarge", "false");
        addField(fields, boundary, "format", "jpeg");
        addField(fields, boundary, "store", "false");
        fields.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"image\"; filename=").append(Json.quote(filename)).append("\r\n")
                .append("Content-Type: application/octet-stream\r\n\r\n");
        byte[] prefix = fields.toString().getBytes(StandardCharsets.UTF_8);
        byte[] file = Files.readAllBytes(image);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + file.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(file, 0, body, prefix.length, file.length);
        System.arraycopy(suffix, 0, body, prefix.length + file.length, suffix.length);
        return body;
    }

    private static void addField(StringBuilder body, String boundary, String name, String value) {
        body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                .append(value).append("\r\n");
    }
}
