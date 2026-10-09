# Secure installation-bound delivery

## Provisioning and activation

Distribute only the compiled Java 8 JAR. It contains public verification keys,
not API private keys, credentials, activation codes or encrypted shared secrets.
There is no .env loader and no AWS SDK/credential chain in the desktop.

1. An authorized PER-18 administrator provisions the installation, scope,
   compatible approved cut and Receiving run. Call
   POST /adm/v1/esus-imports/installations/{installation_id}/activation-codes
   with run_id, scoped context, X-Operation-Id and Idempotency-Key.
   The ten-minute code is returned once; issuance replay never reveals it again.
2. Enter database host, port, name, username and password; HTTPS API origin;
   activation code; and a local protection password (at least 12 characters).
   No installation/run UUID or key path is entered. No client key is generated.
3. POST /integration/v1/esus-installations/credential-activations sends only
   activation_code. The signed opaque token is verified before being saved.
   The API stores its SHA-256 hash and revocable metadata, never the plaintext.
4. GET /integration/v1/esus-imports/current uses that token directly and returns
   the signed installation and Receiving run internally. No run is created.
5. Later starts ask for the local password, not the code, when a token file exists.
   Invalid/expired/revoked tokens prompt reactivation. Use the new-code checkbox
   for deliberate rotation or a lost password. A wrong local password does not
   delete or overwrite the existing credential.

Tokens expire after 365 days. Activation consumes the code exactly once and
revokes the previous token atomically. If activation succeeds but its response
or local save is lost, request a new code. No replay endpoint rediscloses a token.
Old JCEKS files are not read or deleted: upgrade requires fresh activation.
The /activate and /tokens native routes and local RSA/session proofs are removed.

## Public verification keyring

security/api-public-keys.properties contains only key ID = base64 SPKI DER RSA
public key entries. The supplied RSA-3072 public key is bundled. Private keys
remain in API secret management, never this repository or JAR. Missing/unknown
keys, unsupported protocol versions, malformed payloads and invalid signatures
fail closed; HTTPS alone never bypasses response verification.

Use SHA256withRSA, standard base64 signatures and UTF-8 lines separated by LF,
without a trailing LF. Preserve transmitted timestamps exactly when verifying.
The exact ordered fields, including the first domain-separator line, are:

- Token: health-now-installation-token-v1, protocol_version, signing_key_id,
  installation_token_id, installation_token, expires_at.
- Current run: health-now-current-run-v1, protocol_version, signing_key_id,
  installation_id, run_id, state, source_version, mapping_version.
- Upload: health-now-upload-authorization-v1, protocol_version, signing_key_id,
  file_id, run_id, state, object_key, upload_url, expires_at, header count,
  then each lowercase header name and value on separate lines, sorted by name.

The additional signed installation_id preserves the existing manifest contract
without asking the user for an identifier. CR/LF inside fields is rejected.
A public key authenticates the response, not the JAR. A modified client can still
use a valid stolen token; revoke tokens explicitly after compromise. Password
protection is not a defense against malware inspecting a running process.
Enter only the centrally approved HTTPS API origin: response signatures do not
prevent an activation code from being sent to a wrongly entered server. Trusted
distribution of the API URL and TLS validation remain essential.

## Upload and custody

After authorized-run preflight, extraction reads the database through the
connection factory in read-only mode. Source version is queried from
`public.tb_config_sistema` where `co_config_sistema = 'VERSAOBANCODADOS'`; it is not
a manually configured version. SQL files remain in `src/main/resources/queries`.

The archive uses `local-jsonl-1` / `esus-local-1`. The API requires a compatible
approved cut; accepting the transport does not approve clinical mappings.

For `/integration/v1/esus-imports/{run}/files/{file}`:

1. `POST /upload-authorizations` binds checksum, size, versions and cutoff to the
   installation/run/file. Writes carry stable operation and idempotency headers.
2. The client verifies the API RSA signature and validates the returned identity, key, HTTPS AWS S3 destination,
   expiration and exact required headers, then streams the existing ZIP. Required
   headers include SHA-256, size, ZIP content type, three identity metadata fields,
   AES256 encryption and `If-None-Match: *`.
