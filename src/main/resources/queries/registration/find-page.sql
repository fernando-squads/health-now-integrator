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
           citizen.nu_telefone_residencial,
           citizen.nu_telefone_celular,
           citizen.nu_telefone_contato,
           citizen.ds_email AS no_email,
           citizen.ds_cep,
           citizen.ds_logradouro,
           citizen.no_bairro,
           citizen.nu_numero,
           locality.no_localidade,
           state.no_uf,
           (t.dt_registro IS NULL) AS _invalid,
           (c.co_seq_fat_cidadao_pec IS NULL) AS _unmatched,
           (t.dt_registro IS NULL OR t.dt_registro <= p.cutoff) AS _eligible
    FROM public.tb_fat_cad_individual s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = s.co_dim_tempo
    LEFT JOIN public.tb_fat_cidadao_pec c ON c.co_seq_fat_cidadao_pec = s.co_fat_cidadao_pec
    LEFT JOIN public.tb_cidadao citizen ON citizen.co_seq_cidadao = c.co_cidadao
    LEFT JOIN public.tb_localidade locality ON locality.co_localidade = citizen.co_localidade_endereco
    LEFT JOIN public.tb_uf state ON state.co_uf = citizen.co_uf
    WHERE TRUE
)
SELECT source_id, co_fat_cidadao_pec, nu_uuid_ficha, nu_uuid_ficha_origem, co_dim_profissional, co_dim_unidade_saude, co_dim_equipe, co_dim_tempo, nu_micro_area, st_ficha_inativa, st_recusa_cadastro, st_hipertensao_arterial, st_diabete, st_gestante, nu_telefone_residencial, nu_telefone_celular, nu_telefone_contato, no_email, ds_cep, ds_logradouro, no_bairro, nu_numero, no_localidade, no_uf, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
