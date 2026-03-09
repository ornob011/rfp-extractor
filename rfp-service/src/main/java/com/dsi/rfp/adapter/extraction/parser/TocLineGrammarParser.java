package com.dsi.rfp.adapter.extraction.parser;

import org.antlr.v4.runtime.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TocLineGrammarParser {

    public Optional<TocLineResult> parse(String line) {
        ErrorFlag lexerErrors = new ErrorFlag();

        TocLineLexer lexer = new TocLineLexer(CharStreams.fromString(line));

        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerErrors);

        ErrorFlag parserErrors = new ErrorFlag();

        CommonTokenStream tokenStream = new CommonTokenStream(lexer);

        TocLineParser parser = new TocLineParser(tokenStream);

        parser.removeErrorListeners();
        parser.addErrorListener(parserErrors);

        TocLineParser.TocLineContext ctx = parser.tocLine();

        if (lexerErrors.hasError()) {
            return Optional.empty();
        }

        if (parserErrors.hasError()) {
            return Optional.empty();
        }

        int indent = ctx.indentation().getText().length();
        String title = ctx.title().getText();
        String pageNumber = ctx.pageNumber().getText();

        return Optional.of(
            new TocLineResult(
                indent,
                title,
                pageNumber
            )
        );
    }

    public record TocLineResult(
        int indent,
        String title,
        String pageNumber
    ) {
    }

    private static final class ErrorFlag extends BaseErrorListener {

        private boolean hasError;

        @Override
        public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e
        ) {
            hasError = true;
        }

        boolean hasError() {
            return hasError;
        }
    }
}
