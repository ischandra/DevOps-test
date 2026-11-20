# DevOps-test

Test automation tooling. Each project lives in its own folder with its own README:

| Folder | What it is | Language |
|---|---|---|
| Repo root (Maven project) | Synthetic test data generator: schema files -> CSV / JSON / SQL. See [DATAGEN.md](DATAGEN.md) | Java 8+ |
| [`api-test-framework/`](api-test-framework/) | API testing on pytest: direct API URLs, YAML suites, Postman collections, synthetic payloads | Python 3.9+ |
| [`api-synthetic-data/`](api-synthetic-data/) | Synthetic request data generated from your API definitions (OpenAPI, Postman, live URLs): valid, boundary, invalid and hostile cases | Python 3.9+ |

The two Python projects work together: `api-synthetic-data` can export its cases as a suite that `api-test-framework` runs against your API.

```bash
cd api-synthetic-data && pip install -r requirements.txt
python -m apisynth generate --openapi your-spec.yaml -f apitest -o generated --name my-api
cp generated/my-api.api.yaml ../api-test-framework/suites/
cd ../api-test-framework && pip install -r requirements.txt && pytest suites/my-api.api.yaml --env qa
```
