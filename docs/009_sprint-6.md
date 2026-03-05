# Sprint 6 — OCR & Mixed Pages

## 0) Sprint Intent

Fully implement the OCR pipeline: the Python FastAPI sidecar gains real OCR endpoints (easyOCR + Tesseract fallback),
the Java side gains a typed client, scanned pages are extracted via the sidecar, mixed pages have their text-layer and
OCR merged by reading region, multi-column layouts are de-interleaved into reading order, and scanned tables are
reconstructed via an LLM prompt. OCR confidence is propagated into every extracted clause and table. The
`ExtractTextNode` and `ExtractTablesNode` in the LangGraph4J graph are upgraded to use these paths.

---

## 1) Entry Criteria

- Sprint 5 is merged and green on CI.
- `ExtractTablesNode` is fully operational for DIGITAL/MIXED pages.
- `PageClassifier` labels pages as `DIGITAL`, `SCANNED`, or `MIXED`.
- `LlmAdapter` is Resilience4j-wrapped (Sprint 1) and accepts a model ID override parameter.
- Python sidecar container starts and responds to `GET /health` (Sprint 1 stub).
- `docker-compose.yml` runs `rfp-python-sidecar` on port 8000.
- `RestClient` bean is available in the Spring context.
- `DocumentChunkingService` (Sprint 4) is available.

---

## 2) Deliverables

| #    | Deliverable                                  | Type               | Location                                                                   |
|------|----------------------------------------------|--------------------|----------------------------------------------------------------------------|
| D-01 | `image_utils.py`                             | Python module      | `rfp-python-sidecar/image_utils.py`                                            |
| D-02 | `layout_detector.py`                         | Python module      | `rfp-python-sidecar/layout_detector.py`                                        |
| D-03 | `ocr_service.py`                             | Python module      | `rfp-python-sidecar/ocr_service.py`                                            |
| D-04 | `main.py` (full impl)                        | Python FastAPI app | `rfp-python-sidecar/main.py`                                                   |
| D-05 | OCR DTO records                              | Java records       | `rfp-service/.../adapter/ocr/`                                             |
| D-06 | `OcrSidecarClient`                           | Java class         | `rfp-service/.../adapter/ocr/OcrSidecarClient.java`                        |
| D-07 | `OcrResilienceConfig`                        | Java class         | `rfp-service/.../adapter/ocr/OcrResilienceConfig.java`                     |
| D-08 | `ScannedPageExtractor`                       | Java class         | `rfp-service/.../adapter/ocr/ScannedPageExtractor.java`                    |
| D-09 | `MixedPageExtractor`                         | Java class         | `rfp-service/.../adapter/extraction/MixedPageExtractor.java`               |
| D-10 | `ColumnDetector`                             | Java class         | `rfp-service/.../adapter/extraction/ColumnDetector.java`                   |
| D-11 | `ScannedTableReconstructor`                  | Java class         | `rfp-service/.../adapter/table/ScannedTableReconstructor.java`             |
| D-12 | `ExtractTextNode` (updated)                  | Java class         | `rfp-service/.../agent/ExtractTextNode.java`                               |
| D-13 | `ExtractTablesNode` (updated)                | Java class         | `rfp-service/.../agent/ExtractTablesNode.java`                             |
| D-14 | `prompts/scanned-table-reconstruction-v1.md` | Prompt file        | `prompts/scanned-table-reconstruction-v1.md`                               |
| D-15 | `ResultPage.tsx` page summary tab (updated)  | React update       | `rfp-frontend/src/pages/ResultPage.tsx`                                    |
| D-16 | Unit tests — Java                            | Java test classes  | `rfp-service/src/test/java/.../adapter/ocr/` and `.../adapter/extraction/` |
| D-17 | Unit tests — Python                          | pytest files       | `rfp-python-sidecar/tests/`                                                    |

---

## 3) Work Breakdown

### Epic A — Python OCR Sidecar (Full Implementation)

---

#### Story A-1: Implement `image_utils.py`

**Description:** Provide utility functions to render a PDF page to a PNG image at configurable DPI and to convert raw
bytes to a PIL Image object.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Render PDF page to PNG bytes
  Given a valid PDF file path and page_num=0 and dpi=300
  When render_pdf_page_to_image(pdf_path, page_num, dpi) is called
  Then the return value is a non-empty bytes object
  And the bytes represent a valid PNG image (starts with PNG magic bytes \x89PNG)

Scenario: Convert bytes to PIL Image
  Given valid PNG bytes
  When bytes_to_pil(image_bytes) is called
  Then the result is a PIL.Image.Image instance
  And the mode is "RGB"
```

**Interfaces / Contracts:**

```python
# rfp-python-sidecar/image_utils.py

from pdf2image import convert_from_path
from PIL import Image
import io

def render_pdf_page_to_image(pdf_path: str, page_num: int, dpi: int = 300) -> bytes:
    """
    Render a single PDF page to PNG bytes.
    page_num is 0-based; pdf2image uses 1-based first_page/last_page.
    Raises FileNotFoundError if pdf_path does not exist.
    Raises IndexError if page_num >= document page count.
    """
    ...

def bytes_to_pil(image_bytes: bytes) -> Image.Image:
    """
    Decode PNG/JPEG bytes to PIL Image in RGB mode.
    Raises ValueError if bytes are not a valid image.
    """
    ...
```

**Implementation Plan:**

1. `render_pdf_page_to_image`:
    - Call `convert_from_path(pdf_path, dpi=dpi, first_page=page_num+1, last_page=page_num+1)`.
    - If result list is empty → raise `IndexError(f"Page {page_num} not found in {pdf_path}")`.
    - Take `pages[0]`, convert to PNG bytes via `io.BytesIO`:
      `buf = io.BytesIO(); page.save(buf, format="PNG"); return buf.getvalue()`.

2. `bytes_to_pil`:
    - `img = Image.open(io.BytesIO(image_bytes)).convert("RGB")`.
    - Return `img`.

3. `requirements.txt` additions: `pdf2image>=1.17.0`, `Pillow>=10.0.0`.

4. System dependency: `poppler-utils` must be installed in the Docker container. Add
   `RUN apt-get install -y poppler-utils` to `rfp-python-sidecar/Dockerfile`.

**Dependencies:** `pdf2image`, `Pillow`, `poppler-utils` (system).

**Risks + Mitigations:**

| Risk                                                                          | Mitigation                                                                                       |
|-------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `pdf2image` requires `poppler-utils` system package not present in base image | Add `RUN apt-get install -y poppler-utils` in Dockerfile; document in `rfp-python-sidecar/README.md` |
| High DPI (300+) produces large images consuming excess memory                 | Cap DPI at 400 in `render_pdf_page_to_image`; log warning if `dpi > 400`                         |

**Test Plan — `rfp-python-sidecar/tests/test_image_utils.py`:**

```python
def test_render_returns_png_bytes(tmp_path):
    # Create minimal PDF using reportlab, render page 0
    ...
def test_bytes_to_pil_returns_rgb_image():
    # Use a 10x10 white PNG
    ...
def test_render_raises_index_error_for_invalid_page():
    ...
```

**Story Points:** 3

---

#### Story A-2: Implement `layout_detector.py`

**Description:** Use easyOCR word bounding boxes to heuristically classify regions of a page image as text regions or
table regions. Table detection is based on grid-pattern alignment of words.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Image with grid-aligned words
  Given a PIL Image where words are arranged in a 4x3 grid pattern with consistent X and Y spacing
  When detect_layout(image) is called
  Then has_table is True
  And table_regions contains at least one BoundingBox
  And text_regions contains remaining non-table areas

Scenario: Image with free-flowing text paragraphs
  Given a PIL Image with continuous prose text
  When detect_layout(image) is called
  Then has_table is False
  And table_regions is empty
```

