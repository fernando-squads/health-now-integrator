WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_familia_territorio AS source_id,
           s.co_fat_cidadao_pec,
           s.co_fat_cidadao_territorio,
           s.co_fat_cad_domiciliar,
           s.co_dim_municipio,
           s.co_dim_unidade_saude,
           s.co_dim_equipe,
           s.nu_micro_area,
           s.st_familia_consistente,
           s.st_responsavel_vivo,
           (FALSE) AS _invalid,
           (s.co_fat_cidadao_pec IS NULL) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_fat_familia_territorio s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT count(*) AS source_count,
       count(*) FILTER (WHERE NOT _eligible) AS skipped_count
FROM records;
