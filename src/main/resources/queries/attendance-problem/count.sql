WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_atend_ind_problemas AS source_id,
           s.co_fat_atd_ind,
           s.co_fat_cidadao_pec,
           s.co_dim_tempo,
           s.co_dim_profissional_1,
           s.co_dim_profissional_2,
           s.co_dim_unidade_saude_1,
           s.co_dim_unidade_saude_2,
           s.co_dim_equipe_1,
           s.co_dim_equipe_2,
           s.co_dim_cbo_1,
           s.co_dim_cbo_2,
           s.co_dim_cid,
           s.co_dim_ciap,
           s.nu_uuid_problema,
           s.co_unico_evolucao,
           s.co_sequencial_evolucao,
           s.co_dim_situacao_problema,
           s.co_dim_data_inicio_problema,
           s.co_dim_data_fim_problema,
           s.st_avaliado,
           (t.dt_registro IS NULL) AS _invalid,
           (a.co_seq_fat_atd_ind IS NULL OR c.co_seq_fat_cidadao_pec IS NULL) AS _unmatched,
           (COALESCE(pt.dt_registro, t.dt_registro) IS NULL OR COALESCE(pt.dt_registro, t.dt_registro) <= p.cutoff) AS _eligible
    FROM public.tb_fat_atd_ind_problemas s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_fat_atendimento_individual a ON a.co_seq_fat_atd_ind=s.co_fat_atd_ind
    LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo=s.co_dim_tempo
    LEFT JOIN public.tb_dim_tempo pt ON pt.co_seq_dim_tempo=a.co_dim_tempo
    LEFT JOIN public.tb_fat_cidadao_pec c ON c.co_seq_fat_cidadao_pec=s.co_fat_cidadao_pec
    WHERE TRUE
)
SELECT count(*) AS source_count,
       count(*) FILTER (WHERE NOT _eligible) AS skipped_count
FROM records;
