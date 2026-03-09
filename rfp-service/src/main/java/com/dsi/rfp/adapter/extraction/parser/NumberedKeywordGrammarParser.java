package com.dsi.rfp.adapter.extraction.parser;

import org.antlr.v4.runtime.*;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class NumberedKeywordGrammarParser {

    public Optional<KeywordMatch> parse(
        String line,
        NumberedHeadingLexicon lexicon
    ) {
        String normalized = line.toLowerCase(Locale.ROOT);

        ErrorFlag lexerErrors = new ErrorFlag();

        NumberedKeywordLineLexer lexer = new NumberedKeywordLineLexer(
            CharStreams.fromString(normalized)
        );

        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerErrors);

        ErrorFlag parserErrors = new ErrorFlag();

        CommonTokenStream tokenStream = new CommonTokenStream(lexer);

        NumberedKeywordLineParser parser = new NumberedKeywordLineParser(tokenStream);

        parser.removeErrorListeners();
        parser.addErrorListener(parserErrors);

        parser.line();

        if (lexerErrors.hasError()) {
            return Optional.empty();
        }

        if (parserErrors.hasError()) {
            return Optional.empty();
        }

        String firstWord = tokenStream.get(0).getText();

        if (lexicon.keywordHeadings().contains(firstWord)) {
            return Optional.of(
                new KeywordMatch(
                    KeywordType.KEYWORD_HEADING,
                    1
                )
            );
        }

        if (lexicon.chapterKeywords().contains(firstWord)) {
            return Optional.of(
                new KeywordMatch(
                    KeywordType.CHAPTER_HEADING,
                    1
                )
            );
        }

        return Optional.empty();
    }

    public enum KeywordType {
        KEYWORD_HEADING,
        CHAPTER_HEADING
    }

    public record KeywordMatch(
        KeywordType type,
        int level
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
