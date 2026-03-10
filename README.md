# RFP Extractor

Air-gapped document intelligence platform that converts Government of Bangladesh RFP/ToR PDFs into structured JSON, then
runs domain rule packs to produce Bid Clarity outputs.

## Architecture (4 Services)

```
┌─────────────┐     ┌─────────────────┐     ┌──────────────────┐     ┌──────────┐
│  Frontend   │────▶│   Java Service  │────▶│  Python Sidecar  │     │ Postgres │
│  :3000      │     │   :8080         │────▶│  :8000 (OCR)     │     │  :5432   │
│  (Nginx)    │     │  (Spring Boot)  │     │  (FastAPI)       │     │          │
└─────────────┘     └─────────────────┘     └──────────────────┘     └──────────┘
                            │                                              ▲
                            └──────────────────────────────────────────────┘
```

## Prerequisites

- Docker & Docker Compose
- Java 21 + Maven 3.9+ (local dev)
- Node 20+ (frontend dev)
- Python 3.11+ (OCR sidecar dev)
- NVIDIA GPU with drivers + [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html) (for GPU-accelerated OCR sidecar)
- OpenRouter API key (from https://openrouter.ai/) OR local Ollama instance

## Option A: Full Docker Stack (Recommended)

### 1. Create `.env` file

```bash
cp .env.example .env
```

Edit `.env`:

```env
OPENROUTER_API_KEY=sk-or-v1-your-key-here
POSTGRES_DB=rfpdb
POSTGRES_USER=rfpuser
POSTGRES_PASSWORD=rfppass
STORAGE_ENCRYPTION_KEYSET=<base64-encoded-tink-json-keyset>
```

Current backend startup expects `STORAGE_ENCRYPTION_KEYSET` to be present.

### 2. Generate JWT RSA key pair

The backend reads these files from the classpath, so they must exist at:

- `rfp-service/src/main/resources/jwt-private.pem`
- `rfp-service/src/main/resources/jwt-public.pem`

```bash
openssl genrsa -out rfp-service/src/main/resources/jwt-private.pem 2048
openssl rsa -in rfp-service/src/main/resources/jwt-private.pem -pubout -out rfp-service/src/main/resources/jwt-public.pem
```

### 3. Generate Tink encryption keyset

The app uses Google Tink AES256-GCM to encrypt stored files. You need a base64-encoded JSON keyset.

**Option A — Using `tinkey` CLI (recommended for production):**

```bash
# Install tinkey (macOS)
brew tap google/tink https://github.com/google/tink
brew install tinkey

# Or download from https://github.com/tink-crypto/tink-java/releases

# Generate keyset and base64-encode it
tinkey create-keyset --key-template AES256_GCM --out-format json | base64 -w 0
```

**Option B — Dev shortcut (local only, not for production):**

Copy the test keyset from `rfp-service/src/test/resources/application-test.properties` (look for the `app.storage.encryption.keyset` property) and base64-encode it if not already encoded.

**Then set it in `.env`:**

```properties
STORAGE_ENCRYPTION_KEYSET=<paste base64-encoded keyset here>
```

If omitted, encryption adapters won't function.

### 4. Start all services

```bash
docker compose up --build -d
```

### 5. Verify

| Service    | URL                                 | Expected                 |
|------------|-------------------------------------|--------------------------|
| Frontend   | http://localhost:3000               | Login page               |
| Java API   | http://localhost:8080/api/v1/health | JSON health status       |
| Python OCR | http://localhost:8000/health        | JSON with easyOCR status |
| PostgreSQL | localhost:5432                      | DB `rfpdb`               |

### 6. Seed admin user

```bash
docker compose exec postgres psql -U rfpuser -d rfpdb -f /dev/stdin < rfp-service/src/main/resources/db/seed/admin_seed.sql
```

Default seeded admin:

- username: `admin`
- bootstrap password: `Admin@1234`

### 7. Apply source-code changes to Docker

Current Docker setup is image-build based. Containers do not mount your source tree for live reload, so code changes are picked up only after rebuilding the affected service.

Rebuild only the service you changed:

```bash
# Backend Java changes
docker compose up --build -d rfp-service

# Python sidecar changes
docker compose up --build -d rfp-python-sidecar

# Frontend React changes
docker compose up --build -d rfp-frontend
```

Rebuild multiple services together:

```bash
docker compose up --build -d rfp-service rfp-python-sidecar rfp-frontend
```

Useful checks:

```bash
docker compose ps
docker compose logs -f rfp-service
docker compose logs -f rfp-python-sidecar
docker compose logs -f rfp-frontend
```

If Docker cache becomes misleading:

```bash
docker compose build --no-cache rfp-service
docker compose up -d rfp-service
```

## Option B: Local Dev (No Docker for services)

### 1. Start PostgreSQL only

```bash
docker run -d --name rfp-postgres \
  -e POSTGRES_DB=rfpdb \
  -e POSTGRES_USER=rfpuser \
  -e POSTGRES_PASSWORD=rfppass \
  -p 5432:5432 \
  postgres:16-alpine
```

### 2. Start Python OCR sidecar

```bash
cd rfp-python-sidecar
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

First startup downloads easyOCR models (~500MB). Requires system packages for OCR, PDF rendering, and CUDA.

On Ubuntu or Debian (GPU server):

```bash
sudo apt-get update
sudo apt-get install -y tesseract-ocr tesseract-ocr-ben poppler-utils ghostscript libgl1

# CUDA toolkit for PyTorch GPU support (requires NVIDIA drivers already installed)
wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2204/x86_64/cuda-keyring_1.1-1_all.deb
sudo dpkg -i cuda-keyring_1.1-1_all.deb
sudo apt-get update
sudo apt-get install -y cuda-toolkit-12-4
```

Verify GPU is available after installing requirements:

```bash
python3 -c "import torch; print(torch.cuda.is_available())"  # Should print True
```

For Docker deployment, also install the [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html):

```bash
curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list | \
  sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' | \
  sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list
sudo apt-get update
sudo apt-get install -y nvidia-container-toolkit
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker
```

Verify Docker GPU access: `docker run --rm --runtime=nvidia nvidia/cuda:12.4.1-runtime-ubuntu22.04 nvidia-smi`

### 3. Run Java service

Export runtime env vars first:

```bash
export OPENROUTER_API_KEY=sk-or-v1-your-key-here
export POSTGRES_DB=rfpdb
export POSTGRES_USER=rfpuser
export POSTGRES_PASSWORD=rfppass
export STORAGE_ENCRYPTION_KEYSET=<base64-encoded-tink-json-keyset>
```

Preferred local dev command:

```bash
mvn -pl rfp-core,rfp-service spring-boot:run
```

Packaged alternative:

```bash
mvn clean package -DskipTests

java -jar rfp-service/target/rfp-service-1.0.0-SNAPSHOT.jar
```

Default profile connects to `localhost:5432/rfpdb` and sidecar at `localhost:8000`.

### 4. Start React frontend

```bash
cd rfp-frontend
npm install
npm run dev
```

Frontend dev server runs at http://localhost:5173, proxying API calls to http://localhost:8080.

## First Run Checklist

1. Create root `.env`
2. Generate `jwt-private.pem` and `jwt-public.pem`
3. Set `STORAGE_ENCRYPTION_KEYSET`
4. Start services
5. Seed the admin user
6. Open the frontend and log in

## Running Tests

```bash
# Full test suite
mvn test

# Single module
mvn test -pl rfp-service -am
```

Tests use H2 in-memory DB, pre-generated test JWT keys, and test Tink keyset — no external services needed.

## Switching LLM Provider

In `rfp-service/src/main/resources/application.properties`:

```properties
# OpenRouter (cloud)
app.llm.provider=openrouter
# Ollama (air-gapped)
app.llm.provider=ollama
```

Ollama must be running at `http://localhost:11434` with models `llama3.1:8b` and `llama3.1:70b` pulled.

## Key Config Files

| File                                                           | Purpose                  |
|----------------------------------------------------------------|--------------------------|
| `.env.example`                                                 | Template for env vars    |
| `docker-compose.yml`                                           | 4-service orchestration  |
| `rfp-service/src/main/resources/application.properties`        | All backend config       |
| `rfp-service/src/main/resources/application-docker.properties` | Docker profile overrides |
| `rfp-frontend/.env.example`                                    | `VITE_API_BASE_URL`      |
| `rfp-frontend/nginx.conf`                                      | SPA routing + API proxy  |
| `rfp-python-sidecar/requirements.txt`                          | Python deps              |
