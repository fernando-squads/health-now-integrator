WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_cidadao AS source_id, s.co_fat_cad_individual, s.co_fat_familia, s.co_dim_tempo, s.st_vivo, s.st_mudou, s.st_ficha_inativa,
           (t.dt_registro IS NULL) AS _invalid,
           FALSE AS _unmatched,
           (t.dt_registro IS NULL OR t.dt_registro <= p.cutoff) AS _eligible
    FROM public.tb_fat_cidadao s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo=s.co_dim_tempo
)
SELECT count(*) AS source_count, count(*) FILTER (WHERE NOT _eligible) AS skipped_count FROM records;
