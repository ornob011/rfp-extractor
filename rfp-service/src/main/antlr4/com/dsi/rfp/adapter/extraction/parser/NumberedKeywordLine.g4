grammar NumberedKeywordLine;

line
    : headingLine
    ;

headingLine
    : WORD SPACE SPACE* DIGITS tail EOF
    | WORD SPACE SPACE* ROMAN tail EOF
    ;

tail
    : (WORD | ROMAN | DIGITS | SPACE | OTHER)*
    ;

ROMAN
    : [ivxlcdm]+
    ;

DIGITS
    : [0-9]+
    ;

WORD
    : [a-z]+
    ;

SPACE
    : ' '
    ;

OTHER
    : ~[\r\n]
    ;

WS
    : [\t\r\n]+ -> skip
    ;