**Interfaces / Contracts:**

```python
# rfp-python-sidecar/layout_detector.py

from pydantic import BaseModel
from typing import List
from PIL import Image

class BoundingBox(BaseModel):
    x: float       # normalized 0.0–1.0 (left edge)
    y: float       # normalized 0.0–1.0 (top edge)
    width: float   # normalized 0.0–1.0
    height: float  # normalized 0.0–1.0

class LayoutDetectionResult(BaseModel):
    has_table: bool
    table_regions: List[BoundingBox]
    text_regions: List[BoundingBox]

def detect_layout(image: Image.Image, reader=None) -> LayoutDetectionResult:
    """
    reader: easyocr.Reader instance (passed in to avoid re-initialization).
    If reader is None, creates a temporary Reader (slow; only for testing).
    """
    ...
```

**Implementation Plan:**

1. Run `reader.readtext(np.array(image), detail=1)` → list of `(bbox, text, confidence)`. Each `bbox` is
   `[[x0,y0],[x1,y0],[x1,y1],[x0,y1]]`.

2. Normalize bounding boxes by image width/height.

3. **Grid detection heuristic:**
    - Extract all x-left values of bounding boxes. Find unique clusters: group x-values within 3% of image width. If ≥ 3
      distinct x-clusters → evidence of columns.
    - Extract all y-top values. Find unique clusters: group y-values within 1.5% of image height. If ≥ 3 distinct
      y-clusters → evidence of rows.
    - If both column-clusters ≥ 3 and row-clusters ≥ 3 → classify bounding region as table.

4. **Table region bounds:** compute bounding box of all words in the grid region:
   `x=min(x0), y=min(y0), width=max(x1)-min(x0), height=max(y1)-min(y0)`.

5. **Text regions:** any words not part of the table region → group into a single text region bounding box per
   contiguous vertical band.

6. Return `LayoutDetectionResult(has_table=..., table_regions=[...], text_regions=[...])`.

**Dependencies:** `easyocr`, `numpy`, `pydantic`.

**Test Plan — `rfp-python-sidecar/tests/test_layout_detector.py`:**

```python
def test_detects_grid_as_table(mock_reader):
    # Mock reader returns grid-aligned bbox list
    ...
def test_no_table_for_prose_text(mock_reader):
    # Mock reader returns staggered bbox list
    ...
```

**Story Points:** 5

---

#### Story A-3: Implement `ocr_service.py`

**Description:** Provide `OcrService` which tries easyOCR first and falls back to Tesseract when easyOCR mean confidence
falls below 0.5. Returns structured `OcrResult` with per-word confidences.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: easyOCR returns high-confidence result
  Given an image of clear printed text
  When OcrService.extract_page(image_bytes) is called
  Then extraction_method is "easyocr"
  And page_confidence > 0.5
  And word_confidences is a non-empty list

Scenario: easyOCR returns low-confidence result — Tesseract fallback
  Given a mocked easyOCR reader that returns mean confidence 0.3
  When OcrService.extract_page(image_bytes) is called
  Then extraction_method is "tesseract"

Scenario: extract_page_with_layout returns both OCR and layout
  Given a valid image
  When OcrService.extract_page_with_layout(image_bytes) is called
  Then the result is a tuple of (OcrResult, LayoutDetectionResult)
  And both fields are non-None
```

**Interfaces / Contracts:**

```python
# rfp-python-sidecar/ocr_service.py

from pydantic import BaseModel
from typing import List, Tuple
from PIL import Image
import easyocr
import pytesseract
from pytesseract import Output
from layout_detector import LayoutDetectionResult, detect_layout, BoundingBox
from image_utils import bytes_to_pil

class OcrWord(BaseModel):
    text: str
    confidence: float
    bbox: BoundingBox

class OcrResult(BaseModel):
    text: str
    word_confidences: List[OcrWord]
    page_confidence: float        # mean of all word confidences
    word_count: int
    extraction_method: str        # "easyocr" | "tesseract"

class OcrService:
    def __init__(self, reader: easyocr.Reader):
        self._reader = reader

    def extract_page(self, image_bytes: bytes, lang: str = "eng+ben") -> OcrResult:
        """Try easyOCR; fall back to Tesseract if mean confidence < 0.5."""
        ...

    def extract_page_with_layout(self, image_bytes: bytes) -> Tuple[OcrResult, LayoutDetectionResult]:
        """Run OCR and layout detection in sequence."""
        ...

    def _extract_with_easyocr(self, image: Image.Image) -> OcrResult: ...
    def _extract_with_tesseract(self, image: Image.Image, lang: str) -> OcrResult: ...
    def _mean_confidence(self, words: List[OcrWord]) -> float: ...
```

**Implementation Plan:**

1. `_extract_with_easyocr(image)`:
    - `results = self._reader.readtext(np.array(image), detail=1)` → list of `(bbox, text, conf)`.
    - Build `OcrWord` for each: normalize bbox to
      `BoundingBox(x=bbox[0][0]/w, y=bbox[0][1]/h, width=(bbox[1][0]-bbox[0][0])/w, height=(bbox[2][1]-bbox[0][1])/h)`.
    - `text = " ".join([r[1] for r in results])`.
    - Return
      `OcrResult(text=text, word_confidences=words, page_confidence=mean, word_count=len(words), extraction_method="easyocr")`.

2. `_extract_with_tesseract(image, lang)`:
    - `data = pytesseract.image_to_data(image, lang=lang, output_type=Output.DICT)`.
    - Filter entries where `conf >= 0` (Tesseract returns -1 for non-word rows).
    - Build `OcrWord` list. `text = " ".join([d["text"] for d where conf >= 0])`.
    - Return `OcrResult(..., extraction_method="tesseract")`.

3. `extract_page(image_bytes, lang)`:
    - `image = bytes_to_pil(image_bytes)`.
    - Try `_extract_with_easyocr(image)`. If `result.page_confidence >= 0.5` → return.
    - Else: log `"[OcrService] easyOCR confidence {conf:.2f} < 0.5, falling back to Tesseract"`.
    - Return `_extract_with_tesseract(image, lang)`.

4. `extract_page_with_layout(image_bytes)`:
    - `ocr_result = extract_page(image_bytes)`.
    - `image = bytes_to_pil(image_bytes)`.
    - `layout = detect_layout(image, self._reader)`.
    - Return `(ocr_result, layout)`.

**Dependencies:** `easyocr`, `pytesseract`, `tesseract-ocr` system package (add to Dockerfile).

**Test Plan — `rfp-python-sidecar/tests/test_ocr_service.py`:**

```python
def test_uses_easyocr_when_confidence_high(mock_reader):
    ...
def test_falls_back_to_tesseract_when_easyocr_confidence_low(mock_reader, mock_tesseract):
    ...
def test_extract_page_with_layout_returns_tuple(mock_reader):
    ...
