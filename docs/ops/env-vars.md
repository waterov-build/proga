# Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `POSTGRES_DB` | ✅ | `enduro` | Database name |
| `POSTGRES_USER` | ✅ | `enduro` | DB user |
| `POSTGRES_PASSWORD` | ✅ | — | DB password |
| `SECRET_KEY` | ✅ | — | JWT signing key |
| `JWT_ALGORITHM` | ❌ | `HS256` | JWT algorithm |
| `OPENAI_API_KEY` | ✅ | — | OpenAI key for agent service |
| `NEXT_PUBLIC_API_URL` | ❌ | `http://localhost:8000` | API URL for admin panel |
