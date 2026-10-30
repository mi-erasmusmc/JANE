"""Runtime configuration (env/.env). No hardcoded paths. See IMPLEMENTATION_NOTES.md section 6."""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Local path to the `current` symlink that points at the active Tantivy index dir.
    index_path: str = "/var/jane/current"
    s3_bucket: str = ""
    admin_api_key: str = ""
    email_api_key: str = ""

    class Config:
        env_prefix = "JANE_"


settings = Settings()
