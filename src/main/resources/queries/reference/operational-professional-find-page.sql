WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_prof AS source_id,
           s.no_civil_profissional,
           CASE WHEN s.nu_cpf ~ '^[0-9]{11}$' THEN s.nu_cpf END AS cpf,
           CASE WHEN s.nu_cns ~ '^[0-9]{15}$' THEN s.nu_cns END AS cns,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_prof s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, no_civil_profissional, cpf, cns, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
