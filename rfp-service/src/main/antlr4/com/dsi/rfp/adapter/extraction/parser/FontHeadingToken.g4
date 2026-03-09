grammar FontHeadingToken;

fontHeading
    : marker DIGITS EOF
    ;

marker
    : HEADING
    | H
    ;

HEADING
    : [Hh] [Ee] [Aa] [Dd] [Ii] [Nn] [Gg]
    ;

H
    : [Hh]
    ;

DIGITS
    : [0-9]+
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
