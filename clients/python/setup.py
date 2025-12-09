"""
Setup script for bootsandcats-oauth2-client
"""

from setuptools import setup, find_packages

# Read the README file
with open("README.md", "r", encoding="utf-8") as fh:
    long_description = fh.read()

setup(
    name="bootsandcats-oauth2-client",
    version="1.0.0",
    author="Bootsandcats Team",
    author_email="support@example.com",
    description="Python client for OAuth2 Authorization Server API with OpenTelemetry support",
    long_description=long_description,
    long_description_content_type="text/markdown",
    url="https://github.com/ianlintner/bootsandcats",
    project_urls={
        "Bug Tracker": "https://github.com/ianlintner/bootsandcats/issues",
        "Documentation": "https://github.com/ianlintner/bootsandcats/tree/main/clients/python",
    },
    classifiers=[
        "Development Status :: 4 - Beta",
        "Intended Audience :: Developers",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Programming Language :: Python :: 3.12",
    ],
    package_dir={"": "src"},
    packages=find_packages(where="src"),
    python_requires=">=3.9",
    install_requires=[
        "urllib3>=1.25.3,<3.0.0",
        "python-dateutil>=2.8.0",
        "pydantic>=2.0.0,<3.0.0",
        "typing-extensions>=4.0.0",
    ],
    extras_require={
        "tracing": [
            "opentelemetry-api>=1.20.0",
            "opentelemetry-sdk>=1.20.0",
            "opentelemetry-exporter-otlp>=1.20.0",
            "opentelemetry-instrumentation-urllib3>=0.41b0",
        ],
        "dev": [
            "pytest>=7.0.0",
            "pytest-asyncio>=0.21.0",
            "pytest-cov>=4.0.0",
            "black>=23.0.0",
            "isort>=5.12.0",
            "mypy>=1.5.0",
            "ruff>=0.1.0",
        ],
    },
)
