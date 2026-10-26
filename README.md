# Automatic Timetable Generator

A college timetable management starter application using Java 21, Spring Boot 3.5.6, Spring Data JPA/Hibernate, MySQL, Maven, and plain HTML/CSS/JavaScript.

## Current features

- Responsive administration dashboard with summary counts and navigation.
- Create, view, edit, and delete departments, courses, classes, teachers, subjects, classrooms, time slots, teacher-subject assignments, subject requirements, and teacher availability.
- Open local workspace: no sign-in is required to manage records or generate timetables.
- Generate class timetables using a bounded backtracking search.
- Class, teacher, and room clash checks; server-side validation; unique database keys for exact-period clashes.
- Class and teacher timetable filters, weekly grid, printing, and CSV export.
- Conflict report for overlapping saved timetable periods.

This application has no authentication or authorization. Anyone who can reach it can read and change all timetable data. Run it only in a trusted local environment; do not expose it to a shared or public network without adding an approved authentication and authorization system.

## Project layout

```text
automatic-timetable-generator/
|-- pom.xml
|-- README.md
|-- .gitignore
|-- src/
|   |-- main/
|   |   |-- java/com/college/automatictimetable/
|   |   |   |-- AutomaticTimetableApplication.java
|   |   |   |-- controller/ApiExceptionHandler.java
|   |   |   |-- controller/ManagementController.java
|   |   |   |-- controller/TimetableController.java
|   |   |   |-- model/ (JPA entities and audit timestamps)
|   |   |   |-- repository/ (Spring Data JPA repositories)
|   |   |   |-- service/ManagementService.java
|   |   |   |-- service/SchedulingEngine.java
|   |   |   `-- service/TimetableService.java
|   |   `-- resources/
|   |       |-- application.properties
|   |       `-- static/
|   |           |-- index.html
|   |           |-- style.css
|   |           `-- app.js
|   `-- test/java/com/college/automatictimetable/service/
|       |-- ManagementServiceTest.java
|       `-- SchedulingEngineTest.java
```

`pom.xml` holds the Java/Maven build settings and Spring Web, JPA, validation, MySQL, and test dependencies. The `model` classes describe database tables and relationships. Repository interfaces provide database access. The management and timetable services implement business rules, and REST controllers expose them to the browser. The static files are served by Spring Boot at the site root. Tests use an in-memory H2 database and do not need a running XAMPP server.

## Requirements and XAMPP database setup

Install JDK 21, Maven, and XAMPP. Check Java and Maven from PowerShell:

```powershell
java -version
mvn -version
```

Both should use Java 21.

1. Open the XAMPP Control Panel and start **MySQL**.
2. Open `http://localhost/phpmyadmin`.
3. Create a database named `automatic_timetable`.
4. The default local database settings are host `localhost`, port `3306`, and username `root`. XAMPP bundles MariaDB, which accepts the MySQL connection used by this application. If your database has a password, configure it with the environment variable shown below. No database password belongs in project source files.

Hibernate creates and updates tables when the application starts. Back up any important database before changing entity definitions.

## Run the application

Open a PowerShell terminal in the folder containing `pom.xml` (`D:\jv1` in this workspace). No application usernames or passwords are required. If your XAMPP database has a password, configure it before starting Spring Boot:

```powershell
$dbPassword = Read-Host "Enter the MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $dbPassword).Password
mvn spring-boot:run
```

Omit the database password prompt if your local XAMPP database has no password.

When Spring Boot reports that it has started, open **http://localhost:8080/**. No sign-in is needed. Stop the server with `Ctrl+C`.

For a quick build and automated test run:

```powershell
mvn clean test
```

## Database model

- `Department` groups courses, teachers, and subjects.
- `Course` belongs to a department and sets the available semester count.
- `StudentClass` identifies a course, semester, section/batch, and student count (used to select a large-enough room).
- `Teacher` and `Subject` hold staff and curriculum information.
- `TeacherSubjectAssignment` limits which teachers can teach a subject.
- `SubjectRequirement` sets a class's required weekly periods for a subject.
- `Classroom` records room capacity and room type (`STANDARD` or `LAB`).
- `TimeSlot` stores a period's start and end time.
- `TeacherAvailability` explicitly marks a teacher/day/period available or unavailable. Unspecified periods are treated as available.
- `TimetableEntry` relates one class, subject, teacher, classroom, day, and time slot.
- Academic records include `createdAt` and `updatedAt` timestamps. Database unique keys protect codes, assignments, requirements, teacher availability, and exact class/teacher/room period collisions.

