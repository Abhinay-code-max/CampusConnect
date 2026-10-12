-- Run manually as SYSTEM connected to FREEPDB1, for example:
-- CONNECT SYSTEM@//localhost:1521/freepdb1
-- This script does not create/drop users or grant DBA/unlimited privileges.
-- The CAMPUSCONNECT default tablespace was confirmed as USERS by a read-only
-- query of USER_USERS. The guard below checks the target PDB and DBA_USERS
-- before applying the bounded quota.

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

PROMPT Confirm current PDB and CAMPUSCONNECT default tablespace before applying quota:
SHOW CON_NAME
SELECT username, default_tablespace
  FROM dba_users
 WHERE username = 'CAMPUSCONNECT';

DECLARE
    v_container          VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CON_NAME');
    v_default_tablespace VARCHAR2(128);
BEGIN
    IF UPPER(v_container) <> 'FREEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20001, 'Connect to FREEPDB1 before running this script.');
    END IF;

    SELECT default_tablespace
      INTO v_default_tablespace
      FROM dba_users
     WHERE username = 'CAMPUSCONNECT';

    IF UPPER(v_default_tablespace) <> 'USERS' THEN
        RAISE_APPLICATION_ERROR(-20002, 'CAMPUSCONNECT default tablespace is not USERS; review quota first.');
    END IF;
END;
/

ALTER USER CAMPUSCONNECT QUOTA 25M ON USERS;

GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW TO CAMPUSCONNECT;

PROMPT CampusConnect schema permissions applied in FREEPDB1 (25M quota on USERS).
