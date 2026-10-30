"""Open/reopen the Tantivy index at the `current` symlink target.

Each Uvicorn worker holds its own handle; all reopen on the admin reload signal. During a
swap two indexes are briefly mmap'd. See IMPLEMENTATION_NOTES.md section 10.
"""


class IndexManager:
    """Holds the live Tantivy index + a SearchService; supports hot reopen."""

    def open(self) -> None:
        raise NotImplementedError

    def reload(self) -> None:
        """Reopen the index at the (possibly newly swapped) symlink target. TODO."""
        raise NotImplementedError
