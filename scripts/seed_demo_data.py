#!/usr/bin/env python3
"""Seed the database with demo data for local development."""
import asyncio
import httpx

BASE_URL = "http://localhost:8000/api/v1"


async def main() -> None:
    async with httpx.AsyncClient(base_url=BASE_URL) as client:
        # Register organiser
        r = await client.post("/users/", json={"email": "organiser@demo.com", "password": "demo1234"})
        print(f"Register organiser: {r.status_code}")

        # Login
        r = await client.post("/auth/token", data={"username": "organiser@demo.com", "password": "demo1234"})
        token = r.json()["access_token"]
        headers = {"Authorization": f"Bearer {token}"}

        # Create event
        r = await client.post("/events/", json={"name": "Demo Enduro 2025"}, headers=headers)
        event_id = r.json()["id"]
        print(f"Created event: {event_id}")

        # Create segments
        segments = [
            {
                "name": "Stage 1 – Rock Garden",
                "start_lat": 47.001,
                "start_lon": 8.001,
                "finish_lat": 47.005,
                "finish_lon": 8.006,
                "distance_m": 450.0,
            },
            {
                "name": "Stage 2 – Berms Flow",
                "start_lat": 47.010,
                "start_lon": 8.010,
                "finish_lat": 47.015,
                "finish_lon": 8.016,
                "distance_m": 380.0,
            },
        ]
        for seg in segments:
            seg["event_id"] = event_id
            r = await client.post("/segments/", json=seg, headers=headers)
            print(f"Created segment: {r.json()['name']}")

    print("Demo data seeded successfully.")


if __name__ == "__main__":
    asyncio.run(main())
