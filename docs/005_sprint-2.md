# Sprint 2 — Document Ingestion & Page Classification

## 0) Sprint Intent

- Accept a PDF (or DOCX) upload via REST API, assign a UUID job ID, persist job state in PostgreSQL, and return the job ID to
  the caller — enabling async polling within 5 minutes of Sprint 1 being complete.
- Run per-page classification (DIGITAL / SCANNED / MIXED) on every uploaded document using PDFBox heuristics so that
  Sprint 3's section segmenter and Sprint 6's OCR router both have reliable per-page metadata available.
- Establish the foundational async job infrastructure (ThreadPoolTaskExecutor + PostgreSQL job state + file storage) that
  every subsequent sprint's pipeline step will reuse.
- Wire the React frontend upload form to the real API and add a polling job status page so that a live end-to-end demo
  is possible (upload → see QUEUED → see RUNNING → eventually COMPLETED).

**Non-goals:**

- Any text extraction or LLM calls (Sprint 3/4).
- Section segmentation or clause ID assignment (Sprint 3).
- OCR processing of SCANNED pages (Sprint 6).
- Authentication/JWT (Sprint 11).
- Rule packs or artifact generation (Sprints 8/10).
- DOCX-specific processing beyond format detection (treated same as PDF in Sprint 2 — queued but classified pages as
  DIGITAL by default until Sprint 3).

---

## 1) Entry Criteria

- Sprint 1 is fully complete: `mvn clean verify` passes, Docker Compose stack starts, `GET /api/v1/health` returns 200.
- `LlmAdapter`, `LlmResilienceConfig`, `LlmProviderConfig` are functional.
- PostgreSQL is running and reachable for job state and execution metadata.
- PostgreSQL is running and reachable (DataSource bean initializes — schema not yet created via Flyway/Liquibase, using
  `spring.jpa.hibernate.ddl-auto=update`).
- Java 21 virtual thread support confirmed in build.
- `app.storage.base-path` directory exists and is writable by the application process (Docker volume mounted for
  containerized runs).

---

## 2) Deliverables

- `POST /api/v1/rfp/submit` — accepts multipart PDF, validates it, stores to disk, saves job in PostgreSQL, returns
  `{jobId, status: "QUEUED"}` within 500ms.
- `GET /api/v1/rfp/status/{jobId}` — returns job state + progress percentage.
- `GET /api/v1/rfp/jobs` — returns list of all jobs (all users, no auth yet).
- `DocumentValidationService` — validates file size, MIME type, PDF integrity, XFA check.
- `PdfDocumentLoader` — PDFBox wrapper providing text and image metadata per page.
- `PageClassifier` — 3-class classification (DIGITAL / SCANNED / MIXED) with heuristic thresholds.
- `PageClassificationService` — orchestrates per-page classification, stores results in job state.
- `JpaJobStateRepository` — saves/loads `ExtractionJob` from PostgreSQL via JPA (durable, queryable); uses indexed and
  paged queries for list operations.
- `LocalFileStorageAdapter` — stores uploaded files to disk under `{basePath}/{jobId}/`.
- **`MimeTypePort`** (`rfp-core/domain/port/`) — port interface for MIME type detection; implemented by
  `MimeTypeDetector` adapter. Application services depend on this interface, never on the concrete adapter (hexagonal
  rule).
- **`DocumentUnsupportedTypeException`** (`rfp-core/domain/exception/`) — thrown when upload is not PDF or DOCX;
  `GlobalExceptionHandler` maps to HTTP 415.
- **`DocumentValidationException`** (`rfp-core/domain/exception/`) — base/fallback domain exception for other validation
  failures; mapped to HTTP 422.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`) — maps domain exceptions to `ProblemDetail` HTTP responses.
  Required by the exception policy from Sprint 1. Maps at minimum: `DocumentEncryptedException` → 422,
  `DocumentXfaException` → 422, `DocumentUnsupportedTypeException` → 415, `DocumentValidationException` → 422,
  `FileSizeLimitExceededException` → 413, `LlmUnavailableException` → 503,
  `LlmResponseParseException` → 502, `NoSuchFileException` → 404.
- **`JobStateAsyncExceptionHandler`** — implements `AsyncUncaughtExceptionHandler`; marks the job FAILED in PostgreSQL when
  an `@Async` void method throws. Replaces the placeholder log-only handler added in Sprint 1 `AsyncConfig`.
- Updated React frontend: `UploadPage` wires to real API and redirects to `/jobs` on success; `JobStatusPage` polls
  every 3s showing progress bar and status badge; `JobsListPage` lists all submitted jobs with status badge, filename,
  submitted timestamp, and "View" link to `/job/{uuid}`.
- At least 30 unit tests covering validation, classification, and job state.

---

## 3) Work Breakdown

### Epic 1 — Document Validation

#### Story 1.1 — DocumentValidationService

**Description:**
Create a service that runs five distinct validation checks before any processing begins. The checks are: (1) file size
within limit, (2) MIME type is PDF or DOCX, (3) PDFBox can open the file (catches encrypted and corrupt), (4) XFA form
detection (we cannot extract from XFA forms), (5) page count > 0. Each failure returns a distinct `ValidationResult`
with an error code so the API can surface a meaningful error to the caller.

**Acceptance Criteria:**

```gherkin
Given a PDF file larger than app.upload.max-size-mb
When validateDocument() is called
Then ValidationResult.valid() is false
And errorCode is FILE_TOO_LARGE

Given an encrypted PDF
When validateDocument() is called
Then DocumentEncryptedException is thrown
And GlobalExceptionHandler maps it to HTTP 422 with body {"errorCode": "ENCRYPTED", "message": "..."}

Given a PDF with an XFA AcroForm stream
When validateDocument() is called
Then ValidationResult.valid() is false
And errorCode is XFA_FORM

Given a valid multi-page PDF
When validateDocument() is called
Then ValidationResult.valid() is true
And errorCode is null

Given a file with .docx extension and correct OOXML MIME type
When validateDocument() is called
Then ValidationResult.valid() is true (DOCX passes — classified as all-DIGITAL pages)
```

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/ValidationResult.java`:

```java
package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationResult {
    private boolean valid;
    private String errorCode;    // null when valid
    private String errorMessage; // null when valid

    public static ValidationResult ok() {
        return ValidationResult.builder().valid(true).build();
    }

    public static ValidationResult fail(String errorCode, String errorMessage) {
        return ValidationResult.builder()
            .valid(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/exception/DocumentEncryptedException.java`:

```java
package com.dsi.rfp.domain.exception;

public class DocumentEncryptedException extends RuntimeException {
    public DocumentEncryptedException(String message) { super(message); }
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/exception/DocumentCorruptException.java`:

```java
package com.dsi.rfp.domain.exception;

public class DocumentCorruptException extends RuntimeException {
    public DocumentCorruptException(String message, Throwable cause) { super(message, cause); }
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/exception/DocumentXfaException.java`:

```java
package com.dsi.rfp.domain.exception;

public class DocumentXfaException extends RuntimeException {
    public DocumentXfaException(String message) { super(message); }
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/exception/FileSizeLimitExceededException.java`:

```java
package com.dsi.rfp.domain.exception;

public class FileSizeLimitExceededException extends RuntimeException {
    private final long actualBytes;
    private final long limitBytes;
    public FileSizeLimitExceededException(long actualBytes, long limitBytes) {
        super(String.format("File size %d bytes exceeds limit %d bytes", actualBytes, limitBytes));
        this.actualBytes = actualBytes;
        this.limitBytes = limitBytes;
    }
    public long getActualBytes() { return actualBytes; }
    public long getLimitBytes() { return limitBytes; }
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/DocumentValidationService.java`:

```java
package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentValidationService {

    @Value("${app.upload.max-size-mb:100}")
    private int maxSizeMb;

    private static final String PDF_MIME = "application/pdf";
    private static final String DOCX_MIME =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * Validates the given file for processing eligibility.
     *
     * @param filePath path to the stored file
     * @param fileSizeBytes actual file size
     * @param detectedMimeType MIME type detected from file content (not filename)
     * @return ValidationResult with valid=true or error details
     */
    public ValidationResult validateDocument(
        Path filePath,
        long fileSizeBytes,
        String detectedMimeType) throws IOException {

        ValidationResult sizeCheck = checkFileSize(fileSizeBytes);
        if (!sizeCheck.isValid()) return sizeCheck;

        ValidationResult mimeCheck = checkMimeType(detectedMimeType);
        if (!mimeCheck.isValid()) return mimeCheck;

        if (PDF_MIME.equals(detectedMimeType)) {
            return validatePdf(filePath);
        }
        return ValidationResult.ok();
    }

    private ValidationResult checkFileSize(long fileSizeBytes) {
        long limitBytes = (long) maxSizeMb * 1024 * 1024;
        if (fileSizeBytes > limitBytes) {
            return ValidationResult.fail("FILE_TOO_LARGE",
                String.format("File size %.1f MB exceeds limit %d MB",
                    fileSizeBytes / (1024.0 * 1024.0), maxSizeMb));
        }
        return ValidationResult.ok();
    }

    private ValidationResult checkMimeType(String mimeType) {
        if (!PDF_MIME.equals(mimeType) && !DOCX_MIME.equals(mimeType)) {
            return ValidationResult.fail("UNSUPPORTED_TYPE",
                "Only PDF and DOCX files are supported. Detected: " + mimeType);
        }
        return ValidationResult.ok();
    }

    private ValidationResult validatePdf(Path filePath) throws IOException {
        // PDFBox 3.x throws InvalidPasswordException (extends IOException) for encrypted PDFs.
        // Per the exception policy: catch the SPECIFIC exception and rethrow as the domain
        // exception DocumentEncryptedException — do NOT use catch (Exception e).
        // DocumentEncryptedException propagates to GlobalExceptionHandler (Sprint 2 Epic 5)
        // which maps it to HTTP 422 with errorCode="ENCRYPTED".
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            if (doc.getNumberOfPages() == 0) {
                return ValidationResult.fail("EMPTY_DOCUMENT", "PDF has zero pages");
            }
            return checkForXfa(doc);
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            throw new DocumentEncryptedException(
                "PDF is password-protected and cannot be processed. " +
                    "Please provide an unlocked copy.");
        }
    }

    private ValidationResult checkForXfa(PDDocument doc) {
        PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
        if (Objects.nonNull(acroForm) && Objects.nonNull(acroForm.getXFA())) {
            return ValidationResult.fail("XFA_FORM",
                "PDF contains XFA form which cannot be processed. " +
                    "Please export as a standard PDF.");
        }
        return ValidationResult.ok();
    }
}
```

