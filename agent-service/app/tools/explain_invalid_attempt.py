"""Tool: explain_invalid_attempt — generates a human-readable explanation for an invalid attempt."""


def explain_invalid_attempt(validation_result: dict) -> str:
    if validation_result.get("valid"):
        return "Attempt is valid."
    reason = validation_result.get("reason", "Unknown reason")
    explanations = {
        "No GPS data": "Your device did not record any GPS data for this attempt. Ensure GPS is enabled and you have a clear sky view.",
        "Start geofence not triggered": "Your GPS track did not pass through the segment start zone. Make sure you crossed the start line.",
        "Finish geofence not triggered": "Your GPS track did not pass through the segment finish zone. Make sure you crossed the finish line.",
    }
    return explanations.get(reason, f"Attempt invalid: {reason}")
