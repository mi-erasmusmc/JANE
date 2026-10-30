"""Stage 2 orchestration entry point (`jane-index`). See MODERNIZATION_PLAN.md section 6.

Steps: read SQLite -> load journal metadata -> filter (inclusion) -> build Tantivy index
-> package + upload to S3 -> (optionally) promote by writing current.txt.
Config via env/.env (no hardcoded paths).
"""


def main() -> None:
    raise NotImplementedError
