# CreatorOS Backend — Developer README

> **Learning-first README.** Every decision, pattern, and tool is explained — not just listed.
> Built with Spring Boot 3.2 · Java 17 · PostgreSQL · JWT · FFmpeg · Whisper

---

## Table of Contents

1. [What This Project Does](#1-what-this-project-does)
2. [The Core Pipeline — How Data Flows](#2-the-core-pipeline--how-data-flows)
3. [Project Structure — Every Folder Explained](#3-project-structure--every-folder-explained)
4. [Tech Stack — What, Why, and How](#4-tech-stack--what-why-and-how)
5. [Files Written So Far](#5-files-written-so-far)
6. [Application Entry Point](#6-application-entry-point)
7. [Configuration — application.yml Deep Dive](#7-configuration--applicationyml-deep-dive)
8. [Security Architecture](#8-security-architecture)
9. [Response & Error Handling](#9-response--error-handling)
10. [Dockerfile — How the App Gets Containerised](#10-dockerfile--how-the-app-gets-containerised)
11. [How to Run Locally](#11-how-to-run-locally)
12. [What's Coming Next](#12-whats-coming-next)
13. [Concepts to Study Alongside This Build](#13-concepts-to-study-alongside-this-build)

---

## 1. What This Project Does

CreatorOS is a **backend-first AI content repurposing platform**.

A content creator gives it a **raw idea** — and the system:

```
Creator types an idea
        │
        ▼
AI generates a structured video script
(hook + dialogue + B-roll + editing instructions + CTA)
        │
        ▼
Creator uploads their raw video footage
        │
        ▼
Backend runs an async pipeline:
  ├── Whisper transcribes the video (speech → text with timestamps)
  ├── AI/heuristic scores transcript segments for highlight potential
  ├── FFmpeg cuts those segments into individual clips
  └── FFmpeg burns captions into each clip
        │
        ▼
Creator opens dashboard → downloads ready-to-post short-form clips
```

### Why this project is good to build as a learner

It touches **every major backend concern**:
- REST API design
- Authentication & security (JWT)
- Database design (relational schema, migrations)
- Asynchronous background processing
- File I/O and storage abstraction
- Third-party API integration (OpenAI, Whisper)
- System process invocation (FFmpeg)
- Docker & CI/CD deployment

---

## 2. The Core Pipeline — How Data Flows

Understanding **how a request moves through the system** is the most important thing.

```
HTTP Request
    │
    ▼
┌─────────────────────────────────┐
│  Spring Security Filter Chain   │  ← checks JWT, allows/blocks request
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│         Controller              │  ← receives HTTP, validates input, delegates
│   (e.g. VideoController)        │
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│          Service                │  ← all business logic lives here
│   (e.g. VideoService)           │
└──────────────┬──────────────────┘
               │
        ┌──────┴──────┐
        │             │
        ▼             ▼
┌──────────────┐  ┌──────────────┐
│  Repository  │  │ Other        │
│  (Database)  │  │ Services     │
│              │  │ (AI, FFmpeg) │
└──────────────┘  └──────────────┘
        │
        ▼
   PostgreSQL
```

### Rules that are always enforced

| Rule | Why |
|------|-----|
| Controller only handles HTTP in/out | Keeps business logic testable |
| Service contains all business logic | Single responsibility |
| Repository only queries the DB | Clean separation |
| DTOs in/out — never expose entities | Prevents leaking DB structure to API consumers |
| Exceptions bubble up to `GlobalExceptionHandler` | No try-catch in controllers |

---

## 3. Project Structure — Every Folder Explained

```
backend/
│
├── src/main/java/com/creatoros/
│   │
│   ├── CreatorOsApplication.java       ← app entry point (main method)
│   │
│   ├── auth/                           ← WEEK 2: register, login, JWT
│   │   ├── controller/                 ← HTTP layer: POST /auth/register, /auth/login
│   │   ├── service/                    ← logic: hash password, validate, generate JWT
│   │   ├── repository/                 ← DB query: findByEmail
│   │   ├── entity/                     ← User table mapped to Java class
│   │   └── dto/                        ← RegisterRequest, LoginRequest, AuthResponse
│   │
│   ├── user/                           ← WEEK 2: GET/PUT /users/me
│   │   └── ...same structure...
│   │
│   ├── project/                        ← WEEK 2: CRUD for projects
│   │   └── ...same structure...
│   │
│   ├── script/                         ← WEEK 3: AI script generation
│   │   └── ...same structure...
│   │
│   ├── video/                          ← WEEK 4: video upload & storage
│   │   └── ...same structure...
│   │
│   ├── transcription/                  ← WEEK 4: Whisper integration
│   │   └── ...same structure...
│   │
│   ├── clipping/                       ← WEEK 5: highlight detection + FFmpeg
│   │   └── ...same structure...
│   │
│   ├── processing/                     ← WEEK 4: async job runner & status tracking
│   │   └── ...same structure...
│   │
│   ├── ai/                             ← WEEK 3: provider-agnostic AI abstraction
│   │   ├── provider/                   ← ScriptGenerationProvider (interface)
│   │   └── dto/                        ← ScriptRequest, ScriptResponse
│   │
│   └── common/                         ← shared infrastructure (done now)
│       ├── config/
│       │   └── HealthController.java   ← GET /api/v1/health ← DONE ✅
│       ├── exception/
│       │   ├── GlobalExceptionHandler  ← DONE ✅
│       │   ├── ResourceNotFoundException ← DONE ✅
│       │   ├── ConflictException       ← DONE ✅
│       │   └── BusinessException       ← DONE ✅
│       ├── response/
│       │   └── ApiResponse.java        ← DONE ✅
│       └── security/
│           └── SecurityConfig.java     ← DONE ✅
│
├── src/main/resources/
│   ├── application.yml                 ← all config ← DONE ✅
│   └── db/migration/                   ← WEEK 2: Flyway SQL files go here
│
├── src/test/                           ← WEEK 7: unit + integration tests
│
├── pom.xml                             ← Maven deps ← DONE ✅
└── Dockerfile                          ← container build ← DONE ✅
```

### Why this package structure?

Each top-level package (`auth`, `video`, `script`, etc.) is called a **feature module** or **vertical slice**.

Instead of organising by layer (all controllers in one folder, all services in another), you organise by feature. This means:
- `auth/` contains **everything** needed for auth — controller, service, repo, entity, DTOs
- When you work on auth, you only touch files in one folder

This is much easier to navigate in a growing codebase.

---

## 4. Tech Stack — What, Why, and How

### Spring Boot 3.2

**What it is:** A Java framework for building web applications and REST APIs.

**What it gives you:**
- An embedded web server (Tomcat) — no need to deploy to a separate server
- Dependency injection (DI) — Spring manages creating and wiring your objects
- Auto-configuration — Spring detects what's on the classpath and configures it automatically

**Key annotations to know:**
```java
@SpringBootApplication   // marks the entry point; enables component scan
@RestController          // marks a class as a REST API controller
@Service                 // marks a class as a business logic layer
@Repository              // marks a class as a database access layer
@Component               // generic Spring-managed bean
@Bean                    // declares a method that returns a Spring bean
@Autowired               // injects a dependency (Spring 3 style)
// Constructor injection (preferred — explicit, testable):
// Spring auto-injects when there's only one constructor
```

---

### Maven (pom.xml)

**What it is:** The build tool. Manages dependencies, compiles code, runs tests, packages the JAR.

**Key sections in pom.xml:**
```xml
<parent>          <!-- inherits Spring Boot's version management -->
<properties>      <!-- java.version = 17 -->
<dependencies>    <!-- every library the project uses -->
<build><plugins>  <!-- spring-boot-maven-plugin: makes the JAR runnable -->
```

**Why use a parent POM?**
The Spring Boot parent BOM (Bill of Materials) manages dependency versions for you. You declare `spring-boot-starter-web` without a version — Spring Boot picks a tested, compatible version automatically.

---

### PostgreSQL + Spring Data JPA + Hibernate

**What they are:**
- **PostgreSQL** — the relational database that stores all your data
- **JPA (Java Persistence API)** — a specification (set of rules) for mapping Java objects to database tables
- **Hibernate** — the implementation of JPA. Translates your Java entity classes into SQL
- **Spring Data JPA** — wraps Hibernate to give you repositories with zero SQL for common queries

**How it works together:**
```
Your Java Entity class
         │
         ▼  (Hibernate translates to SQL)
    PostgreSQL table
```

You write:
```java
@Entity
public class User {
    @Id UUID id;
    String email;
    String passwordHash;
}
```

Hibernate sees this and knows the `users` table has columns `id`, `email`, `password_hash`.

**Spring Data JPA repositories:**
```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email); // Spring generates the SQL for this
}
```
`findByEmail` → Spring generates `SELECT * FROM users WHERE email = ?` automatically.

---

### Flyway (Database Migrations)

**What it is:** A tool that manages changes to your database schema over time.

**Why it matters:**
Without Flyway, you'd manually run SQL scripts on each machine. With Flyway, it runs automatically when the app starts and tracks which scripts have already run.

**How it works:**
- You create SQL files in `src/main/resources/db/migration/`
- Files must be named: `V1__description.sql`, `V2__description.sql`, etc.
- On startup, Flyway checks a `flyway_schema_history` table in Postgres
- Any migration not yet run gets executed in order

```
V1__create_users.sql          → runs first
V2__create_projects.sql       → runs second
V3__create_scripts.sql        → runs third
...and so on
```

This means any developer who clones the project just runs `mvn spring-boot:run` and gets the full schema automatically.

---

### JWT (JSON Web Tokens)

**What it is:** A way to authenticate users without storing sessions on the server.

**How it works:**
```
1. User logs in with email + password
2. Server verifies credentials
3. Server generates a signed JWT:
   { "sub": "user-uuid", "iat": 1234, "exp": 5678 }
   ↓ signed with a secret key
   eyJhbGci...  (compact string)
4. Client stores the JWT
5. Every future request includes: Authorization: Bearer eyJhbGci...
6. Server verifies the signature — no DB lookup needed
```

**Why stateless auth?**
REST APIs should be **stateless** — the server should not remember anything between requests. JWT carries all the identity information in the token itself.

**Library used:** `io.jsonwebtoken:jjwt` — the most common Java JWT library.

---

### Spring Security

**What it is:** A framework that handles authentication and authorization for Spring apps.

**Key concepts:**
- **Filter Chain** — every HTTP request passes through a sequence of security filters before hitting your controller
- **Authentication** — who are you? (JWT validation)
- **Authorization** — what are you allowed to do? (`hasRole`, `permitAll`, etc.)

**Our current `SecurityConfig`:**
```java
// CSRF disabled — REST APIs use tokens, not browser cookies
// Session = STATELESS — server keeps no session memory
// /api/v1/health and /auth/** → open to everyone
// Everything else → requires a valid JWT
```

The JWT filter (coming in Week 2) will sit in this chain and validate the token on every protected request.

---

### Lombok

**What it is:** Annotation-based code generation. Eliminates boilerplate.

```java
// Without Lombok — 30 lines of getters, setters, constructors:
public class User {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    // ...and a constructor
}

// With Lombok — 5 lines:
@Data               // generates getters, setters, toString, equals, hashCode
@Builder            // generates builder pattern
@NoArgsConstructor  // generates no-arg constructor
@AllArgsConstructor // generates all-args constructor
public class User {
    private String name;
}
```

---

### BCrypt (Password Hashing)

**What it is:** A one-way hashing algorithm for passwords.

**Why you never store passwords in plaintext:**
If the database is ever compromised, attackers shouldn't be able to read users' passwords.

**How BCrypt works:**
```
password "secret123"
        ↓  BCryptPasswordEncoder.encode()
"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
        ↓  BCryptPasswordEncoder.matches("secret123", hash)
        →  true
```

The hash is different every time (random salt is embedded), but `matches()` always works.

---

### OpenAI / Groq (AI Provider — coming Week 3)

**What it is:** REST APIs you call to generate text (scripts) using large language models.

**Why provider-agnostic?**
We've defined an `ai/provider/` package that will contain an interface:
```java
public interface ScriptGenerationProvider {
    ScriptResponse generate(ScriptRequest request);
}
```
Implementations: `OpenAiScriptProvider`, `GroqScriptProvider`.

You switch providers by changing one line in `application.yml` (`ai.provider: groq`). This is the **Strategy Pattern**.

---

### Whisper (Transcription — coming Week 4)

**What it is:** OpenAI's speech-to-text model. Converts audio → text with timestamps.

**Output format (what we store):**
```json
{
  "segments": [
    { "start": 0.0,  "end": 4.2,  "text": "Most students are preparing wrong." },
    { "start": 4.2,  "end": 9.8,  "text": "Here's what actually works." }
  ]
}
```

These timestamps are what the clip detector uses to find highlight moments.

---

### FFmpeg (Video Processing — coming Week 5)

**What it is:** A command-line tool for manipulating video and audio files.

**How we'll use it:**
```bash
# Extract audio from video (for Whisper)
ffmpeg -i input.mp4 -vn -acodec pcm_s16le output.wav

# Cut a clip from a video
ffmpeg -i input.mp4 -ss 4.2 -to 9.8 -c copy clip.mp4

# Burn captions into a clip
ffmpeg -i clip.mp4 -vf "subtitles=captions.srt" output_captioned.mp4
```

We call these commands from Java using `ProcessBuilder` — Java's way of running system commands.

---

### Spring @Async (Background Job Processing)

**What it is:** Spring's built-in mechanism to run methods in a background thread pool.

**Why we need it:**
Video processing takes 30–120 seconds. If this ran synchronously, the HTTP request would hang for 2 minutes. Unacceptable.

**How it works:**
```java
@Async  // ← this method runs in a background thread
public void processVideo(UUID videoId) {
    // runs in background thread pool (configured in application.yml)
    // 1. extract audio
    // 2. call Whisper
    // 3. detect clips
    // 4. run FFmpeg
    // 5. update job status to COMPLETED
}
```

The HTTP response returns immediately with `{ "jobId": "..." }`.
The client polls `GET /api/v1/jobs/{jobId}` to check status.

Enabled at the app level with `@EnableAsync` in `CreatorOsApplication.java`.

---

### Docker

**What it is:** A tool that packages your app and all its dependencies into a portable container.

**Why use it?**
"It works on my machine" problem — Docker makes your app run identically everywhere.

**Multi-stage build (our Dockerfile):**
```dockerfile
# Stage 1: Build — uses JDK (large)
FROM eclipse-temurin:17-jdk-alpine AS builder
# ... compile, build JAR

# Stage 2: Runtime — uses only JRE (much smaller)
FROM eclipse-temurin:17-jre-alpine AS runtime
# ... copy JAR from stage 1, run it
```

This keeps the final image lean — you don't ship the compiler to production.

---

## 5. Files Written So Far

### ✅ [`pom.xml`](pom.xml)

The Maven build file. Contains all dependencies:

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API + embedded Tomcat |
| `spring-boot-starter-security` | Auth + filter chain |
| `spring-boot-starter-data-jpa` | Database ORM (Hibernate) |
| `spring-boot-starter-validation` | Request body validation (`@Valid`) |
| `postgresql` | JDBC driver for Postgres |
| `flyway-core` | Database migration management |
| `jjwt-api/impl/jackson` | JWT generation and validation |
| `lombok` | Boilerplate code generation |
| `spring-boot-devtools` | Hot reload during development |
| `commons-io` | File copy/move utilities |
| `software.amazon.awssdk:s3` | S3 storage (switchable from local) |
| `spring-boot-starter-test` | JUnit 5 + Mockito testing |
| `h2` | In-memory DB for tests (test scope only) |

---

### ✅ [`src/main/resources/application.yml`](src/main/resources/application.yml)

Spring Boot reads this file at startup to configure everything.

**Key design decision — environment variables:**
```yaml
url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/creatoros}
#      ↑ env var name           ↑ default value if env var not set
```

This means:
- **Locally:** uses the default (your laptop's Postgres)
- **On Railway/Render:** sets the env var, overrides the default
- **Never** hardcode secrets in this file

**Profiles:** The file has three sections separated by `---`:
1. **default** — used in all environments
2. **local** — extra SQL debug logging when you run with `-Dspring.profiles.active=local`
3. **test** — H2 in-memory DB for running unit tests (no Postgres needed)

**Thread pool config (for async jobs):**
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 4      # always-on threads
        max-size: 8       # max threads under load
        queue-capacity: 100  # jobs queued before rejecting
```

---

### ✅ [`CreatorOsApplication.java`](src/main/java/com/creatoros/CreatorOsApplication.java)

```java
@SpringBootApplication
@EnableAsync
public class CreatorOsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreatorOsApplication.class, args);
    }
}
```

**`@SpringBootApplication`** is actually three annotations combined:
- `@Configuration` — this class can define Spring beans
- `@EnableAutoConfiguration` — Spring auto-configures based on classpath
- `@ComponentScan` — Spring scans this package and all sub-packages for `@Component`, `@Service`, `@Repository`, `@Controller`

**`@EnableAsync`** — activates Spring's background thread pool so `@Async` methods work.

---

### ✅ [`common/response/ApiResponse.java`](src/main/java/com/creatoros/common/response/ApiResponse.java)

**The problem it solves:**
Without a wrapper, different endpoints return different shapes:
```json
{ "name": "project1" }           // from ProjectController
{ "error": "not found" }         // from error handlers
{ "token": "eyJ..." }            // from AuthController
```

**With ApiResponse, every response is consistent:**
```json
{
    "success": true,
    "data": { "name": "project1" },
    "timestamp": "2026-09-15T09:41:53Z"
}
```
or on error:
```json
{
    "success": false,
    "message": "Project not found with id: abc-123",
    "timestamp": "2026-09-15T09:41:53Z"
}
```

**Why `@Builder`?**
The Builder pattern lets you construct objects without giant constructor calls:
```java
// Without builder:
new ApiResponse(true, null, data, Instant.now());  // confusing

// With builder:
ApiResponse.ok(data);  // clear factory method
```

**`@JsonInclude(NON_NULL)`** — null fields are not included in the JSON output. So if `message` is null (success case), it won't appear in the response.

---

### ✅ [`common/exception/GlobalExceptionHandler.java`](src/main/java/com/creatoros/common/exception/GlobalExceptionHandler.java)

**The pattern:** `@RestControllerAdvice`

This class intercepts exceptions thrown anywhere in the application and converts them to HTTP responses.

**Without it — every controller needs this:**
```java
try {
    return projectService.findById(id);
} catch (ResourceNotFoundException e) {
    return ResponseEntity.status(404).body(new ErrorDto(e.getMessage()));
} catch (Exception e) {
    return ResponseEntity.status(500).body(new ErrorDto("Something went wrong"));
}
```

**With GlobalExceptionHandler — controllers are clean:**
```java
// Controller:
return projectService.findById(id);  // just throw, handler catches it

// Handler:
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(404).body(ApiResponse.error(ex.getMessage()));
}
```

**Exception types handled:**

| Exception | HTTP Status | When it's thrown |
|---|---|---|
| `MethodArgumentNotValidException` | 400 Bad Request | `@Valid` fails on a request body field |
| `ResourceNotFoundException` | 404 Not Found | Entity not found by ID |
| `ConflictException` | 409 Conflict | Duplicate email on register |
| `BadCredentialsException` | 401 Unauthorized | Wrong password on login |
| `AccessDeniedException` | 403 Forbidden | User tries to access another user's resource |
| `MaxUploadSizeExceededException` | 413 Payload Too Large | File bigger than 500MB |
| `BusinessException` | 422 Unprocessable Entity | Business rule violation |
| `Exception` (catch-all) | 500 Internal Server Error | Anything unexpected |

---

### ✅ [`common/security/SecurityConfig.java`](src/main/java/com/creatoros/common/security/SecurityConfig.java)

**The filter chain:**
Every HTTP request passes through this chain in order. If any filter rejects the request, the chain stops.

```
HTTP Request
     ↓
[SessionManagementFilter]      ← we told it: STATELESS (no sessions)
     ↓
[JwtAuthFilter]                ← (COMING WEEK 2) reads & validates JWT
     ↓
[AuthorizationFilter]          ← checks if the route requires auth
     ↓                              /health → permit all
     ↓                              /auth/** → permit all
     ↓                              everything else → must be authenticated
Controller method
```

**Key decisions explained:**

```java
.csrf(AbstractHttpConfigurer::disable)
```
CSRF (Cross-Site Request Forgery) protection is for browser-based apps that use cookies. Since we use JWT headers (not cookies), CSRF isn't needed.

```java
.sessionManagement(session ->
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```
Spring Security by default creates an HTTP session. We don't want that — JWT carries all state. STATELESS tells Spring never to create or use a session.

**`PasswordEncoder` bean:**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```
Declaring this as a `@Bean` means Spring manages it and injects it wherever `PasswordEncoder` is needed — in `AuthService.register()`, for example.

---

### ✅ [`common/config/HealthController.java`](src/main/java/com/creatoros/common/config/HealthController.java)

```
GET /api/v1/health → 200 OK
{
    "success": true,
    "data": {
        "status": "UP",
        "service": "creatoros-backend"
    }
}
```

**Why a health endpoint?**
- Docker's `HEALTHCHECK` pings it
- Railway/Render pings it to know the app is ready to receive traffic
- You can `curl` it to quickly verify the app is running

It's declared in `PUBLIC_PATHS` in `SecurityConfig` so it never needs a JWT.

---

### ✅ [`Dockerfile`](Dockerfile)

**Multi-stage build walkthrough:**

```dockerfile
# ── Stage 1: Builder ──────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copy pom.xml first — cache this layer
# If pom.xml doesn't change, Docker reuses the cached layer
# and skips the slow dependency download
COPY pom.xml .
RUN mvn dependency:go-offline -B   # download all deps

# Copy source and compile
COPY src ./src
RUN mvn package -DskipTests -B    # build the JAR

# ── Stage 2: Runtime ──────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime
# JRE = Java Runtime only (no compiler)
# Much smaller than JDK — final image is ~180MB instead of ~400MB

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

# Run as non-root user (security best practice)
RUN adduser -S creatoros
USER creatoros

EXPOSE 8080

# JVM flags for containers:
# -XX:+UseContainerSupport — respects Docker's memory limits
# -XX:MaxRAMPercentage=75.0 — uses at most 75% of container RAM for heap
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

## 6. Application Entry Point

```
mvn spring-boot:run
        │
        ▼
CreatorOsApplication.main()
        │
        ▼
SpringApplication.run()
        │
        ├── Scans all @Component, @Service, @Repository, @Controller classes
        ├── Reads application.yml
        ├── Auto-configures: DataSource, JPA, Security, Web MVC
        ├── Runs Flyway migrations (when enabled)
        ├── Starts embedded Tomcat on port 8080
        └── App ready — listening for requests
```

**Startup time: ~2.2 seconds locally.**

---

## 7. Configuration — application.yml Deep Dive

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/creatoros}
```

`${VAR_NAME:default}` — this is Spring's property placeholder syntax:
- At runtime, Spring replaces `${SPRING_DATASOURCE_URL:...}` with the actual env var value
- If the env var isn't set, it falls back to the default after the colon

**HikariCP (connection pool):**
```yaml
hikari:
  maximum-pool-size: 10
```
Instead of opening a new DB connection on every request (slow), HikariCP keeps a pool of 10 open connections ready. Requests borrow from the pool and return when done.

**`jpa.open-in-view: false`:**
A common Spring Boot gotcha. By default, Spring keeps the JPA session open until the HTTP response is sent — this can cause lazy-loaded queries to fire unexpectedly. Setting this to `false` forces all DB access to happen within the service layer.

**`jpa.hibernate.ddl-auto: validate`:**
Hibernate has 5 modes:
- `create` — drops and recreates schema on startup (dev only, data loss!)
- `create-drop` — creates on start, drops on stop
- `update` — tries to alter tables to match entities (dangerous in production)
- `validate` — checks entities match the DB schema, errors if not (our choice)
- `none` — does nothing

We use `validate` because **Flyway manages the schema**. Hibernate should never touch it.

---

## 8. Security Architecture

```
Current state (Phase 1):
┌─────────────────────────────────────┐
│  GET /api/v1/health                 │ ← PUBLIC (no auth needed)
│  POST /api/v1/auth/register         │ ← PUBLIC
│  POST /api/v1/auth/login            │ ← PUBLIC
├─────────────────────────────────────┤
│  Everything else                    │ ← 401 Unauthorized (JWT needed)
└─────────────────────────────────────┘

Coming in Phase 3 (auth week):
┌─────────────────────────────────────────────────────┐
│  Client sends: Authorization: Bearer eyJhbGci...    │
│                                                     │
│  JwtAuthFilter:                                     │
│  1. Extract token from header                       │
│  2. Validate signature with secret key              │
│  3. Extract userId from token payload               │
│  4. Set Authentication in SecurityContext           │
│  5. Request proceeds to controller                  │
│                                                     │
│  In service layer:                                  │
│  SecurityContextHolder.getContext()                 │
│      .getAuthentication().getName()  → userId       │
└─────────────────────────────────────────────────────┘
```

**Why check ownership in the service, not the controller?**
If you only check "is the user authenticated?" at the controller, a user could request `GET /videos/someone-elses-video-id` and get it. The service must check `video.getUserId().equals(currentUserId)` before returning.

---

## 9. Response & Error Handling

**Every response follows this contract:**

```
Success:
HTTP 200 (or 201 Created)
{
    "success": true,
    "data": { ... },
    "timestamp": "..."
}

Validation error:
HTTP 400
{
    "success": false,
    "message": "Validation failed",
    "timestamp": "..."
}

Auth error:
HTTP 401
{
    "success": false,
    "message": "Invalid email or password",
    "timestamp": "..."
}

Not found:
HTTP 404
{
    "success": false,
    "message": "Video not found with id: abc-123",
    "timestamp": "..."
}

Server error:
HTTP 500
{
    "success": false,
    "message": "An unexpected error occurred. Please try again later.",
    "timestamp": "..."
}
```

The `"timestamp"` field is useful for correlating with server logs when debugging.

---

## 10. Dockerfile — How the App Gets Containerised

```
Developer machine:
mvn package → target/creatoros-backend-0.0.1-SNAPSHOT.jar

docker build -t creatoros-backend .
        │
        ▼
Stage 1 (builder):
  - Pulls eclipse-temurin:17-jdk-alpine
  - Downloads Maven deps (cached after first build)
  - Compiles source → JAR

Stage 2 (runtime):
  - Pulls eclipse-temurin:17-jre-alpine  (much smaller)
  - Copies JAR from stage 1
  - Sets up non-root user
  - Configures JVM memory flags

Final image: ~180MB (vs ~400MB with JDK)

docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=... \
  -e JWT_SECRET=... \
  creatoros-backend
```

---

## 11. How to Run Locally

### Prerequisites
- Java 17 (`java -version`)
- Maven (`mvn -version`)
- PostgreSQL running locally

### One-time setup
```bash
# Create the database
createdb creatoros

# Clone/navigate to project
cd backend/
```

### Start the app
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/creatoros \
SPRING_DATASOURCE_USERNAME=$(whoami) \
SPRING_DATASOURCE_PASSWORD="" \
SPRING_FLYWAY_ENABLED=false \
mvn spring-boot:run
```

### Verify it works
```bash
curl http://localhost:8080/api/v1/health
# Expected:
# {"success":true,"data":{"status":"UP","service":"creatoros-backend"},...}
```

### Test that protected routes require auth
```bash
curl http://localhost:8080/api/v1/projects
# Expected: 401 (once JWT filter is wired in Week 2)
```

---

## 12. What's Coming Next

| Phase | Week | What Gets Built |
|---|---|---|
| **Phase 2** | Week 2 Day 1–2 | 7 Flyway SQL migration files (all 7 tables) |
| **Phase 3** | Week 2 Day 3–5 | User entity, `AuthService`, JWT generation, login/register endpoints |
| **Phase 4** | Week 2 Day 5 | Projects CRUD (the first real feature) |
| **Phase 5** | Week 3 | AI abstraction layer, script generation endpoint |
| **Phase 6** | Week 4 | Video upload, file storage abstraction |
| **Phase 7** | Week 4 | Async processing job runner + status polling |
| **Phase 8** | Week 4 | Whisper transcription integration |
| **Phase 9** | Week 5 | Clip detection + FFmpeg clip generation |
| **Phase 10** | Week 6 | Caption burning, silence removal |

---

## 13. Concepts to Study Alongside This Build

Study these **as you build the relevant phase**, not all upfront:

### Week 2 (auth + DB)
- **REST API conventions** — HTTP verbs, status codes, URL design
- **SQL fundamentals** — `CREATE TABLE`, `PRIMARY KEY`, `FOREIGN KEY`, `JOIN`
- **BCrypt and password hashing** — why one-way hashing, what salt is
- **JWT structure** — header.payload.signature, what claims are

### Week 3 (AI)
- **Prompt engineering basics** — how to structure prompts for consistent JSON output
- **Strategy Pattern** — interface + multiple implementations, switching at runtime
- **RestClient / WebClient** — making HTTP calls from Java

### Week 4 (video + async)
- **Multipart file upload** — how files are sent over HTTP
- **Thread pools** — core-size, max-size, queue-capacity and what happens when they fill up
- **Idempotency** — why processing should be safe to retry

### Week 5–6 (FFmpeg)
- **ProcessBuilder** — running system commands from Java
- **SRT subtitle format** — `00:00:04,200 --> 00:00:09,800\nCaption text`

### Week 7 (deployment)
- **Environment variables and secrets management**
- **Managed PostgreSQL** — Railway's Postgres vs self-hosted
- **GitHub Actions YAML** — triggers, jobs, steps

---

## Quick Reference — Commands

```bash
# Start app
mvn spring-boot:run

# Build JAR
mvn package -DskipTests

# Run tests
mvn test

# Build Docker image
docker build -t creatoros-backend .

# Run Docker container
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/creatoros \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e JWT_SECRET=your_secret_here \
  creatoros-backend

# Check health
curl http://localhost:8080/api/v1/health | python3 -m json.tool
```

---

*This README is a living document — it will be updated as each phase is built.*
