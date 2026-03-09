package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.ClarificationQuestion;
import com.dsi.rfp.domain.model.RfpDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClarificationQuestionsDocxWriter {

    private final ClarificationQuestionsModelFactory modelFactory;
    private final ClarificationDocxBlockFactory blockFactory;

    public byte[] write(
        List<ClarificationQuestion> questions,
        RfpDocument document
    ) {
        try (XWPFDocument docx = new XWPFDocument()) {
            renderDocument(
                docx,
                blockFactory.create(
                    modelFactory.create(
                        questions,
                        document
                    )
                )
            );
            return toBytes(docx);
        } catch (IOException exception) {
            throw new SystemIoException(
                "Failed to generate clarification questions DOCX",
                exception
            );
        }
    }

    private void renderDocument(
        XWPFDocument document,
        List<ClarificationDocxBlock> blocks
    ) {
        blocks.forEach(block -> addLine(
            document,
            block
        ));
    }

    private void addLine(
        XWPFDocument document,
        ClarificationDocxBlock block
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(block.type().alignment());

        XWPFRun run = paragraph.createRun();
        run.setText(block.text());
        run.setFontSize(11);
        run.setBold(block.type().bold());
        run.setItalic(block.type().italic());
    }

    private byte[] toBytes(XWPFDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.write(output);
        log.debug("DOCX written: {} bytes", output.size());
        return output.toByteArray();
    }
}
