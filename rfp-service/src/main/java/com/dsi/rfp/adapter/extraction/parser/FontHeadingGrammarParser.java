package com.dsi.rfp.adapter.extraction.parser;

import org.antlr.v4.runtime.*;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;

@Component
public class FontHeadingGrammarParser {

    public OptionalInt parseLevel(String token) {
        ErrorFlag lexerErrors = new ErrorFlag();
        FontHeadingTokenLexer lexer = new FontHeadingTokenLexer(
            CharStreams.fromString(token)
        );
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerErrors);

        ErrorFlag parserErrors = new ErrorFlag();
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        FontHeadingTokenParser parser = new FontHeadingTokenParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(parserErrors);

        FontHeadingTokenParser.FontHeadingContext ctx = parser.fontHeading();

        if (lexerErrors.hasError()) {
            return OptionalInt.empty();
        }

        if (parserErrors.hasError()) {
            return OptionalInt.empty();
        }

        int level = Integer.parseInt(ctx.DIGITS().getText());
        return OptionalInt.of(level);
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
