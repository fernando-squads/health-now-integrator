# Local database extraction

## Execution and ownership

After the Swing connection succeeds, `ExtractionService` uses a dedicated connection from
`ConnectionFactory.openExtractionConnection()`. PostgreSQL enforces a read-only,
repeatable-read transaction. Every page and count observes that same snapshot.
The service ends the transaction before compression. The caller closes the connection.

Java 8 is required. This component implements local extraction and file generation,
not downstream consolidation. The Swing workflow now uses `IntegratorService` to
authorize delivery, upload to S3 and confirm API custody; see `LOAD_DELIVERY.md`.

## Components

- `CitizenJDBC`: DW citizen, operational identity, individual registration and citizen-history bridge.
- `TerritoryJDBC`: territory and materialized follow-up references.
- `FamilyJDBC`: territorial family, household, family registration and family history.
- `AcsJDBC`: candidate ACS relationships, with explicit ambiguity.
- `AttendanceJDBC`, `AttendanceProblemJDBC`, `AttendanceExamJDBC`,
  `AttendanceProcedureJDBC`, `AttendanceReferralJDBC`: independent clinical collections.
- `ReferenceJDBC`: clinical dimensions and operational professional/team/unit identifiers.
- `ExtractionRepository`: bound parameters, bounded pages, safe errors and resource cleanup.
- `ExtractionRecord`: ordered projection DTO; its fields come exclusively from explicit SQL columns.
  Null values and numeric precision are preserved. Dates use ISO strings and timestamp-with-time-zone
  values use UTC instants.
- `ExtractionOptions`: run configuration.
- `LoadFileWriter`: UTF-8 JSONL, hashes, manifest serialization and immutable-by-creation ZIP output.

## SQL and file inventory

All paths below are relative to `src/main/resources/queries`. Every page query also
has a `count.sql` (or `*-count.sql`) companion. `citizen/find-by-id.sql` supports lookup
without using a patient identifier as SQL text. All SQL is loaded by `SqlQueryLoader`.
The existing `cidadao/find-all.sql` remains for the legacy API.

| Local entity file | Source table | Page SQL |
| --- | --- | --- |
| `citizen.jsonl` | `tb_fat_cidadao_pec` | `citizen/find-page.sql` |
| `citizen-source.jsonl` | `tb_cidadao` | `citizen-source/find-page.sql` |
| `registration.jsonl` | `tb_fat_cad_individual` | `registration/find-page.sql` |
| `territory.jsonl` | `tb_fat_cidadao_territorio` | `territory/find-page.sql` |
| `territory-followup.jsonl` | `tb_acomp_cidadaos_vinculados` | `territory-followup/find-page.sql` |
| `family.jsonl` | `tb_fat_familia_territorio` | `family/find-page.sql` |
| `family-registration.jsonl` | `tb_fat_cad_dom_familia` | `family-registration/find-page.sql` |
| `family-history.jsonl` | `tb_fat_familia` | `family-history/find-page.sql` |
| `acs.jsonl` | `tb_cidadao_nucleo_familiar` | `acs/find-page.sql` |
| `attendance.jsonl` | `tb_fat_atendimento_individual` | `attendance/find-page.sql` |
| `attendance-problem.jsonl` | `tb_fat_atd_ind_problemas` | `attendance-problem/find-page.sql` |
| `attendance-exam.jsonl` | `tb_fat_atd_ind_exames` | `attendance-exam/find-page.sql` |
| `attendance-procedure.jsonl` | `tb_fat_atd_ind_procedimentos` | `attendance-procedure/find-page.sql` |
| `attendance-referral.jsonl` | `tb_fat_atd_ind_encaminhamentos` | `attendance-referral/find-page.sql` |
| `professional.jsonl` | `tb_dim_profissional` | `reference/professional-find-page.sql` |
| `team.jsonl` | `tb_dim_equipe` | `reference/team-find-page.sql` |
| `health-unit.jsonl` | `tb_dim_unidade_saude` | `reference/health-unit-find-page.sql` |
| `operational-professional.jsonl` | `tb_prof` | `reference/operational-professional-find-page.sql` |
| `operational-team.jsonl` | `tb_equipe` | `reference/operational-team-find-page.sql` |
| `operational-health-unit.jsonl` | `tb_unidade_saude` | `reference/operational-health-unit-find-page.sql` |
| `cid.jsonl` | `tb_dim_cid` | `reference/cid-find-page.sql` |
| `ciap.jsonl` | `tb_dim_ciap` | `reference/ciap-find-page.sql` |
| `procedure.jsonl` | `tb_dim_procedimento` | `reference/procedure-find-page.sql` |
| `specialty.jsonl` | `tb_dim_especialidade` | `reference/specialty-find-page.sql` |
| `problem-status.jsonl` | `tb_dim_situacao_problema` | `reference/problem-status-find-page.sql` |
| `sex.jsonl` | `tb_dim_sexo` | `reference/sex-find-page.sql` |
| `time.jsonl` | `tb_dim_tempo` | `reference/time-find-page.sql` |
| `cbo.jsonl` | `tb_dim_cbo` | `reference/cbo-find-page.sql` |
| `referral-risk.jsonl` | `tb_dim_classificacao_risc_enc` | `reference/referral-risk-find-page.sql` |
| `attendance-type.jsonl` | `tb_dim_tipo_atendimento` | `reference/attendance-type-find-page.sql` |
| `municipality.jsonl` | `tb_dim_municipio` | `reference/municipality-find-page.sql` |
| `priority.jsonl` | `tb_dim_prioridade_cuidado` | `reference/priority-find-page.sql` |
| `citizen-history.jsonl` | `tb_fat_cidadao` | `citizen-history/find-page.sql` |
| `household.jsonl` | `tb_fat_cad_domiciliar` | `household/find-page.sql` |

