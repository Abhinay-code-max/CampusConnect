# CampusConnect 🎓

**CampusConnect** is a comprehensive, production-grade College Event Management and Participation Platform built with **Jakarta EE 10 (Servlet API 6.0)**, **Oracle Database 23ai**, and modern **Vanilla HTML5/CSS3/JavaScript**.

It facilitates end-to-end event lifecycles across colleges and universities—from event creation, team formations, and live notifications to multi-criteria judging, weighted scoring, and administrative governance.

---

## 🌟 Key Features

### 🔔 Real-Time Event Notifications & Announcements
- **Instant Event Alerts**: When an Organizer or Admin publishes an event, students receive instant notifications containing event details and a brief description.
- **Notification Bell & Popover**: Interactive notification bell with real-time unread badges located in the main navigation bar and directly on the Student Dashboard.
- **One-Click Redirection**: Clicking on any notification immediately takes the student to the active event with a visual highlight.
- **Cross-Tab Synchronization**: Real-time cross-tab updates using the HTML5 `BroadcastChannel` API and automated fallback polling.

### 👨‍🎓 Student Experience
- **Event Discovery**: Search, filter, and view upcoming, live, and completed college events.
- **Flexible Registration**: Register individually or form/join teams via secure invitation codes.
- **Student Dashboard**: Overview of registered events, registration status (confirmed, waitlisted, checked-in), upcoming announcements, and performance results.

### 📋 Organizer Management
- **Event Lifecycle**: Create, update, publish, and complete events with custom capacity, registration windows, and participation modes (`INDIVIDUAL` vs. `TEAM`).
- **Team & Attendance Tracking**: Monitor registrations, accept/reject participants, and confirm attendee check-ins.
- **Scoring Configuration**: Configure multi-dimensional scoring rubrics with weights (summing to 100%) and assign qualified judges.
- **Publish & Notify**: Optional one-click instant student notification broadcast upon event publication.

### ⚖️ Judge Scoring & Evaluation
- **Assigned Event Portal**: Access assigned events and active participant rosters.
- **Criterion-Based Scoring**: Submit scorecards against predefined criteria with strict boundary validation against maximum points.
- **Aggregated Results**: Automatic weighted score calculation and dynamic leaderboard generation via database views.

### 🛡️ Administrative Governance & Security
- **Role-Based Access Control (RBAC)**: Enforced via `ApiAuthenticationFilter` and `@RequireRoles` supporting `STUDENT`, `ORGANIZER`, `JUDGE`, and `ADMIN`.
- **User Management**: Activate/deactivate accounts and assign operational roles with audit logging.
- **Security Protections**: Session cookie hardening (`HttpOnly`, `SameSite`), CSRF token verification, and rate limiting on authentication endpoints (`LoginRateLimiter`).
- **Database Health**: Real-time DB connectivity and performance diagnostics via health endpoints.

---

## 🏗️ Architecture & Tech Stack

```
CampusConnect/
├── backend/                       # Jakarta EE 10 / Servlet 6.0 Backend
│   ├── src/main/java/com/campusconnect/
│   │   ├── controller/            # Servlets (Auth, Events, Teams, Scoring, Admin, Health)
│   │   ├── dao/                   # JDBC Data Access Objects with transaction runner
│   │   ├── filter/                # Security headers, authentication & role authorization
│   │   ├── model/                 # Request/Response POJOs and domain models
│   │   ├── service/               # Core business policies and validators
│   │   └── util/                  # DB connection pool, JSON serializer, PBKDF2 hashing
│   └── pom.xml                    # Maven build configuration (Java 17, OJDBC 23, Jackson)
├── frontend/                      # Responsive Single-Page Application (SPA)
│   ├── css/styles.css             # Modern CSS with custom theme properties & animations
│   ├── index.html                 # Main entrypoint with navbar & notification dropdown
│   └── js/
│       ├── app.js                 # Router and SPA view controller
│       ├── auth.js                # Session and JWT/cookie state manager
│       ├── notifications.js       # Real-time notification manager & broadcast channel
│       └── views/                 # View controllers (student, organizer, judge, admin, etc.)
└── database/                      # Oracle SQL Schema and Migration Scripts
    ├── 00_schema_permissions.sql  # PDB tablespace quota & user permissions
    ├── 01_tables.sql              # Core schema (Users, Events, Registrations, Scores, etc.)
    ├── 02_sequences.sql           # Oracle identity column notes
    ├── 03_views.sql               # V_CC_EVENT_RESULTS and event summary views
    ├── 04_sample_data.sql         # Seed data for demo and initial testing
    ├── 05_verify.sql              # Schema and constraint validation checks
    ├── 06_auth_credentials.sql    # Credentials schema extension
    ├── 07_team_invitations.sql    # Team invite tokens schema
    ├── 08_phase7_scoring.sql      # Scoring window & finalization lifecycle
    └── 09_admin_controls.sql      # Admin audit logging and controls
```

