WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_cad_individual AS source_id,
           s.co_fat_cidadao_pec,
           s.nu_uuid_ficha,
           s.nu_uuid_ficha_origem,
           s.co_dim_profissional,
           s.co_dim_unidade_saude,
           s.co_dim_equipe,
           s.co_dim_tempo,
           s.nu_micro_area,
           s.st_ficha_inativa,
           s.st_recusa_cadastro,
           s.st_hipertensao_arterial,
           s.st_diabete,
           s.st_gestante,
           (t.dt_registro IS NULL) AS _invalid,
           (c.co_seq_fat_cidadao_pec IS NULL) AS _unmatched,
           (t.dt_registro IS NULL OR t.dt_registro <= p.cutoff) AS _eligible
    FROM public.tb_fat_cad_individual s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = s.co_dim_tempo
    LEFT JOIN public.tb_fat_cidadao_pec c ON c.co_seq_fat_cidadao_pec = s.co_fat_cidadao_pec
    WHERE TRUE
)
SELECT count(*) AS source_count,
       count(*) FILTER (WHERE NOT _eligible) AS skipped_count
FROM records;
