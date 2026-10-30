"""Open/reopen the Tantivy index at the `current` symlink target.

Each Uvicorn worker holds its own handle; all reopen on the admin reload signal. During a
swap two indexes are briefly mmap'd. See IMPLEMENTATION_NOTES.md section 10.
"""

from __future__ import annotations

import threading

import tantivy
from jane_core.search_service import SearchService


class IndexManager:
    """Holds the live Tantivy index + a SearchService; supports hot reopen."""

    def __init__(self, index_path: str, min_doc_freq: int | None = None) -> None:
        self.index_path = index_path
        self.min_doc_freq = min_doc_freq
        self._lock = threading.RLock()
        self._index: tantivy.Index | None = None
        self._service: SearchService | None = None

    def open(self) -> None:
        """Open the Tantivy index at the symlink target and build a SearchService."""
        index = tantivy.Index.open(self.index_path)
        if self.min_doc_freq is None:
            service = SearchService(index)
        else:
            service = SearchService(index, min_doc_freq=self.min_doc_freq)
        with self._lock:
            self._index = index
            self._service = service

    def reload(self) -> None:
        """Reopen the index at the (possibly newly swapped) symlink target."""
        self.open()

    @property
    def service(self) -> SearchService:
        with self._lock:
            if self._service is None:
                raise RuntimeError("Index not opened; call open() first.")
            return self._service

    @property
    def is_open(self) -> bool:
        with self._lock:
            return self._service is not None
