from functools import lru_cache
from typing import Optional

from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application configuration loaded from environment variables or a .env file."""

    app_name: str = "github-review-service"
    environment: str = Field("local", description="Deployment environment name")
    openai_api_key: str = Field(..., env="OPENAI_API_KEY")
    openai_model: str = Field("gpt-4o-mini", description="OpenAI chat model to use")
    github_token: Optional[str] = Field(
        None, env="GITHUB_TOKEN", description="Personal access token for GitHub API"
    )
    redis_url: str = Field(
        "redis://redis:6379/0",
        env="REDIS_URL",
        description="Redis connection URL used by the API and worker",
    )
    redis_queue_name: str = Field("assessments", description="RQ queue name")
    github_api_url: str = Field(
        "https://api.github.com", description="Base URL for GitHub REST API"
    )
    request_timeout: float = Field(15.0, description="GitHub/OpenAI HTTP timeout (seconds)")
    max_repos: int = Field(8, description="Maximum repositories to sample per user")
    max_files_per_repo: int = Field(3, description="Maximum files to sample per repository")
    max_chars_per_file: int = Field(4000, description="Maximum characters per sampled file")
    user_agent: str = Field(
        "bootsandcats-github-review/0.1",
        description="User-Agent header for outbound GitHub requests",
    )
    allow_anonymous: bool = Field(
        False,
        description="Allow requests without JWT headers (local dev only)",
    )

    class Config:
        env_file = ".env"
        env_prefix = "GH_REVIEW_"
        case_sensitive = False


@lru_cache()
def get_settings() -> Settings:
    return Settings()