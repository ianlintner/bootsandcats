import asyncio
from typing import Any, Dict, Optional

from .analysis import summarize_github_profile
from .config import Settings, get_settings
from .github_client import fetch_user_and_samples


async def _run_assessment(
    request: Dict[str, Any], settings: Settings
) -> Dict[str, Any]:
    github_username = request.get("github_username")
    email = request.get("email")
    requested_by = request.get("requested_by")

    user, repos = await fetch_user_and_samples(settings, github_username, email)
    if user is None:
        raise ValueError("Unable to resolve GitHub user from username or email")
    summary = summarize_github_profile(settings, user, repos)
    return {
        "user": user,
        "repositories": repos,
        "summary": summary,
        "requested_by": requested_by,
    }


def run_assessment(request: Dict[str, Any]) -> Dict[str, Any]:
    """Entry point for RQ worker (sync wrapper around async logic)."""

    settings = get_settings()
    return asyncio.run(_run_assessment(request, settings))