3. `PUT /availability` confirms the candidate. A conditional PUT 412 may mean an
   earlier upload succeeded; only API HEAD verification can accept that object.
4. The API verifies all metadata and checksum, pins the S3 version, and commits
   one stable receipt and one pending Scheduler intent atomically.

`registered` means custody accepted, **not processed or published**. The UI reports
this distinction. The client never calls batch manifest or clinical publication
endpoints. HTTPS certificates use the JVM trust store; TLS validation is never
disabled. Client S3 destinations are restricted to standard AWS virtual-hosted
regional/global S3 HTTPS hosts; custom endpoints require an explicit future change.

## Recovery and retention

Use **Retomar ZIP** to resume the same artifact after a restart without rereading
the database. The private version-2 journal binds API origin, installation, run,
file, hash, size and authorized object key. It contains no token, password,
activation code, presigned URL or clinical payload. A local lock prevents concurrent
delivery. Save updates are atomic.

Status lookup recovers lost confirmation responses. Expired upload authorizations
are renewed with a new generation for the same bytes; confirmation identity stays
stable. Retries are bounded, and other failures remain resumable rather than
triggering extraction again. Never edit the artifact or journal to bypass a
conflict. Legacy version-1 journals are rejected and require administrative review
of custody before a deliberate new delivery.

Exports, JSONL, reports and ZIPs contain sensitive healthcare information. They
remain on disk after success/failure; no automatic retention policy was approved.
Use full-disk encryption, restricted accounts and an approved secure cleanup
procedure. The token file contains only origin, protocol, token ID, token and expiry,
protected with password-derived AES-GCM. It contains no database credentials.

## Rotation, revocation and server responsibilities

ADM revokes a token using
POST /adm/v1/esus-imports/installations/{installation_id}/tokens/{installation_token_id}/revoke.
It takes scoped context and operation/idempotency headers. Revocation is immediate
for API calls; an already-issued S3 capability expires independently within minutes.
A new code rotates the token. The local encrypted file is retained on signature or
authentication failure for safe troubleshooting, not silently deleted.

API signing configuration is ESUS_INSTALLATION_SIGNING_PRIVATE_KEY (base64 PKCS#8)
and ESUS_INSTALLATION_SIGNING_KEY_ID. Use the API provisioning script on a trusted
host and copy only its public properties output into the resource keyring.
Distribute a compatible JAR before changing the API's active signing key ID.
Do not replace private key management with a secret encrypted inside the JAR.
TLS, clock synchronization and protected distribution of codes remain operational
responsibilities. Signing-key rotation does not itself revoke installation tokens.

The API uses the official AWS SDK with server-side workload IAM. Its configured
bucket must have all Block Public Access controls, versioning and bucket-owner
enforced ownership. Startup validates these controls. Grant only the required
bucket-control reads and object Put/Get/HEAD for the configured prefix; prohibit
public access and insecure transport. Keep lifecycle rules from deleting pending
versions. The client cannot choose a bucket, arbitrary prefix or object key.
See the API documentation for migration, IAM and Scheduler boundaries.

## Verification

Use a Java 8 JDK: `mvn clean verify`. Tests use synthetic local HTTP and data, with
no AWS service or real activation. Real database tests remain disabled unless
explicitly enabled with their documented opt-in. Validate Swing on supported
desktops and Windows file permissions before rollout. Use a maintained Java 8
distribution and OS trust store for deployment; a successful build on an older
JDK is not a security patch assessment.

Validation on 2026-10-08: Java 8 Maven verify passed 25 tests; the two real e-SUS
tests remained disabled. Coverage includes code-only activation, signature and
key-ID tampering, encrypted origin-bound token storage, expiry/revocation,
conditional Swing fields, upload renewal and receipt recovery. The macOS JDK
emitted its missing-Times-font fallback warning during headless Swing tests;
visual layout and Windows ACL behavior still need platform validation.

JAR inspection confirmed Java 8 bytecode (major 52), the exact supplied public
keyring, and absence of private-key files, .env, AWS SDK and old identity classes.
No real AWS, source database, patient data or API private key was used by tests.
