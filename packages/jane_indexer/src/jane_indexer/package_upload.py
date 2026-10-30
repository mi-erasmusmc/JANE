"""Package the index and publish to S3 with an atomic pointer. See MODERNIZATION_PLAN.md section 5.

Flow: tar+zstd the Tantivy dir -> upload s3://<bucket>/index-YYYY-MM.tar.zst ->
write s3://<bucket>/current.txt LAST (atomic promote + manual gate).
"""


def package_and_upload(index_dir: str, bucket: str, version: str, promote: bool = False):
    """Create the tarball, upload it, and (if promote) write current.txt last. TODO."""
    raise NotImplementedError
