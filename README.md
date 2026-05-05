# HR ERP System - Azerbaijan (Monorepo)

A complete HR ERP system with face-recognition device integration, built as a monorepo containing:

- **`/backend`** — Spring Boot 3 (Java 17, Maven) — HR ERP core API
- **`/frontend`** — React 18 + TypeScript (Vite) — HR ERP web UI
- **`/isapi`** — Spring Boot (Java 21, Gradle) — ISAPI device integration service (face-recognition hardware)

## Architecture

```
Frontend (React) ──► Backend API (Spring Boot, :8080) ──► PostgreSQL
                                                               ▲
                     ISAPI Service (Spring Boot, :8081) ───────┘
                           │
                    Face-Recognition Devices (ISAPI protocol)
```

The ISAPI service connects to physical access-control / face-recognition devices, collects attendance punch events, and stores them in the database. The backend HR API reads these events for attendance tracking, shift management, and reporting.

## Quick Start

```bash
docker-compose up --build
```

Access:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- ISAPI Service: http://localhost:8081
- pgAdmin: http://localhost:5050

Login: admin / admin123

## Modules

### /backend (Maven, Java 17)
Spring Boot REST API for HR operations: employees, attendance, shifts, leave management, reporting.

### /frontend (Vite + React + TypeScript)
Single-page application — dashboards, HR reports, employee management, real-time attendance view.

### /isapi (Gradle, Java 21)
Connects to ISAPI-compatible face-recognition and access-control devices. Polls or streams attendance events and stores them for downstream consumption by the backend.

## Multi-Tenant Support

The system is designed to serve multiple companies (tenants) from a single deployment. Each company's data is isolated by `company_id` / `tenant_id` fields across tables. Device-to-tenant mapping is configured through the ISAPI service.

## Tech Stack

- **Backend**: Spring Boot 3, Spring Security, JPA/Hibernate, Flyway
- **Frontend**: React 18, TypeScript, Vite, Tailwind CSS, Zustand
- **ISAPI**: Spring Boot, Spring Data JPA, Jackson, Springdoc OpenAPI
- **Database**: PostgreSQL 15
- **DevOps**: Docker, Docker Compose, Nginx
