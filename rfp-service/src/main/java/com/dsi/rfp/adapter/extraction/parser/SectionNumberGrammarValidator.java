package com.dsi.rfp.adapter.extraction.parser;

import org.antlr.v4.runtime.*;
import org.springframework.stereotype.Component;

@Component
public class SectionNumberGrammarValidator {

    public boolean isValid(String token) {
        ErrorFlag lexerErrors = new ErrorFlag();

        SectionNumberLexer lexer = new SectionNumberLexer(
            CharStreams.fromString(token)
        );

        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerErrors);

        ErrorFlag parserErrors = new ErrorFlag();

        CommonTokenStream tokenStream = new CommonTokenStream(lexer);

        SectionNumberParser parser = new SectionNumberParser(tokenStream);

        parser.removeErrorListeners();
        parser.addErrorListener(parserErrors);
        parser.sectionNumber();

        if (lexerErrors.hasError()) {
            return false;
        }

        return !parserErrors.hasError();
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