**MIME type detection note:** Use Apache Tika to detect MIME type from file content (not filename) before calling
`validateDocument()`. Add a `MimeTypeDetector.java` helper in the same package:

```java
package com.dsi.rfp.adapter.extraction;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class MimeTypeDetector implements MimeTypePort {
    private final Tika tika = new Tika();

    @Override
    public String detect(Path filePath) throws IOException {
        return tika.detect(filePath.toFile());
    }
}
```

Add Apache Tika to `rfp-service/pom.xml`:

```xml

<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
```

Add `tika-core` version to parent POM `<dependencyManagement>`.

**Dependencies:** Sprint 1 complete.

**Test Plan:**
Class: `DocumentValidationServiceTest`
Mocks: None needed — use temp files.

```java
class DocumentValidationServiceTest {

    private DocumentValidationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentValidationService();
        // Set maxSizeMb via reflection or use @SpringBootTest slice — prefer reflection here
        ReflectionTestUtils.setField(service, "maxSizeMb", 10);
    }

    @Test
    void shouldFailWhenFileSizeExceedsLimit() {
        long tooLarge = 11L * 1024 * 1024;
        ValidationResult result = service.validateDocument(Path.of("/tmp/test.pdf"),
            tooLarge, "application/pdf");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void shouldFailWhenMimeTypeIsNotPdfOrDocx() {
        ValidationResult result = service.validateDocument(Path.of("/tmp/test.txt"),
            1000L, "text/plain");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("UNSUPPORTED_TYPE");
    }

    @Test
    void shouldFailWhenPdfIsEncrypted() throws Exception {
        Path encryptedPdf = createEncryptedPdfFixture();
        ValidationResult result = service.validateDocument(encryptedPdf,
            encryptedPdf.toFile().length(), "application/pdf");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("ENCRYPTED");
    }

    @Test
    void shouldPassWhenValidPdfIsProvided() throws Exception {
        Path validPdf = createValidPdfFixture();
        ValidationResult result = service.validateDocument(validPdf,
            validPdf.toFile().length(), "application/pdf");
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldFailWhenPdfHasZeroPages() throws Exception {
        // Note: PDFBox may not allow creating 0-page PDFs easily.
        // Use a pre-created test fixture from testdata/fixtures/zero-pages.pdf
    }
}
```

Use `testdata/fixtures/` for PDF test files — create them with PDFBox in a `@BeforeAll` or store them as resource files.

**Observability:**

- Log INFO: `"Document validation: file={} size={}MB result={}"`
- Log WARN: `"Document validation failed: errorCode={} errorMessage={}"`

**Estimation:** 5 SP

---

### Epic 2 — PDF Document Loading

#### Story 2.1 — PdfDocumentLoader

**Description:**
Create `PdfDocumentLoader.java` in `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/`. This class wraps a
PDFBox `PDDocument` and provides per-page text, bounding box information for text blocks, embedded image metadata, and
font metadata. It does NOT keep the `PDDocument` open between calls — each method opens and closes via
try-with-resources on the stored file path.

**Acceptance Criteria:**

```gherkin
Given a valid PDF with 5 pages of digital text
When loadPageText(3) is called
Then the text for page 3 is returned as a non-blank String

Given a PDF with embedded images
When loadPageImages(1) is called
Then EmbeddedImageInfo objects are returned with width, height, and area

Given a PDF with mixed fonts
When loadFontMetadata() is called
Then a Map is returned mapping font names to FontInfo objects with averageFontSize

Given getPageCount() is called
Then the integer page count is returned without opening the full document

Given a PDF file that is deleted
When any method is called
Then IOException is thrown (not swallowed)
```

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/TextBlock.java`:

```java

@Data
@Builder
public class TextBlock {
    private float x;
    private float y;
    private float width;
    private float height;
    private String text;
    private float fontSize;
    private String fontName;
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/EmbeddedImageInfo.java`:

```java
@Data @Builder
public class EmbeddedImageInfo {
    private int pageNumber;
    private float x;
    private float y;
    private float width;
    private float height;
    // area() is computed: width * height
    public float area() { return width * height; }
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/FontInfo.java`:

```java

@Data
@Builder
public class FontInfo {
    private String fontName;
    private float minFontSize;
    private float maxFontSize;
    private float averageFontSize;
    private long occurrenceCount;
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/PdfDocumentLoader.java`:

```java
package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.EmbeddedImageInfo;
import com.dsi.rfp.domain.model.FontInfo;
import com.dsi.rfp.domain.model.TextBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PdfDocumentLoader {

    /**
     * Extracts all text from the document as a single string.
     *
     * @param pdfPath path to the PDF file
     * @return full document text
     * @throws IOException if the file cannot be read
     */
    public String loadFullText(Path pdfPath) throws IOException { ...}

    /**
     * Extracts text from a specific 0-indexed page.
     *
     * @param pdfPath path to the PDF file
     * @param pageIndex 0-based page index
     * @return text content of the page (may be empty for scanned pages)
     * @throws IOException if the file cannot be read
     */
    public String loadPageText(Path pdfPath, int pageIndex) throws IOException { ...}

    /**
     * Extracts TextBlock bounding boxes for all text on a specific page.
     *
     * @param pdfPath path to the PDF file
     * @param pageIndex 0-based page index
     * @return list of TextBlock objects with position and font data
     * @throws IOException if the file cannot be read
     */
    public List<TextBlock> loadPageBoundingBoxes(Path pdfPath, int pageIndex) throws IOException { ...}

    /**
     * Extracts embedded image bounding boxes for a specific page.
     *
     * @param pdfPath path to the PDF file
     * @param pageIndex 0-based page index
     * @return list of EmbeddedImageInfo with position and size
     * @throws IOException if the file cannot be read
     */
    public List<EmbeddedImageInfo> loadPageImages(Path pdfPath, int pageIndex) throws IOException { ...}

    /**
     * Extracts font metadata aggregated across the entire document.
     *
     * @param pdfPath path to the PDF file
     * @return map of fontName to FontInfo (min/max/avg font size, occurrence count)
     * @throws IOException if the file cannot be read
     */
    public Map<String, FontInfo> loadFontMetadata(Path pdfPath) throws IOException { ...}

    /**
     * Returns the page count without loading the full document content.
     *
     * @param pdfPath path to the PDF file
     * @return number of pages
     * @throws IOException if the file cannot be read
     */
    public int getPageCount(Path pdfPath) throws IOException { ...}
}
```

**Implementation details for each method:**

`loadFullText()`:

```java
public String loadFullText(Path pdfPath) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
        PDFTextStripper stripper = new PDFTextStripper();
        return stripper.getText(doc);
    }
}
```

`loadPageText()`:

```java
public String loadPageText(Path pdfPath, int pageIndex) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1);  // PDFBox is 1-indexed
        stripper.setEndPage(pageIndex + 1);
        return stripper.getText(doc);
    }
}
```

`loadPageBoundingBoxes()` — use a custom `PDFTextStripper` subclass that overrides `writeString()` and captures
`TextPosition` objects:

```java
// Inner class or separate class: BoundingBoxStripper extends PDFTextStripper
// Override: protected void writeString(String string, List<TextPosition> textPositions)
//   For each TextPosition tp:
//     TextBlock block = TextBlock.builder()
//       .x(tp.getX()).y(tp.getY())
//       .width(tp.getWidth()).height(tp.getHeight())
//       .text(tp.getUnicode()).fontSize(tp.getFontSizeInPt())
//       .fontName(tp.getFont().getName())
//       .build();
//     results.add(block);
```

`loadPageImages()` — traverse `PDPage.getResources().getXObjectNames()`, check `isStream()`, cast to `PDImageXObject`,
get width/height. For position, use `PDPage.getContentStream()` matrix interpretation — simplified: just return
width/height and assume full-page placement if CTM is unavailable. Concrete implementation:

```java
public List<EmbeddedImageInfo> loadPageImages(Path pdfPath, int pageIndex) throws IOException {
    List<EmbeddedImageInfo> images = new ArrayList<>();
    try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
        PDPage page = doc.getPage(pageIndex);
        PDRectangle box = page.getMediaBox();
        // Use PDFRenderer to count image objects, or iterate XObjects:
        for (COSName name : page.getResources().getXObjectNames()) {
            PDXObject xobj = page.getResources().getXObject(name);
            if (xobj instanceof PDImageXObject img) {
                images.add(EmbeddedImageInfo.builder()
                    .pageNumber(pageIndex)
                    .x(0).y(0)
                    .width(img.getWidth()).height(img.getHeight())
                    .build());
            }
        }
    }
    return images;
}
```

Note: Precise image position requires parsing the content stream's graphics state machine. For Sprint 2 (classification
only), image dimensions from `PDImageXObject` are sufficient to compute coverage ratio.

`loadFontMetadata()`:

- Iterate all pages, call `BoundingBoxStripper` (from loadPageBoundingBoxes inner logic), collect all `TextPosition`
  font names and sizes.
- Group by fontName, compute min/max/avg/count.
- Return `Map<String, FontInfo>`.

`getPageCount()`:

```java
public int getPageCount(Path pdfPath) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
        return doc.getNumberOfPages();
    }
}
```

**Dependencies:** Story 1.1 (file stored to disk so Path exists).

**Test Plan:**
Class: `PdfDocumentLoaderTest`
Use test PDF fixtures in `rfp-service/src/test/resources/fixtures/`:

- `digital-text.pdf` — 3 pages, pure text (create with PDFBox in a static factory method or include as resource).
- `empty-page.pdf` — 1 page, no text or images.

```java
class PdfDocumentLoaderTest {
    private PdfDocumentLoader loader;
    private Path digitalPdf;

    @BeforeAll
    static void createFixtures() throws Exception {
        // Use PDFBox to create minimal test PDFs programmatically
    }

    @BeforeEach
    void setUp() {
        loader = new PdfDocumentLoader();
    }

    @Test
    void shouldReturnNonBlankTextWhenDigitalPdfLoaded() throws IOException { ... }

    @Test
    void shouldReturnPageCountMatchingFixture() throws IOException { ... }

