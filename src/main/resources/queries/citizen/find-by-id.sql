WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_cidadao_pec AS source_id,
           s.co_cidadao,
           s.no_cidadao,
           CASE WHEN s.nu_cpf_cidadao ~ '^[0-9]{11}$' THEN s.nu_cpf_cidadao END AS cpf,
           CASE WHEN s.nu_cns ~ '^[0-9]{15}$' THEN s.nu_cns END AS cns,
           s.co_dim_tempo_nascimento,
           s.co_dim_sexo,
           s.st_faleceu,
           s.st_deletar,
           s.co_dim_unidade_saude_vinc,
           s.co_dim_equipe_vinc,
           ((s.nu_cpf_cidadao IS NOT NULL AND s.nu_cpf_cidadao !~ '^[0-9]{11}$') OR (s.nu_cns IS NOT NULL AND s.nu_cns !~ '^[0-9]{15}$')) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_fat_cidadao_pec s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, co_cidadao, no_cidadao, cpf, cns, co_dim_tempo_nascimento, co_dim_sexo, st_faleceu, st_deletar, co_dim_unidade_saude_vinc, co_dim_equipe_vinc, _invalid, _unmatched FROM records WHERE source_id = ?;