```

**Story Points:** 8

---

#### Story A-4: Implement `main.py` (full FastAPI application)

**Description:** FastAPI application with startup lifespan initializing easyOCR, three endpoints (health, OCR page, OCR
page with layout), proper error handling.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Health endpoint
  When GET /health is called
  Then HTTP 200 is returned
  And body is {"status": "ok", "version": "1.0.0", "ocr_engine": "easyocr+tesseract"}

Scenario: OCR endpoint with valid base64 image
  Given a valid base64-encoded PNG image in the request body
  When POST /ocr/page is called
  Then HTTP 200 is returned
  And the response body contains text, page_confidence, word_count, extraction_method

Scenario: OCR endpoint with invalid base64
  Given a malformed base64 string
  When POST /ocr/page is called
  Then HTTP 422 is returned

Scenario: OCR service not ready
  Given OcrService was not initialized (startup failed)
  When POST /ocr/page is called
  Then HTTP 503 is returned
  And the body contains {"detail": "OCR service not available"}
```

**Interfaces / Contracts:**

```python
# rfp-python-sidecar/main.py

from fastapi import FastAPI, HTTPException
from contextlib import asynccontextmanager
from pydantic import BaseModel
import easyocr
import base64
from ocr_service import OcrService, OcrResult
from layout_detector import LayoutDetectionResult

class OcrRequest(BaseModel):
    image_base64: str
    lang: str = "eng+ben"
    dpi: int = 300

class OcrPageWithLayoutResponse(BaseModel):
    ocr_result: OcrResult
    layout: LayoutDetectionResult

# Global service instance — initialized in lifespan
ocr_service: OcrService | None = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global ocr_service
    reader = easyocr.Reader(["en", "bn"], gpu=False)
    ocr_service = OcrService(reader)
    yield
    # Cleanup (none needed for easyOCR)

app = FastAPI(lifespan=lifespan)

@app.get("/health")
def health(): ...

@app.post("/ocr/page", response_model=OcrResult)
def ocr_page(request: OcrRequest): ...

@app.post("/ocr/page-with-layout", response_model=OcrPageWithLayoutResponse)
def ocr_page_with_layout(request: OcrRequest): ...
```

**Implementation Plan:**

1. `health()`: return `{"status": "ok", "version": "1.0.0", "ocr_engine": "easyocr+tesseract"}`.

2. `ocr_page(request)`:
    - If `ocr_service is None` → raise `HTTPException(503, detail="OCR service not available")`.
    - Decode base64: `try: image_bytes = base64.b64decode(request.image_base64, validate=True)` except
      `binascii.Error` → raise `HTTPException(422, detail="Invalid base64 encoding")`.
    - Call `ocr_service.extract_page(image_bytes, request.lang)` → `OcrResult`.
    - Return result (FastAPI auto-serializes Pydantic model).
    - On `Exception` in OCR:
      `return OcrResult(text="", word_confidences=[], page_confidence=0.0, word_count=0, extraction_method="error")` —
      HTTP 200 with degraded result.

3. `ocr_page_with_layout(request)`:
    - Same guard and base64 decode as above.
    - Call `ocr_service.extract_page_with_layout(image_bytes)` → `(ocr_result, layout)`.
    - Return `OcrPageWithLayoutResponse(ocr_result=ocr_result, layout=layout)`.

4. `requirements.txt` additions: `fastapi>=0.115.0`, `uvicorn[standard]>=0.30.0`, `easyocr>=1.7.2`,
   `pytesseract>=0.3.13`.

5. `Dockerfile` (update `rfp-python-sidecar/Dockerfile`):

```dockerfile
FROM python:3.11-slim
RUN apt-get update && apt-get install -y poppler-utils tesseract-ocr tesseract-ocr-ben && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**Test Plan — `rfp-python-sidecar/tests/test_main.py`:** Use `fastapi.testclient.TestClient`.

```python
def test_health_returns_200():
def test_ocr_page_rejects_invalid_base64():
def test_ocr_page_returns_503_when_service_not_ready():
def test_ocr_page_returns_result_for_valid_image(mock_ocr_service):
```

**Observability:**

```python
import logging
log = logging.getLogger(__name__)
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [OCR] page processed lang=%s confidence=%.3f method=%s words=%d",
    lang, result.page_confidence, result.extraction_method, result.word_count)
```

**Story Points:** 8

---

### Epic B — Java OCR Client

---

#### Story B-1: Define OCR DTOs and implement `OcrSidecarClient`

**Description:** Typed Java records mirroring the Python response models, plus a Spring `RestClient`-based HTTP client.
Includes Resilience4j retry wrapping.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Successful OCR page call
  Given the sidecar returns HTTP 200 with valid OcrResult JSON
  When OcrSidecarClient.extractPage(imageBytes, "eng+ben") is called
  Then the returned Optional contains an OcrResultDto with non-empty text

Scenario: Sidecar returns 503
  Given the sidecar returns HTTP 503
  When OcrSidecarClient.extractPage(imageBytes, "eng+ben") is called
  Then OcrUnavailableException is thrown

Scenario: Network timeout
  Given the sidecar does not respond within 60 seconds
  When OcrSidecarClient.extractPage(imageBytes, "eng+ben") is called
  Then a timeout exception propagates (wrapped by Resilience4j retry)

Scenario: Resilience4j retries on failure
  Given the first call throws OcrUnavailableException
  And the second call succeeds
  When OcrSidecarClient.extractPage(imageBytes, "eng+ben") is called
  Then the result is non-empty
  And the sidecar was called exactly 2 times
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/ocr/OcrPageRequest.java
public record OcrPageRequest(String imageBase64, String lang, int dpi) {
}

// rfp-service/.../adapter/ocr/BoundingBoxDto.java
public record BoundingBoxDto(double x, double y, double width, double height) {
}

// rfp-service/.../adapter/ocr/OcrWordDto.java
public record OcrWordDto(String text, double confidence, BoundingBoxDto bbox) {
}

// rfp-service/.../adapter/ocr/OcrResultDto.java
public record OcrResultDto(
    String text,
    List<OcrWordDto> wordConfidences,
    double pageConfidence,
    int wordCount,
    String extractionMethod
) {
}

// rfp-service/.../adapter/ocr/LayoutDetectionDto.java
public record LayoutDetectionDto(
    boolean hasTable,
    List<BoundingBoxDto> tableRegions,
    List<BoundingBoxDto> textRegions
) {
}

// rfp-service/.../adapter/ocr/OcrPageWithLayoutResultDto.java
public record OcrPageWithLayoutResultDto(OcrResultDto ocrResult, LayoutDetectionDto layout) {
}

// rfp-service/.../adapter/ocr/OcrUnavailableException.java
public class OcrUnavailableException extends RuntimeException {
    public OcrUnavailableException(String message) {
        super(message);
    }

    public OcrUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

// rfp-service/.../adapter/ocr/OcrSidecarClient.java
@Component
public class OcrSidecarClient {

    private final RestClient restClient;

    // Base URL injected from @Value("${app.sidecar.url:http://rfp-python-sidecar:8000}")
    public OcrSidecarClient(RestClient.Builder builder,
                            @Value("${app.sidecar.url:http://rfp-python-sidecar:8000}") String baseUrl) {
        this.restClient = builder
            .baseUrl(baseUrl)
            .build();
    }

    @Retry(name = "ocrRetry")
    public Optional<OcrResultDto> extractPage(byte[] imageBytes, String lang) { ...}

    @Retry(name = "ocrRetry")
    public Optional<OcrPageWithLayoutResultDto> extractPageWithLayout(byte[] imageBytes) { ...}
}
```

**Implementation Plan:**

1. Create all DTO record files in `rfp-service/src/main/java/com/dsi/rfp/adapter/ocr/`.

