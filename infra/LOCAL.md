# Local stack

Mongo 8, MinIO, the backend and (optionally) the web app in Docker Compose (project `nashi-v2`, separate from the V1
containers).

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

## Web

`infra/scripts/local-up.sh --with-web` also builds and starts the `web` service: nginx on `NASHI_WEB_PORT` (default
4175) serving the production build of `web/` and proxying `/api/` to the backend.

```bash
infra/scripts/local-up.sh --with-web
curl -fsS http://127.0.0.1:4175/ | grep -q '<div id="root">' && echo "web is up"
curl -fsS http://127.0.0.1:4175/api/v2/health                     # {"status":"UP"} through nginx
curl -sI http://127.0.0.1:4175/ | grep -i cache-control            # no-cache (the SPA shell)
curl -sI -H 'Accept-Encoding: gzip' http://127.0.0.1:4175/geo/regions.geojson | grep -i -E 'content-(type|encoding)'
                                                                   # application/geo+json, gzip
```

- Hashed `/assets/` are cached for a year (`immutable`); `index.html`, every SPA route and `/geo/` are `no-cache`
  (revalidated by ETag). JS, CSS, JSON and GeoJSON are gzipped.
- The image holds no test files: the MSW worker and the fixture audio/images go only into mock builds
  (`VITE_API_MOCK=1`), and `npm run check:bundle -w web` fails a production `dist` that contains them.
- For development use the Vite dev server instead (`web/README.md`): `npm run dev -w web` on 127.0.0.1:5174 proxies
  `/api` to the backend on 8081 and drops `Origin`, so POSTs (likes, submissions) pass the backend's CORS check. The
  compose CORS list also names `http://localhost:5174` and `http://127.0.0.1:5174`.
