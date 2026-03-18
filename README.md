# Enduro Garmin Platform

A platform for tracking enduro MTB race segments using Garmin devices, an Android companion app, a Python backend, and an AI agent service.

## Components

- **garmin-app**: Connect IQ app for Fenix, Enduro, and Edge devices
- **android-companion**: Android app bridging Garmin device and backend API
- **backend**: FastAPI backend for activity ingestion, segment management, leaderboards
- **agent-service**: AI-powered race operations agent

## Quick Start

Copy `.env.example` to `.env` and fill in the values, then run:

```bash
docker-compose up --build
```