## API overview

API calls use JSON. Management list/create/update/delete routes use `/api/{resource}`:

| Resource | Path | Main fields |
|---|---|---|
| Departments | `/api/departments` | `name`, `code` |
| Courses | `/api/courses` | `name`, `code`, `semesterCount`, `departmentId` |
| Classes/batches | `/api/classes` | `name`, `courseId`, `semester`, `section`, `studentCount` |
| Teachers | `/api/teachers` | `name`, `email`, `phone`, `departmentId`, `active` |
| Subjects | `/api/subjects` | `name`, `code`, `departmentId`, `practical`, `active` |
| Classrooms | `/api/classrooms` | `name`, `capacity`, `roomType`, `active` |
| Time slots | `/api/time-slots` | `label`, `startTime`, `endTime`, `active` |
| Teacher-subject assignments | `/api/assignments` | `teacherId`, `subjectId` |
| Subject requirements | `/api/requirements` | `classId`, `subjectId`, `periodsPerWeek` |
| Teacher availability | `/api/teacher-availability` | `teacherId`, `dayOfWeek`, `timeSlotId`, `available` |

For each management resource, `GET /api/{resource}` lists rows, `POST /api/{resource}` creates, `PUT /api/{resource}/{id}` updates, and `DELETE /api/{resource}/{id}` deletes. Foreign keys in request JSON are sent as IDs. Invalid values return an HTTP error with a JSON `message`.

Other endpoints:

- `GET /api/dashboard` — dashboard counts.
- `POST /api/timetable/generate` — generate a schedule. Example:

  ```json
  {
    "classId": 1,
    "days": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    "timeSlotIds": [1, 2, 3, 4],
    "replaceExisting": false
  }
  ```

- `GET /api/timetable` — all entries.
- `GET /api/timetable?classId=1` — class-wise view.
- `GET /api/timetable?teacherId=1` — teacher-wise view.
- `GET /api/conflicts` — overlapping saved entries.

All GET APIs allow the `ADMIN` and `STAFF` roles. Management writes and timetable generation require `ADMIN`. The browser UI signs in using the account credentials configured in the environment.

## Scheduling algorithm

The scheduler expands each subject requirement into one scheduling task per weekly period. It creates possible placements only for selected active days and time slots, assigned active teachers, available teachers, and suitable active rooms. Practical subjects require a `LAB` room; non-practical subjects use non-LAB rooms.

Tasks with the fewest placement options are tried first. A backtracking search rejects any overlapping placement for the class, teacher, or room, including different slot IDs whose time ranges overlap. It also checks existing entries for other classes. The search has a safety limit and returns a clear error for missing assignments, insufficient class periods, unavailable rooms, or impossible constraints. A new timetable is saved only after the complete schedule is found. Replacing an existing class timetable requires the `replaceExisting` flag and browser confirmation. Database uniqueness constraints provide an additional guard for exact-period collisions.

The current design schedules one class at a time and treats unconfigured teacher availability as available. For these reasons, generated timetables still need review by college staff.

## Six-person Git collaboration

Suggested module owners:

1. Dashboard and authentication UI/API.
2. Teacher records and availability.
3. Departments, courses, classes, and subjects.
4. Classrooms, time slots, and room configuration.
5. Scheduling algorithm, validation, and conflict tests.
6. Timetable views, exports, and integration testing.

Agree on API fields before splitting work. Give each person a short-lived branch, such as `feature/teacher-management`. Keep commits focused, pull the shared branch before opening a pull request, and request one teammate review. Avoid editing the same files at the same time; coordinate before changing shared entities, `ManagementService`, `app.js`, or `pom.xml`. Merge small pull requests frequently, then run `mvn clean test` on the integrated branch. Keep database passwords in local environment variables, never in Git.

To connect this local Git repository to a GitHub repository, create an empty repository on GitHub, then use the remote URL provided by GitHub:

```powershell
git remote add origin <your-github-repository-url>
git branch -M main
git push -u origin main
```

No GitHub repository or remote is created automatically by this project.
#   j a v a  
 