    @Test
    void shouldReturnEmptyTextBlocksForEmptyPage() throws IOException { ... }

    @Test
    void shouldReturnPositiveWidthAndHeightForEmbeddedImages() throws IOException { ... }

    @Test
    void shouldThrowIOExceptionWhenFileDoesNotExist() {
        assertThatThrownBy(() -> loader.loadFullText(Path.of("/nonexistent.pdf")))
            .isInstanceOf(IOException.class);
    }
}
```

**Observability:**

- Log DEBUG: `"PdfDocumentLoader: loading page {} of {} from {}"` (not INFO — too verbose).
- Micrometer timer: `pdf.load.duration` with tag `operation=fullText|pageText|images`.

**Estimation:** 8 SP

---

### Epic 3 — Page Classification

#### Story 3.1 — PageClassifier with 3-Class Algorithm

**Description:**
Create `PageClassifier.java` in `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/`. This class takes the text
block list and image list from `PdfDocumentLoader` and applies the 3-class classification algorithm using character
density and raster coverage thresholds.

**Acceptance Criteria:**

```gherkin
Given a page with charDensity=0.005 and rasterCoverage=0.1
When classify() is called
Then classification is DIGITAL

Given a page with charDensity=0.00005 (essentially no text) and rasterCoverage=0.95
When classify() is called
Then classification is SCANNED

Given a page with charDensity=0.002 and rasterCoverage=0.65
When classify() is called
Then classification is MIXED

Given a completely blank page (no text, no images)
When classify() is called
Then classification is SCANNED (treated as a blank scanned page, not digital)
```

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/PageClassification.java`:

```java
package com.dsi.rfp.domain.model;

public enum PageClassification {
    DIGITAL,
    SCANNED,
    MIXED
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/PageClassificationResult.java`:

```java

@Data
@Builder
public class PageClassificationResult {
    private int pageNumber;             // 0-based
    private PageClassification classification;
    private double charDensity;
    private double rasterCoverage;
    private int charCount;
    private float pageWidth;
    private float pageHeight;
    private double pageAreaPixels;
    private double totalImageArea;
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/extraction/PageClassifier.java`:

```java
package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.EmbeddedImageInfo;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageClassificationResult;
import com.dsi.rfp.domain.model.TextBlock;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PageClassifier {

    // Thresholds — package-private for testing
    static final double DIGITAL_CHAR_DENSITY_THRESHOLD = 0.001;
    static final double SCANNED_CHAR_DENSITY_THRESHOLD = 0.0001;
    static final double SCANNED_RASTER_COVERAGE_THRESHOLD = 0.80;
    static final double DIGITAL_MAX_RASTER_COVERAGE = 0.60;

    /**
     * Classifies a single page as DIGITAL, SCANNED, or MIXED.
     *
     * @param pageNumber 0-based page index
     * @param textBlocks list of text bounding boxes from PdfDocumentLoader
     * @param images list of embedded image info from PdfDocumentLoader
     * @param pageWidth page width in PDF user units (typically points)
     * @param pageHeight page height in PDF user units (typically points)
     * @return PageClassificationResult with classification and raw metrics
     */
    public PageClassificationResult classify(
            int pageNumber,
            List<TextBlock> textBlocks,
            List<EmbeddedImageInfo> images,
            float pageWidth,
            float pageHeight) {

        double pageArea = pageWidth * pageHeight;
        int totalChars = computeTotalCharCount(textBlocks);
        double charDensity = pageArea > 0 ? totalChars / pageArea : 0.0;

        double totalImageArea = images.stream()
            .mapToDouble(EmbeddedImageInfo::area)
            .sum();
        double rasterCoverage = pageArea > 0 ? totalImageArea / pageArea : 0.0;

        PageClassification classification = applyThresholds(charDensity, rasterCoverage);

        return PageClassificationResult.builder()
            .pageNumber(pageNumber)
            .classification(classification)
            .charDensity(charDensity)
            .rasterCoverage(rasterCoverage)
            .charCount(totalChars)
            .pageWidth(pageWidth)
            .pageHeight(pageHeight)
            .pageAreaPixels(pageArea)
            .totalImageArea(totalImageArea)
            .build();
    }

    private PageClassification applyThresholds(double charDensity, double rasterCoverage) {
        if (charDensity > DIGITAL_CHAR_DENSITY_THRESHOLD
                && rasterCoverage < DIGITAL_MAX_RASTER_COVERAGE) {
            return PageClassification.DIGITAL;
        }
        if (charDensity < SCANNED_CHAR_DENSITY_THRESHOLD
                || rasterCoverage > SCANNED_RASTER_COVERAGE_THRESHOLD) {
            return PageClassification.SCANNED;
        }
        return PageClassification.MIXED;
    }

    private int computeTotalCharCount(List<TextBlock> textBlocks) {
        return textBlocks.stream()
            .mapToInt(tb -> Objects.nonNull(tb.getText()) ? tb.getText().length() : 0)
            .sum();
    }
}
```

**Test Plan:**
Class: `PageClassifierTest` — all tests use no mocks (pure logic).

```java
class PageClassifierTest {
    private PageClassifier classifier;

    @BeforeEach
    void setUp() { classifier = new PageClassifier(); }

    @Test
    void shouldReturnDigitalWhenHighCharDensityAndLowRasterCoverage() {
        List<TextBlock> blocks = createTextBlocks(500, "A");  // 500 chars
        List<EmbeddedImageInfo> images = List.of();
        // page 100x100 = 10000 area; 500/10000 = 0.05 >> 0.001
        PageClassificationResult result = classifier.classify(0, blocks, images, 100f, 100f);
        assertThat(result.getClassification()).isEqualTo(PageClassification.DIGITAL);
    }

    @Test
    void shouldReturnScannedWhenLowCharDensityAndHighRasterCoverage() {
        List<TextBlock> blocks = List.of();
        List<EmbeddedImageInfo> images = List.of(
            EmbeddedImageInfo.builder().width(900).height(900).build());
        // page 1000x1000=1000000; image 900*900=810000; coverage=0.81 > 0.80
        PageClassificationResult result = classifier.classify(0, blocks, images, 1000f, 1000f);
        assertThat(result.getClassification()).isEqualTo(PageClassification.SCANNED);
    }

    @Test
    void shouldReturnMixedWhenBothTextAndImagesPresent() {
        List<TextBlock> blocks = createTextBlocks(30, "word ");  // sparse text
        List<EmbeddedImageInfo> images = List.of(
            EmbeddedImageInfo.builder().width(500).height(500).build());
        // page 1000x1000; charDensity=0.00015; rasterCoverage=0.25 → MIXED
        PageClassificationResult result = classifier.classify(0, blocks, images, 1000f, 1000f);
        assertThat(result.getClassification()).isEqualTo(PageClassification.MIXED);
    }

    @Test
    void shouldReturnScannedWhenPageIsCompletelyBlank() {
        PageClassificationResult result = classifier.classify(
            0, List.of(), List.of(), 595f, 842f);
        assertThat(result.getClassification()).isEqualTo(PageClassification.SCANNED);
    }

    @Test
    void shouldNotReturnNullClassification() {
        PageClassificationResult result = classifier.classify(
            0, List.of(), List.of(), 595f, 842f);
        assertThat(result.getClassification()).isNotNull();
    }
}
```

**Observability:**

- Log DEBUG per page: `"Page {} classified as {} (charDensity={}, rasterCoverage={})"`
- Micrometer counter: `page.classification` with tag `result=DIGITAL|SCANNED|MIXED`

**Estimation:** 5 SP

---

#### Story 3.2 — PageClassificationService (Orchestrator)

**Description:**
Create `PageClassificationService.java` in `rfp-service/src/main/java/com/dsi/rfp/application/service/`. Given a PDF
path, this service iterates all pages, calls `PdfDocumentLoader` for text blocks + images + page dimensions, calls
`PageClassifier`, and returns the full list of results. Updates job progress as it processes each page.

**Acceptance Criteria:**

```gherkin
Given a 10-page PDF
When classifyAllPages() is called
Then 10 PageClassificationResult objects are returned (one per page)
And each result has a non-null classification

Given a 10-page PDF with progress tracking enabled
When classifyAllPages() runs
Then job progress is updated every page (10% increments for a 10-page doc)
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/application/service/PageClassificationService.java`:

```java
package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.extraction.PageClassifier;
import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.domain.model.PageClassificationResult;
import com.dsi.rfp.domain.port.JobStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageClassificationService {

    private final PdfDocumentLoader pdfDocumentLoader;
    private final PageClassifier pageClassifier;
    private final JobStatePort jobStatePort;

    /**
     * Classifies all pages of a PDF. Updates job progress incrementally.
     *
     * @param pdfPath path to the PDF
     * @param jobId UUID of the job for progress tracking
     * @return ordered list of PageClassificationResult (index = page number)
     * @throws IOException if PDF cannot be read
     */
    public List<PageClassificationResult> classifyAllPages(Path pdfPath, UUID jobId)
            throws IOException {

        int pageCount = pdfDocumentLoader.getPageCount(pdfPath);
        List<PageClassificationResult> results = new ArrayList<>(pageCount);

        for (int i = 0; i < pageCount; i++) {
            PageClassificationResult result = classifySinglePage(pdfPath, i);
            results.add(result);
            int progress = computeProgress(i + 1, pageCount);
            jobStatePort.updateProgress(jobId, progress);
            log.debug("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=DEBUG Page {}/{} classified as {}", i + 1, pageCount,
                result.getClassification());
        }

        logClassificationSummary(jobId, results);
        return results;
    }

    private PageClassificationResult classifySinglePage(Path pdfPath, int pageIndex)
            throws IOException {
        var textBlocks = pdfDocumentLoader.loadPageBoundingBoxes(pdfPath, pageIndex);
        var images = pdfDocumentLoader.loadPageImages(pdfPath, pageIndex);
        // Get page dimensions from PDFBox (requires a helper method in PdfDocumentLoader)
        float[] dims = pdfDocumentLoader.getPageDimensions(pdfPath, pageIndex); // [width, height]
        return pageClassifier.classify(pageIndex, textBlocks, images, dims[0], dims[1]);
    }

    private int computeProgress(int completed, int total) {
        return (int) (((double) completed / total) * 100);
    }

    private void logClassificationSummary(UUID jobId, List<PageClassificationResult> results) {
        long digital = results.stream().filter(r -> r.getClassification() == PageClassification.DIGITAL).count();
        long scanned = results.stream().filter(r -> r.getClassification() == PageClassification.SCANNED).count();
        long mixed = results.stream().filter(r -> r.getClassification() == PageClassification.MIXED).count();
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO Classification summary: jobId={} total={} DIGITAL={} SCANNED={} MIXED={}",
            jobId, results.size(), digital, scanned, mixed);
    }
}
```

