-- Run manually as CAMPUSCONNECT after 01_tables.sql, 03_views.sql, and
-- 04_sample_data.sql. This is read-only and raises an error if expected
-- objects, enabled constraints, relationships, or demo results are missing.

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM user_tables
     WHERE table_name IN (
         'CC_USERS', 'CC_EVENTS', 'CC_TEAMS', 'CC_TEAM_MEMBERS', 'CC_REGISTRATIONS',
         'CC_EVENT_JUDGES', 'CC_SCORING_CRITERIA', 'CC_SCORES'
     );
    IF v_count <> 8 THEN
        RAISE_APPLICATION_ERROR(-20010, 'Expected 8 CampusConnect tables; found ' || v_count);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_views
     WHERE view_name IN ('V_CC_EVENT_REGISTRATIONS', 'V_CC_EVENT_RESULTS');
    IF v_count <> 2 THEN
        RAISE_APPLICATION_ERROR(-20011, 'Expected both CampusConnect views; found ' || v_count);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_constraints
     WHERE table_name IN (
         'CC_USERS', 'CC_EVENTS', 'CC_TEAMS', 'CC_TEAM_MEMBERS', 'CC_REGISTRATIONS',
         'CC_EVENT_JUDGES', 'CC_SCORING_CRITERIA', 'CC_SCORES'
     )
       AND constraint_type IN ('P', 'R', 'U', 'C')
       AND status = 'ENABLED'
       AND validated = 'VALIDATED';
    IF v_count < 30 THEN
        RAISE_APPLICATION_ERROR(-20012, 'Expected at least 30 enabled and validated constraints; found ' || v_count);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_constraints
     WHERE constraint_name IN (
         'PK_CC_USERS', 'PK_CC_EVENTS', 'PK_CC_TEAMS', 'PK_CC_TEAM_MEMBERS',
         'PK_CC_REGISTRATIONS', 'PK_CC_EVENT_JUDGES', 'PK_CC_SCORING_CRITERIA', 'PK_CC_SCORES',
         'UQ_CC_REG_ID_EVENT', 'UQ_CC_SCORE_ONCE',
         'FK_CC_SCORE_REG_EVENT', 'FK_CC_SCORE_CRIT_EVENT', 'FK_CC_SCORE_JUDGE'
     )
       AND status = 'ENABLED'
       AND validated = 'VALIDATED';
    IF v_count <> 13 THEN
        RAISE_APPLICATION_ERROR(-20015, 'Expected all primary, duplicate-prevention, and score relationship constraints; found ' || v_count);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_indexes
     WHERE index_name IN ('UX_CC_REG_EVENT_USER', 'UX_CC_REG_EVENT_TEAM')
       AND uniqueness = 'UNIQUE'
       AND status = 'VALID';
    IF v_count <> 2 THEN
        RAISE_APPLICATION_ERROR(-20018, 'Expected both valid filtered unique registration indexes; found ' || v_count);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM cc_registrations r
      JOIN cc_events e ON e.event_id = r.event_id
     WHERE e.event_code LIKE 'DEMO-%';
    IF v_count <> 2 THEN
        RAISE_APPLICATION_ERROR(-20019, 'Expected exactly two demo registrations; found ' || v_count);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM cc_registrations r
      JOIN cc_events e ON e.event_id = r.event_id
     WHERE e.event_code LIKE 'DEMO-%'
       AND ((e.participation_mode = 'TEAM' AND r.registration_type = 'TEAM'
            AND r.team_id IS NOT NULL AND r.participant_user_id IS NULL)
        OR (e.participation_mode = 'INDIVIDUAL' AND r.registration_type = 'INDIVIDUAL'
            AND r.participant_user_id IS NOT NULL AND r.team_id IS NULL));
    IF v_count <> 2 THEN
        RAISE_APPLICATION_ERROR(-20013, 'Expected two mode-consistent demo registrations; found ' || v_count);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM cc_team_members tm
      JOIN cc_teams t ON t.team_id = tm.team_id
      JOIN cc_users u ON u.user_id = tm.user_id
     WHERE t.team_name = 'DEMO Team Alpha';
    IF v_count <> 2 THEN
        RAISE_APPLICATION_ERROR(-20016, 'Expected two related members for DEMO Team Alpha; found ' || v_count);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM cc_scores s
      JOIN cc_registrations r
        ON r.registration_id = s.registration_id AND r.event_id = s.event_id
      JOIN cc_scoring_criteria c
        ON c.criterion_id = s.criterion_id AND c.event_id = s.event_id
      JOIN cc_event_judges j
        ON j.event_id = s.event_id AND j.judge_user_id = s.judge_user_id
      JOIN cc_events e ON e.event_id = s.event_id
     WHERE e.event_code LIKE 'DEMO-%';
    IF v_count <> 2 THEN
        RAISE_APPLICATION_ERROR(-20017, 'Expected two scores linked to their registrations, criteria, and assigned judges; found ' || v_count);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM v_cc_event_results
     WHERE (event_code = 'DEMO-TEAM-2026' AND registrant_name = 'DEMO Team Alpha'
            AND weighted_score = 85 AND criteria_scored = 1)
        OR (event_code = 'DEMO-IND-2026' AND registrant_name = 'DEMO Student One'
            AND weighted_score = 90 AND criteria_scored = 1);
    IF v_count <> 2 THEN
        RAISE_APPLICATION_ERROR(-20014, 'Expected team and individual demo results at 85 and 90; found ' || v_count);
    END IF;

    DBMS_OUTPUT.PUT_LINE('PASS: 8 tables, 2 views, and at least 30 enabled/validated constraints exist.');
    DBMS_OUTPUT.PUT_LINE('PASS: two registrations match their event participation modes.');
    DBMS_OUTPUT.PUT_LINE('PASS: demo event results are 85 (team) and 90 (individual).');
END;
/

PROMPT CampusConnect object inventory:
SELECT object_type, object_name, status
  FROM user_objects
 WHERE object_name LIKE 'CC\_%' ESCAPE '\'
    OR object_name LIKE 'V\_CC\_%' ESCAPE '\'
 ORDER BY object_type, object_name;

PROMPT CampusConnect enabled constraints:
SELECT table_name, constraint_name, constraint_type, status, validated
  FROM user_constraints
 WHERE table_name LIKE 'CC\_%' ESCAPE '\'
 ORDER BY table_name, constraint_type, constraint_name;

PROMPT Demo registration rows:
SELECT event_code, event_name, participation_mode, registration_type,
       registrant_name, participant_count, registration_status
  FROM v_cc_event_registrations
 WHERE event_code LIKE 'DEMO-%'
 ORDER BY event_code, registrant_name;

PROMPT Expected demo results (team 85, individual 90):
SELECT event_code, event_name, registration_type, registrant_name,
       weighted_score, criteria_scored, result_rank
  FROM v_cc_event_results
 WHERE event_code LIKE 'DEMO-%'
 ORDER BY event_code, result_rank, registrant_name;
