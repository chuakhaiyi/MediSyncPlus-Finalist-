# MediSync+

MediSync+ is an AI-powered post-discharge healthcare companion for chronic care patients, initially targeting Congestive Heart Failure (CHF). It bridges the gap between hospital discharge and long-term recovery through proactive monitoring, a multi-agent AI system, and a secure hospital backend integration layer.

---

## Documents
- [Deployment Plan](./Deployment%20Plan.pdf)
- [Business Proposal](./Business%20Proposal%20(Final%20Round).pdf)
- [RQATD](./MediSyncPlus-Finalist-%20Refined%20Testing%20Analysis%20Documentation%20(Final%20Round).docx.pdf)
- [Pitch Deck](./MediSyncPlus-Finalist-%20MediSync+%20Pitch%20Deck.pdf)


## Overview

After discharge, patients often struggle to stay on top of medications, vital measurements, and follow-up care. MediSync+ puts an intelligent assistant in their pocket that understands their specific care plan, tracks adherence, flags warning signs, and communicates with the hospital system in the background.

The app runs entirely offline-capable using a local Room database and a mock hospital API for development. When a real backend is available, it seamlessly connects to a Node.js/PostgreSQL server that hospitals use to post care records and sync data to the app.

---

## Key Features

### Multi-Agent AI System

Five specialized agents work together to support the patient:

- **Medicine Agent** — tracks medication adherence, calculates risk from missed doses, and prompts follow-up when patterns are concerning
- **Symptom Agent** — analyzes reported symptoms using clinical triage guidelines and flags escalation triggers
- **Risk Trajectory Agent** — predicts health deterioration trends from longitudinal vitals and adherence data
- **Chat Agent** — a context-aware virtual nurse that answers questions using the patient's full medical history
- **Discharge Agent** — parses discharge notes into structured care instructions that populate the app automatically

### AI-Generated Daily Checklists

Each day the app generates a personalized task list by fetching the patient's active medications and doctor-prescribed care plan, building a prompt from real patient data, and asking the LLM to produce a structured JSON checklist. If the LLM is unavailable, it falls back gracefully to the raw care plan tasks.

### Hospital Backend Integration

A Node.js/Express + PostgreSQL backend lets hospitals post patient care records via a REST API. The backend uses Claude AI to auto-generate a daily timetable from the doctor's instructions, queues it for delivery to the app, and maintains a HIPAA-ready immutable audit trail of every access and mutation.

### Vitals Monitoring

Tracks blood pressure, weight, blood sugar, temperature, heart rate, and oxygen saturation — with metric/imperial unit toggling that persists per-user.

### Offline-First Architecture

All patient data is stored in Room (SQLite). The app works fully without a network connection and syncs with the backend opportunistically via `EmrSyncWorker`.

---

## Project Structure

```
MediSync/
├── app/src/main/java/com/medisyncplus/
│   ├── ai/                    # Agent orchestration + LLM service
│   │   └── agents/            # ChatAgent, MedicineAgent, SymptomAgent, etc.
│   ├── data/
│   │   ├── database/          # Room DB, DAOs, DatabaseSeeder
│   │   ├── mock/              # MockHospitalApi.kt (dev/testing)
│   │   ├── models/            # Room entities
│   │   └── repository/        # MediSyncRepository
│   ├── di/                    # Hilt dependency injection
│   ├── navigation/            # Compose navigation graph
│   ├── ui/
│   │   ├── components/        # Reusable Compose components
│   │   └── screens/           # All app screens
│   ├── viewmodel/             # MediSyncViewModel + UI state classes
│   └── workers/               # Background WorkManager tasks
│
└── backend/
    ├── src/
    │   ├── agents/            # Claude-powered backend agents
    │   ├── middleware/        # Auth, audit logging
    │   ├── routes/            # REST API endpoints
    │   ├── services/          # LLM service, timetable generation
    │   └── db/                # Prisma client
    └── prisma/schema.prisma   # Full database schema
```

---

## Tech Stack

**Android App**
- Kotlin + Jetpack Compose
- Hilt (dependency injection)
- Room (local database)
- WorkManager (background jobs)
- OkHttp (LLM API calls)

**Backend**
- Node.js + Express + TypeScript
- PostgreSQL + Prisma ORM
- AES-256-GCM encryption on all PHI fields
- bcrypt-hashed API keys, short-lived JWTs

---

## Setup

### Android App

1. Open the root project folder in Android Studio (API 26+)
2. In `app/build.gradle.kts`, set your LLM credentials:
   ```kotlin
   buildConfigField("String", "LLM_API_KEY", "\"your-key-here\"")
   buildConfigField("String", "LLM_BASE_URL", "\"https://api.your-llm-provider.com\"")
   ```
3. Add your `google-services.json` to `app/` (required for Firebase; a placeholder is included)
4. Sync Gradle and run — the mock hospital API works out of the box, no backend needed

### Backend (optional — only needed for real hospital integration)

```bash
cd backend
cp .env.example .env        # fill in DATABASE_URL, CLAUDE_API_KEY, JWT_SECRET, etc.
npm install
npx prisma migrate deploy
npx prisma generate
npm run dev
```

---

## Mock Hospital API

During development, `MockHospitalApi.kt` replaces all real hospital HTTP calls. It returns typed data that mirrors the real API contract — no network, no Retrofit, no running server required.

When you're ready to connect to a real backend, create a Retrofit interface that mirrors `MockHospitalApi`'s method signatures, add it to `AppModule.kt` as a `@Singleton`, and inject it in place of the mock calls. The data class contracts are defined inside `MockHospitalApi` — copy them to your Retrofit DTOs and the rest of the app requires no changes.

---

## Backend API Reference

### Authentication

| Mode | Header |
|------|--------|
| Hospital system | `X-Hospital-API-Key: msk_...` |
| Doctor dashboard | `Authorization: Bearer <jwt>` |

### Endpoints

```
POST /api/auth/hospital/register    Register hospital (admin secret required)
POST /api/auth/doctor/login         Doctor login → JWT

POST /api/patients                  Create patient
GET  /api/patients                  List patients
GET  /api/patients/:id              Patient details
POST /api/patients/:id/link-app     Link to Android app user ID

POST /api/records                   Submit care record → AI timetable → sync queue
GET  /api/records/:patientId        Get active care record

GET  /api/sync/:appUserId           App polls for pending sync items
POST /api/sync/:appUserId/ack       App acknowledges delivery
```

---

## Security & Privacy

- AES-256-GCM encryption on all PHI database fields
- Multi-tenant isolation — all queries scoped to `hospitalId`
- bcrypt-hashed API keys, never stored in plaintext
- Short-lived JWTs (8h) for doctor dashboard sessions
- Rate limiting — 100 requests / 15 min per IP
- Immutable audit log — every data access and mutation recorded with actor, IP, and timestamp

---

## System Architecture

```
Hospital EMR  →  POST /api/records  →  Backend (Node.js + PostgreSQL)
                                              ↓ Claude AI (timetable)
                                              ↓ Sync Queue
Android App  ←────────────────────  GET /api/sync/:appUserId

         ── Development / No-backend mode ──
MockHospitalApi  →  Room DB  →  MediSyncViewModel  →  Compose UI
```

---

## License

This project is provided for demonstration and research purposes.