Add `getPageDimensions()` to `PdfDocumentLoader`:

```java
public float[] getPageDimensions(Path pdfPath, int pageIndex) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
        PDPage page = doc.getPage(pageIndex);
        PDRectangle box = page.getMediaBox();
        return new float[]{ box.getWidth(), box.getHeight() };
    }
}
```

**Test Plan:**
Class: `PageClassificationServiceTest`
Mocks: `PdfDocumentLoader` (mock), `PageClassifier` (mock), `JobStatePort` (mock).

```java
@ExtendWith(MockitoExtension.class)
class PageClassificationServiceTest {
    @Mock PdfDocumentLoader pdfDocumentLoader;
    @Mock PageClassifier pageClassifier;
    @Mock JobStatePort jobStatePort;
    @InjectMocks PageClassificationService service;

    @Test
    void shouldReturnOneResultPerPage() throws IOException {
        UUID jobId = UUID.randomUUID();
        Path fakePath = Path.of("/fake/doc.pdf");
        when(pdfDocumentLoader.getPageCount(fakePath)).thenReturn(3);
        when(pdfDocumentLoader.loadPageBoundingBoxes(any(), anyInt())).thenReturn(List.of());
        when(pdfDocumentLoader.loadPageImages(any(), anyInt())).thenReturn(List.of());
        when(pdfDocumentLoader.getPageDimensions(any(), anyInt())).thenReturn(new float[]{595, 842});
        when(pageClassifier.classify(anyInt(), any(), any(), anyFloat(), anyFloat()))
            .thenReturn(PageClassificationResult.builder()
                .pageNumber(0).classification(PageClassification.DIGITAL)
                .charDensity(0.01).rasterCoverage(0.1).build());

        List<PageClassificationResult> results = service.classifyAllPages(fakePath, jobId);

        assertThat(results).hasSize(3);
    }

    @Test
    void shouldUpdateProgressForEachPage() throws IOException {
        UUID jobId = UUID.randomUUID();
        when(pdfDocumentLoader.getPageCount(any())).thenReturn(2);
        when(pdfDocumentLoader.loadPageBoundingBoxes(any(), anyInt())).thenReturn(List.of());
        when(pdfDocumentLoader.loadPageImages(any(), anyInt())).thenReturn(List.of());
        when(pdfDocumentLoader.getPageDimensions(any(), anyInt())).thenReturn(new float[]{595, 842});
        when(pageClassifier.classify(anyInt(), any(), any(), anyFloat(), anyFloat()))
            .thenReturn(PageClassificationResult.builder().classification(PageClassification.DIGITAL).build());

        service.classifyAllPages(Path.of("/fake/doc.pdf"), jobId);

        verify(jobStatePort).updateProgress(jobId, 50);
        verify(jobStatePort).updateProgress(jobId, 100);
    }
}
```

**Estimation:** 3 SP

---

### Epic 4 — Async Job Infrastructure

#### Story 4.1 — Domain Model: ExtractionJob and JobStatus

**Description:**
Create the `ExtractionJob` domain model and `JobStatus` enum in `rfp-core`. These are pure Java data classes with no
framework annotations. The `ExtractionJob` carries all metadata about a document extraction job.

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/JobStatus.java`:

```java
package com.dsi.rfp.domain.model;

public enum JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    PARTIAL   // completed with some sections/entities missing
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/model/ExtractionJob.java`:

```java
package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ExtractionJob {
    private UUID jobId;
    private JobStatus status;
    private Instant submittedAt;
    private Instant completedAt;      // null until COMPLETED or FAILED
    private String documentId;        // filename as stored
    private int progress;             // 0-100
    private String errorMessage;      // null unless FAILED or PARTIAL
    private String userId;            // null until Sprint 11 auth is added
    private int pageCount;            // populated after validation
    private String originalFilename;
}
```

**Estimation:** 1 SP

---

#### Story 4.2 — JobStatePort and JpaJobStateRepository

**Description:**
Create the `JobStatePort` interface in `rfp-core` and implement `JpaJobStateRepository` in
`rfp-service/adapter/persistence/`. Analysis jobs are stored as JPA entities with primary key `jobId` and indexed
status fields. Persistence is durable (no TTL); entities are mapped to/from domain models via mapper classes.

**Acceptance Criteria:**

```gherkin
Given an ExtractionJob with status=QUEUED
When save() is called
Then a row is stored in `analysis_jobs` in PostgreSQL

Given a saved job
When findById(jobId) is called
Then the original ExtractionJob is returned with all fields intact via mapper conversion

Given a saved job with status=QUEUED
When updateStatus(jobId, RUNNING) is called
Then findById(jobId) returns the job with status=RUNNING

Given an unknown jobId
When findById() is called
Then Optional.empty() is returned
```

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/port/JobStatePort.java`:

```java
package com.dsi.rfp.domain.port;

import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.model.JobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobStatePort {
    void save(ExtractionJob job);
    Optional<ExtractionJob> findById(UUID jobId);
    void updateStatus(UUID jobId, JobStatus status);
    void updateProgress(UUID jobId, int progress);
    List<ExtractionJob> findAll();
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/persistence/JpaJobStateRepository.java`:

```java
package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.model.JobStatus;
import com.dsi.rfp.domain.port.JobStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JpaJobStateRepository implements JobStatePort {

    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisJobMapper analysisJobMapper;

    @Override
    public void save(ExtractionJob job) {
        analysisJobRepository.save(analysisJobMapper.toEntity(job));
        log.debug("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=DEBUG Saved job {} to PostgreSQL via JPA", job.getJobId());
    }

    @Override
    public Optional<ExtractionJob> findById(UUID jobId) {
        return analysisJobRepository.findById(jobId).map(analysisJobMapper::toDomain);
    }

    @Override
    public void updateStatus(UUID jobId, JobStatus status) {
        findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
                job.setCompletedAt(Instant.now());
            }
            save(job);
        });
    }

    @Override
    public void updateProgress(UUID jobId, int progress) {
        findById(jobId).ifPresent(job -> {
            job.setProgress(Math.min(100, Math.max(0, progress)));
            save(job);
        });
    }

    @Override
    public List<ExtractionJob> findAll() {
        return analysisJobRepository.findAll().stream()
            .map(analysisJobMapper::toDomain)
            .toList();
    }
}
```

Add `JpaPersistenceConfig.java` to `rfp-service/src/main/java/com/dsi/rfp/config/`:

```java
package com.dsi.rfp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JpaPersistenceConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

**Test Plan:**
Class: `JpaJobStateRepositoryTest`
Mocks: `AnalysisJobRepository` (mock), `AnalysisJobMapper` (mock).
Note: job state persistence is fully JPA-based; no Redis template should be involved.

```java
@ExtendWith(MockitoExtension.class)
class JpaJobStateRepositoryTest {
    @Mock AnalysisJobRepository analysisJobRepository;
    @Mock AnalysisJobMapper analysisJobMapper;

