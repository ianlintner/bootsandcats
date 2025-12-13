import os
from typing import Optional

import redis
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
from rq import Queue
from rq.job import Job

from .config import Settings, get_settings
from .models import AssessmentRequest, AssessmentResult, AssessmentStatus
from .tasks import run_assessment

app = FastAPI(title="GitHub Review Service")


class Health(BaseModel):
    status: str = "ok"


def get_queue(settings: Settings = Depends(get_settings)) -> Queue:
    connection = redis.from_url(settings.redis_url)
    return Queue(settings.redis_queue_name, connection=connection)


@app.get("/api/status", response_model=Health)
async def status() -> Health:
    return Health()


@app.post("/api/assessments", response_model=AssessmentStatus)
async def create_assessment(
    payload: AssessmentRequest,
    queue: Queue = Depends(get_queue),
    settings: Settings = Depends(get_settings),
    x_jwt_sub: Optional[str] = Header(None),
    x_jwt_username: Optional[str] = Header(None),
    x_jwt_email: Optional[str] = Header(None),
):
    subject = x_jwt_username or x_jwt_email or x_jwt_sub

    request_data = payload.model_dump()
    request_data["requested_by"] = subject

    job: Job = queue.enqueue(run_assessment, request_data)
    return AssessmentStatus(job_id=job.id, status=job.get_status())


@app.get("/api/assessments/{job_id}", response_model=AssessmentStatus)
async def get_assessment(job_id: str, queue: Queue = Depends(get_queue)):
    try:
        job = Job.fetch(job_id, connection=queue.connection)
    except Exception:
        raise HTTPException(status_code=404, detail="Job not found")
    status = job.get_status(refresh=True)
    result = None
    error = None
    if status == "finished" and job.result:
        result = AssessmentResult(**job.result)
    if status == "failed":
        error = str(job.exc_info) if job.exc_info else "Job failed"
    return AssessmentStatus(job_id=job.id, status=status, result=result, error=error)


@app.get("/", response_class=HTMLResponse)
async def root():
    index_path = os.path.join(os.path.dirname(__file__), "static", "index.html")
    with open(index_path, "r", encoding="utf-8") as f:
        return HTMLResponse(content=f.read())
