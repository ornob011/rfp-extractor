package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.ClarificationQuestion;
import com.dsi.rfp.domain.model.RfpDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClarificationQuestionsDocxWriter {

    private final Configuration freemarkerConfig;
    private final ArtifactGenerationConfig config;
    private final ClarificationQuestionsModelFactory modelFactory;
    private final ObjectMapper objectMapper;

    public byte[] write(
        List<ClarificationQuestion> questions,
        RfpDocument document
    ) {
        try (XWPFDocument docx = new XWPFDocument()) {
            renderDocument(
                docx,
                renderTemplate(
                    modelFactory.create(
                        questions,
                        document
                    )
                )
            );
            return toBytes(docx);
        } catch (IOException | TemplateException exception) {
            throw new SystemIoException(
                "Failed to generate clarification questions DOCX",
                exception
            );
        }
    }

    private void renderDocument(
        XWPFDocument document,
        ClarificationDocxTemplate.Document content
    ) {
        addTitleParagraph(
            document,
            content.title()
        );
        content.metadata().forEach(line -> addMetadataParagraph(
            document,
            line
        ));
        content.questions().forEach(line -> addQuestionParagraphs(
            document,
            line
        ));
        addClosingParagraph(
            document,
            content.closingInstruction()
        );
    }

    private ClarificationDocxTemplate.Document renderTemplate(
        ClarificationQuestionsTemplateModel.DocumentView view
    ) throws IOException, TemplateException {
        Template template = freemarkerConfig.getTemplate(
            config.clarificationQuestionsTemplateName()
        );
        StringWriter writer = new StringWriter();
        template.process(
            view,
            writer
        );

        return objectMapper.readValue(
            writer.toString(),
            ClarificationDocxTemplate.Document.class
        );
    }

    private void addTitleParagraph(
        XWPFDocument document,
        String title
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);

        XWPFRun run = paragraph.createRun();
        run.setText(title);
        run.setFontSize(11);
        run.setBold(true);
    }

    private void addMetadataParagraph(
        XWPFDocument document,
        ClarificationDocxTemplate.MetadataLine line
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);

        XWPFRun labelRun = paragraph.createRun();
        labelRun.setText(String.format("%s: ", line.label()));
        labelRun.setFontSize(11);
        labelRun.setBold(true);

        XWPFRun valueRun = paragraph.createRun();
        valueRun.setText(line.value());
        valueRun.setFontSize(11);
    }

    private void addQuestionParagraphs(
        XWPFDocument document,
        ClarificationDocxTemplate.QuestionLine line
    ) {
        XWPFParagraph questionParagraph = document.createParagraph();
        questionParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);

        XWPFRun labelRun = questionParagraph.createRun();
        labelRun.setText(String.format("%s: ", line.label()));
        labelRun.setFontSize(11);
        labelRun.setBold(true);

        XWPFRun textRun = questionParagraph.createRun();
        textRun.setText(line.text());
        textRun.setFontSize(11);

        XWPFParagraph sourceParagraph = document.createParagraph();
        sourceParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);

        XWPFRun sourceRun = sourceParagraph.createRun();
        sourceRun.setText(String.format(
            "%s: %s",
            line.sourceLabel(),
            line.sourceText()
        ));
        sourceRun.setFontSize(11);
        sourceRun.setItalic(true);
    }

    private void addClosingParagraph(
        XWPFDocument document,
        String closingInstruction
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);

        XWPFRun run = paragraph.createRun();
        run.setText(closingInstruction);
        run.setFontSize(11);
        run.setItalic(true);
    }

    private byte[] toBytes(XWPFDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.write(output);
        log.debug("DOCX written: {} bytes", output.size());
        return output.toByteArray();
    }
}
