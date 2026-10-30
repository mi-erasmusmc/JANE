"""Metrics-only structured logging (decision #19): timestamp, endpoint, result counts,
latency, status. NO raw query text. Emitted as a single structured log line per request.
"""

from __future__ import annotations

import contextlib
import logging
import time
from collections.abc import Iterator
from logging.handlers import RotatingFileHandler

logger = logging.getLogger("jane.metrics")

# Rotating file defaults for the dedicated metrics log.
_MAX_BYTES = 10 * 1024 * 1024
_BACKUP_COUNT = 5


def configure_metrics_logging(path: str = "") -> None:
    """Route feature-usage metrics to a dedicated rotating file when ``path`` is set.

    Each request emits one structured line (``endpoint=… status=… results=… latency_ms=…``)
    prefixed with a timestamp. When ``path`` is empty the metrics logger keeps propagating to
    the root logger (console), preserving the previous behavior.
    """
    for handler in list(logger.handlers):
        logger.removeHandler(handler)
    if not path:
        logger.propagate = True
        return
    handler = RotatingFileHandler(path, maxBytes=_MAX_BYTES, backupCount=_BACKUP_COUNT)
    handler.setFormatter(logging.Formatter("%(asctime)s %(message)s"))
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    # Keep the dedicated feed isolated from interleaved uvicorn/app console output.
    logger.propagate = False


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
