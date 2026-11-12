package dev.deliveryproof;

import java.nio.file.Path;

public record ShipmentEvent(
        String eventId,
        String shipmentId,
        EventType type,
        Path proofFile,
        String exceptionReason) {

    public enum EventType { DELIVERED, EXCEPTION }
}
