# Health Now Integrator Architecture

## Scope and decision

The Java 8 Swing application extracts an immutable local ZIP from PostgreSQL,
requests a narrowly scoped S3 PUT capability from Health Now API, uploads it and
confirms custody. Registration is not clinical processing or publication.

On 2026-10-08 the owner explicitly replaced the proposed mTLS enrollment with
native installation authentication: one-time activation, an individual revocable
key and short-lived installation tokens. ADM/professional sessions cannot be used
as installation sessions. The distributed JAR contains no secret or encrypted
configuration decryptable by the JAR itself.

## Components and boundaries

| Component | Responsibility |
| --- | --- |
| `Application` / `ApplicationSwing` | Default desktop entry point, interactive credentials, background work and progress/error presentation. The console entry point is not the full delivery workflow. |
| `InstallationForm` | Public API/installation/run settings and transient activation/local-password input. |
| `InstallationKey` / `KeyEnvelope` | Locally generated RSA-3072 identity, API/installation binding, password-protected encrypted key custody. |
| `InstallationSessionClient` | Signed activation and nonce/timestamp session proofs; token cached only in memory. |
| `IntegratorService` | Authorized-run preflight, extraction, resumable delivery and stable receipt recovery. |
| `ExtractionService` / JDBC repositories | Read-only extraction through connection factory and resource SQL; PEC version comes from the source database. |
| `LoadFileWriter` | JSONL, manifest, reconciliation report and immutable ZIP with checksum. |
| `FileAvailabilityAPI` / `HttpTransport` | Strict bounded JSON contracts, HTTPS, timeouts, no redirects and no raw remote error disclosure. |
| `S3Service` | Java standard HTTP streaming to an API-authorized presigned URL; no AWS SDK or AWS credential chain. |
| `DeliveryJournal` | Atomic private recovery metadata bound to origin, installation, run, file, bytes and object key. |

The API alone resolves bucket/key, checks current installation and run authority,
presigns PUT, verifies S3 HEAD and atomically writes receipt plus pending Scheduler
intent. AWS credentials and signing authority never cross into the desktop.
The Scheduler is the only load-processing component; this client does not invoke
batch acceptance, reconciliation or publication routes.

## Local security and recovery

The key is generated on first use and stored in a local JCEKS file with restricted
POSIX permissions or owner-only Windows ACL. Its private bytes are additionally
encrypted with AES-256-GCM and PBKDF2-HMAC-SHA256 (600,000 iterations, fresh salt and
nonce). The user's password (minimum 12 characters) is never persisted. AAD binds
the envelope to the API origin and installation. This is not hardware-backed key
custody: a compromised desktop or stolen file plus weak password remains a risk.

API tokens, activation codes, database/local passwords and signed URLs are absent
from journals. Local clinical output is sensitive and retained for deliberate
recovery; it is not encrypted by the ZIP format. Use an encrypted managed disk and
an approved retention policy. No automatic deletion policy is invented here.

Each retry uses the same immutable artifact. Status recovers a lost registration
response; authorization renewal advances the journal generation without changing
file identity. Confirmation is idempotent. A changed archive, origin, installation
or authorized key fails closed. See [Load delivery](LOAD_DELIVERY.md).

## Verification and deployment boundaries

Maven builds target Java 8 with UTF-8 source encoding. Tests cover extraction,
local synthetic HTTP, protected key custody, request validation and delivery
recovery. Real e-SUS tests are explicit opt-in; delivery tests use no AWS or patient
data. Visual Swing behavior and Windows ACL behavior require platform validation.
The API migrations, private versioned S3 bucket, workload IAM, HTTPS deployment,
authorized run/cut provisioning and Scheduler worker rollout remain operational
responsibilities. See the API's `docs/ESUS_FILE_DELIVERY.md`.
