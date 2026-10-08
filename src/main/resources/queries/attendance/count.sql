WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_atd_ind AS source_id,
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
           s.nu_uuid_ficha,
           s.nu_atendimento,
           s.dt_inicial_atendimento,
           s.dt_final_atendimento,
           s.nu_peso,
           s.nu_altura,
           s.nu_pressao_sistolica,
           s.nu_pressao_diastolica,
           s.nu_temperatura,
           s.nu_saturacao_o2,
           s.nu_glicemia,
           s.co_dim_tipo_atendimento,
           (t.dt_registro IS NULL) AS _invalid,
           (c.co_seq_fat_cidadao_pec IS NULL) AS _unmatched,
           (t.dt_registro IS NULL OR t.dt_registro <= p.cutoff) AS _eligible
    FROM public.tb_fat_atendimento_individual s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo=s.co_dim_tempo
    LEFT JOIN public.tb_fat_cidadao_pec c ON c.co_seq_fat_cidadao_pec=s.co_fat_cidadao_pec
    WHERE TRUE
)
SELECT count(*) AS source_count,
       count(*) FILTER (WHERE NOT _eligible) AS skipped_count
FROM records;