2. `OcrSidecarClient` constructor: build `RestClient` with `baseUrl`, connect timeout 5s, read timeout 60s via
   `ClientHttpRequestFactory` (use `SimpleClientHttpRequestFactory` with `setConnectTimeout(5000)` and
   `setReadTimeout(60000)`).

3. `extractPage(imageBytes, lang)`:
    - `String base64 = Base64.getEncoder().encodeToString(imageBytes)`.
    - Build `OcrPageRequest(base64, lang, 300)`.
    - Call `restClient.post().uri("/ocr/page").contentType(MediaType.APPLICATION_JSON).body(request).retrieve()`.
    -
   `.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> { throw new OcrUnavailableException("OCR sidecar returned " + res.getStatusCode()); })`.
    - `.toEntity(OcrResultDto.class)`.
    - Return `Optional.ofNullable(response.getBody())`.
    - On `ResourceAccessException` (network) → `log.error(...)`, return `Optional.empty()`.

4. `extractPageWithLayout(imageBytes)`: same pattern but `uri("/ocr/page-with-layout")` and
   `toEntity(OcrPageWithLayoutResultDto.class)`.

5. `OcrResilienceConfig` (Story B-2 below).

**Test Plan — `OcrSidecarClientTest.java`:**

```
shouldReturnOcrResultDtoOnSuccess
shouldThrowOcrUnavailableExceptionOn503
shouldReturnEmptyOptionalOnNetworkError
shouldEncodeImageBytesToBase64InRequest
```

Use `MockRestServiceServer` (Spring Test) or WireMock to stub HTTP responses.

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [OcrSidecarClient] Page OCR completed: lang={} confidence={} method={} words={}",
         lang, result.pageConfidence(),result.

extractionMethod(),result.

wordCount());
```

**Story Points:** 8

---

#### Story B-2: Implement `OcrResilienceConfig`

**Description:** Resilience4j retry configuration for OCR calls — 2 attempts, 2 second fixed wait, no exponential
backoff (OCR is slow; exponential would add excessive delay).

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: First attempt fails, second succeeds
  Given OcrSidecarClient annotated with @Retry(name="ocrRetry")
  And first call throws OcrUnavailableException
  And second call succeeds
  Then the method returns successfully after 2 total attempts
  And total elapsed time is approximately 2 seconds (the fixed wait)
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/ocr/OcrResilienceConfig.java
@Configuration
public class OcrResilienceConfig {

    @Bean
    public RetryConfig ocrRetryConfig() {
        return RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(OcrUnavailableException.class, ResourceAccessException.class)
            .ignoreExceptions(IllegalArgumentException.class)
            .build();
    }

    @Bean
    public RetryRegistry ocrRetryRegistry(RetryConfig ocrRetryConfig) {
        return RetryRegistry.of(Map.of("ocrRetry", ocrRetryConfig));
    }
}
```

**Implementation Plan:**

1. Add `application.properties` entries (as fallback / documentation):

```properties
# OCR sidecar URL
app.sidecar.url=http://rfp-python-sidecar:8000
# Resilience4j OCR retry — configured programmatically in OcrResilienceConfig
```

2. No circuit breaker on OCR (sidecar is stateless; circuit breaker adds complexity without benefit for a synchronous
   per-page call pattern).
3. No rate limiter (OCR calls are naturally serialized per page).

**Test Plan:** Backend unit tests only via `OcrSidecarClientTest` with mocked HTTP behavior for retry/failure paths.

**Story Points:** 2

---

### Epic C — Page-Level Extraction

---

#### Story C-1: Implement `ScannedPageExtractor`

**Description:** Render a scanned PDF page to PNG at 300 DPI using PDFBox `PDFRenderer`, base64-encode, call
`OcrSidecarClient`, return structured result with confidence.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Successful OCR of a scanned page
  Given page 3 is classified SCANNED
  And OcrSidecarClient returns OcrResultDto with text="Contract No: ABC" and pageConfidence=0.82
  When ScannedPageExtractor.extractPage(doc, 3) is called
  Then the result has text="Contract No: ABC"
  And confidence=0.82
  And pageNum=3

Scenario: OCR sidecar unavailable
  Given OcrSidecarClient throws OcrUnavailableException
  When ScannedPageExtractor.extractPage(doc, 3) is called
  Then the result has text=""
  And confidence=0.0
  And an error is logged at ERROR level
  And no exception is propagated to the caller
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/ocr/ScannedPageExtractionResult.java
public record ScannedPageExtractionResult(
        int pageNum,
        String text,
        double confidence,
        int wordCount
    ) {
}

// rfp-service/.../adapter/ocr/ScannedPageExtractor.java
@Component
public class ScannedPageExtractor {

    private final OcrSidecarClient ocrClient;

    public ScannedPageExtractor(OcrSidecarClient ocrClient) { ...}

    /**
     * Renders the page and extracts text via OCR sidecar.
     * Never throws; returns degraded result on failure.
     */
    public ScannedPageExtractionResult extractPage(PDDocument doc, int pageNum) { ...}
}
```

**Implementation Plan:**

1. `extractPage(doc, pageNum)`:
    - `PDFRenderer renderer = new PDFRenderer(doc)`.
    - `BufferedImage image = renderer.renderImageWithDPI(pageNum, 300, ImageType.RGB)`.
    - Convert to PNG bytes:
      `ByteArrayOutputStream baos = new ByteArrayOutputStream(); ImageIO.write(image, "PNG", baos); byte[] pngBytes = baos.toByteArray()`.
    - Call `ocrClient.extractPage(pngBytes, "eng+ben")`.
    - On `Optional.empty()` result → return `new ScannedPageExtractionResult(pageNum, "", 0.0, 0)`.
    - On `OcrUnavailableException` caught →
      `log.error("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=ERROR [ScannedPageExtractor] OCR unavailable for page={}", pageNum)`,
      return degraded result.
    - On success → return `new ScannedPageExtractionResult(pageNum, dto.text(), dto.pageConfidence(), dto.wordCount())`.

**Test Plan — `ScannedPageExtractorTest.java`:**

```
shouldReturnOcrTextAndConfidenceOnSuccess
shouldReturnDegradedResultWhenOcrUnavailable
shouldNotPropagateOcrUnavailableException
```

Mock `OcrSidecarClient`. Use a real 1-page PDDocument built with PDFBox to test rendering (or mock `PDFRenderer` via
subclass).

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [ScannedPageExtractor] Page={} confidence={} words={} method={}",
         pageNum, result.confidence(),result.

wordCount(), "easyocr|tesseract");
```

**Story Points:** 5

---

#### Story C-2: Implement `ColumnDetector`

**Description:** Detect single vs. two-column layout from text bounding boxes and reorder blocks into correct reading
order.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Two-column layout detected
  Given text blocks with a clear X-gap at x=300 on a 600-unit-wide page (gap > 12% of width)
  When ColumnDetector.detectColumns(blocks, 600) is called
  Then columnCount equals 2
  And columnBoundaryX equals Optional.of(300)

Scenario: Single-column layout
  Given text blocks with no X-gap > 12% of page width
  When ColumnDetector.detectColumns(blocks, 600) is called
  Then columnCount equals 1
  And columnBoundaryX equals Optional.empty()

