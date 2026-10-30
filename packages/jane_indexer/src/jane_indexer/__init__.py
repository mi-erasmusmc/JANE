"""JANE indexer (Stage 2, heavy box).

Reads the existing PubMed.sqlite (Stage 1 is out of scope), fetches + joins journal
metadata, applies inclusion rules, builds the Tantivy index, packages it, and uploads
to S3 with an atomic pointer. See MODERNIZATION_PLAN.md sections 5-6.
"""
