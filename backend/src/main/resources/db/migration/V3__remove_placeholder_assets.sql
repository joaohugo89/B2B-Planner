-- Os template_assets do V2 eram só metadados de exemplo, sem arquivo real.
-- Os templates verdadeiros entram pelo upload da tela "Exportador de Templates".
DELETE FROM template_assets
WHERE code IN (
    'BG-V01', 'BG-V02', 'BG-V03', 'BG-V04', 'BG-V05', 'BG-V06',
    'SQ-01', 'SQ-02', 'SQ-03',
    'LOGO-SW-D', 'LOGO-SW-L', 'LOGO-HW-D', 'LOGO-HW-L', 'LOGO-GR-D', 'LOGO-GR-L',
    'GUIDE-45'
)
AND download_url IS NULL;