Scenario: Reading order — two columns
  Given blocks in left and right columns (left at x~50, right at x~350)
  When ColumnDetector.sortBlocksForReading(blocks, twoColumnLayout) is called
  Then left column blocks appear first (sorted by y ascending)
  And right column blocks appear after (sorted by y ascending)
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/extraction/ColumnLayout.java
public record ColumnLayout(int columnCount, Optional<Integer> columnBoundaryX) {
}

// rfp-service/.../adapter/extraction/ColumnDetector.java
@Component
public class ColumnDetector {

    private static final double COLUMN_GAP_RATIO = 0.12;

    public ColumnLayout detectColumns(List<TextBlock> blocks, int pageWidth) { ...}

    public List<TextBlock> sortBlocksForReading(List<TextBlock> blocks, ColumnLayout layout) { ...}

    private List<Integer> findGaps(List<Float> sortedXPositions, int pageWidth) { ...}
}
```

**Implementation Plan:**

1. `detectColumns(blocks, pageWidth)`:
    - Collect all `block.x` values. Sort ascending. Deduplicate within 2px tolerance.
    - Iterate sorted positions. If `sorted[i+1] - sorted[i] > pageWidth * COLUMN_GAP_RATIO` → found gap at midpoint.
    - First such gap → `columnBoundaryX = Optional.of((int)midpoint)`. Set `columnCount = 2`.
    - No gap → return `ColumnLayout(1, Optional.empty())`.

2. `sortBlocksForReading(blocks, layout)`:
    - If `layout.columnCount() == 1` → sort by `block.y` ascending; return.
    - Else: partition by `block.x < layout.columnBoundaryX().get()`.
    - Sort left partition by y ascending. Sort right partition by y ascending.
    - Return concatenation: left + right.

**Test Plan — `ColumnDetectorTest.java`:**

```
shouldDetectTwoColumnLayoutWithClearGap
shouldReturnSingleColumnWhenNoGap
shouldSortSingleColumnByY
shouldSortTwoColumnLeftThenRight
shouldMakeGapThresholdConfigurable
```

**Story Points:** 5

---

#### Story C-3: Implement `MixedPageExtractor`

**Description:** For MIXED pages: get text from text layer, get OCR results with layout, merge by region — prefer OCR
where text layer has no coverage, prefer text layer where it does.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Region covered by text layer
  Given a MIXED page where the left half has text-layer coverage and right half is image
  When MixedPageExtractor.extractPage(doc, loader, pageNum) is called
  Then the left region uses text-layer text (higher quality)
  And the right region uses OCR text
  And method equals "text+ocr"

Scenario: OCR sidecar unavailable for mixed page
  Given OcrSidecarClient is unavailable
  When MixedPageExtractor.extractPage(doc, loader, pageNum) is called
  Then the result uses text-layer text only
  And method equals "text_only"
  And confidence reflects text-layer quality only
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/extraction/MixedPageContent.java
public record MixedPageContent(String text, double confidence, String method) {
}

// rfp-service/.../adapter/extraction/MixedPageExtractor.java
@Component
public class MixedPageExtractor {

    private final PdfDocumentLoader loader;
    private final OcrSidecarClient ocrClient;
    private final PDFRenderer pdfRenderer;  // injected or created per-call

    public MixedPageExtractor(PdfDocumentLoader loader, OcrSidecarClient ocrClient) { ...}

    public MixedPageContent extractPage(PDDocument doc, int pageNum) { ...}

    private boolean hasTextLayerCoverage(BoundingBoxDto region, List<TextBlock> textBlocks,
                                         float pageWidth, float pageHeight) { ...}

    private String mergeRegions(List<TextBlock> textBlocks,
                                OcrResultDto ocrResult,
                                LayoutDetectionDto layout,
                                float pageWidth, float pageHeight) { ...}
}
```

**Implementation Plan:**

1. `extractPage(doc, pageNum)`:
    - Get text-layer text blocks: `List<TextBlock> textBlocks = loader.loadPageBoundingBoxes(pageNum)`.
    - Render page to PNG:
      `PDFRenderer r = new PDFRenderer(doc); BufferedImage img = r.renderImageWithDPI(pageNum, 300, ImageType.RGB); byte[] png = toBytes(img)`.
    - Call `ocrClient.extractPageWithLayout(png)`. On `OcrUnavailableException` or `Optional.empty()` → fall back to
      text-layer only.
    - For each detected layout region:
        - If `hasTextLayerCoverage(region, textBlocks, pageWidth, pageHeight)` → use text from textBlocks overlapping
          that region (normalized box → pixel box → block centroid check).
        - Else → use OCR text for that region (from `ocrResult.text()` — no per-region OCR text available, use full OCR
          text as fallback for image-dominant regions).
    - Sort merged regions top-to-bottom.
    - `confidence = Math.min(textLayerQuality(textBlocks), ocrResult.pageConfidence())`. `textLayerQuality` =
      `min(1.0, textBlocks.size() / 50.0)` (heuristic: 50+ text blocks = high quality).
    - Return `MixedPageContent(mergedText, confidence, "text+ocr")`.

2. `hasTextLayerCoverage(region, textBlocks, pageWidth, pageHeight)`: convert normalized region to pixel coords. Count
   textBlocks whose centroid falls within region. If count > 0 → true.

**Test Plan — `MixedPageExtractorTest.java`:**

```
shouldPreferTextLayerForCoveredRegions
shouldPreferOcrForImageDominantRegions
shouldFallBackToTextOnlyWhenOcrUnavailable
shouldSetMethodToTextPlusOcr
```

Mock `OcrSidecarClient` and `PdfDocumentLoader`.

**Story Points:** 8

---

### Epic D — Scanned Table Reconstruction

---

#### Story D-1: Write `prompts/scanned-table-reconstruction-v1.md`

**Description:** Provide the full versioned prompt instructing the LLM to parse OCR text into a structured table JSON.

**File content — `prompts/scanned-table-reconstruction-v1.md`:**

```markdown
---
id: scanned-table-reconstruction-v1
version: "1.0"
model: google/gemini-2.0-flash-001
max_tokens: 2048
temperature: 0.0
---

# Scanned Table Reconstruction

## System

You are a document parsing assistant specializing in Government of Bangladesh procurement documents. Your task is to
reconstruct a structured table from OCR-extracted text that originated from a scanned PDF page.

The OCR text may have minor recognition errors. Apply domain knowledge to correct obvious errors (e.g., "0" vs "O",
missing spaces).

## Instructions

1. Analyze the OCR text below and determine if it contains tabular data.
2. If yes, identify:
    - The column headers (first row of the table, or inferred from context)
    - All data rows
3. Return a JSON object exactly as specified below. Do not include any text outside the JSON block.
4. If the text does not appear to be a table, return `{"headers": [], "rows": []}`.
5. Do not invent data. Only extract what is present in the OCR text.
6. If a cell value spans multiple OCR lines, join them with a space.
7. If a cell is blank, use an empty string `""`.

## Output Format

Return ONLY this JSON (no markdown fences, no explanation):

```json
{
  "headers": ["Column 1 Name", "Column 2 Name", "..."],
  "rows": [
    ["row1col1 value", "row1col2 value", "..."],
    ["row2col1 value", "row2col2 value", "..."]
  ]
}
```

## OCR Text

{{ocr_text}}

```

**Story Points:** 2

---

#### Story D-2: Implement `ScannedTableReconstructor`