    private JpaJobStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaJobStateRepository(analysisJobRepository, analysisJobMapper);
    }

    @Test
    void shouldSaveJobWhenRepositoryCalled() {
        ExtractionJob job = buildSampleJob();
        AnalysisJobEntity entity = buildSampleEntity();
        when(analysisJobMapper.toEntity(job)).thenReturn(entity);
        repository.save(job);
        verify(analysisJobMapper).toEntity(job);
        verify(analysisJobRepository).save(entity);
    }

    @Test
    void shouldReturnEmptyWhenJobNotFoundInDatabase() {
        when(analysisJobRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        Optional<ExtractionJob> result = repository.findById(UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnJobWhenFoundInDatabase() {
        ExtractionJob job = buildSampleJob();
        AnalysisJobEntity entity = buildSampleEntity();
        when(analysisJobRepository.findById(job.getJobId())).thenReturn(Optional.of(entity));
        when(analysisJobMapper.toDomain(entity)).thenReturn(job);
        Optional<ExtractionJob> result = repository.findById(job.getJobId());
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void shouldUpdateStatusToRunning() {
        ExtractionJob job = buildSampleJob();
        AnalysisJobEntity entity = buildSampleEntity();
        when(analysisJobRepository.findById(job.getJobId())).thenReturn(Optional.of(entity));
        when(analysisJobMapper.toDomain(entity)).thenReturn(job);
        when(analysisJobMapper.toEntity(any(ExtractionJob.class))).thenReturn(entity);
        repository.updateStatus(job.getJobId(), JobStatus.RUNNING);
        ArgumentCaptor<ExtractionJob> jobCaptor = ArgumentCaptor.forClass(ExtractionJob.class);
        verify(analysisJobMapper, atLeastOnce()).toEntity(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(JobStatus.RUNNING);
        verify(analysisJobRepository, atLeastOnce()).save(any(AnalysisJobEntity.class));
    }
}
```

**Estimation:** 5 SP

---

#### Story 4.3 — FileStoragePort and LocalFileStorageAdapter

**Description:**
Create `FileStoragePort` interface in `rfp-core` and `LocalFileStorageAdapter` in `rfp-service/adapter/persistence/`.
Files are stored at `{app.storage.base-path}/{jobId}/{filename}`. Directories are created atomically if absent.

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/port/FileStoragePort.java`:

```java
package com.dsi.rfp.domain.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public interface FileStoragePort {
    /**
     * Stores file content for a job.
     * @return the path where the file was stored
     */
    Path store(UUID jobId, byte[] content, String filename) throws IOException;

    /**
     * Retrieves file content for a job.
     * @throws java.io.FileNotFoundException if file does not exist
     */
    byte[] retrieve(UUID jobId, String filename) throws IOException;

    /**
     * Returns the directory path for a job (may not exist yet).
     */
    Path jobDirectory(UUID jobId);
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/persistence/LocalFileStorageAdapter.java`:

```java
package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.domain.port.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path basePath;

    public LocalFileStorageAdapter(@Value("${app.storage.base-path:/tmp/rfp-storage}") String basePath) {
        this.basePath = Path.of(basePath);
    }

    @Override
    public Path store(UUID jobId, byte[] content, String filename) throws IOException {
        Path dir = jobDirectory(jobId);
        Files.createDirectories(dir);
        Path target = dir.resolve(sanitizeFilename(filename));
        Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO Stored file: jobId={} filename={} bytes={}", jobId, filename, content.length);
        return target;
    }

    @Override
    public byte[] retrieve(UUID jobId, String filename) throws IOException {
        Path target = jobDirectory(jobId).resolve(sanitizeFilename(filename));
        if (!Files.exists(target)) {
            throw new NoSuchFileException(target.toString(),
                null, "File not found for jobId=" + jobId);
        }
        return Files.readAllBytes(target);
    }

    @Override
    public Path jobDirectory(UUID jobId) {
        return basePath.resolve(jobId.toString());
    }

    private String sanitizeFilename(String filename) {
        // Strip path traversal characters
        return Path.of(filename).getFileName().toString()
            .replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
```

**Test Plan:**
Class: `LocalFileStorageAdapterTest` — use `@TempDir` JUnit 5 annotation for real filesystem tests.

```java
class LocalFileStorageAdapterTest {
    @TempDir Path tempDir;
    private LocalFileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalFileStorageAdapter(tempDir.toString());
    }

    @Test
    void shouldStoreFileAndReturnCorrectPath() throws IOException {
        UUID jobId = UUID.randomUUID();
        byte[] content = "test content".getBytes();
        Path stored = adapter.store(jobId, content, "test.pdf");
        assertThat(stored).exists();
        assertThat(stored.getFileName().toString()).isEqualTo("test.pdf");
    }

    @Test
    void shouldRetrieveStoredFileContent() throws IOException {
        UUID jobId = UUID.randomUUID();
        byte[] content = "pdf bytes".getBytes();
        adapter.store(jobId, content, "doc.pdf");
        byte[] retrieved = adapter.retrieve(jobId, "doc.pdf");
        assertThat(retrieved).isEqualTo(content);
    }

    @Test
    void shouldThrowWhenFileNotFound() {
        assertThatThrownBy(() -> adapter.retrieve(UUID.randomUUID(), "missing.pdf"))
            .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void shouldSanitizeFilenameWithPathTraversal() throws IOException {
        UUID jobId = UUID.randomUUID();
        adapter.store(jobId, new byte[]{}, "../../../etc/passwd");
        // Should store as "passwd" or similar, not in /etc/
        assertThat(adapter.jobDirectory(jobId).resolve("passwd")).exists();
    }
}
```

**Estimation:** 3 SP

---

### Epic 5 — REST API for Submission and Status

#### Story 5.1 — RfpSubmissionService and RfpController

**Description:**
Create the submission service and REST controller. `POST /api/v1/rfp/submit` accepts a multipart file, runs validation,
stores the file, creates a job  in PostgreSQL (status=QUEUED), dispatches the extraction pipeline asynchronously, and returns
the job ID. The async pipeline in Sprint 2 only runs page classification (not full extraction). The full pipeline is
wired in Sprint 4.

**Acceptance Criteria:**

```gherkin
Given a valid PDF file under 100MB
When POST /api/v1/rfp/submit is called with the file
Then 202 Accepted is returned with body {"jobId": "<uuid>", "status": "QUEUED"}
And the job is persisted  in PostgreSQL

Given an encrypted PDF
When POST /api/v1/rfp/submit is called
Then 422 Unprocessable Entity is returned with body {"error": "ENCRYPTED", "message": "..."}

Given a file larger than the configured max size
When POST /api/v1/rfp/submit is called
Then 413 Payload Too Large is returned

Given job exists and classification has completed
When GET /api/v1/rfp/status/{jobId} is called
Then 200 OK with {"jobId": "...", "status": "COMPLETED", "progress": 100, ...}

Given unknown jobId
When GET /api/v1/rfp/status/{jobId} is called
Then 404 Not Found
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/api/RfpController.java`:

```java
package com.dsi.rfp.adapter.api;

import com.dsi.rfp.application.service.RfpJobService;
import com.dsi.rfp.application.service.RfpSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rfp")
@RequiredArgsConstructor
public class RfpController {

    private final RfpSubmissionService submissionService;
    private final RfpJobService jobService;

    // Controller extracts bytes from MultipartFile here — Spring web types never enter application layer.
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitResponse> submit(
            @RequestPart("file") MultipartFile file) throws IOException {
        UUID jobId = submissionService.submit(file.getBytes(), file.getOriginalFilename());
        SubmitResponse response = SubmitResponse.builder()
            .jobId(jobId.toString())
            .status("QUEUED")
            .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<JobStatusResponse> getStatus(@PathVariable UUID jobId) {
        return jobService.findById(jobId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobStatusResponse>> listJobs() {
        return ResponseEntity.ok(jobService.findAll());
    }
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/api/SubmitResponse.java`:

```java

@Data
@Builder
public class SubmitResponse {
    private String jobId;
    private String status;
    private String message;
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/api/JobStatusResponse.java`:

```java

@Data
@Builder
public class JobStatusResponse {
    private String jobId;
    private String status;
    private int progress;
    private String submittedAt;   // ISO 8601
    private String completedAt;   // ISO 8601, null if not done
    private String errorMessage;
    private String originalFilename;
    private int pageCount;
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/application/service/RfpSubmissionService.java`:

```java
package com.dsi.rfp.application.service;

// Hexagonal rule: application services ONLY import from domain (rfp-core) and other application services.
// FORBIDDEN imports: adapter.api.*, adapter.extraction.MimeTypeDetector, Spring web types
// (MultipartFile, ResponseStatusException, HttpStatus). Violations break the dependency rule.
//
// NOTE on DocumentValidationService: it lives in adapter.extraction because it uses PDFBox.
// To be fully hexagonal, introduce DocumentValidationPort in rfp-core/domain/port/ and have
// DocumentValidationService implement it — then import DocumentValidationPort here.
// This is deferred: the current import is a known borderline violation tracked in the backlog.
import com.dsi.rfp.adapter.extraction.DocumentValidationService;
import com.dsi.rfp.domain.exception.DocumentUnsupportedTypeException;
import com.dsi.rfp.domain.exception.DocumentValidationException;
import com.dsi.rfp.domain.exception.DocumentXfaException;
import com.dsi.rfp.domain.exception.FileSizeLimitExceededException;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.model.JobStatus;
import com.dsi.rfp.domain.model.ValidationResult;
import com.dsi.rfp.domain.port.FileStoragePort;
import com.dsi.rfp.domain.port.JobStatePort;
import com.dsi.rfp.domain.port.MimeTypePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RfpSubmissionService {

    // Architecture rule: application services depend on port interfaces, never on concrete adapters.
    // MimeTypeDetector (adapter) → inject via MimeTypePort (port interface in rfp-core). Correct.
    // DocumentValidationService (adapter.extraction) → ideally behind DocumentValidationPort.
    // Current import is a known borderline violation; deferred to a backlog refactor.
    private final DocumentValidationService validationService;
    private final MimeTypePort mimeTypePort;        // NOT MimeTypeDetector (adapter)
    private final FileStoragePort fileStorage;
    private final JobStatePort jobStatePort;
    private final ExtractionPipelineService pipelineService;

    /**
     * Validates, stores, and queues a document for extraction.
     *
     * Architecture rules applied here:
     * 1. Parameter is raw (byte[], String) — NOT MultipartFile (Spring web type belongs in adapter/api).
     *    The RfpController reads the bytes from MultipartFile BEFORE calling this service.
     * 2. Return type is UUID — NOT SubmitResponse (adapter DTO belongs in adapter/api).
     *    The RfpController maps the UUID to SubmitResponse.
     * 3. Throws domain exceptions (DocumentEncryptedException etc.) — NOT ResponseStatusException
     *    (Spring web type). GlobalExceptionHandler maps domain exceptions to HTTP responses.
     *
     * @param fileBytes  raw file content
     * @param filename   original filename (for storage and logging)
     * @return UUID of the created extraction job
     * @throws IOException if file storage fails
     */
    public UUID submit(byte[] fileBytes, String filename) throws IOException {
        UUID jobId = UUID.randomUUID();
        Path storedPath = fileStorage.store(jobId, fileBytes, filename);
        validateFile(storedPath, fileBytes.length);
        ExtractionJob job = createJob(jobId, filename);
        jobStatePort.save(job);
        pipelineService.runAsync(jobId, storedPath);
        log.info("event=job.submitted component=RfpSubmissionService status=SUCCESS jobId={} file={} sizeKB={} durationMs=NA errorCode=NA traceId={} spanId=NA",
            jobId, filename, fileBytes.length / 1024, MDC.get("traceId"));
        return jobId;
    }

    private void validateFile(Path path, long sizeBytes) throws IOException {
        String mime = mimeTypePort.detect(path);
        ValidationResult result = validationService.validateDocument(path, sizeBytes, mime);
        if (result.isValid()) return;
        // Translate ValidationResult failures into typed domain exceptions that propagate
        // to GlobalExceptionHandler — never throw ResponseStatusException from application layer.
        throw switch (result.getErrorCode()) {
            case "FILE_TOO_LARGE"   -> new FileSizeLimitExceededException(sizeBytes, /* limit */ 0);
            case "UNSUPPORTED_TYPE" -> new DocumentUnsupportedTypeException(result.getErrorMessage());
            case "XFA_FORM"         -> new DocumentXfaException(result.getErrorMessage());
            default                 -> new DocumentValidationException(result.getErrorCode(), result.getErrorMessage());
        };
    }

    private ExtractionJob createJob(UUID jobId, String filename) {
        return ExtractionJob.builder()
            .jobId(jobId)
            .status(JobStatus.QUEUED)
            .submittedAt(Instant.now())
            .originalFilename(filename)
            .progress(0)
            .build();
    }
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/port/MimeTypePort.java`:

```java
package com.dsi.rfp.domain.port;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Port interface for MIME type detection.
 * Application services depend on this interface; the concrete adapter (MimeTypeDetector)
 * is in rfp-service/adapter/ and injected by Spring at runtime.
 * This preserves the hexagonal architecture boundary.
 */
public interface MimeTypePort {
    /**
     * Detect the MIME type of the file at the given path.
     *
     * @param path the file to inspect
     * @return MIME type string, e.g. "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
     * @throws IOException if the file cannot be read
     */
    String detect(Path path) throws IOException;
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/exception/DocumentUnsupportedTypeException.java`:

```java
package com.dsi.rfp.domain.exception;

/**
 * Thrown when the uploaded document is not a supported type (PDF or DOCX).
 * GlobalExceptionHandler maps this to HTTP 415 Unsupported Media Type.
 */
public class DocumentUnsupportedTypeException extends RuntimeException {
    public DocumentUnsupportedTypeException(String message) {
        super(message);
    }
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/exception/DocumentValidationException.java`:

```java
package com.dsi.rfp.domain.exception;

/**
 * Fallback domain exception for validation failures not covered by a more specific type.
 * Carries an errorCode for structured error responses.
 * GlobalExceptionHandler maps this to HTTP 422 Unprocessable Entity.
 */
public class DocumentValidationException extends RuntimeException {
    private final String errorCode;

    public DocumentValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/application/service/RfpJobService.java`:

```java
package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.api.JobStatusResponse;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.port.JobStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RfpJobService {

    private final JobStatePort jobStatePort;

    public Optional<JobStatusResponse> findById(UUID jobId) {
        return jobStatePort.findById(jobId).map(this::toResponse);
    }

    public List<JobStatusResponse> findAll() {
        return jobStatePort.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private JobStatusResponse toResponse(ExtractionJob job) {
        return JobStatusResponse.builder()
            .jobId(job.getJobId().toString())
            .status(job.getStatus().name())
            .progress(job.getProgress())
            .submittedAt(Objects.nonNull(job.getSubmittedAt()) ? job.getSubmittedAt().toString() : null)
            .completedAt(Objects.nonNull(job.getCompletedAt()) ? job.getCompletedAt().toString() : null)
            .errorMessage(job.getErrorMessage())
            .originalFilename(job.getOriginalFilename())
            .pageCount(job.getPageCount())
            .build();
    }
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/application/service/ExtractionPipelineService.java`:

```java
package com.dsi.rfp.application.service;

import com.dsi.rfp.domain.model.JobStatus;
import com.dsi.rfp.domain.port.JobStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionPipelineService {

    private final JobStatePort jobStatePort;
    private final PageClassificationService pageClassificationService;

    /**
     * Runs the extraction pipeline asynchronously (Sprint 2: page classification only).
     * Sprint 4 replaces this with ExtractionOrchestrationService + LangGraph4J graph.
     *
     * Exception policy: do NOT use catch (Exception e) here. If this method throws,
     * the exception is routed to JobStateAsyncExceptionHandler (see Epic 5 below), which
     * marks the job FAILED in PostgreSQL. This is the Spring @Async contract for void methods:
     * unhandled exceptions propagate to AsyncUncaughtExceptionHandler, NOT to @RestControllerAdvice.
     *
     * DOCX handling: Sprint 2 does not classify DOCX pages. A PDF-only guard is applied
     * via the MimeTypePort interface (not a direct adapter class — see architecture rules).
     * DOCX files queued in Sprint 2 stay RUNNING until Sprint 3 adds DOCX text extraction.
     * The current Sprint 2 scope is intentionally PDF-only for page classification.
     */
    @Async("rfpTaskExecutor")
    public void runAsync(UUID jobId, Path documentPath) throws IOException {
        log.info("event=pipeline.start component=ExtractionPipelineService status=INFO jobId={} durationMs=NA errorCode=NA traceId=NA spanId=NA",
            jobId);
        jobStatePort.updateStatus(jobId, JobStatus.RUNNING);

        var classifications = pageClassificationService.classifyAllPages(documentPath, jobId);
        int pageCount = classifications.size();
        jobStatePort.findById(jobId).ifPresent(job -> {
            job.setPageCount(pageCount);
            jobStatePort.save(job);
        });
        jobStatePort.updateStatus(jobId, JobStatus.COMPLETED);
        log.info("event=pipeline.complete component=ExtractionPipelineService status=SUCCESS jobId={} pages={} durationMs=NA errorCode=NA traceId=NA spanId=NA",
            jobId, pageCount);
    }
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/api/GlobalExceptionHandler.java`:

```java
package com.dsi.rfp.adapter.api;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
// NO ResponseStatusException import: per exception policy, services throw domain exceptions,
// NOT Spring web types. This handler maps domain exceptions to HTTP; it does NOT handle
// ResponseStatusException (which should never be thrown from application or domain layers).

// GlobalExceptionHandler maps domain exceptions to HTTP ProblemDetail responses.
// Per the exception policy: ALL services throw domain exceptions; NO service throws
// ResponseStatusException or Spring web types. This handler is the ONLY place that
// translates domain errors to HTTP status codes.
// Extended in Sprint 11 to add JWT/security exception handlers.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentEncryptedException.class)
    public ResponseEntity<ProblemDetail> handleEncrypted(DocumentEncryptedException ex) {
        log.warn("event=validation.fail component=GlobalExceptionHandler status=WARN jobId=NA durationMs=NA errorCode=ENCRYPTED traceId={} spanId=NA",
            MDC.get("traceId"));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setProperty("errorCode", "ENCRYPTED");
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(DocumentXfaException.class)
    public ResponseEntity<ProblemDetail> handleXfa(DocumentXfaException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setProperty("errorCode", "XFA_FORM");
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(DocumentUnsupportedTypeException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedType(DocumentUnsupportedTypeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        problem.setProperty("errorCode", "UNSUPPORTED_TYPE");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
    }

    @ExceptionHandler(DocumentValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(DocumentValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setProperty("errorCode", ex.getErrorCode());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(FileSizeLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleFileTooLarge(FileSizeLimitExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
        problem.setProperty("errorCode", "FILE_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
    }

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleLlmUnavailable(LlmUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, "LLM service is temporarily unavailable");
        problem.setProperty("errorCode", "LLM_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "30")
            .body(problem);
    }

    @ExceptionHandler(LlmResponseParseException.class)
    public ResponseEntity<ProblemDetail> handleLlmParseFail(LlmResponseParseException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY, "LLM returned an unparseable response");
        problem.setProperty("errorCode", "LLM_PARSE_FAIL");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchFileException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, "Resource not found");
        problem.setProperty("errorCode", "NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/config/JobStateAsyncExceptionHandler.java`:

```java
package com.dsi.rfp.config;

import com.dsi.rfp.domain.model.JobStatus;
import com.dsi.rfp.domain.port.JobStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Handles exceptions thrown by @Async void methods.
 *
 * Spring's @RestControllerAdvice does NOT intercept exceptions from @Async void methods.
 * Those exceptions are routed to this handler via AsyncConfigurer.getAsyncUncaughtExceptionHandler().
 *
 * This handler inspects the method parameters for a UUID jobId argument and marks the
 * corresponding job FAILED  in PostgreSQL so the polling client sees a terminal state rather
 * than the job staying RUNNING indefinitely.
 *
 * Replaces the log-only placeholder registered in Sprint 1 AsyncConfig.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobStateAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private final JobStatePort jobStatePort;

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error(
            "event=async.uncaught component=JobStateAsyncExceptionHandler status=FAIL " +
            "method={} error={} traceId=NA spanId=NA jobId=NA durationMs=NA errorCode=NA",
            method.getName(), ex.getMessage(), ex);

        // Best-effort: if the first parameter is a UUID we treat it as the jobId.
        // ExtractionPipelineService.runAsync(UUID jobId, Path documentPath) follows this convention.
        if (params.length > 0 && params[0] instanceof UUID jobId) {
            try {
                jobStatePort.updateStatus(jobId, JobStatus.FAILED);
                log.info(
                    "event=job.failed component=JobStateAsyncExceptionHandler status=OK " +
                    "method={} jobId={} traceId=NA spanId=NA durationMs=NA errorCode=NA",
                    method.getName(), jobId);
            } catch (Exception updateEx) {
                // If the database status update fails we log and give up — we must not throw
                // from an AsyncUncaughtExceptionHandler as Spring will ignore it.
                log.error(
                    "event=async.handler.fail component=JobStateAsyncExceptionHandler " +
                    "status=FAIL method={} jobId={} error={} traceId=NA spanId=NA durationMs=NA errorCode=NA",
                    method.getName(), jobId, updateEx.getMessage(), updateEx);
            }
        }
    }
}
```

Update `AsyncConfig` to register `JobStateAsyncExceptionHandler` (replaces the log-only placeholder from Sprint 1):

```java
// In AsyncConfig (rfp-service/src/main/java/com/dsi/rfp/config/AsyncConfig.java):
// Add field injection and override:

@RequiredArgsConstructor
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private final JobStateAsyncExceptionHandler jobStateAsyncExceptionHandler;

    // ... existing executor beans unchanged ...

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // JobStateAsyncExceptionHandler marks the job FAILED in PostgreSQL and logs the error.
        // This replaces the log-only placeholder registered in Sprint 1.
        return jobStateAsyncExceptionHandler;
    }
}
```

**Configure multipart file size in application.properties:**

```properties
spring.servlet.multipart.max-file-size=110MB
spring.servlet.multipart.max-request-size=110MB
```

**Test Plan:**
Class: `RfpSubmissionServiceTest`

```java
@ExtendWith(MockitoExtension.class)
class RfpSubmissionServiceTest {
    @Mock DocumentValidationService validationService;
    @Mock MimeTypePort mimeTypePort;           // NOT MimeTypeDetector (adapter class)
    @Mock FileStoragePort fileStorage;
    @Mock JobStatePort jobStatePort;
    @Mock ExtractionPipelineService pipelineService;
    @InjectMocks RfpSubmissionService service;

    @Test
    void shouldReturnJobIdWhenValidPdfSubmitted() throws Exception { ... }
    @Test
    void shouldThrowDocumentEncryptedExceptionForEncryptedPdf() throws Exception { ... }
    @Test
    void shouldThrowDocumentUnsupportedTypeExceptionForInvalidMime() throws Exception { ... }
    @Test
    void shouldSaveJobWithQueuedStatusBeforeDispatch() throws Exception { ... }
    @Test
    void shouldDispatchPipelineAfterSuccessfulSave() throws Exception { ... }
}
```

Class: `RfpJobServiceTest`:

```java

@Test
void shouldReturnEmptyWhenJobNotFound()

@Test
void shouldMapExtractionJobToJobStatusResponse()

@Test
void shouldReturnAllJobsFromPort()
```

**Observability:**

- Log INFO: `"Job submitted: jobId={} file={} size={}KB"`
- Log INFO: `"Pipeline starting: jobId={}"`
- Log INFO: `"Pipeline completed (page classification only): jobId={} pages={}"`
- Log ERROR: `"Pipeline failed: jobId={} error={}"`
- Micrometer counter: `rfp.submissions` with tag `result=success|validation_fail`

**Estimation:** 8 SP

---

### Epic 6 — Frontend Update

#### Story 6.1 — Wire UploadPage, Add Polling JobStatusPage, and Add JobsListPage

**Description:**
Update `UploadPage.tsx` to call the real API and redirect to `/jobs` on success. Create `JobsListPage.tsx` as the
home/landing page, calling `GET /api/v1/rfp/jobs` and listing all submitted jobs sorted by `submittedAt` descending
with a status badge, filename, submitted timestamp, and "View" link. Create `JobStatusPage.tsx` with React Query
polling every 3 seconds, showing a progress bar, status badge with color coding, and per-page classification
placeholder.

**Acceptance Criteria:**

```gherkin
Given the upload page
When a PDF is selected and submitted
Then POST /api/v1/rfp/submit is called
And on success, the user is navigated to /jobs (the jobs list page)

Given the jobs list page
When the page renders
Then GET /api/v1/rfp/jobs is called
And all jobs are listed sorted by submittedAt descending
And each row shows: filename, status badge, submitted timestamp, and a "View" link to /job/{uuid}

Given the job status page for a RUNNING job
When the page renders
Then a progress bar showing the current progress % is visible
And status badge shows "RUNNING" in yellow

Given the job status page for a COMPLETED job
When the page renders
Then status badge shows "COMPLETED" in green
And a "View Results" link to /result/{jobId} is visible
And polling stops

Given the job status page for a FAILED job
When the page renders
Then status badge shows "FAILED" in red
And errorMessage is displayed below the progress bar
```

**Interfaces/Contracts:**

File: `rfp-frontend/src/pages/JobStatusPage.tsx`:

```tsx
import {useParams, Link} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {getJobStatus} from '../api/rfpClient';
import {JobStatusResponse} from '../types/rfp';

const STATUS_COLORS: Record<string, string> = {
    QUEUED: 'bg-gray-100 text-gray-700',
    RUNNING: 'bg-yellow-100 text-yellow-700',
    COMPLETED: 'bg-green-100 text-green-700',
    FAILED: 'bg-red-100 text-red-700',
    PARTIAL: 'bg-orange-100 text-orange-700',
};

export function JobStatusPage() {
    const {jobId} = useParams<{ jobId: string }>();

    const {data, error} = useQuery<JobStatusResponse>({
        queryKey: ['jobStatus', jobId],
        queryFn: () => getJobStatus(jobId!),
        refetchInterval: (data) =>
            data?.status === 'COMPLETED' || data?.status === 'FAILED' ? false : 3000,
        enabled: !!jobId,
    });

    if (error) return <div className="p-8 text-red-600">Failed to load job status.</div>;
    if (!data) return <div className="p-8 text-gray-500">Loading...</div>;

    const colorClass = STATUS_COLORS[data.status] ?? 'bg-gray-100 text-gray-700';

    return (
        <div className="max-w-2xl mx-auto p-8">
            <h1 className="text-2xl font-bold mb-2 text-gray-800">Extraction Job</h1>
            <p className="text-sm text-gray-500 mb-6 font-mono">{jobId}</p>

            <div className={`inline-block px-3 py-1 rounded-full text-sm font-medium mb-4 ${colorClass}`}>
                {data.status}
            </div>

            <div className="mb-6">
                <div className="flex justify-between text-sm text-gray-600 mb-1">
                    <span>Progress</span>
                    <span>{data.progress}%</span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-3">
                    <div
                        className="bg-blue-500 h-3 rounded-full transition-all duration-500"
                        style={{width: `${data.progress}%`}}
                    />
                </div>
            </div>

            {data.errorMessage && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-4">
                    <p className="text-red-700 text-sm">{data.errorMessage}</p>
                </div>
            )}

            {data.status === 'COMPLETED' && (
                <Link
                    to={`/result/${jobId}`}
                    className="inline-block bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700"
                >
                    View Results →
                </Link>
            )}

            <div className="mt-6 text-sm text-gray-500">
                <p>Submitted: {data.submittedAt ? new Date(data.submittedAt).toLocaleString() : '—'}</p>
                {data.completedAt && (
                    <p>Completed: {new Date(data.completedAt).toLocaleString()}</p>
                )}
                {data.pageCount > 0 && <p>Pages: {data.pageCount}</p>}
            </div>
        </div>
    );
}
```

Update `rfp-frontend/src/main.tsx` to wrap App with `QueryClientProvider`:

```tsx
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';

const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById('root')!).render(
    <QueryClientProvider client={queryClient}>
        <App/>
    </QueryClientProvider>
);
```

File: `rfp-frontend/src/pages/JobsListPage.tsx`:

```tsx
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { listJobs } from '../api/rfpClient';
import { JobStatusResponse } from '../types/rfp';

const STATUS_COLORS: Record<string, string> = {
    QUEUED: 'bg-gray-100 text-gray-700',
    RUNNING: 'bg-yellow-100 text-yellow-700',
    COMPLETED: 'bg-green-100 text-green-700',
    FAILED: 'bg-red-100 text-red-700',
    PARTIAL: 'bg-orange-100 text-orange-700',
};

export function JobsListPage() {
    const { data, error } = useQuery<JobStatusResponse[]>({
        queryKey: ['jobs'],
        queryFn: listJobs,
    });

    if (error) return <div className="p-8 text-red-600">Failed to load jobs.</div>;
    if (!data) return <div className="p-8 text-gray-500">Loading...</div>;

    const sorted = [...data].sort(
        (a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime()
    );

    return (
        <div className="max-w-4xl mx-auto p-8">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold text-gray-800">Submitted Jobs</h1>
                <Link
                    to="/upload"
                    className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 text-sm"
                >
                    + New Upload
                </Link>
            </div>

            {sorted.length === 0 && (
                <p className="text-gray-500">No jobs submitted yet.</p>
            )}

            <ul className="space-y-3">
                {sorted.map((job) => {
                    const colorClass = STATUS_COLORS[job.status] ?? 'bg-gray-100 text-gray-700';
                    return (
                        <li
                            key={job.jobId}
                            className="flex items-center justify-between border rounded-lg p-4 bg-white shadow-sm"
                        >
                            <div>
                                <p className="font-medium text-gray-800">{job.filename ?? job.jobId}</p>
                                <p className="text-xs text-gray-500 mt-0.5">
                                    {job.submittedAt ? new Date(job.submittedAt).toLocaleString() : '—'}
                                </p>
                            </div>
                            <div className="flex items-center gap-4">
                                <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${colorClass}`}>
                                    {job.status}
                                </span>
                                <Link
                                    to={`/job/${job.jobId}`}
                                    className="text-blue-600 hover:underline text-sm"
                                >
                                    View →
                                </Link>
                            </div>
                        </li>
                    );
                })}
            </ul>
        </div>
    );
}
```

Add `/jobs` route and update upload redirect in `rfp-frontend/src/App.tsx`:

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { UploadPage } from './pages/UploadPage';
import { JobsListPage } from './pages/JobsListPage';
import { JobStatusPage } from './pages/JobStatusPage';

export function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Navigate to="/jobs" replace />} />
                <Route path="/upload" element={<UploadPage />} />
                <Route path="/jobs" element={<JobsListPage />} />
                <Route path="/job/:jobId" element={<JobStatusPage />} />
            </Routes>
        </BrowserRouter>
    );
}
```

