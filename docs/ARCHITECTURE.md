# Health Now Integrator Architecture

## Scope and decision

The Java 8 Swing application extracts an immutable local ZIP from PostgreSQL,
requests a narrowly scoped S3 PUT capability from Health Now API, uploads it and
confirms custody. Registration is not clinical processing or publication.

On 2026-10-08 the owner explicitly replaced the proposed mTLS enrollment with
native installation authentication: one-time activation, an individual revocable
opaque token. The API signs responses with its private RSA key; only the public
verification keyring is bundled in the JAR. ADM sessions cannot impersonate devices. The distributed JAR contains no secret or encrypted
configuration decryptable by the JAR itself.

## Components and boundaries

| Component | Responsibility |
| --- | --- |
| `Application` / `ApplicationSwing` | Default desktop entry point, interactive credentials, background work and progress/error presentation. The console entry point is not the full delivery workflow. |
| `InstallationForm` | Transient activation input; API URL comes from `application.properties`; no UUID, key-path or local-password fields. |
| `InstallationTokenStore` | API-origin-bound token custody in an owner-only local file. |
| `InstallationClient` / `ApiSignatureVerifier` | Bootstrap activation code, import discovery, signed upload authorization and direct Bearer authentication. |
| `IntegratorService` | Authorized-run preflight, extraction, resumable delivery and stable receipt recovery. |
| `ExtractionService` / JDBC repositories | Read-only extraction through connection factory and resource SQL; PEC version comes from the source database. |
| `LoadFileWriter` | JSONL, manifest, reconciliation report and immutable ZIP with checksum. |
| `FileAvailabilityAPI` / `HttpTransport` | Strict bounded JSON contracts, HTTP/HTTPS API origins, HTTPS S3 uploads, timeouts, no redirects and safe display of structured API error messages. |
| `S3Service` | Java standard HTTP streaming to an API-authorized presigned URL; no AWS SDK or AWS credential chain. |
| `DeliveryJournal` | Atomic private recovery metadata bound to origin, installation, run, file, bytes and object key. |

The API alone resolves bucket/key, checks bootstrap-import authority,
presigns PUT, verifies S3 HEAD and atomically writes receipt plus pending Scheduler
intent. AWS credentials and signing authority never cross into the desktop.
The Scheduler is the only load-processing component; this client does not invoke
batch acceptance, reconciliation or publication routes.

## Local security and recovery

The API issues a random opaque token after consuming a one-time activation code.
The token and minimized metadata are stored under the current user's
`.health-now-integrator` directory in an origin-specific file with owner-only
POSIX permissions or Windows ACL. Writes are atomic and locked. The token is
protected by the operating-system user account rather than a user-supplied local
password; use an encrypted managed disk where workstation-at-rest protection is required.
No local RSA pair or session proof exists. Existing legacy keystores are left
untouched and are not reused; migration requires a new activation code.
A compromised workstation or a process running as the same local user remains a risk.

API tokens, activation codes, database passwords and signed URLs are absent
from journals. Local clinical output is sensitive and retained for deliberate
recovery; it is not encrypted by the ZIP format. Use an encrypted managed disk and
an approved retention policy. No automatic deletion policy is invented here.

Each retry uses the same immutable artifact. Status recovers a lost registration
response; authorization renewal advances the journal generation without changing
file identity. Confirmation is idempotent. A changed archive, origin, installation
or authorized key fails closed. See [Load delivery](LOAD_DELIVERY.md).

## Verification and deployment boundaries

Maven builds target Java 8 with UTF-8 source encoding. Tests cover extraction,
local synthetic HTTP, protected token custody, request validation and delivery
recovery. Real e-SUS tests are explicit opt-in; delivery tests use no AWS or patient
data. Visual Swing behavior and Windows ACL behavior require platform validation.
The API migrations, private versioned S3 bucket, workload IAM, HTTPS deployment,
authorized run/cut provisioning and Scheduler worker rollout remain operational
responsibilities. See the API's `docs/ESUS_FILE_DELIVERY.md`.
