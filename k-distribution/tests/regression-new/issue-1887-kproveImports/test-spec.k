// Copyright (c) 2020 K Team. All Rights Reserved.

requires "test.k"

module VERIFICATION
    imports TEST1

    syntax KItem ::= runLemma ( Int ) | doneLemma ( Int )

    rule <k> runLemma(I) => doneLemma(I) ... </k>
endmodule

module TEST-SPEC
    imports VERIFICATION

    claim <k> runLemma(3 +Int 7) => doneLemma(10) ... </k>
endmodule
