// Copyright (c) 2020 K Team. All Rights Reserved.

module TEST-SYNTAX
endmodule

module TEST1
  imports INT
  configuration <c1> <k> .K </k> </c1>
endmodule

module TEST
  imports TEST1
  configuration <a> <c1/> <b> .K </b> </a>
endmodule
