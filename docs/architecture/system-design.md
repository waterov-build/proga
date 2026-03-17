# System Design

```
[Garmin Device]
      │ BLE
      ▼
[Android Companion]
      │ HTTPS
      ▼
[Backend API]  ←→  [PostgreSQL]
      │              [Redis (cache/queue)]
      ▼
[Agent Service]  ←→  [OpenAI]
      │
[Admin Panel]
```

## Services
| Service | Tech | Responsibility |
|---------|------|----------------|
| backend | FastAPI + SQLAlchemy | Core API, auth, data storage |
| agent-service | FastAPI + LangChain | AI race ops, segment building, support |
| admin-panel | Next.js + React | Organiser UI |
| garmin-app | Connect IQ / MonkeyC | On-device recording & detection |
| android-companion | Kotlin + Jetpack Compose | BLE bridge, companion sync |
