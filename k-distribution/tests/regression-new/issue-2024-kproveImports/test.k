// Copyright (c) 2020 K Team. All Rights Reserved.

requires "domains.md"

module TEST-SYNTAX
endmodule

module EVM
    imports INT

    configuration <k> $PGM:Int </k>
endmodule

module TEST
    imports EVM

    syntax Int ::= Int "up/Int" Int [function, no-evaluators]
 // ---------------------------------------------------------

    syntax Int ::= #ceil32 ( Int )
 // ------------------------------
    rule #ceil32(N) => (N up/Int 32) *Int 32 [macro]
endmodule
