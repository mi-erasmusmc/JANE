"""Package the index and publish to S3 with an atomic pointer. See MODERNIZATION_PLAN.md section 5.

Flow: tar+zstd the Tantivy dir -> upload s3://<bucket>/index-YYYY-MM.tar.zst ->
write s3://<bucket>/current.txt LAST (atomic promote + manual gate).
"""

from __future__ import annotations

import os
import tarfile

import zstandard

POINTER_KEY = "current.txt"


def artifact_name(version: str) -> str:
    return f"index-{version}.tar.zst"


def make_tarball(index_dir: str, out_path: str) -> str:
    """Create a .tar.zst of the Tantivy index directory at out_path."""
    cctx = zstandard.ZstdCompressor(level=10)
    with (
        open(out_path, "wb") as fh,
        cctx.stream_writer(fh) as compressor,
        tarfile.open(mode="w|", fileobj=compressor) as tar,
    ):
        tar.add(index_dir, arcname=".")
    return out_path


def extract_tarball(archive_path: str, dest_dir: str) -> str:
    """Extract a .tar.zst archive into dest_dir (used by the server pull job / tests)."""
    os.makedirs(dest_dir, exist_ok=True)
    dctx = zstandard.ZstdDecompressor()
    with (
        open(archive_path, "rb") as fh,
        dctx.stream_reader(fh) as reader,
        tarfile.open(mode="r|", fileobj=reader) as tar,
    ):
        tar.extractall(dest_dir, filter="data")
    return dest_dir


def _s3_client():
    import boto3

    return boto3.client("s3")


def package_and_upload(
    index_dir: str,
    bucket: str,
    version: str,
    promote: bool = False,
    tmp_dir: str | None = None,
) -> str:
    """Create the tarball, upload it, and (if promote) write current.txt LAST.

    Returns the uploaded artifact key. The pointer write is the atomic promotion and the
    manual gate: only set promote=True once the build has been verified.
    """
    tmp_dir = tmp_dir or os.path.dirname(os.path.abspath(index_dir)) or "."
    key = artifact_name(version)
    local = os.path.join(tmp_dir, key)
    make_tarball(index_dir, local)

    client = _s3_client()
    client.upload_file(local, bucket, key)
    if promote:
        client.put_object(
            Bucket=bucket,
            Key=POINTER_KEY,
            Body=version.encode("utf-8"),
            ContentType="text/plain",
        )
    return key
