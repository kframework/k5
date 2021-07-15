// Copyright (c) 2020 K Team. All Rights Reserved.

requires "test.k"

module VERIFICATION
    imports EVM

    syntax KItem ::= runLemma ( Step ) | doneLemma ( Step )
 // -------------------------------------------------------
    rule <k> runLemma(S) => doneLemma(S) ... </k>

    syntax Step ::= Bool
 // --------------------

endmodule

module TEST-SPEC
    imports VERIFICATION

    claim <k> runLemma(3 <Int 4) => doneLemma(true) ... </k>

endmodule
