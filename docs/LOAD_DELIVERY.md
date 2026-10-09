# Secure bootstrap file delivery

## Operator and activation flow

The ADM operator creates an import with only description and mode through
`POST /adm/v1/esus-imports`. An active professional session and platform N1 or
platform-administrator authority are required by the API. No municipality,
installation, scope, approved cut or PEC/mapping version is entered by the operator.

The one-time activation code is entered in the integrator with the source database
connection. `POST /integration/v1/esus-imports/bootstrap/activations` exchanges only
the code for an opaque token bound to the import. The client verifies the RSA
signature before storing it in its private, API-origin-bound local token file.
The API stores token/code hashes, not recoverable plaintext codes. A lost activation
response or token requires a new import/code; replay does not redisclose a secret.
The expiry returned by the API is authoritative; there is no client-defined lifetime.

`GET /integration/v1/esus-imports/bootstrap/current` uses the token and checks the
returned import ID against the saved credential. It must be activated for a new
extraction. Current status is not represented as a separately signed legacy run.

## Public verification keyring

`security/api-public-keys.properties` contains key ID = base64 SPKI DER RSA public
keys only. Private keys remain in API secret management. Verification uses
SHA256withRSA, base64 signatures and UTF-8 LF-separated fields without a trailing LF.

Signed token fields, in order:
`health-now-bootstrap-token-v1`, protocol version, signing key ID, import ID,
access token and expiry.

Signed upload fields, in order:
`health-now-bootstrap-upload-v1`, protocol version, signing key ID, import ID,
file ID, upload URL, expiry, required-header count, then lowercase header names
and values sorted by name. Preserve transmitted timestamp strings exactly.
Missing keys, invalid signatures, malformed fields and identity mismatches fail closed.

Signing-key rotation requires distributing the compatible public keyring first.
No private key, AWS credential, shared secret or decryptable secret configuration
is packaged in the JAR. Production API traffic must use HTTPS. Owner-only files
do not protect against a compromised OS user; use managed encrypted storage.

## Extraction, upload and custody

Extraction is read-only and repeatable-read. Schema `local-jsonl-1` /
mapping `esus-local-1` contain 35 entity files and explicit persistent PEC source
identity/municipal unit associations; see [Database extraction](DATABASE_EXTRACTION.md).
Versions are obtained/exported by the integrator, not configured by the ADM operator.
The compatibility boundary is the archive schema, not a manually approved PEC version.

1. `POST /integration/v1/esus-imports/bootstrap/current/file/upload-authorizations`
   authorizes the immutable file ID, SHA-256 and byte length.
2. The client verifies the signature, import/file IDs, expiry, headers and HTTPS AWS
   S3 destination before streaming the ZIP through a short-lived signed URL.
3. `PUT /integration/v1/esus-imports/bootstrap/current/file/availability` confirms
   availability. A conditional PUT 412 is not a receipt; the API must verify S3.
4. The API validates custody, pins the object version and writes the durable Scheduler
   work intent atomically. Receipt recovery also verifies the returned import ID.

Custody is not clinical completion. Scheduler alone downloads, validates and
materializes the data. No batch/publication API is called by the desktop.
The existing presigned S3 transport, required headers and version pinning are unchanged.

Legacy schema-v1 ZIPs cannot be upgraded by changing their manifest or hash.
They lack authoritative source metadata: preserve custody evidence and generate
a new schema-v2 load with a new import when materialization is required.

## Recovery, revocation and retention

Use **Retomar arquivo...** to retry the same immutable artifact without querying PEC.
The private journal binds origin, import/run, file, hash, size and object key; it
contains no token, code, database password, signed URL or clinical payload.
Upload renewal changes the operation generation, not the bytes. Confirmation is
idempotent. Never edit the artifact/journal to bypass an identity mismatch.

ADM revokes its request through
`POST /adm/v1/esus-imports/requests/{import_id}/revoke`, with the existing operation
and idempotency headers. Scheduler rechecks current request/issuer authority before
publication. Previously issued S3 capabilities expire independently within minutes.

Raw local exports remain sensitive and are retained for deliberate recovery. There
is no automatic deletion or invented retention policy. Use restricted encrypted
workstations and an institution-approved cleanup procedure. The Scheduler's temporary
download is private and removed after use; retained custody versions remain governed
by server retention/legal-hold policy.

## Verification and rollout

`mvn -o verify` passed for this extension on JDK 17 targeting Java 8. Synthetic HTTP
tests cover token/signature failures, identity mismatches, upload renewal and receipt
recovery without AWS or real patient data. The opt-in metadata test prepared all
35 SQL projections against local PEC in read-only mode without exporting clinical rows.

Actual versioned S3/IAM deployment, production TLS, maintained Java 8 runtime,
Swing layout and Windows ACL validation remain rollout checks. API migrations
outside disposable Docker remain a human action; neither API nor Scheduler runs
them automatically at startup.
