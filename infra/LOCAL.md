# Local stack

Mongo 8, MinIO and the backend in Docker Compose (project `nashi-v2`, separate from the V1 containers).

```bash
infra/scripts/local-up.sh              # mongo, minio, minio-init (bucket), backend
curl -fsS http://127.0.0.1:8081/api/v2/health   # {"status":"UP"}
infra/scripts/local-down.sh            # stop, keep data; add --volumes to delete Mongo and MinIO data
```

## Ports (127.0.0.1 only, overridable)

| Service | Default | Variable |
|---|---|---|
| Mongo | 27019 | `NASHI_MONGO_PORT` |
| Backend | 8081 | `NASHI_BACKEND_PORT` |
| MinIO API | 9000 | `NASHI_MINIO_PORT` |
| MinIO console | 9001 | `NASHI_MINIO_CONSOLE_PORT` |
| Web | 4175 | `NASHI_WEB_PORT` |

The defaults avoid V1 (Mongo on 27018, frontend on 4173, backend on 8080).

## Details

- MinIO credentials `minioadmin` / `minioadmin` are for local use only. Nothing secret lives in this repo.
- `minio-init` creates the bucket `nashi-v2` and allows anonymous download under `artists/`.
- MinIO images come from the community fork `pgsty/*` because the official `minio/minio` and `minio/mc` images are gone from Docker Hub.
- The backend runs with profile `local` (Telegram polling and sending off) and reaches `mongo` and `minio` by service name.
- Running the backend from Gradle on the host uses `application-local.properties`, which points at `localhost:27019` and `localhost:9000`.

## Web (not available yet)

The `web` service (nginx on `NASHI_WEB_PORT`, proxying `/api/` to the backend) is under compose profile `web` and is not started by default, because `web/` does not exist until Task 3. After Task 3: `infra/scripts/local-up.sh --with-web`, then `curl -fsS http://127.0.0.1:4175/ | grep -q '<div id="root">'`. That check was skipped in Task 4.
