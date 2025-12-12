import asyncio
from typing import Any, Dict, List, Optional

import httpx

from .config import Settings


class GitHubClient:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        headers = {
            "Accept": "application/vnd.github+json",
            "User-Agent": settings.user_agent,
        }
        if settings.github_token:
            headers["Authorization"] = f"Bearer {settings.github_token}"
        self._client = httpx.AsyncClient(
            base_url=settings.github_api_url,
            headers=headers,
            timeout=settings.request_timeout,
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def get_user(self, username: str) -> Optional[Dict[str, Any]]:
        resp = await self._client.get(f"/users/{username}")
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        return resp.json()

    async def find_user_by_email(self, email: str) -> Optional[Dict[str, Any]]:
        # GitHub search by email is limited and may require elevated scopes; best-effort only.
        query = f"{email} in:email"
        resp = await self._client.get("/search/users", params={"q": query, "per_page": 1})
        if resp.status_code in (403, 422):
            return None
        resp.raise_for_status()
        data = resp.json()
        items = data.get("items") or []
        if not items:
            return None
        username = items[0].get("login")
        if not username:
            return None
        return await self.get_user(username)

    async def list_repos(self, username: str) -> List[Dict[str, Any]]:
        params = {"per_page": 50, "sort": "updated"}
        resp = await self._client.get(f"/users/{username}/repos", params=params)
        resp.raise_for_status()
        repos = resp.json()
        filtered = [
            r
            for r in repos
            if not r.get("fork") and not r.get("archived")
        ]
        filtered.sort(key=lambda r: r.get("stargazers_count", 0), reverse=True)
        return filtered[: self.settings.max_repos]

    async def get_readme(self, full_name: str) -> Optional[str]:
        resp = await self._client.get(f"/repos/{full_name}/readme")
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        data = resp.json()
        download_url = data.get("download_url")
        if not download_url:
            return None
        raw_resp = await self._client.get(download_url)
        if raw_resp.is_success:
            return raw_resp.text[: self.settings.max_chars_per_file]
        return None

    async def get_repo_files(self, full_name: str) -> List[Dict[str, Any]]:
        resp = await self._client.get(f"/repos/{full_name}/contents")
        if resp.status_code != 200:
            return []
        items = resp.json()
        files = [i for i in items if i.get("type") == "file"]
        files.sort(key=lambda f: f.get("size", 0), reverse=True)
        samples = files[: self.settings.max_files_per_repo]
        results: List[Dict[str, Any]] = []
        for item in samples:
            download_url = item.get("download_url")
            if not download_url:
                continue
            try:
                raw = await self._client.get(download_url)
                if raw.is_success:
                    results.append(
                        {
                            "path": item.get("path"),
                            "size": item.get("size"),
                            "content": raw.text[: self.settings.max_chars_per_file],
                        }
                    )
            except httpx.HTTPError:
                continue
        return results

    async def gather_repo_samples(self, username: str) -> List[Dict[str, Any]]:
        repos = await self.list_repos(username)
        samples: List[Dict[str, Any]] = []
        for repo in repos:
            full_name = repo.get("full_name")
            if not full_name:
                continue
            readme, files = await asyncio.gather(
                self.get_readme(full_name), self.get_repo_files(full_name)
            )
            repo_sample = {
                "name": repo.get("name"),
                "full_name": full_name,
                "description": repo.get("description"),
                "html_url": repo.get("html_url"),
                "stargazers_count": repo.get("stargazers_count", 0),
                "language": repo.get("language"),
                "topics": repo.get("topics", []),
                "readme": readme,
                "sampled_files": files,
            }
            samples.append(repo_sample)
        return samples


async def fetch_user_and_samples(
    settings: Settings, github_username: Optional[str], email: Optional[str]
) -> tuple[Optional[Dict[str, Any]], List[Dict[str, Any]]]:
    client = GitHubClient(settings)
    try:
        user: Optional[Dict[str, Any]] = None
        if github_username:
            user = await client.get_user(github_username)
        if user is None and email:
            user = await client.find_user_by_email(email)
        if user is None:
            return None, []
        repos = await client.gather_repo_samples(user.get("login"))
        return user, repos
    finally:
        await client.aclose()