`verification/transaction-state.sql` verifies server-side transaction settings in integration tests.

The additional citizen-history and household projections resolve the physical keys used by
family-history. They do not export household addresses or protected identity values.
`tb_prof`, `tb_lotacao`, `tb_cbo`, `tb_equipe` and `tb_unidade_saude` also participate
in the ACS lookup; lotations and CBO operational rows are not separate export entities.

## Relationships

Every file preserves the physical source ID as `source_id` and the original foreign-key
column names. IDs are scoped by installation and source entity. An operational citizen ID
is not a DW citizen ID. Resolve `citizen.co_cidadao` against `citizen-source.source_id`.
Clinical children resolve `co_fat_atd_ind` against `attendance.source_id`, with no
one-to-many flattening. A missing parent does not remove a child.

Territory resolves `co_fat_familia_territorio` against `family.source_id`.
Family history uses `co_fat_cad_dom_familia` and `co_fat_cidadao` to reference
`family-registration` and `citizen-history`. The latter references `registration`,
which carries the DW citizen key. Household references target `household`.
Do not manufacture a family relationship from an address or a null `co_fat_familia`.

ACS records are restricted to source CBO 515105. Professional CNS, lotation CBO,
team INE and unit CNES must match. The lateral aggregate preserves one row per
family-nucleus record even if multiple lotations match. Only exactly one match yields
professional/team/unit IDs; zero or multiple matches are flagged as unmatched.
Operational professional/team/unit files resolve those IDs. Lotations can be historical;
these are source relationship candidates, not proven current assignments or access grants.

Clinical CID/CIAP foreign keys resolve against their actual local dimensions, including
the different physical names in referrals. Priority resolves to
`tb_dim_prioridade_cuidado`, as confirmed by the database foreign key.

## Cutoff and paging semantics

The cutoff is an inclusive ISO calendar date. Facts with `co_dim_tempo` join
`tb_dim_tempo.dt_registro`; clinical children use the parent's date, falling back
to their own date when unavailable. Unknown dates remain in the output and are
flagged invalid. Exams are selected by attendance date; their result may have been
recorded later. This is not a historical database reconstruction.

Undated/current-state entities (citizens, ACS, territory, territorial families and
reference dimensions) reflect the current consistent snapshot, even for an old
clinical cutoff. Future dimensions remain available to resolve source references.
No sequential ID is used as an incremental-update checkpoint. Every run performs
full reconciliation of the local database; no municipality sub-filter is currently applied.

Pages use `source_id > ? ORDER BY source_id LIMIT ?`. The cursor starts anew for
each entity and run. Non-advancing/duplicate IDs abort the run. Failed snapshots are
not resumed; a retry receives new run/file UUIDs.

## Privacy and reconciliation

Only explicit SQL projections are exported. In particular:
- `tb_fat_cad_individual.no_nome`, other protected registration names/contact fields,
  addresses, family-responsible identifiers and unrelated personal fields are excluded.
