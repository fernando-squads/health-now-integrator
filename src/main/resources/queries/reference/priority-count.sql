WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_prioridade_cuidado AS source_id, s.nu_identificador, s.ds_prioridade_cuidado,
           FALSE AS _invalid,
           FALSE AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_prioridade_cuidado s
    CROSS JOIN parameters p

)
SELECT count(*) AS source_count, count(*) FILTER (WHERE NOT _eligible) AS skipped_count FROM records;
