"""Runtime configuration (env/.env). No hardcoded paths. See IMPLEMENTATION_NOTES.md section 6."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="JANE_")

    # Local path to the `current` symlink that points at the active Tantivy index dir.
    index_path: str = "/var/jane/current"
    s3_bucket: str = ""
    admin_api_key: str = ""
    # Optional path for the dedicated feature-usage metrics log. When unset, metrics lines
    # go to the console (default logging). When set, they are written to this rotating file.
    metrics_log: str = ""


settings = Settings()