`UploadPage` navigates to `/jobs` (not `/job/{uuid}`) after a successful submit:

```tsx
// Inside UploadPage.tsx — on successful POST response:
navigate('/jobs');
```

Add `listJobs()` to `rfp-frontend/src/api/rfpClient.ts`:

```ts
export async function listJobs(): Promise<JobStatusResponse[]> {
    const res = await fetch('/api/v1/rfp/jobs');
    if (!res.ok) throw new Error('Failed to fetch jobs');
    return res.json();
}
```

**Dependencies:** Stories 5.1 (API endpoints exist).
**Estimation:** 5 SP

---

## 4) PR Plan

### PR 1: `feat/sprint2-domain` — Domain Models, Ports, Validation, Storage

**Contains:**

- `rfp-core` additions: `ValidationResult`, `ExtractionJob`, `JobStatus`, `PageClassification`,
  `PageClassificationResult`, `TextBlock`, `EmbeddedImageInfo`, `FontInfo`, custom exceptions (
  `DocumentEncryptedException`, `DocumentCorruptException`, `DocumentXfaException`,
  `FileSizeLimitExceededException`, `DocumentUnsupportedTypeException`, `DocumentValidationException`).
- `rfp-core` ports: `JobStatePort`, `FileStoragePort`, `MimeTypePort`.
- `DocumentValidationService`, `MimeTypeDetector` (implements `MimeTypePort`).
- `LocalFileStorageAdapter`.
- Unit tests for all above.

