"""Metrics-only structured logging (decision #19): timestamp, endpoint, result counts,
latency, status. NO raw query text. Emitted as a single structured log line per request.
"""

from __future__ import annotations

import contextlib
import logging
import time
from collections.abc import Iterator

logger = logging.getLogger("jane.metrics")


@contextlib.contextmanager
def log_metrics(endpoint: str) -> Iterator[dict]:
    """Context manager that logs endpoint, latency, result count and status.

    Yields a mutable dict; set ``m["results"]`` to record the result count. Never log the
    query text.
    """
    data: dict = {"results": 0}
    start = time.perf_counter()
    status = "ok"
    try:
        yield data
    except Exception:
        status = "error"
        raise
    finally:
        latency_ms = (time.perf_counter() - start) * 1000.0
        logger.info(
            "endpoint=%s status=%s results=%s latency_ms=%.1f",
            endpoint,
            status,
            data.get("results", 0),
            latency_ms,
        )
