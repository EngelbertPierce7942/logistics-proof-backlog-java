package dev.deliveryproof;

import java.nio.file.Path;

public final class ProofDecisionTest {
    public static void main(String[] args) {
        ShipmentEvent delivered = new ShipmentEvent("evt-101", "ship-8", ShipmentEvent.EventType.DELIVERED,
                Path.of("proof.jpg"), "");
        ShipmentEvent missingProof = new ShipmentEvent("evt-102", "ship-9", ShipmentEvent.EventType.DELIVERED,
                null, "");
        ShipmentEvent exception = new ShipmentEvent("evt-103", "ship-10", ShipmentEvent.EventType.EXCEPTION,
                null, "address inaccessible");

        expect(ProofDecision.Action.PROCESS_PROOF, ProofDecision.evaluate(delivered).action());
        expect(ProofDecision.Action.REJECT, ProofDecision.evaluate(missingProof).action());
        expect(ProofDecision.Action.RECORD_EXCEPTION, ProofDecision.evaluate(exception).action());
        System.out.println("ProofDecisionTest passed");
    }

    private static void expect(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
}
