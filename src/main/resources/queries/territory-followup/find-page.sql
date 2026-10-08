WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_acomp_cidadaos_vinc AS source_id,
           s.co_cidadao,
           s.co_fat_cidadao_pec,
           s.nu_cnes_vinc_equipe,
           s.nu_ine_vinc_equipe,
           s.nu_micro_area_tb_cidadao,
           s.nu_micro_area_domicilio,
           (FALSE) AS _invalid,
           (s.co_fat_cidadao_pec IS NULL) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_acomp_cidadaos_vinculados s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, co_cidadao, co_fat_cidadao_pec, nu_cnes_vinc_equipe, nu_ine_vinc_equipe, nu_micro_area_tb_cidadao, nu_micro_area_domicilio, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