**Description:** Check if OCR text looks tabular, call LLM with the prompt, parse response into `TableExtractionResult`.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: OCR text looks tabular — LLM returns valid JSON
  Given OCR text with tab-separated columns and multiple rows
  And LlmAdapter returns {"headers":["Item","Cost"],"rows":[["Software","100000"]]}
  When ScannedTableReconstructor.reconstructTable(ocrText, 5, 0.78) is called
  Then the result is a non-empty Optional
  And the TableExtractionResult has headers=["Item","Cost"]
  And the grid has 3 cells (1 header row + 1 data row x 2 cols)
  And confidence.method equals "ocr_llm_reconstruct"
  And confidence.score equals 0.78 * 0.8 = 0.624

Scenario: OCR text is prose, not a table
  Given OCR text with no tab patterns and no column alignment
  When ScannedTableReconstructor.reconstructTable(ocrText, 5, 0.7) is called
  Then the result is Optional.empty()
  And LlmAdapter is never called

Scenario: LLM returns malformed JSON
  Given OCR text that looks tabular
  And LlmAdapter returns non-JSON text
  When ScannedTableReconstructor.reconstructTable(ocrText, 5, 0.7) is called
  Then the result is Optional.empty()
  And an error is logged
```

**Interfaces / Contracts:**

```java
// rfp-service/.../adapter/table/ScannedTableReconstructor.java
@Component
public class ScannedTableReconstructor {

    private final LlmAdapter llmAdapter;
    private final ObjectMapper objectMapper;

    public ScannedTableReconstructor(LlmAdapter llmAdapter, ObjectMapper objectMapper) { ...}

    /**
     * @param ocrText raw OCR text from a page
     * @param pageNum page number (for metadata)
     * @param ocrPageConfidence confidence from OCR extraction (0.0–1.0)
     * @return Optional.empty() if text is not tabular or LLM fails
     */
    public Optional<TableExtractionResult> reconstructTable(
        String ocrText, int pageNum, double ocrPageConfidence) { ...}

    private boolean looksLikeTable(String text) { ...}

    private String buildPrompt(String ocrText) { ...}

    private TableExtractionResult parseResponse(String llmJson, int pageNum,
                                                double ocrPageConfidence) { ...}

    private List<TableCell> buildGrid(List<String> headers, List<List<String>> rows) { ...}
}
```

**Implementation Plan:**

1. `looksLikeTable(text)`:
    - Count lines containing `\t` (tab character). If ≥ 3 → return true.
    - Count lines with ≥ 2 runs of 2+ spaces. If ≥ 3 → return true.
    - Count lines matching `\s{2,}` separations. If ≥ 3 → return true.
    - Else → return false.

2. `buildPrompt(ocrText)`: load `prompts/scanned-table-reconstruction-v1.md` from classpath, replace `{{ocr_text}}` with
   the OCR text. Limit to first 3000 characters of ocrText.

3. `LlmAdapter.extractStructured(prompt, model)` call using model `google/gemini-2.0-flash-001` (extraction model, not
   judgment model).

4. `parseResponse(llmJson, pageNum, ocrPageConfidence)`:
    - `JsonNode node = objectMapper.readTree(llmJson)`.
    - `headers = node.get("headers")` → `List<String>`.
    - `rows = node.get("rows")` → `List<List<String>>`.
    - If headers empty → return degraded result or propagate empty.
    - Call `buildGrid(headers, rows)`.
    - `TableTypeClassifier.classify(headers, "")` → type.
    - Set `provenance = TableProvenance.SCANNED` for reconstructed tables.
    -
   `confidence = ExtractionConfidence.builder().score(ocrPageConfidence * 0.8).method("ocr_llm_reconstruct").build()`.
    - Return `Optional.of(TableExtractionResult.builder()...build())`.

5. `buildGrid(headers, rows)`:
    - Row 0: header cells (`isHeader=true`), `rowspan=1, colspan=1`.
    - Rows 1..n: data cells.
    - All cells: `rowspan=1, colspan=1` (LLM reconstruction cannot detect merged cells).

**Dependencies:** `LlmAdapter` (Sprint 1), `TableTypeClassifier` (Sprint 5 B-3), `ObjectMapper`.

**Test Plan — `ScannedTableReconstructorTest.java`:**

```
shouldReturnEmptyWhenTextIsNotTabular
shouldCallLlmWhenTextLooksTabular
shouldParseValidLlmResponseIntoTableResult
shouldReturnEmptyWhenLlmResponseIsMalformedJson
shouldComputeConfidenceAsOcrConfidenceTimesPointEight
shouldMarkFirstRowAsHeaders
```

Mock `LlmAdapter`. Test `looksLikeTable` with direct string inputs.

**Observability:**

```java
log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO [ScannedTableReconstructor] Page={} tabular={} llmSuccess={} confidence={}",
         pageNum, looksTabular, llmSuccess, confidence);
```

**Story Points:** 8

---

### Epic E — Agent Graph Updates

---

#### Story E-1: Update `ExtractTextNode`

**Description:** Route extraction by page classification: SCANNED → `ScannedPageExtractor`, MIXED →
`MixedPageExtractor`, DIGITAL → existing `PdfDocumentLoader`. Apply `ColumnDetector` to all pages. Propagate OCR
confidence into extracted text metadata.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Document with mixed page types
  Given state.pageClassifications = {0: DIGITAL, 1: SCANNED, 2: MIXED}
  When ExtractTextNode.execute(state) is called
  Then page 0 text comes from PdfDocumentLoader
  And page 1 text comes from ScannedPageExtractor
  And page 2 text comes from MixedPageExtractor
  And state.pageTexts has 3 entries

Scenario: ColumnDetector applied to all pages
  Given page 0 is a two-column document
  When ExtractTextNode.execute(state) is called
  Then the text for page 0 has left column content before right column content
```

**Interfaces / Contracts:**

```java
// rfp-service/.../agent/ExtractTextNode.java
@Component
public class ExtractTextNode implements NodeAction<ExtractionState> {

    private final PdfDocumentLoader digitalLoader;
    private final ScannedPageExtractor scannedExtractor;
    private final MixedPageExtractor mixedExtractor;
    private final ColumnDetector columnDetector;

    @Override
    public ExtractionState execute(ExtractionState state) { ...}

    private String extractTextForPage(PDDocument doc, int pageNum,
                                      PageClass pageClass) { ...}
}
```

**Implementation Plan:**

1. Open `PDDocument` from `state.documentPath`.
2. For each page 0..N-1:
    - Switch on `pageClassifications.get(page)`:
        - `DIGITAL` → `loader.loadPageText(pageNum)` (returns String). Get blocks via
          `loader.loadPageBoundingBoxes(pageNum)`.
        - `SCANNED` → `scannedExtractor.extractPage(doc, pageNum)`. Store `result.confidence()` in
          `state.pageConfidences.put(pageNum, confidence)`.
        - `MIXED` → `mixedExtractor.extractPage(doc, pageNum)`. Store confidence.
    - Get `List<TextBlock>` for page (for DIGITAL/MIXED from loader; for SCANNED reconstruct from OCR words if
      available).
    - `ColumnLayout layout = columnDetector.detectColumns(blocks, pageWidth)`.
    - `List<TextBlock> sorted = columnDetector.sortBlocksForReading(blocks, layout)`.
    - Build page text from sorted blocks.
    - `state.pageTexts.put(pageNum, text)`.
3. Close `PDDocument` in `finally`.

**Test Plan — `ExtractTextNodeTest.java`:**

```
shouldUseDigitalLoaderForDigitalPages
shouldUseScannedExtractorForScannedPages
shouldUseMixedExtractorForMixedPages
shouldApplyColumnDetectorToAllPages
shouldStoreOcrConfidenceInState
```

