-- Run manually as CAMPUSCONNECT after 01_tables.sql.
-- All accounts below are marked DEMO, use reserved .invalid email addresses,
-- and have no password/authentication column. They are not login accounts.
-- The whole dependent data set is one transaction. A failure rolls back all
-- sample inserts; the final COMMIT occurs only after every insert succeeds.

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DECLARE
    v_student_one     cc_users.user_id%TYPE;
    v_student_two     cc_users.user_id%TYPE;
    v_organizer       cc_users.user_id%TYPE;
    v_judge           cc_users.user_id%TYPE;
    v_admin           cc_users.user_id%TYPE;
    v_team_event      cc_events.event_id%TYPE;
    v_individual_event cc_events.event_id%TYPE;
    v_team            cc_teams.team_id%TYPE;
    v_team_registration cc_registrations.registration_id%TYPE;
    v_individual_registration cc_registrations.registration_id%TYPE;
    v_team_criterion  cc_scoring_criteria.criterion_id%TYPE;
    v_ind_criterion   cc_scoring_criteria.criterion_id%TYPE;
BEGIN
    INSERT INTO cc_users (email, display_name, role_code)
    VALUES ('demo.student.one@campusconnect.invalid', 'DEMO Student One', 'STUDENT')
    RETURNING user_id INTO v_student_one;

    INSERT INTO cc_users (email, display_name, role_code)
    VALUES ('demo.student.two@campusconnect.invalid', 'DEMO Student Two', 'STUDENT')
    RETURNING user_id INTO v_student_two;

    INSERT INTO cc_users (email, display_name, role_code)
    VALUES ('demo.organizer@campusconnect.invalid', 'DEMO Organizer', 'ORGANIZER')
    RETURNING user_id INTO v_organizer;

    INSERT INTO cc_users (email, display_name, role_code)
    VALUES ('demo.judge@campusconnect.invalid', 'DEMO Judge', 'JUDGE')
    RETURNING user_id INTO v_judge;

    INSERT INTO cc_users (email, display_name, role_code)
    VALUES ('demo.admin@campusconnect.invalid', 'DEMO Admin', 'ADMIN')
    RETURNING user_id INTO v_admin;

    INSERT INTO cc_events (
        event_code, event_name, description, owner_user_id, participation_mode,
        event_status, registration_opens, registration_closes, starts_at, ends_at,
        capacity, location
    ) VALUES (
        'DEMO-TEAM-2026', 'DEMO Team Challenge', 'Non-login demo team event.', v_organizer, 'TEAM',
        'COMPLETED',
        TO_TIMESTAMP_TZ('2026-07-01 09:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        TO_TIMESTAMP_TZ('2026-08-01 17:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        TO_TIMESTAMP_TZ('2026-08-10 09:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        TO_TIMESTAMP_TZ('2026-08-10 17:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        20, 'DEMO Hall A'
    ) RETURNING event_id INTO v_team_event;

    INSERT INTO cc_events (
        event_code, event_name, description, owner_user_id, participation_mode,
        event_status, registration_opens, registration_closes, starts_at, ends_at,
        capacity, location
    ) VALUES (
        'DEMO-IND-2026', 'DEMO Individual Showcase', 'Non-login demo individual event.', v_organizer,
        'INDIVIDUAL', 'COMPLETED',
        TO_TIMESTAMP_TZ('2026-07-01 09:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        TO_TIMESTAMP_TZ('2026-08-15 17:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        TO_TIMESTAMP_TZ('2026-09-01 09:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        TO_TIMESTAMP_TZ('2026-09-01 17:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        30, 'DEMO Hall B'
    ) RETURNING event_id INTO v_individual_event;

    INSERT INTO cc_teams (team_name, created_by_user_id)
    VALUES ('DEMO Team Alpha', v_student_one)
    RETURNING team_id INTO v_team;

    INSERT INTO cc_team_members (team_id, user_id) VALUES (v_team, v_student_one);
    INSERT INTO cc_team_members (team_id, user_id) VALUES (v_team, v_student_two);

    INSERT INTO cc_registrations (
        event_id, registration_type, team_id, registration_status, registered_at
    ) VALUES (
        v_team_event, 'TEAM', v_team, 'CONFIRMED',
        TO_TIMESTAMP_TZ('2026-07-15 10:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM')
    ) RETURNING registration_id INTO v_team_registration;

    INSERT INTO cc_registrations (
        event_id, registration_type, participant_user_id, registration_status, registered_at
    ) VALUES (
        v_individual_event, 'INDIVIDUAL', v_student_one, 'CONFIRMED',
        TO_TIMESTAMP_TZ('2026-07-20 10:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM')
    ) RETURNING registration_id INTO v_individual_registration;

    INSERT INTO cc_event_judges (event_id, judge_user_id) VALUES (v_team_event, v_judge);
    INSERT INTO cc_event_judges (event_id, judge_user_id) VALUES (v_individual_event, v_judge);

    INSERT INTO cc_scoring_criteria (event_id, criterion_name, max_points, weight_percent, display_order)
    VALUES (v_team_event, 'DEMO Overall Score', 10, 100, 1)
    RETURNING criterion_id INTO v_team_criterion;

    INSERT INTO cc_scoring_criteria (event_id, criterion_name, max_points, weight_percent, display_order)
    VALUES (v_individual_event, 'DEMO Presentation Score', 10, 100, 1)
    RETURNING criterion_id INTO v_ind_criterion;

    INSERT INTO cc_scores (event_id, registration_id, criterion_id, judge_user_id, awarded_points, feedback)
    VALUES (v_team_event, v_team_registration, v_team_criterion, v_judge, 8.50,
            'DEMO score; not a real evaluation.');

    INSERT INTO cc_scores (event_id, registration_id, criterion_id, judge_user_id, awarded_points, feedback)
    VALUES (v_individual_event, v_individual_registration, v_ind_criterion, v_judge, 9.00,
            'DEMO score; not a real evaluation.');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('DEMO sample data inserted and committed.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/
