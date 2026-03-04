package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.TextBlock;
import lombok.Getter;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.util.ArrayList;
import java.util.List;

@Getter
class BoundingBoxTextStripper extends PDFTextStripper {

    private final List<TextBlock> textBlocks = new ArrayList<>();
    private final int targetPage;

    BoundingBoxTextStripper(int targetPage) {
        this.targetPage = targetPage;
    }

    @Override
    protected void writeString(
        String text,
        List<TextPosition> textPositions
    ) {
        if (textPositions.isEmpty()) {
            return;
        }

        TextPosition first = textPositions.getFirst();
        TextPosition last = textPositions.getLast();

        float x = first.getXDirAdj();
        float y = first.getYDirAdj();
        float width = last.getXDirAdj() + last.getWidthDirAdj() - x;
        float height = first.getHeightDir();

        textBlocks.add(
            TextBlock.builder()
                     .x(x)
                     .y(y)
                     .width(width)
                     .height(height)
                     .text(text)
                     .fontSize(first.getFontSizeInPt())
                     .fontName(first.getFont().getName())
                     .build()
        );
    }
}
