# Deployment Secrets

Create these files locally with owner-only permissions before running the production Compose reference:

* `database-password`: PostgreSQL password.
* `model-secret-key`: Base64 text that decodes to exactly 32 random bytes.
* `notification-secret-key`: a different Base64 value that decodes to exactly 32 random bytes.
* `embedding-api-key`: embedding provider API key.
* `bootstrap-password`: one-time initial administrator password; remove it after the forced password change.

This directory is ignored except for this README. Do not store secret values, hashes, partial values, or generated backups in the repository.