- CPF/CNS are strings and appear only in citizen identity projections; professional
  CNS is also validated. Exactly 11/15 ASCII digits are required. This is format validation,
  not a check-digit or identity match. Invalid values become null, without decryption.
- No fallback guesses, name-based merging, automatic access grants or deletion inference.
- The current clear-name sources are operational/DW materialized identity fields.
  Source changes require a new review of those projections.
- Logs and displayed driver failures do not include raw driver messages or causes.

Each entity in `reconciliation.json` records `source`, `extracted`, `skipped`,
`invalid` and `unmatched`. `source = extracted + skipped`; skipped counts
refer to cutoff exclusion within that entity's source scope (ACS scope is CBO 515105).
Invalid and unmatched count affected records, can overlap, and remain included in
extracted. Per-record flags preserve those pending cases.

Unmatched checks cover primary citizen/attendance links, family-territory links,
null family-registration references and ACS ambiguity. This is not a full
foreign-key audit of every exported dimension or a downstream identity adjudication.

## Artifacts and failure behavior

Default output:
```text
exports/<run_id>/
  <entity>.jsonl (34 files)
  reconciliation.json
  manifest.json
  <file_id>.zip
  archive.json
```

The ZIP contains the entity files, reconciliation and manifest. The manifest includes
installation, scope, source/mapping/schema versions, cutoff, timestamps, entity counts,
byte sizes and SHA-256 values. `archive.json` describes final ZIP size/hash and IDs;
a ZIP cannot contain its own final hash without creating a circular dependency.

A run initially uses `<run_id>.partial`. It becomes a completed directory only after
all queries, reconciliation, metadata and compression succeed. Partial directories are
retained for diagnosis and are never advertised as completed loads. There is no
automatic retention cleanup. On POSIX systems run directories are owner-only, JSON
files are owner-readable/writable and the completed ZIP is owner-readable only.
Consumers must verify checksums; filesystem permissions alone are not tamper-proof storage.

Output is ignored by Git. Keep customized output directories outside source control.
The `local-jsonl-1` / `esus-local-1` format is accepted by the file transport only
with a compatible approved run/cut. Transport acceptance is not clinical mapping
homologation. Do not treat local generation or API custody as server publication.

## Configuration and validation

System properties:
- `integrator.output`: output directory (default `exports`).
- `integrator.installation`: installation ID (default `local-esus`, local testing only).
- The delivery run UUID is resolved internally from the signed API response; it is not a user setting.
- `integrator.cutoff`: inclusive date, default today in the local timezone.
- `integrator.pageSize`: 1–10000, default 1000.
- `integrator.timeoutSeconds`: per-query timeout, default 30.

For desktop delivery, the signed API response supplies installation and run IDs. `source_version` is read automatically
through `configuration/find-source-version.sql` from `tb_config_sistema.ds_texto`,
where `co_config_sistema = 'VERSAOBANCODADOS'`, within the extraction snapshot.
It denotes the PEC database version, not the PostgreSQL server or application binary.
Missing, blank or duplicate version records abort extraction; no manual override is used.
Physical compatibility is checked by query execution.
The local schema was inspected on 2026-10-08; institutional/domain homologation is separate.

```sh
mvn test
mvn -Desus.integration=true test
mvn clean package
java -Dintegrator.api=https://api.example.invalid \
  -Dintegrator.cutoff=2026-10-08 \
  -jar target/integrator-1.0.0-jar-with-dependencies.jar
```

Database tests are opt-in and use `esus.host`, `esus.port`, `esus.database`,
`esus.user` and environment variable `ESUS_PASSWORD`. They read the database and
generate a real ignored local archive. They verify server read-only/repeatable-read
settings, EXPLAIN for every page query, page boundaries, lookup, cutoff counts,
record counts, identifier formats, hashes and ZIP membership.
Unit tests cover missing resources, allowlisted projections, UTF-8/null output,
overwrite prevention, invalid configuration and safe failure without a completed artifact.

Maven verification uses JDK 1.8.0_202 and a locally installed Maven executable.
Synthetic extraction and delivery tests run without external AWS or patient data.
Real e-SUS tests were not rerun for secure delivery. Visual Swing behavior and
Windows ACL enforcement still require platform validation before rollout.
