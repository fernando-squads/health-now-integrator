# Load generation and delivery

## Implemented integrator behavior

`IntegratorService.integrate(options, status)` orchestrates configuration validation,
archive generation through `ExtractionService` and `ConnectionFactory`, verified S3
upload, then API registration through the `LoadRegistrar` port. The old null citizen
file and unrelated `/store/auth/v1` call have been removed from this service.

`integrate()` retains the original public entry point. `resume(archive, status)` resumes
delivery after restart without extracting again. After a delivery failure, repeated
calls on the same service instance reuse `pendingArchive()`. A successful new call
starts a new extraction; explicitly resume an existing archive to reconcile it.

`delivery.json` is atomically replaced after each confirmed transition:
`created -> uploaded -> registered`. A per-directory file lock excludes simultaneous
delivery of the same archive. The journal binds installation, run, file, checksum,
storage and API destinations. Changing those on retry is rejected. A failed notification
leaves `uploaded`; the next attempt uses the same idempotency key. Registration must
return a durable opaque receipt. Registered does not mean processed or published.

The default `FileAvailabilityAPI` deliberately fails its preflight because the inspected
API does not implement file registration. It does not make a fake HTTP call or report
success. Consequently the default integrated run fails before generation/upload.
The existing Swing local-only extraction remains available and unchanged. The completed
remote workflow can be wired to that screen once the API adapter exists.

## Official AWS SDK

`pom.xml` declares `software.amazon.awssdk:s3:2.35.6` (AWS SDK for Java v2).
`S3Service` uses `S3Client`, `PutObjectRequest`, `RequestBody.fromFile` and `HeadObject`.
The implementation and tests compile/run with JDK 1.8.0_202.

Configuration:

| Variable | Meaning |
| --- | --- |
| `AWS_REGION` | Authorized bucket region |
| `AWS_BUCKET` | Private destination bucket |
| `INTEGRATOR_S3_PREFIX` | Authorized root prefix, no leading/trailing slash |
| AWS default credentials chain | Environment, profiles or workload credentials; temporary session credentials supported |

Object key: `<prefix>/<installation_id>/<run_id>/<file_id>.zip`.
IAM must restrict the configured installation prefix and deny public access.
No ACL is set. Each write requests S3-managed AES-256 encryption and supplies the
base64 SHA-256 checksum of the exact final ZIP bytes. Conditional `If-None-Match: *`
prevents overwriting. A 412 response triggers reconciliation using HEAD; size,
server checksum and ownership metadata must match. Missing/mismatched verification
fails instead of notifying the API. No ETag-as-SHA assumption is made.

Uploads stream from disk and have bounded SDK call timeouts. This implementation uses
single PUT and rejects files over 5 GiB; multipart upload is not implemented.
The AWS SDK owns its internal retry policy. After an ambiguous failure, resume the
same archive. Provider exception text, credentials and object bytes are not logged.

AWS documentation: [conditional writes](https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html)
and [Java v2 file upload](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/migration-s3-client.html).

## API inspection (2026-10-08)

Evidence: sibling `health-now-api/src/interfaces/http/esus_import/{mod,delivery,operator,dto}.rs`.

| Existing endpoint | Actual behavior | Applicability to S3 load |
| --- | --- | --- |
| `POST /adm/v1/esus-imports` | Operator starts an import with context, cut ID, entities and revision configuration; bearer session required | Existing batch-run lifecycle; local ZIP manifest is not this request |
| `GET /integration/v1/esus-imports/capabilities?run_id=...` | Advertises batch protocol/schema v1 and batch/body limits for an authorized run | Does not advertise file registration or S3 upload |
| `PUT /integration/v1/esus-imports/{run_id}/batches/{batch_id}` | Receives bounded JSON records with digest and idempotency headers | Not a ZIP upload or S3 notification |
| `GET /integration/v1/esus-imports/{run_id}/batches/{batch_id}` | Reads a persisted batch receipt | Cannot reconcile an S3 object registration |
| `PUT /integration/v1/esus-imports/{run_id}/manifest` | Verifies every referenced batch was received, then seals the run | Cannot register an arbitrary file or local manifest |
| `GET /integration/v1/esus-imports/{run_id}` | Reads existing import summary | Future file states need backend support |
| `GET /adm/v1/esus-imports/operations/{operation_id}` | Operator reads an operation outcome | Existing operator authorization and context are required |

Technical routes require a trusted mTLS gateway's signed assertion, bound to method,
request target, body and operation/idempotency headers. The integrator must not mint
`X-Import-Gateway-Assertion` or treat a professional bearer token as installation identity.
The old Java `/store/auth/v1` path was not found in this API's routes.
Catalog S3 upload routes belong to a different domain and cannot be used for clinical loads.

## Concrete API change proposed, not implemented

Add an installation-authenticated file registration operation, for example:

```text
PUT /integration/v1/esus-imports/{run_id}/files/{file_id}
Idempotency-Key: <installation_id>:<run_id>:<file_id>:<sha256>
X-Operation-Id: <stable operation UUID>
X-Body-SHA256: <digest of exact request bytes>
```

The path above is a proposal, not an existing endpoint. Proposed minimal JSON contract:

```json
{
  "protocol_version": 1,
  "installation_id": "installation UUID",
  "run_id": "run UUID",
  "file_id": "file UUID",
  "scope_id": "authorized scope UUID",
  "object_key": "authorized-prefix/installation/run/file.zip",
  "size_bytes": 12345,
  "sha256": "64 lowercase hexadecimal characters",
  "extraction_cutoff": "2026-10-08",
  "source_version": "PEC database version from VERSAOBANCODADOS",
  "mapping_version": "esus-local-1",
  "schema_version": "local-jsonl-1"
}
```

Backend work must define file-run creation/authorization and bind the installation,
scope, municipality and existing import lifecycle. The current manifest's text `scope`
is not the API's authorized scope UUID. Transport must not silently equate these.
Resolve bucket/prefix from trusted server configuration, not a supplied public URL or
arbitrary bucket. Verify object size/hash/ownership, persist an idempotent registration
and durable pending scheduler work in one transaction, and reject conflicting replay.
Return a stable receipt correlated with installation/run/file/hash and an explicit
registered/pending state. Equivalent retries must recover that receipt.

Then implement `LoadRegistrar`'s HTTP adapter with gateway mTLS, bounded timeouts,
strict correlated-receipt validation and safe error handling. The adapter's `destination()`
must identify the fixed API target without secrets. Only after this should the Swing
flow switch from local generation to remote integration.

## Verification

`DeliveryTest` uses synthetic archives and fake API/storage ports. SDK request tests
use official AWS request/response classes and a fake `S3Client`; no real AWS or API
request is made. Tests cover successful ordered delivery, upload failure, lost API
response, resume across service instances, already-registered replay, integrity failure,
destination changes, missing API preflight, conditional/encrypted/checksummed upload,
matching 412 replay and conflicting remote content.

Run `mvn test` with Java 8 when Maven is available. In the current environment, all
main sources were compiled with Java 8 `javac`; JUnitCore ran `DeliveryTest` and
`ExtractionTest` (13 passing tests). Maven packaging, real bucket IAM/S3 behavior and
real HTTP registration remain unverified. No source database extraction was rerun
and no real clinical files were transmitted for these tests.
