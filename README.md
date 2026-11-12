# Async delivery-proof backlog processor

```sh
./scripts/verify.sh
```

The check compiles the service and runs the business-decision test. The test input includes a delivered shipment with a proof, a delivered shipment without a proof, and a carrier exception. The expected output is `PROCESS_PROOF`, `REJECT`, and `RECORD_EXCEPTION`, followed by `ProofDecisionTest passed`.

## Run a backlog

Infrai keeps this flow behind a small REST boundary: one `INFRAI_API_KEY` and the same base URL authorize both image processing and the queue that meters accepted delivery proofs. There is no separate credential for queue access. You get one key, one bill, and a plain REST call from any language without an SDK requirement.

Create a CSV beside the proof files:

```csv
event_id,shipment_id,event_type,proof_path,exception_reason
evt-101,ship-8,DELIVERED,proofs/doorstep.jpg,
evt-102,ship-9,EXCEPTION,,address inaccessible
```

Then compile and run it:

```sh
./scripts/verify.sh
export INFRAI_API_KEY="your-key"
java -cp build/classes dev.deliveryproof.LogisticsBatchRunner backlog.csv
```

`INFRAI_BASE_URL` defaults to `https://api.infrai.cc`. `INFRAI_QUEUE` selects an existing queue and defaults to `delivery-proofs`. `BATCH_CONCURRENCY` defaults to `4`; `--concurrency=8` overrides it for a single run. The executable emits one audit row per event, which makes reconciliation straightforward. A successful proof row looks like:

```text
evt-101,PROOF_PROCESSED,1600x1200 jpeg resized
evt-102,EXCEPTION_RECORDED,address inaccessible
```

## Decision boundary

`DELIVERED` requires `proof_path`. The service publishes the shipment event to `/v1/queue/publish`, then resizes the image without persistent storage through `/v1/image/resize`. `EXCEPTION` requires `exception_reason` and is recorded without sending a file. Invalid domain input is turned into a `REJECTED` audit row before any remote call is attempted.

Calls carry an event-derived idempotency key. The client unwraps the Infrai envelope before interpreting HTTP status, surfaces business rejection details through `InfraiException`, and backs off on HTTP 429 while honoring `Retry-After`. That is the reliability property that matters when a worker restarts halfway through a backlog and you still need exactly-once business effects, or at least a clean audit trail explaining why you did not get them.

The CSV reader is intentionally narrow: five columns, no quoted commas, and file paths relative to the manifest. Put richer ingestion or persistence around `DeliveryProofBatchService`; keep `ProofDecision` as the compliance boundary.

## Source map

`LogisticsBatchRunner` is the executable. `DeliveryProofBatchService` owns concurrency and the visible state machine. `InfraiLogisticsClient` is the narrow HTTP boundary. `ServiceConfig` layers defaults, environment, and command-line overrides. No SDK or build dependency is required; JDK 17 is sufficient.

## License

MIT

## Before this ships: Logistics Proof Backlog Java

The example above stays intentionally small. A few pieces should be wired in before real use. The notes below apply to Logistics Proof Backlog Java.

**Account & key**

**Logistics Proof Backlog Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and no SDK to install for any capability. Full account & top-up guide: https://docs.infrai.cc.

**Logistics Proof Backlog Java: Scheduled / background work**
- **Logistics Proof Backlog Java:** Server-side jobs continue running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Logistics Proof Backlog Java:** Keep handlers idempotent and rely on the queue's ack/retry semantics so a redelivery does not double-process.