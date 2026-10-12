-- Phase 7 additive scoring lifecycle migration.
-- Run only as CAMPUSCONNECT connected to FREEPDB1.
-- Does not drop objects or change registrations, criteria, or scores.
-- Existing complete COMPLETED events are marked finalized/published so their
-- already verified results remain publicly available through the new API.

WHENEVER SQLERROR EXIT SQL.SQLCODE

DECLARE
    v_count NUMBER;
    PROCEDURE add_column_if_missing(p_name VARCHAR2, p_definition VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_tab_columns
         WHERE table_name = 'CC_EVENTS' AND column_name = UPPER(p_name);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE cc_events ADD (' || p_name || ' ' || p_definition || ')';
        END IF;
    END;
    PROCEDURE add_constraint_if_missing(p_name VARCHAR2, p_definition VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_constraints
         WHERE table_name = 'CC_EVENTS' AND constraint_name = UPPER(p_name);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE cc_events ADD CONSTRAINT ' || p_name || ' CHECK (' || p_definition || ')';
        END IF;
    END;
BEGIN
    add_column_if_missing('scoring_opens', 'TIMESTAMP WITH TIME ZONE');
    add_column_if_missing('scoring_closes', 'TIMESTAMP WITH TIME ZONE');
    add_column_if_missing('results_finalized_at', 'TIMESTAMP WITH TIME ZONE');
    add_column_if_missing('results_published_at', 'TIMESTAMP WITH TIME ZONE');
    add_constraint_if_missing('ck_cc_events_scoring_window',
        '(scoring_opens IS NULL AND scoring_closes IS NULL) OR ' ||
        '(scoring_opens IS NOT NULL AND scoring_closes IS NOT NULL AND scoring_opens < scoring_closes)');
    add_constraint_if_missing('ck_cc_events_result_publish',
        'results_published_at IS NULL OR results_finalized_at IS NOT NULL');
END;
/

-- Preserve result visibility for legacy completed events only when their
-- criteria weights and all confirmed-registration/judge/criterion scores are
-- already complete. Score and result calculation data are not modified.
UPDATE cc_events e
   SET results_finalized_at = NVL(results_finalized_at, SYSTIMESTAMP),
       results_published_at = NVL(results_published_at, SYSTIMESTAMP)
 WHERE e.event_status = 'COMPLETED'
   AND e.results_finalized_at IS NULL
   AND e.results_published_at IS NULL
   AND (SELECT COUNT(*) FROM cc_registrations r
         WHERE r.event_id = e.event_id AND r.registration_status = 'CONFIRMED') > 0
   AND (SELECT COUNT(*) FROM cc_scoring_criteria c WHERE c.event_id = e.event_id) > 0
   AND (SELECT SUM(c.weight_percent) FROM cc_scoring_criteria c WHERE c.event_id = e.event_id) = 100
   AND (SELECT COUNT(*) FROM cc_event_judges j WHERE j.event_id = e.event_id) > 0
   AND NOT EXISTS (
       SELECT 1
         FROM cc_registrations r
         CROSS JOIN cc_event_judges j
         CROSS JOIN cc_scoring_criteria c
        WHERE r.event_id = e.event_id
          AND r.registration_status = 'CONFIRMED'
          AND j.event_id = e.event_id
          AND c.event_id = e.event_id
          AND NOT EXISTS (
              SELECT 1 FROM cc_scores s
               WHERE s.event_id = e.event_id
                 AND s.registration_id = r.registration_id
                 AND s.judge_user_id = j.judge_user_id
                 AND s.criterion_id = c.criterion_id));

COMMIT;

PROMPT Phase 7 scoring lifecycle columns and constraints are ready.
