# ftw-realtime

Spring Boot 3.4.4 backend for the FairTradeWorker marketplace. Provides REST API, STOMP WebSocket real-time messaging, JWT authentication, and ConstructionAI integration via RunPod Serverless.

## Tech Stack

- **Language**: Kotlin 2.1.10, Java 21
- **Framework**: Spring Boot 3.4.4
- **Build**: Gradle Kotlin DSL
- **Database**: PostgreSQL via JPA/Hibernate, Flyway migrations
- **Auth**: JWT (HS256, jjwt), Argon2 password hashing (BouncyCastle)
- **Real-time**: STOMP over WebSocket with SockJS fallback
- **Caching**: Caffeine (FairScope), ConcurrentHashMap (FairPrice)
- **Rate Limiting**: Bucket4j per-IP
- **Storage**: AWS S3 / Cloudflare R2 with local fallback
- **Email**: Spring Mail (SMTP)
- **Push**: Expo push notifications
- **AI**: ConstructionAI via RunPod Serverless (~$0.002/inference)
- **Deploy**: Docker on Render.com

## Prerequisites

- Java 21 (Eclipse Temurin recommended)
- PostgreSQL 15+
- Gradle 8+ (wrapper included)

## Setup

```bash
# Set Java home (macOS with Homebrew)
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Create database
createdb ftw_realtime_dev

# Run (starts on localhost:4000)
./gradlew bootRun

# Compile check
./gradlew compileKotlin

# Build + test
./gradlew build

# Build fat jar
./gradlew bootJar

# Docker
docker build -t ftw-realtime .
docker run -p 4000:4000 ftw-realtime
```

## Configuration

All configuration is in `src/main/resources/application.yml`. Environment variables override defaults.

### Required for Production

| Variable          | Purpose           |
| ----------------- | ----------------- |
| `DB_HOST`         | PostgreSQL host   |
| `DB_PORT`         | PostgreSQL port   |
| `DB_NAME`         | Database name     |
| `DB_USERNAME`     | Database user     |
| `DB_PASSWORD`     | Database password |
| `SECRET_KEY_BASE` | JWT signing key   |

### Optional

| Variable                | Default     | Purpose                           |
| ----------------------- | ----------- | --------------------------------- |
| `PORT`                  | 4000        | HTTP port (Render uses 10000)     |
| `POOL_SIZE`             | 10          | Hikari connection pool size       |
| `SMTP_HOST`             | localhost   | Email relay                       |
| `SMTP_PORT`             | 587         | Email relay port                  |
| `SMTP_USERNAME`         |             | Email auth                        |
| `SMTP_PASSWORD`         |             | Email auth                        |
| `EXPO_ACCESS_TOKEN`     |             | Expo push notifications           |
| `STORAGE_BUCKET`        |             | S3/R2 bucket name                 |
| `STORAGE_ENDPOINT`      |             | S3-compatible endpoint URL        |
| `STORAGE_LOCAL_PATH`    | uploads     | Local fallback upload directory   |
| `AWS_REGION`            | us-east-1   | S3 region                         |
| `AWS_ACCESS_KEY_ID`     |             | S3 access key                     |
| `AWS_SECRET_ACCESS_KEY` |             | S3 secret key                     |
| `RUNPOD_URL`            |             | ConstructionAI inference endpoint |
| `ADMIN_PASSWORD`        | faircommand | Admin authentication              |

## Project Structure

```
src/main/kotlin/com/strata/ftw/
├── FtwApplication.kt              # Entry point
├── config/
│   ├── SecurityConfig.kt          # Spring Security, CORS, JWT filter chain
│   ├── WebSocketConfig.kt         # STOMP broker, SockJS, JWT on CONNECT
│   ├── JacksonConfig.kt           # snake_case JSON, ISO-8601 dates
│   └── AsyncConfig.kt             # Thread pool for @Async
├── domain/
│   ├── entity/                    # 27 JPA entities
│   └── repository/
│       └── Repositories.kt        # 27 Spring Data JPA repositories
├── service/
│   ├── MarketplaceService.kt      # Core business logic
│   ├── AuthService.kt             # JWT, passwords, role switching
│   ├── FairTrustService.kt        # Verification + quality scoring
│   ├── EmailService.kt            # Async email
│   ├── PushService.kt             # Expo push notifications
│   ├── StorageService.kt          # S3/R2 file uploads
│   └── FairRecordPdfService.kt    # HTML certificate generation
├── ai/
│   └── AiGateway.kt               # FairPrice, FairScope, EstimateAgent
├── web/
│   ├── controller/                # 18 REST controllers
│   ├── filter/
│   │   ├── JwtAuthFilter.kt       # Bearer token extraction
│   │   └── RateLimitFilter.kt     # Bucket4j rate limiting
│   └── websocket/
│       └── MessageHandlers.kt     # STOMP message handlers
├── worker/
│   └── ScheduledWorkers.kt        # 5 scheduled background jobs
src/main/resources/
├── application.yml                # All configuration
└── db/migration/
    ├── V1__baseline.sql           # Initial schema
    └── V2__sub_contractor.sql     # Sub-contractor tables
```

## API Overview

18 REST controllers serving endpoints under `/api`. Full endpoint listing in [CLAUDE.md](CLAUDE.md).

Key API groups:

- **Auth**: Login, register, role switching (multi-role: homeowner, contractor, sub_contractor)
- **Jobs**: Post jobs, place/accept bids, status transitions
- **Sub-Jobs**: Contractor-to-subcontractor job delegation with separate bid flow
- **Chat**: Conversation-based messaging
- **Estimates/Invoices/Projects**: Full CRUD for contractor business tools
- **AI**: FairPrice lookup, FairScope analysis, ConstructionAI estimation
- **FairRecord**: Verifiable project completion certificates
- **Verification**: Multi-step contractor identity/license verification (Persona, Checkr webhooks)

## WebSocket

STOMP over SockJS at `/ws`. JWT auth on CONNECT.

Topics for jobs feed, individual job bids, chat messages, user notifications, sub-job feed, and sub-job bids.

## Deployment

Render.com via `render.yaml`. Provisions a web service (Docker, starter plan, Oregon) and PostgreSQL database (starter plan, Oregon).

Dockerfile uses multi-stage build: `eclipse-temurin:21-jdk-alpine` for build, `eclipse-temurin:21-jre-alpine` for runtime.
