grammar SectionNumber;

sectionNumber
    : DIGITS (DOT DIGITS)* DOT? EOF
    ;

DIGITS
    : [0-9]+
    ;

DOT
    : '.'
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