Mock all four collaborators.

**Story Points:** 5

---

#### Story E-2: Update `ExtractTablesNode` for SCANNED pages

**Description:** For pages classified as SCANNED, call `ScannedTableReconstructor` using the OCR text stored in
`state.pageTexts` and the confidence from `state.pageConfidences`.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Scanned page has a table
  Given state.pageClassifications has page 3 = SCANNED
  And state.pageTexts[3] contains tab-separated text
  And state.pageConfidences[3] = 0.75
  When ExtractTablesNode.execute(state) is called
  Then ScannedTableReconstructor.reconstructTable is called with ocrText and 0.75
  And the result table is added to state.tables

Scenario: Scanned page has no table
  Given state.pageTexts[3] is prose text
  When ExtractTablesNode.execute(state) is called
  Then ScannedTableReconstructor returns Optional.empty()
  And state.tables is not modified for page 3
```

**Implementation Plan:**

1. In `ExtractTablesNode.execute(state)`, after existing DIGITAL/MIXED extraction:
2. For each page in `state.pageClassifications` where value == `SCANNED`:
    - `String ocrText = state.pageTexts.getOrDefault(pageNum, "")`.
    - `double conf = state.pageConfidences.getOrDefault(pageNum, 0.0)`.
    - `Optional<TableExtractionResult> t = scannedTableReconstructor.reconstructTable(ocrText, pageNum, conf)`.
    - `t.ifPresent(table -> state.tables.add(table))`.
3. Inject `ScannedTableReconstructor` via constructor.

**Test Plan — `ExtractTablesNodeTest.java` (additions):**

```
shouldCallScannedTableReconstructorForScannedPages
shouldNotAddToTablesWhenReconstructorReturnsEmpty
```

**Story Points:** 3

---

### Epic F — Frontend Update

---

#### Story F-1: Update `ResultPage.tsx` with page summary tab

**Description:** Add a "Pages" tab showing a table with one row per page: page number, classification badge (
DIGITAL/SCANNED/MIXED), extraction method badge, confidence bar.

**Acceptance Criteria (Gherkin):**

```gherkin
Scenario: Page summary tab renders all pages
  Given a result with 10 pages, 8 DIGITAL and 2 SCANNED
  When the user clicks the "Pages" tab
  Then 10 rows are shown in the summary table
  And 8 rows have a green "DIGITAL" badge
  And 2 rows have a red "SCANNED" badge

Scenario: Confidence bar for each page
  Given page 3 has OCR confidence 0.63
  When viewing the Pages tab
  Then page 3 row shows a confidence bar filled to 63%
  And a numeric label "63%" is shown
```

**Implementation Plan:**

1. API response type: extend `RfpResultDto` to include `pageDetails: PageDetail[]` where
   `PageDetail = { pageNum: number; classification: PageClassification; extractionMethod: PageExtractionMethod; confidence: number }`
   and:
   `PageClassification = DIGITAL | SCANNED | MIXED`
   `PageExtractionMethod = TEXT_LAYER | OCR | TEXT_PLUS_OCR | OCR_LLM_RECONSTRUCT | OCR_FAILED`.
2. Java: add `pageDetails` field to the result DTO in `RfpController`/response mapper. Populate from
   `state.pageClassifications` and `state.pageConfidences`.
3. Frontend: add `PageSummaryTab.tsx` component. Render using shadcn `<Table>` with columns: Page, Type, Method,
   Confidence.
4. Classification badges using shadcn `<Badge variant="outline">` with className overrides:
    - `DIGITAL` → `className="text-green-700 border-green-300"`
    - `SCANNED` → `className="text-red-700 border-red-300"`
    - `MIXED` → `className="text-yellow-700 border-yellow-300"`
5. Confidence bar: shadcn `<Progress value={confidence * 100} className="h-1.5" />` (same as Sprint 5).
6. Add "Pages" `<TabsTrigger>` and `<TabsContent>` to the existing shadcn `<Tabs>` in `ResultPage.tsx`.

**Story Points:** 5

---

## 4) PR Plan

### PR 1 — Python Sidecar Full Implementation (Stories A-1 through A-4)

**Title:** `feat(ocr-sidecar): Full OCR FastAPI implementation with easyOCR + Tesseract fallback`

**Contents:**

- `rfp-python-sidecar/image_utils.py`
- `rfp-python-sidecar/layout_detector.py`
- `rfp-python-sidecar/ocr_service.py`
- `rfp-python-sidecar/main.py` (full, replacing Sprint 1 stub)
- `rfp-python-sidecar/Dockerfile` (updated with poppler + tesseract)
- `rfp-python-sidecar/requirements.txt` (updated)
- `rfp-python-sidecar/tests/test_image_utils.py`
- `rfp-python-sidecar/tests/test_layout_detector.py`
- `rfp-python-sidecar/tests/test_ocr_service.py`
- `rfp-python-sidecar/tests/test_main.py`

**Review Checklist:**

- [ ] easyOCR `gpu=False` set (CI environment has no GPU)
- [ ] Tesseract Bengali language pack (`tesseract-ocr-ben`) installed in Dockerfile
- [ ] HTTP 503 returned when `ocr_service is None`
- [ ] HTTP 200 with `confidence=0.0` returned on OCR processing failure (not HTTP 500)
- [ ] Invalid base64 returns HTTP 422 (not 500)
- [ ] FastAPI lifespan pattern used (not deprecated `on_event`)

---

### PR 2 — Java OCR Client + Page Extractors + ColumnDetector (Stories B-1 through C-3)

**Title:** `feat(ocr): Java OCR client, ScannedPageExtractor, MixedPageExtractor, ColumnDetector`

**Contents:**

- All DTO records in `adapter/ocr/`
- `OcrSidecarClient.java`
- `OcrResilienceConfig.java`
- `OcrUnavailableException.java`
- `ScannedPageExtractor.java`
- `MixedPageExtractor.java`
- `ColumnDetector.java`
- Unit tests for all the above

**Review Checklist:**

- [ ] `OcrSidecarClient` uses `RestClient` (not `RestTemplate`)
- [ ] 60-second read timeout configured on `RestClient`
- [ ] `OcrUnavailableException` is NOT caught inside `OcrSidecarClient` — propagates to `ScannedPageExtractor` which
  handles it
- [ ] `ScannedPageExtractor` never throws; returns degraded result
- [ ] `ColumnDetector.COLUMN_GAP_RATIO` is a named constant (not a magic number)
- [ ] `MixedPageExtractor` handles `OcrUnavailableException` gracefully (text-only fallback)

---

### PR 3 — Scanned Table Reconstruction + Agent Updates + Frontend (Stories D-1 through F-1)

**Title:**
`feat(extraction): Scanned table reconstruction, ExtractTextNode routing by page class, ResultPage page summary`

**Contents:**

- `prompts/scanned-table-reconstruction-v1.md`
- `ScannedTableReconstructor.java`
- Updated `ExtractTextNode.java`
- Updated `ExtractTablesNode.java`
- Updated `ResultPage.tsx` + new `PageSummaryTab.tsx`
- Unit tests: `ScannedTableReconstructorTest`, `ExtractTextNodeTest` (additions), `ExtractTablesNodeTest` (additions)

**Review Checklist:**

- [ ] `ScannedTableReconstructor` uses extraction model (`gemini-2.0-flash-001`), not judgment model
- [ ] Prompt file has valid YAML frontmatter with `id`, `version`, `model`, `max_tokens`, `temperature`
- [ ] `looksLikeTable` uses at least 2 independent heuristics
- [ ] `ExtractTextNode` routes ALL page types (no page left without text extraction)
- [ ] `state.pageConfidences` populated for OCR pages
- [ ] Frontend page badge uses correct Tailwind classes per classification type

---

## 5) Validation & Demo Script

### Step 1: Verify sidecar health

```bash
curl http://localhost:8000/health | jq .

