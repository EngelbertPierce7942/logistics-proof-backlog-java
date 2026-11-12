package dev.deliveryproof;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DeliveryProofBatchService implements AutoCloseable {
    public record Result(String eventId, String state, String detail) {}

    private final InfraiLogisticsClient client;
    private final ExecutorService workers;

    public DeliveryProofBatchService(InfraiLogisticsClient client, int concurrency) {
        this.client = client;
        this.workers = Executors.newFixedThreadPool(concurrency);
    }

    public List<Result> process(List<ShipmentEvent> backlog) {
        List<CompletableFuture<Result>> futures = new ArrayList<>();
        for (ShipmentEvent event : backlog) {
            ProofDecision decision = ProofDecision.evaluate(event);
            if (decision.action() == ProofDecision.Action.REJECT) {
                futures.add(CompletableFuture.completedFuture(new Result(event.eventId(), "REJECTED", decision.reason())));
            } else if (decision.action() == ProofDecision.Action.RECORD_EXCEPTION) {
                futures.add(CompletableFuture.completedFuture(new Result(event.eventId(), "EXCEPTION_RECORDED", decision.reason())));
            } else {
                futures.add(CompletableFuture.supplyAsync(() -> processProof(event), workers));
            }
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private Result processProof(ShipmentEvent event) {
        try {
            client.publish(event, event.eventId() + ":queue");
            client.resizeProof(event.proofFile(), event.eventId() + ":resize");
            return new Result(event.eventId(), "PROOF_PROCESSED", "1600x1200 jpeg resized");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }

    @Override public void close() { workers.shutdown(); }
}
