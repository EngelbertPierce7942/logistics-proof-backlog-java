package dev.deliveryproof;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LogisticsBatchRunner {
    private LogisticsBatchRunner() {}

    public static void main(String[] args) throws Exception {
        Path manifest = manifestPath(args);
        ServiceConfig config = ServiceConfig.load(args, System.getenv());
        List<ShipmentEvent> backlog = readManifest(manifest);
        try (DeliveryProofBatchService service = new DeliveryProofBatchService(
                new InfraiLogisticsClient(config), config.concurrency())) {
            for (DeliveryProofBatchService.Result result : service.process(backlog)) {
                System.out.printf("%s,%s,%s%n", result.eventId(), result.state(), result.detail());
            }
        }
    }

    static List<ShipmentEvent> readManifest(Path manifest) throws Exception {
        List<String> lines = Files.readAllLines(manifest);
        List<ShipmentEvent> events = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] row = lines.get(i).split(",", -1);
            if (row.length != 5) throw new IllegalArgumentException("line " + (i + 1) + " must have five columns");
            Path proof = row[3].isBlank() ? null : manifest.toAbsolutePath().getParent().resolve(row[3]).normalize();
            events.add(new ShipmentEvent(row[0], row[1], ShipmentEvent.EventType.valueOf(row[2]), proof, row[4]));
        }
        return events;
    }

    private static Path manifestPath(String[] args) {
        for (String arg : args) if (!arg.startsWith("--")) return Path.of(arg);
        throw new IllegalArgumentException("manifest CSV path is required");
    }
}
