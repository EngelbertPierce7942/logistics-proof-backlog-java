package dev.deliveryproof;

public record ProofDecision(Action action, String reason) {
    public enum Action { PROCESS_PROOF, RECORD_EXCEPTION, REJECT }

    public static ProofDecision evaluate(ShipmentEvent event) {
        if (event.type() == ShipmentEvent.EventType.EXCEPTION) {
            return event.exceptionReason() == null || event.exceptionReason().isBlank()
                    ? new ProofDecision(Action.REJECT, "exception_reason is required")
                    : new ProofDecision(Action.RECORD_EXCEPTION, event.exceptionReason());
        }
        if (event.proofFile() == null) {
            return new ProofDecision(Action.REJECT, "proof_path is required for DELIVERED");
        }
        return new ProofDecision(Action.PROCESS_PROOF, "delivery proof accepted");
    }
}
