package com.dsi.rfp.adapter.extraction.parser;

import org.antlr.v4.runtime.*;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;

@Component
public class BanglaHeadingGrammarParser {

    public OptionalInt parseLevel(String line) {
        ErrorFlag lexerErrors = new ErrorFlag();
        BanglaHeadingLineLexer lexer = new BanglaHeadingLineLexer(
            CharStreams.fromString(line)
        );
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerErrors);

        ErrorFlag parserErrors = new ErrorFlag();
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        BanglaHeadingLineParser parser = new BanglaHeadingLineParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(parserErrors);

        BanglaHeadingLineParser.LineContext ctx = parser.line();

        if (lexerErrors.hasError()) {
            return OptionalInt.empty();
        }

        if (parserErrors.hasError()) {
            return OptionalInt.empty();
        }

        if (ctx.chapterLine() != null) {
            return OptionalInt.of(1);
        }

        if (ctx.dharaLine() != null) {
            return OptionalInt.of(2);
        }

        if (ctx.anuchchhedLine() != null) {
            return OptionalInt.of(3);
        }

        return OptionalInt.empty();
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