**Review Checklist:**

- [ ] All domain models in `rfp-core` have zero Spring/framework imports.
- [ ] `ValidationResult` uses static factory methods (`ok()`, `fail()`).
- [ ] `LocalFileStorageAdapter.sanitizeFilename()` rejects path traversal (`../`).
- [ ] `DocumentValidationService` uses try-with-resources on `PDDocument`.
- [ ] `FileSizeLimitExceededException` stores both actual and limit bytes.
- [ ] Custom exceptions extend `RuntimeException` (not checked exceptions).
- [ ] All unit test methods named `should{Behaviour}When{Condition}`.

---

### PR 2: `feat/sprint2-pipeline` — PDF Loader, Classifier, Async Pipeline, REST API

**Contains:**

- `PdfDocumentLoader` (full implementation).
- `PageClassifier`.
- `PageClassificationService`.
- `JpaJobStateRepository` + `JpaPersistenceConfig`.
- `AsyncConfig` (move from Sprint 1 if not already merged).
- `RfpSubmissionService`, `RfpJobService`, `ExtractionPipelineService`.
- `RfpController`, `GlobalExceptionHandler`, `JobStateAsyncExceptionHandler`, DTOs.
- `MimeTypePort` interface and `MimeTypeDetector` adapter implementing it.
- `DocumentUnsupportedTypeException`, `DocumentValidationException` domain exceptions.
- application.properties updates (multipart limits).
- Unit tests for all above.

