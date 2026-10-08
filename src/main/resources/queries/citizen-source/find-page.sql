WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_cidadao AS source_id,
           s.no_cidadao,
           CASE WHEN s.nu_cpf ~ '^[0-9]{11}$' THEN s.nu_cpf END AS cpf,
           CASE WHEN s.nu_cns ~ '^[0-9]{15}$' THEN s.nu_cns END AS cns,
           s.dt_nascimento,
           s.st_faleceu,
           s.st_ativo,
           s.st_unificado,
           ((s.nu_cpf IS NOT NULL AND s.nu_cpf !~ '^[0-9]{11}$') OR (s.nu_cns IS NOT NULL AND s.nu_cns !~ '^[0-9]{15}$')) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_cidadao s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, no_cidadao, cpf, cns, dt_nascimento, st_faleceu, st_ativo, st_unificado, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
