from typing import Any, Dict, List

from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI

from .config import Settings

PROMPT_TEMPLATE = """
You are an experienced engineering hiring manager. You will see metadata and small samples of a candidate's public GitHub work. Produce:
1) A short summary of the candidate's profile and technical breadth.
2) Strengths and positive signals.
3) Risks or gaps to explore in an interview.
4) Suggested interview focus areas.
Keep it concise (under 300 words). Avoid fabricating details. Prefer evidence from repo samples.
"""


def summarize_github_profile(
    settings: Settings, user: Dict[str, Any], repos: List[Dict[str, Any]]
) -> str:
    if not settings.openai_api_key:
        raise ValueError(
            "OpenAI is not configured. Set OPENAI_API_KEY (or GH_REVIEW_OPENAI_API_KEY) to run assessments."
        )
    model = ChatOpenAI(
        model=settings.openai_model,
        api_key=settings.openai_api_key,
        temperature=0.35,
        max_tokens=600,
    )

    repo_snippets = []
    for repo in repos:
        snippet = {
            "name": repo.get("name"),
            "description": repo.get("description"),
            "stars": repo.get("stargazers_count", 0),
            "language": repo.get("language"),
            "topics": repo.get("topics", []),
            "readme": repo.get("readme"),
            "sampled_files": repo.get("sampled_files", []),
        }
        repo_snippets.append(snippet)

    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", PROMPT_TEMPLATE),
            (
                "human",
                "User profile: {user}\nRepositories: {repos}\nProvide the evaluation now.",
            ),
        ]
    )

    chain = prompt | model
    result = chain.invoke({"user": user, "repos": repo_snippets})
    return result.content
