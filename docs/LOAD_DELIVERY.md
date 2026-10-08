# Secure installation-bound delivery

## Provisioning and activation

The owner approved native installation authentication instead of mTLS on
2026-10-08. Do not distribute credentials, populated keystores or encrypted secret
configuration with the JAR. `.env.example` documents public options only; the
application does not load `.env` and does not use AWS credentials.

1. Provision the installation and its authorized scope through existing central
   administration. An authorized PER-18 operator obtains a receiving run and an
   approved compatible import cut through the existing e-SUS lifecycle.
2. That operator calls `POST /adm/v1/esus-imports/installations/{id}/activations`
   with professional authorization, operation/idempotency headers and scoped
   context. Deliver the returned activation code through a protected channel.
   It expires after ten minutes. The code is returned only once; an issuance
   replay returns metadata, not the code. Issue a new code if the response is lost.
3. In Swing, enter the HTTPS API origin, installation/run UUIDs, local keystore
   path, a strong local password of at least twelve characters and activation
   code. The client creates RSA-3072 locally and sends only public key plus a
   signed proof to `/integration/v1/esus-installations/activate`.
4. The client signs a timestamp and fresh nonce to obtain a fifteen-minute token
   from `/integration/v1/esus-installations/tokens`. The token stays in memory.
   Later starts need the local key password, not the activation code.

The API stores code/token hashes and public keys only. Keys expire in ninety days
and are individually revocable. Session proofs have thirty seconds of clock skew
tolerance: maintain synchronized workstation and server clocks. ADM credentials
are never entered in the desktop integrator.

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
2. The client validates the returned identity, key, HTTPS AWS S3 destination,
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
procedure. Protect the keystore separately; losing its password requires a new
activation and revocation of the previous key, not a recovery secret in the JAR.

## Rotation, revocation and server responsibilities

Create a new keystore at a different path using a new activation code. The API
allows at most two active keys for overlap. Verify the new identity, then an
authorized operator calls
`POST /adm/v1/esus-imports/installations/{id}/keys/{fingerprint}/revoke`.
Revocation invalidates further use of existing tokens for that key. Installation
suspension and per-run scope checks are also enforced on every protected request.

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

Validation on 2026-10-08: Maven `clean verify` completed using JDK 1.8.0_202;
19 tests passed and the two real e-SUS tests were skipped. The assembled 1.7 MiB
JAR has Java 8 bytecode (major 52), no AWS SDK classes, no `.env`, no keystore and
no obsolete authentication/delivery adapter classes. This verifies packaging and
synthetic behavior, not production AWS, GUI layout or Windows ACL operation.
