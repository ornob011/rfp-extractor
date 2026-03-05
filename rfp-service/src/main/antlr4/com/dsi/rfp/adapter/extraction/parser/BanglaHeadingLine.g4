grammar BanglaHeadingLine;

line
    : chapterLine
    | dharaLine
    | anuchchhedLine
    ;

chapterLine
    : (ADHYAY | PROTHOM_ADHYAY | DITIO_ADHYAY | TRITIO_ADHYAY) NUMBER? OTHER* EOF
    ;

dharaLine
    : DHARA NUMBER OTHER* EOF
    ;

anuchchhedLine
    : ANUCHCHHED NUMBER OTHER* EOF
    ;

ADHYAY
    : 'অধ্যায়'
    ;

PROTHOM_ADHYAY
    : 'প্রথম অধ্যায়'
    ;

DITIO_ADHYAY
    : 'দ্বিতীয় অধ্যায়'
    ;

TRITIO_ADHYAY
    : 'তৃতীয় অধ্যায়'
    ;

DHARA
    : 'ধারা'
    ;

ANUCHCHHED
    : 'অনুচ্ছেদ'
    ;

NUMBER
    : [0-9০-৯]+
    ;

WS
    : [ \t]+ -> skip
    ;

NEWLINE
    : [\r\n]+ -> skip
    ;

OTHER
    : .
    ;
