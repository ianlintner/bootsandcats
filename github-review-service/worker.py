import redis
from rq import Connection, Queue, Worker

from app.config import get_settings
from app.tasks import run_assessment


if __name__ == "__main__":
    settings = get_settings()
    conn = redis.from_url(settings.redis_url)
    with Connection(conn):
        worker = Worker([settings.redis_queue_name])
        worker.work()
