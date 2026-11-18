# Splitly on Coolify

The backend runs as a Docker Image resource on port 8080, with a dedicated
PostgreSQL 18 resource. The frontend is a separate Nginx container on port 8080.
Images are published to `registry.toniess.dev/splitly/backend` and
`registry.toniess.dev/splitly/frontend`.

## Runtime settings

Backend:

- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://<private-db-host>:5432/splitly`
- `SPRING_DATASOURCE_USERNAME=splitly`
- `SPRING_DATASOURCE_PASSWORD`: new database password, stored only in Coolify.
- `JWT_SECRET`: retain the existing production value when migrating sessions.
- `CORS_ALLOWED_ORIGINS`: comma-separated frontend origins, including the final
  production origin and the temporary migration frontend while verifying.
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0`: leave room for native JVM memory.

Start with a 1 GiB backend memory limit and 768 MiB for PostgreSQL. The frontend
can start with 128 MiB. Tune after observing normal traffic.

Health checks: `/actuator/health/readiness` for backend traffic readiness,
`/healthz` for frontend. Allow time for JVM startup and Flyway migrations.

## CI/CD

The new workflow `.github/workflows/coolify.yml` runs manually, or on pushes to
`main` when repository variable `COOLIFY_DEPLOY_ENABLED=true`. It runs tests,
builds an amd64 image, publishes `sha-<commit>` and `latest`, then calls a Coolify
POST deployment webhook. A successful webhook means queued, not deployed: verify
the deployment and health in Coolify.

Set repository secrets for each repository:

- `COOLIFY_REGISTRY_USER`
- `COOLIFY_REGISTRY_PASSWORD`
- `COOLIFY_DEPLOY_WEBHOOK`: the resource's deployment webhook.
- `COOLIFY_DEPLOY_TOKEN`: a Coolify token scoped to the required resources with
  Deploy permission. Enable API access for the webhook to work.

Frontend also needs repository variable `COOLIFY_API_BASE` because Vite embeds
its API address at build time. Runtime environment changes cannot change it.

Keep the old Cloud.ru publishing workflow while validating the new deployment.
After cutover, disable the old workflow and enable the new push-to-deploy path.
Do not remove old images or Cloud.ru resources until the new installation is
verified. Retain known-good SHA tags for rollback; the registry needs separate
retention/garbage collection to reclaim disk space.

## Data migration and cutover

1. Connect to the direct Neon endpoint (not its transaction pooler). Use
   PostgreSQL 18 client tools; keep credentials and dumps outside Git.
2. Create a consistent custom-format dump with `pg_dump --format=custom
   --no-owner --no-acl`. Restore to the dedicated new database with `pg_restore
   --no-owner --no-acl --exit-on-error`. Never restore over an unrelated DB.
3. Compare per-table row counts, verify Flyway history and sequences, and check
   login, group lists, balances and expenses with the new API. Source and target
   must use the same application revision during initial comparison.
4. Prevent the test deployment from accepting real production writes. Before
   final migration, stop writes on the old Cloud.ru backend and wait for active
   transactions to finish. Take a fresh dump, recreate **only the new Splitly
   database** if necessary, and restore it. Recheck counts before starting the
   new backend. Do not allow both backends to write to divergent databases.
5. Rebuild frontend with the final API address and set final CORS origins. Point
   `splitly.toniess.dev` and the chosen API hostname at the VPS, issue HTTPS, and
   test real browser requests. GitHub Pages' existing CNAME must be replaced for
   the web hostname.
6. Existing mobile builds default to `https://splitly.containerapps.ru`.
   Moving DNS for `api.toniess.dev` does not update those installed clients:
   publish a mobile update or retain a compatible forwarding endpoint before
   retiring Cloud.ru. No mobile source changes are included in this preparation.

Once new production writes begin, returning to the old Neon snapshot would lose
those writes. Rollback requires reconciliation or migration of the new data;
rolling back only the application image is a separate operation.

External backups are not configured. Preserve the migration dump securely until
verification is complete; do not add it to Git or CI artifacts.
