#!/usr/bin/env python3
"""Export the OpenAPI schema from the backend to openapi.json."""
import json
import sys


def main() -> None:
    try:
        from app.main import app  # noqa: PLC0415
        schema = app.openapi()
        with open("openapi.json", "w") as f:
            json.dump(schema, f, indent=2)
        print("OpenAPI schema exported to openapi.json")
    except ImportError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
