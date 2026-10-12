-- Phase 4 migration. Run manually as CAMPUSCONNECT against FREEPDB1.
-- This migration is additive: it preserves every existing account and row.
-- Credential columns remain NULL for old accounts, which consequently cannot log in.
-- Oracle DDL commits implicitly; stop and inspect USER_TAB_COLUMNS/USER_INDEXES if this fails.

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'CC_USERS'
       AND column_name IN ('PASSWORD_SALT', 'PASSWORD_HASH', 'PASSWORD_ITERATIONS');

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cc_users ADD (
            password_salt RAW(16),
            password_hash RAW(32),
            password_iterations NUMBER(7)
        )';
    ELSIF v_count <> 3 THEN
        RAISE_APPLICATION_ERROR(-20040,
            'Only some auth credential columns exist; inspect CC_USERS before continuing.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_constraints
     WHERE table_name = 'CC_USERS'
       AND constraint_name = 'CK_CC_USERS_PASSWORD_FIELDS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cc_users ADD CONSTRAINT ck_cc_users_password_fields CHECK (
            (password_salt IS NULL AND password_hash IS NULL AND password_iterations IS NULL)
            OR
            (password_salt IS NOT NULL AND password_hash IS NOT NULL
             AND password_iterations IS NOT NULL AND password_iterations >= 600000)
        ) ENABLE VALIDATE';
    ELSE
        SELECT COUNT(*) INTO v_count
          FROM user_constraints
         WHERE table_name = 'CC_USERS'
           AND constraint_name = 'CK_CC_USERS_PASSWORD_FIELDS'
           AND status = 'ENABLED'
           AND validated = 'VALIDATED';
        IF v_count <> 1 THEN
            RAISE_APPLICATION_ERROR(-20042,
                'Password credential constraint exists but is not enabled and validated. Inspect it before continuing.');
        END IF;
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM (
        SELECT LOWER(TRIM(email)) normalized_email
          FROM cc_users
         GROUP BY LOWER(TRIM(email))
        HAVING COUNT(*) > 1
      );
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20041,
            'Case-insensitive email duplicates exist. Resolve them without deleting accounts, then retry.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_indexes
     WHERE index_name = 'UX_CC_USERS_EMAIL_NORM'
       AND uniqueness = 'UNIQUE'
       AND status = 'VALID';
    IF v_count = 0 THEN
        SELECT COUNT(*) INTO v_count
          FROM user_indexes
         WHERE index_name = 'UX_CC_USERS_EMAIL_NORM';
        IF v_count > 0 THEN
            RAISE_APPLICATION_ERROR(-20043,
                'Normalized-email index exists but is not unique and valid. Inspect it before continuing.');
        END IF;
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ux_cc_users_email_norm
                           ON cc_users (LOWER(TRIM(email)))';
    END IF;
END;
/

PROMPT Phase 4 credential columns and normalized-email index are ready.
PROMPT Existing accounts retain NULL password metadata and cannot log in until a password is set.
SELECT
    SYS_CONTEXT('USERENV', 'SESSION_USER') AS account_name,
    SYS_CONTEXT('USERENV', 'CON_NAME') AS database_name
FROM dual;

SELECT column_name
FROM user_tab_columns
WHERE table_name = 'CC_USERS'
  AND column_name IN (
      'PASSWORD_SALT',
      'PASSWORD_HASH',
      'PASSWORD_ITERATIONS'
  )
ORDER BY column_name;
SELECT USER AS account_name FROM dual;