**Review Checklist:**

- [ ] `PdfDocumentLoader` uses try-with-resources on every `PDDocument` open.
- [ ] `PageClassifier` threshold constants are package-private (not magic numbers inline).
- [ ] `JpaJobStateRepository.findAll()` uses indexed and paged JPA queries; no unbounded full-table scan in runtime
  paths.
- [ ] `RfpSubmissionService` dispatches async AFTER saving job to PostgreSQL (not before).
- [ ] `ExtractionPipelineService.runAsync()` has NO `catch (Exception e)` — `JobStateAsyncExceptionHandler` handles
  failures via `AsyncUncaughtExceptionHandler`.
- [ ] HTTP status codes: 202 Accepted for submit, 404 for unknown jobId, 413 for file too large, 422 for ENCRYPTED/XFA.
- [ ] `@Async("rfpTaskExecutor")` references the correct executor bean name.
- [ ] No `@Autowired` field injection anywhere.

---

### PR 3: `feat/sprint2-frontend` — UploadPage Wired + JobsListPage + JobStatusPage Polling

**Contains:**

- Updated `UploadPage.tsx` with real API call (redirects to `/jobs` on success).
- `JobsListPage.tsx` — home/landing page listing all submitted jobs.
- `JobStatusPage.tsx` with React Query polling.
- Updated `App.tsx` with `/jobs` route and root redirect.
- `QueryClientProvider` added to `main.tsx`.
- `rfpClient.ts` with `getJobStatus()` and `listJobs()` functions.
- Updated `JobStatusResponse` TypeScript type.

**Review Checklist:**

- [ ] `JobsListPage` shows all jobs from `GET /api/v1/rfp/jobs` sorted by `submittedAt` descending.
- [ ] Each row has: filename, status badge, submitted time, and "View" link to `/job/{uuid}`.
- [ ] `UploadPage` redirects to `/jobs` after successful submit (not directly to `/job/{uuid}`).
- [ ] Polling stops when status is COMPLETED or FAILED.
- [ ] Status badge colors match design spec (green=COMPLETED, yellow=RUNNING, red=FAILED).
- [ ] Error state displayed when `errorMessage` is non-null.
- [ ] "View Results" link only shown when COMPLETED.
- [ ] TypeScript strict mode — no `any` types.
- [ ] `npm run build` exits 0 with zero type errors.

---

## 5) Validation & Demo Script

### Step 1: Build All Modules

```bash
cd rfp-extractor
mvn clean verify
# Expected: BUILD SUCCESS, all tests pass
```

### Step 2: Start Docker Compose Stack

```bash
docker compose up --build -d
docker compose ps
# Expected: all services show "healthy"
```

### Step 3: Submit a Valid PDF

```bash
curl -s -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@/path/to/sample-rfp.pdf" | jq .
```

Expected output:

```json
{
  "jobId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "QUEUED",
  "message": null
}
```

### Step 4: Poll Job Status

```bash
JOB_ID="3fa85f64-5717-4562-b3fc-2c963f66afa6"
curl -s http://localhost:8080/api/v1/rfp/status/$JOB_ID | jq .
```

Expected (while running):

```json
{
  "jobId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "RUNNING",
  "progress": 45,
  "submittedAt": "2025-06-01T10:00:00Z",
  "completedAt": null,
  "errorMessage": null,
  "originalFilename": "sample-rfp.pdf",
  "pageCount": 0
}
```

Expected (after completion):

```json
{
  "jobId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "COMPLETED",
  "progress": 100,
  "submittedAt": "2025-06-01T10:00:00Z",
  "completedAt": "2025-06-01T10:00:12Z",
  "errorMessage": null,
  "originalFilename": "sample-rfp.pdf",
  "pageCount": 23
}
```

### Step 5: Submit an Encrypted PDF

```bash
curl -s -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@/path/to/encrypted.pdf" | jq .
```

Expected:

```json
{
  "error": "422 UNPROCESSABLE_ENTITY",
  "message": "PDF is password-protected and cannot be processed"
}
```

HTTP status: 422.

### Step 6: Submit Oversized File

```bash
# Create a 101MB fake file
dd if=/dev/zero of=/tmp/big.pdf bs=1M count=101
curl -s -X POST http://localhost:8080/api/v1/rfp/submit \
  -F "file=@/tmp/big.pdf" -w "\nHTTP_STATUS: %{http_code}\n" | jq .
```

Expected: HTTP 413

### Step 7: List All Jobs

```bash
curl -s http://localhost:8080/api/v1/rfp/jobs | jq 'length'
```

Expected: `1` (or more if multiple submissions were made).

### Step 8: Check Classification Logs

```bash
docker compose logs rfp-service | grep "Classification summary"
```

Expected log line:

```
INFO  Classification summary: jobId=3fa85f64... total=23 DIGITAL=18 SCANNED=3 MIXED=2
```

### Step 9: Frontend Demo

1. Open `http://localhost:3000` in browser.
2. Select a PDF file and click "Extract RFP Data".
3. Page navigates to `/job/{uuid}`.
4. Progress bar increments every 3s.
5. Status badge changes from RUNNING (yellow) to COMPLETED (green).
6. "View Results →" link appears.

### Performance Checks:

- `POST /api/v1/rfp/submit` response time < 500ms (async dispatch is non-blocking).
- 50-page PDF classification completes in < 30s.
- PostgreSQL lookup by jobId returns within target latency (verify with `psql query on analysis_jobs by id`).
- Memory usage of rfp-service < 512MB after processing a 50-page PDF.

---

## 6) Exit Criteria (NON-NEGOTIABLE)

- [ ] `mvn clean verify` exits with code 0. Minimum 30 unit tests across all new classes. Zero failures.
- [ ] `POST /api/v1/rfp/submit` returns HTTP 202 with `{"jobId":"...","status":"QUEUED"}` for a valid PDF — verified by
  curl.
- [ ] `GET /api/v1/rfp/status/{jobId}` returns HTTP 404 for an unknown UUID — verified by curl.
- [ ] An encrypted PDF submission returns HTTP 422 — verified by curl.
- [ ] A file >100MB returns HTTP 413 — verified by curl.
- [ ] After submitting a 10-page PDF, `status` transitions to COMPLETED and `progress` equals 100 — verified by polling.
- [ ] `PageClassifier` returns `DIGITAL` for a page with charDensity > 0.001 and rasterCoverage < 0.60 — verified by
  unit test.
- [ ] `PageClassifier` returns `SCANNED` for a blank page (no text, no images) — verified by unit test
  `shouldReturnScannedWhenPageIsCompletelyBlank`.
- [ ] `LocalFileStorageAdapter` rejects path traversal filenames (e.g., `../../../etc/passwd`) — verified by unit test
  `shouldSanitizeFilenameWithPathTraversal`.
- [ ] `JpaJobStateRepository` persists and retrieves jobs via `AnalysisJobRepository` with mapper conversion — verified by unit tests.
- [ ] `ExtractionPipelineService` sets job status to FAILED (not RUNNING) on any unhandled exception — verified by unit
  test.
- [ ] End-to-end browser journey verified: upload a PDF → redirected to `/jobs` list → job row visible → click "View"
  → `JobStatusPage` polls to COMPLETED.
- [ ] Frontend `JobStatusPage` stops polling when status is COMPLETED — verified by browser network tab showing no more
  requests after completion.
- [ ] No `@Autowired` field injection in any new class — verified by `grep -r "@Autowired" rfp-service/src/main`.
- [ ] Each new Java class is under 250 lines. Verified during code review.
- [ ] `rfp-core` module still has zero Spring framework dependencies after Sprint 2 additions — verified by
  `mvn dependency:analyze`.

---

## 7) Notes: Assumptions / Open Questions

**Assumption:** Apache Tika `tika-core` (version 2.9.2) is used for MIME type detection. This adds a ~5MB JAR but avoids
relying on the file extension, which can be spoofed. The detection reads the first bytes of the file content.

**Assumption:** `PDImageXObject` position within the page is approximated as full-page coverage for Sprint 2
classification purposes. Precise CTM (current transformation matrix) parsing is deferred to Sprint 6 (OCR integration).
The classification algorithm still works correctly because raster coverage is computed from image dimensions relative to
page dimensions, which is sufficient for the SCANNED threshold (0.80).

**Assumption:** PostgreSQL credentials are configured in the development/Docker environment (
`spring.datasource.url=jdbc:postgresql://postgres:5432/rfpdb`). If the production environment requires database authentication, add
`spring.datasource.password=${POSTGRES_PASSWORD}` in `application-docker.properties` and update Docker Compose.

**Assumption:** The `@Async("rfpTaskExecutor")` executor uses `ThreadPoolTaskExecutor` from `AsyncConfig` defined in
Sprint 1. If Sprint 1's `AsyncConfig` was not yet merged, it must be added in PR 2 of this sprint.

**Decision (FIX-P):** `JpaJobStateRepository.findAll()` MUST use `JPA paged query` with cursor from day one.
Unindexed full-table scans are unacceptable at scale; repository queries must use indexed lookups and pagination.
Do not use unbounded `findAll()` in runtime paths that can grow with tenant volume.

**Open Question:** Should the classification results be persisted somewhere (DB) for use in Sprint 3's section
segmenter? Decision: store `List<PageClassificationResult>` as part of the `ExtractionJob` state. In Sprint 2, add
`pageClassifications` field to `ExtractionJob` as a `Map<Integer, String>` (pageIndex → classificationName). The full
`PageClassificationResult` details are in-memory only; only the classification label is persisted.

**Assumption:** The multipart max file size in Spring Boot is set to 110MB (
`spring.servlet.multipart.max-file-size=110MB`) to accommodate the 100MB application limit plus headers overhead. The
application-level check (100MB) runs first and gives a better error message than Spring's default multipart size error.

**Assumption:** DOCX files pass validation (`ValidationResult.ok()`) in Sprint 2 and are stored, but the page
classification step skips them (returns empty list). The `ExtractionPipelineService` checks the MIME type before calling
`PageClassificationService` — if DOCX, set `pageCount=0` and mark `COMPLETED`. Full DOCX support via LangChain4J
`ApachePdfBoxDocumentParser` (or rather, `ApacheTikaDocumentParser`) is added in Sprint 3.
