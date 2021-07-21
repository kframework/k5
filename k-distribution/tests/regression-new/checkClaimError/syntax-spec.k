// Copyright (c) 2021 K Team. All Rights Reserved.

module SYNTAX-SPEC
    imports ERRORCLAIM

    syntax X ::= "errorHere"
    syntax Y
    claim <k> doIt(foo) => doIt(0) ... </k>
endmodule
