from typing import List, Optional

from pydantic import BaseModel, Field, HttpUrl


class AssessmentRequest(BaseModel):
    github_username: Optional[str] = Field(
        None, description="GitHub username if known (preferred over email)"
    )
    email: Optional[str] = Field(
        None, description="Email address to look up GitHub user when username missing"
    )


class RepositorySample(BaseModel):
    name: str
    description: Optional[str] = None
    stargazers_count: int = 0
    language: Optional[str] = None
    html_url: HttpUrl
    topics: List[str] = Field(default_factory=list)
    sampled_files: List[dict] = Field(default_factory=list)


class AssessmentResult(BaseModel):
    user: dict
    repositories: List[RepositorySample]
    summary: str
    requested_by: Optional[str] = None


class AssessmentStatus(BaseModel):
    job_id: str
    status: str
    result: Optional[AssessmentResult] = None
    error: Optional[str] = None
