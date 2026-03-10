package com.dsi.rfp.adapter.ocr;

import com.dsi.rfp.domain.model.ReadingOrderMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrSidecarClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private OcrSidecarClient client;

    @BeforeEach
    void setUp() {
        client = new OcrSidecarClient(restClient);
    }

    @Test
    void shouldExtractPageSuccessfully() {
        byte[] imageBytes = "fake-png".getBytes(StandardCharsets.UTF_8);
        OcrResultDto expected = new OcrResultDto(
            "Hello world",
            List.of(),
            0.85,
            2,
            "easyocr"
        );

        stubPostChain();
        when(responseSpec.body(OcrResultDto.class)).thenReturn(expected);

        OcrResultDto result = client.extractPage(imageBytes, "eng+ben");

        assertThat(result.text()).isEqualTo("Hello world");
        assertThat(result.pageConfidence()).isEqualTo(0.85);
    }

    @Test
    void shouldEncodeImageAsBase64() {
        byte[] imageBytes = "test-image".getBytes(StandardCharsets.UTF_8);
        OcrResultDto expected = new OcrResultDto(
            "text",
            List.of(),
            0.9,
            1,
            "easyocr"
        );

        stubPostChain();
        when(responseSpec.body(OcrResultDto.class)).thenReturn(expected);

        client.extractPage(imageBytes, "eng");

        ArgumentCaptor<OcrPageRequest> captor =
            ArgumentCaptor.forClass(OcrPageRequest.class);
        verify(requestBodySpec).body(captor.capture());

        String expectedBase64 = Base64.getEncoder()
                                      .encodeToString(imageBytes);
        assertThat(captor.getValue().imageBase64())
            .isEqualTo(expectedBase64);
    }

    @Test
    void shouldExtractPageWithLayoutSuccessfully() {
        byte[] imageBytes = "fake-png".getBytes(StandardCharsets.UTF_8);

        OcrResultDto ocrResult = new OcrResultDto(
            "table data",
            List.of(),
            0.75,
            3,
            "easyocr"
        );
        LayoutDetectionDto layout = new LayoutDetectionDto(
            true,
            List.of(),
            List.of()
        );
        ReadingOrderDto readingOrder = new ReadingOrderDto(
            "ordered text",
            ReadingOrderMethod.PDFPLUMBER_LAYOUT
        );
        OcrPageWithLayoutResultDto expected =
            new OcrPageWithLayoutResultDto(
                ocrResult,
                layout,
                readingOrder,
                List.of(
                    new OcrScannedTableDto(
                        List.of("A", "B"),
                        List.of(List.of("1", "2")),
                        0.8,
                        "lattice"
                    )
                )
            );

        stubPostChain();
        when(responseSpec.body(OcrPageWithLayoutResultDto.class))
            .thenReturn(expected);

        OcrPageWithLayoutResultDto result =
            client.extractPageWithLayout(
                imageBytes,
                "/tmp/sample.pdf",
                1
            );

        assertThat(result.ocrResult().text()).isEqualTo("table data");
        assertThat(result.layout().hasTable()).isTrue();
        assertThat(result.readingOrder().orderedText()).isEqualTo("ordered text");
        assertThat(result.scannedTables()).hasSize(1);
    }

    @Test
    void shouldIncludeDocumentContextForOrderedExtraction() {
        byte[] imageBytes = "img".getBytes(StandardCharsets.UTF_8);
        OcrPageWithLayoutResultDto expected = new OcrPageWithLayoutResultDto(
            new OcrResultDto(
                "t",
                List.of(),
                0.9,
                1,
                "easyocr"
            ),
            new LayoutDetectionDto(
                false,
                List.of(),
                List.of()
            ),
            new ReadingOrderDto(
                "ordered",
                ReadingOrderMethod.OCR_TEXT_FLOW
            ),
            List.of()
        );

        stubPostChain();
        when(responseSpec.body(OcrPageWithLayoutResultDto.class)).thenReturn(expected);

        client.extractPageWithLayout(
            imageBytes,
            "/tmp/sample.pdf",
            3
        );

        ArgumentCaptor<OcrPageRequest> captor =
            ArgumentCaptor.forClass(OcrPageRequest.class);
        verify(requestBodySpec).body(captor.capture());

        assertThat(captor.getValue().documentPath()).isEqualTo("/tmp/sample.pdf");
        assertThat(captor.getValue().pageNumber()).isEqualTo(3);
    }

    @Test
    void shouldPostToCorrectUrl() {
        byte[] imageBytes = "img".getBytes(StandardCharsets.UTF_8);
        OcrResultDto expected = new OcrResultDto(
            "t",
            List.of(),
            0.9,
            1,
            "easyocr"
        );

        stubPostChain();
        when(responseSpec.body(OcrResultDto.class)).thenReturn(expected);

        client.extractPage(imageBytes, "eng");

        verify(requestBodyUriSpec).uri("/ocr/page");
    }

    private void stubPostChain() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class)))
            .thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON))
            .thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(OcrPageRequest.class)))
            .thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }
}
