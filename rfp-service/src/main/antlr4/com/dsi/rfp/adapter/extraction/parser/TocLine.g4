grammar TocLine;

tocLine
    : indentation title leader pageNumber EOF
    ;

indentation
    : SPACE*
    ;

title
    : titlePart (SPACE titlePart)*
    ;

leader
    : dottedLeader
    | spacedLeader
    ;

dottedLeader
    : SPACE* DOT DOT DOT DOT* SPACE*
    ;

spacedLeader
    : SPACE SPACE SPACE SPACE SPACE*
    ;

pageNumber
    : NUMBER
    ;

DOT
    : '.'
    ;

SPACE
    : ' '
    ;

NUMBER
    : [0-9]+
    ;

WORD
    : ~[. 0-9\r\n]+
    ;

titlePart
    : WORD
    | NUMBER
    ;

NEWLINE
    : [\r\n]+ -> skip
    ;
