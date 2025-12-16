DELETE
FROM polsepakke p
WHERE p.yrkesaktivitetidentifikator = 'JORDBRUKER'
  AND EXISTS (
    SELECT 1 FROM polsepakke p2
    WHERE p2.person_id = p.person_id
      AND p2.yrkesaktivitetidentifikator = 'SELVSTENDIG'
);

UPDATE polsepakke
SET yrkesaktivitetidentifikator = 'SELVSTENDIG'
WHERE yrkesaktivitetidentifikator = 'JORDBRUKER';

UPDATE polsepakke_historikk
SET yrkesaktivitetidentifikator = 'SELVSTENDIG'
WHERE yrkesaktivitetidentifikator = 'JORDBRUKER';