### Technology Matrix
- **Runtime / Language**: Java 17 (LTS)
- **Servlet Container**: Apache Tomcat 10.1+ (Jakarta EE 10 / Servlet API 6.0)
- **Database**: Oracle Database 23ai / 21c (PDB: `FREEPDB1`)
- **Database Driver**: Oracle JDBC Driver (`ojdbc11:23.6.0.24.10`)
- **JSON Processing**: Jackson Databind 2.16+
- **Frontend**: Vanilla JavaScript (ES6 Modules), HTML5, CSS3, Font Awesome
- **Testing**: JUnit 5, Mockito

---

## 🚀 Getting Started

### Prerequisites
Ensure the following are installed on your machine:
1. **JDK 17 or higher** (`java -version`)
2. **Apache Maven 3.9+** (`mvn -version`)
3. **Apache Tomcat 10.1+** (Download Tomcat 10 for Jakarta EE 10 support)
4. **Oracle Database 23ai Free** (Docker container or local installation)

---

### 1. Database Setup

1. Start your Oracle 23ai database instance (e.g., using Docker):
   ```bash
   docker run -d --name campusconnect-oracle \
     -p 1521:1521 \
     -e ORACLE_PWD=YourStrongPassword \
     gvenzl/oracle-free:23-slim-faststart
   ```

2. Execute the schema setup scripts against pluggable database `FREEPDB1`:
   - Run `00_schema_permissions.sql` as `SYSTEM`.
   - Connect as `CAMPUSCONNECT` and execute in order:
     ```sql
     @database/01_tables.sql
     @database/03_views.sql
     @database/04_sample_data.sql
     @database/05_verify.sql
     @database/06_auth_credentials.sql
     @database/07_team_invitations.sql
     @database/08_phase7_scoring.sql
     @database/09_admin_controls.sql
     ```

3. Provide database connection configuration:
   Create a properties file at `C:\CBPCC-config\campusconnect.properties` (or set environment variables `ORACLE_JDBC_URL`, `ORACLE_USER`, `ORACLE_PASSWORD`):
   ```properties
   db.url=jdbc:oracle:thin:@localhost:1521/FREEPDB1
   db.username=CAMPUSCONNECT
   db.password=YourStrongPassword
   db.pool.min=2
   db.pool.max=10
   ```

---

### 2. Build the Application

From the root directory or inside `backend/`:

```bash
cd backend
mvn clean package -DskipTests
```

This compiles the Java backend and bundles the `frontend/` assets into a single deployable web archive: `target/CampusConnect.war`.

---

### 3. Deploy to Apache Tomcat

1. Copy the generated `CampusConnect.war` to your Tomcat `webapps/` folder:
   ```bash
   cp backend/target/CampusConnect.war <TOMCAT_DIR>/webapps/
   ```

2. Start Apache Tomcat:
   - **Linux / macOS**: `<TOMCAT_DIR>/bin/startup.sh`
   - **Windows**: `<TOMCAT_DIR>\bin\startup.bat`

3. Access CampusConnect in your browser:
   ```
   http://localhost:8080/CampusConnect/
   ```

---

## 👥 Default Demo Roles & Logins

When initialized with `04_sample_data.sql` and `06_auth_credentials.sql`:

| Role | Username / Email | Default Password | Access / Capabilities |
| :--- | :--- | :--- | :--- |
| **Admin** | `admin@campusconnect.local` | `Admin#2026` | Full administrative controls, user roles, audit trail |
| **Organizer** | `organizer@campusconnect.local` | `Organizer#2026` | Create events, publish, manage scoring criteria, notify students |
| **Student** | `student@campusconnect.local` | `Student#2026` | Browse events, receive notifications, register, view results |
| **Judge** | `judge@campusconnect.local` | `Judge#2026` | Review assigned participants and score criteria |

*(Passwords can also be set or reset via the authentication API).*

---

## 📡 API Reference Overview

| Endpoint | Method | Role | Description |
| :--- | :--- | :--- | :--- |
| `/api/auth/login` | `POST` | Public | Authenticate user and initiate secure session |
| `/api/auth/logout` | `POST` | Authenticated | Terminate session and invalidate cookie |
| `/api/auth/me` | `GET` | Authenticated | Retrieve current session profile and roles |
| `/api/events` | `GET` | Public | List upcoming and active published events |
| `/api/events` | `POST` | `ORGANIZER`, `ADMIN` | Create a new event draft or published event |
| `/api/events/{id}` | `GET` | Public | Get detailed event information and registration rules |
| `/api/events/{id}/register` | `POST` | `STUDENT` | Register for an individual or team event |
| `/api/events/{id}/scores` | `POST` | `JUDGE` | Submit evaluation scores for participants |
| `/api/events/{id}/results` | `GET` | Public | View computed leaderboard and rankings |
| `/api/admin/users` | `GET`, `PATCH` | `ADMIN` | Manage user accounts, activation status, and roles |
| `/api/health` | `GET` | Public | Application and database connection health check |

---

## 🧪 Testing

To run the automated backend test suite:

```bash
cd backend
mvn test
```

To run end-to-end authenticated API regression checks:

```powershell
./test_authenticated_suite.ps1
```

---

## 📄 License
This project is developed for educational and institutional event management purposes. Licensed under the [MIT License](LICENSE).
