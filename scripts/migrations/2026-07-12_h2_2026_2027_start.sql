BEGIN;

UPDATE study_period_rule
   SET start_date = DATE '2027-01-11',
       updated_at = NOW()
 WHERE academic_year = '2026/2027'
   AND code IN ('H2_1_9', 'H2_10')
   AND study_period = 'H2'
   AND start_date = DATE '2027-01-10';

COMMIT;
