-- Run manually as CAMPUSCONNECT after 01_tables.sql.
-- Replacing these views affects only the views; no tables or data are changed.

WHENEVER SQLERROR EXIT SQL.SQLCODE

CREATE OR REPLACE VIEW v_cc_event_registrations AS
SELECT e.event_id,
       e.event_code,
       e.event_name,
       e.participation_mode,
       r.registration_id,
       r.registration_type,
       CASE r.registration_type
           WHEN 'INDIVIDUAL' THEN u.display_name
           WHEN 'TEAM' THEN t.team_name
       END AS registrant_name,
       CASE r.registration_type
           WHEN 'INDIVIDUAL' THEN 1
           WHEN 'TEAM' THEN (SELECT COUNT(*) FROM cc_team_members tm WHERE tm.team_id = t.team_id)
       END AS participant_count,
       r.registration_status,
       r.registered_at
  FROM cc_events e
  JOIN cc_registrations r ON r.event_id = e.event_id
  LEFT JOIN cc_users u ON u.user_id = r.participant_user_id
  LEFT JOIN cc_teams t ON t.team_id = r.team_id;

CREATE OR REPLACE VIEW v_cc_event_results AS
WITH criterion_scores AS (
    SELECT s.event_id,
           s.registration_id,
           s.criterion_id,
           AVG(s.awarded_points) AS avg_awarded_points,
           c.max_points,
           c.weight_percent
      FROM cc_scores s
      JOIN cc_scoring_criteria c
        ON c.criterion_id = s.criterion_id
       AND c.event_id = s.event_id
     GROUP BY s.event_id, s.registration_id, s.criterion_id, c.max_points, c.weight_percent
), registration_totals AS (
    SELECT r.registration_id,
           r.event_id,
           ROUND(SUM((cs.avg_awarded_points / cs.max_points) * cs.weight_percent), 2)
               AS weighted_score,
           COUNT(cs.criterion_id) AS criteria_scored
      FROM cc_registrations r
      LEFT JOIN criterion_scores cs
        ON cs.registration_id = r.registration_id
       AND cs.event_id = r.event_id
     WHERE r.registration_status = 'CONFIRMED'
     GROUP BY r.registration_id, r.event_id
)
SELECT e.event_id,
       e.event_code,
       e.event_name,
       r.registration_id,
       r.registration_type,
       CASE r.registration_type
           WHEN 'INDIVIDUAL' THEN u.display_name
           WHEN 'TEAM' THEN t.team_name
       END AS registrant_name,
       rt.weighted_score,
       rt.criteria_scored,
       DENSE_RANK() OVER (
           PARTITION BY e.event_id ORDER BY rt.weighted_score DESC NULLS LAST
       ) AS result_rank
  FROM cc_events e
  JOIN cc_registrations r ON r.event_id = e.event_id
  JOIN registration_totals rt
    ON rt.registration_id = r.registration_id
   AND rt.event_id = r.event_id
  LEFT JOIN cc_users u ON u.user_id = r.participant_user_id
  LEFT JOIN cc_teams t ON t.team_id = r.team_id;

PROMPT CampusConnect registration and result views created.