# Expected:
# {
#   "status": "ok",
#   "version": "1.0.0",
#   "ocr_engine": "easyocr+tesseract"
# }
```

### Step 2: Direct sidecar OCR test

```bash
# Encode a test image
BASE64_IMG=$(base64 -w 0 testdata/scanned-page-sample.png)

curl -X POST http://localhost:8000/ocr/page \
  -H "Content-Type: application/json" \
  -d "{\"image_base64\": \"$BASE64_IMG\", \"lang\": \"eng+ben\"}" \
  | jq .

# Expected:
# {
#   "text": "Government of Bangladesh...",
#   "word_confidences": [...],
#   "page_confidence": 0.83,
#   "word_count": 142,
#   "extraction_method": "easyocr"
# }
```

### Step 3: Submit a scanned document

```bash
curl -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@testdata/ground-truth/scanned-ict-rfp.pdf" \
  -F "procRef=SCAN-2025-001" \
  | jq .

# Expected:
# {"jobId": "d4e5f6a7-...", "status": "SUBMITTED"}
```

### Step 4: Poll for completion

```bash
JOB_ID="d4e5f6a7-..."
curl http://localhost:8080/api/v1/rfp/status/$JOB_ID | jq .

# Expected:
# {"jobId": "...", "status": "COMPLETED"}
```

### Step 5: Verify page details in result

```bash
curl http://localhost:8080/api/v1/rfp/result/$JOB_ID \
  | jq '.pageDetails[] | select(.classification == "SCANNED")'

# Expected (at least one SCANNED page):
# {
#   "pageNum": 4,
#   "classification": "SCANNED",
#   "extractionMethod": "easyocr",
#   "confidence": 0.81
# }
```

### Step 6: Verify scanned table reconstructed

```bash
curl http://localhost:8080/api/v1/rfp/result/$JOB_ID \
  | jq '[.tables[] | select(.confidence.method == "ocr_llm_reconstruct")]'

# Expected: at least one table with ocr_llm_reconstruct method
# {
#   "tableId": "...",
#   "pageStart": 7,
#   "confidence": {"score": 0.65, "method": "ocr_llm_reconstruct"},
#   ...
# }
```

### Step 7: Verify mixed document with column detection

```bash
curl -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@testdata/ground-truth/mixed-two-column.pdf" \
  -F "procRef=MIXED-2025-001" \
  | jq .

JOB_ID="<new-job-id>"
curl http://localhost:8080/api/v1/rfp/result/$JOB_ID \
  | jq '.entities.general.client_name'

# Expected: entity extracted correctly despite two-column layout
# {"value": "Department of ICT, GoB", "confidence": 0.91}
```

---

## 6) Exit Criteria (NON-NEGOTIABLE)

| #     | Criterion                                                                                                       | Measure                                                                                  |
|-------|-----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| EC-01 | Python sidecar passes all pytest tests                                                                          | `pytest rfp-python-sidecar/tests/ -v` → 0 failures                                           |
| EC-02 | `GET /health` on sidecar returns `{"status": "ok"}` within 2 seconds after container start                      | Manual curl or Docker healthcheck                                                        |
| EC-03 | `ScannedPageExtractor` extracts text from 3 fixture scanned-page PDFs with `confidence >= 0.5`                  | Manual check against `testdata/fixtures/scanned-*.pdf`                                   |
| EC-04 | `ColumnDetector` correctly identifies 2-column layout on 4 of 5 known 2-column test documents                   | Manual verification: correct reading order (left column before right)                    |
| EC-05 | `ScannedTableReconstructor` successfully reconstructs table from OCR text for 2 of 3 fixture scanned-table docs | Compare grid to fixture expectation; ≥ 80% cell value match                              |
| EC-06 | `OcrSidecarClient` retries exactly once (2 total attempts) on first-call failure                                | `OcrSidecarClientTest.shouldRetryOnce` passes                                            |
| EC-07 | `MixedPageExtractor` does not crash when OCR sidecar is unavailable (text-only fallback)                        | `MixedPageExtractorTest.shouldFallBackToTextOnlyWhenOcrUnavailable` passes               |
| EC-08 | `ExtractTextNode` routes SCANNED pages to `ScannedPageExtractor`, not to `PdfDocumentLoader`                    | `ExtractTextNodeTest.shouldUseScannedExtractorForScannedPages` passes                    |
| EC-09 | All Java unit tests pass                                                                                        | `mvn test -pl rfp-service` → 0 failures                                                  |
| EC-10 | `ResultPage.tsx` Pages tab renders per-page classification with correct badge color                             | Browser visual check; code review confirms Tailwind class names                          |
| EC-11 | `prompts/scanned-table-reconstruction-v1.md` has valid YAML frontmatter                                         | `grep -A5 "^---" prompts/scanned-table-reconstruction-v1.md` shows all 5 required fields |
| EC-12 | No class exceeds 250 lines; no method exceeds 20 lines                                                          | Manual audit + checkstyle                                                                |

---

## 7) Notes: Assumptions / Open Questions

**Assumption:** easyOCR model files (for English + Bengali) are downloaded during the Docker image build (not at
runtime). Add `RUN python -c "import easyocr; easyocr.Reader(['en','bn'], gpu=False)"` to `Dockerfile` to pre-download
model weights. Without this, the first request will time out waiting for model download.

**Assumption:** `PDFRenderer.renderImageWithDPI(pageNum, dpi, ImageType.RGB)` is available in PDFBox 3.x under
`org.apache.pdfbox.rendering.PDFRenderer`. The `ImageType` import is `org.apache.pdfbox.rendering.ImageType`. Verify
against PDFBox 3.x API.

**Assumption:** `state.pageTexts` is a `Map<Integer, String>` field on `ExtractionState` added in Sprint 4. If it does
not exist, add it to `ExtractionState` in `rfp-core`. Similarly `state.pageConfidences` is a `Map<Integer, Double>` —
add if not present.

**Assumption:** `PdfDocumentLoader.loadPageText(int pageNum)` returns the plain text string for a page. If this method
does not yet exist on `PdfDocumentLoader`, add it (PDFBox `PDFTextStripper` scoped to a single page using
`setStartPage`/`setEndPage`).

**Assumption:** The `LlmAdapter.extractStructured(String prompt, String modelId)` method accepts a model ID override. If
it currently only uses the default model, add an overloaded method `extractStructured(String prompt, String modelId)`
that passes the model ID to OpenRouter.

**Decision:** `ColumnDetector` supports 1- and 2-column layouts only in Sprint 6. If 3+ column gaps are detected, log
`WARN` and continue with best-effort 2-column handling.

**Decision:** No OCR page-cache is implemented in Sprint 6. Retry/cost optimization is deferred to Sprint 7 checkpoint
persistence work.

**Non-Goal:** Bangla text segmentation / word segmentation. EasyOCR handles Bangla as a sequence of characters;
downstream entity extractors receive the raw Bangla text. Dedicated Bangla NLP (morphological analysis) is deferred
post-Sprint 12.
