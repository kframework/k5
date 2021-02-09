---
copyright: Copyright (c) 2015-2020 K Team. All Rights Reserved.
---

K Language Features
===================

Defined below is a series of modules used to parse inner syntax in K (ie, the
contents of rules, configuration declarations, and contexts).

Much of this file exists in tight correspondence with the K implementation, and
K will not work correctly if it is altered without corresponding changes to the
source code of the K tools.

Users should only import a few modules from this file. In particular, this
includes `SORT-K`, `BASIC-K`, `ML-SYNTAX`, `DEFAULT-LAYOUT`,
`DEFAULT-CONFIGURATION`, and `K-AMBIGUITIES`. The remaining modules should not
be imported by the user; they are used implicitly by the implementation of K.

Basic K Sorts
-------------

The `SORT-K` module declares the `K` sort, and nothing else.

```k
module SORT-K
  syntax K [hook(K.K)]
endmodule
```

The `BASIC-K` module declares the `K`, `KItem`, and `KConfigVar` sorts, and
imports the syntax of matching logic.

```k
module BASIC-K
  imports ML-SYNTAX
  imports SORT-BOOL
  syntax KItem [hook(K.KItem)]
  syntax K     ::= KItem
  syntax KConfigVar [token]
endmodule
```

KAST Abstract Syntax
--------------------

Below is defined the abstract syntax of concrete terms in K, the `KAST` syntax.
Users should rarely if ever have to refer to this syntax; in general, it
suffices to use concrete syntax in rules, configuration declarations, contexts,
etc.

This syntax is used directly by the K implementation, and exists here as a
reference for the syntax of `KAST`, but it should not be imported directly by
the user.

```k
module KSTRING
  syntax KString ::= r"[\\\"](([^\\\"\\n\\r\\\\])|([\\\\][nrtf\\\"\\\\])|([\\\\][x][0-9a-fA-F]{2})|([\\\\][u][0-9a-fA-F]{4})|([\\\\][U][0-9a-fA-F]{8}))*[\\\"]"      [token]
  // optionally qualified strings, like in Scala "abc", i"abc", r"a*bc", etc.
endmodule

module BUILTIN-ID-TOKENS
  syntax #LowerId ::= r"[a-z][a-zA-Z0-9]*"                    [prec(2), token]
  syntax #UpperId ::= r"[A-Z][a-zA-Z0-9]*"                    [prec(2), token]
endmodule

module SORT-KBOTT
  imports SORT-K
  syntax KBott
endmodule

module KAST
  imports BASIC-K
  imports SORT-KBOTT
  imports KSTRING
  imports BUILTIN-ID-TOKENS

  syntax KBott ::= "#token" "(" KString "," KString ")"  [klabel(#KToken), symbol]
                 | "#klabel" "(" KLabel ")"              [klabel(#WrappedKLabel), symbol]
                 | KLabel "(" KList ")"                  [klabel(#KApply), symbol]
  syntax KItem ::= KBott

  syntax KLabel ::= r"`(\\\\`|\\\\\\\\|[^`\\\\\\n\\r])+`" [token]
                  | #LowerId                                   [token]
                  | r"(?<![a-zA-Z0-9])[#a-z][a-zA-Z0-9]*"               [token, prec(1)]
                       // something that doesn't collide with meta-variables

  syntax KList ::= K
                 | ".KList"          [klabel(#EmptyKList), symbol]
                 | ".::KList"        [klabel(#EmptyKList), symbol, unparseAvoid]
                 | KList "," KList   [klabel(#KList), left, assoc, unit(#EmptyKList), symbol, prefer]
endmodule


// To be used when parsing/pretty-printing ground configurations
module KSEQ
  imports KAST
  imports K-TOP-SORT
  syntax K ::= ".K"      [klabel(#EmptyK), symbol, unparseAvoid]
             | "."       [klabel(#EmptyK), symbol]
             | ".::K"    [klabel(#EmptyK), symbol, unparseAvoid]
  syntax K ::= K "~>" K  [klabel(#KSequence), left, assoc, unit(#EmptyK), symbol]
  syntax left #KSequence
  syntax {Sort} Sort     ::= "(" Sort ")"    [bracket]
endmodule
```

Syntax of Matching Logic
------------------------

K provides direct access to the symbols of Matching Logic, while giving them
their own concrete syntax distinct from the syntax of the `KORE` intermediate
representation. These symbols are primarily used during symbolic execution.
The LLVM Backend has relatively little understanding of Matching Logic directly
and use of these symbols directly in rules is likely to cause it to crash.
However, these symbols are necessary when providing lemmas and other types of
logical assistance to proofs and symbolic execution in the Haskell Backend.

The correspondance between K symbols and KORE symbols is as follows:

* `#Top` - `\top`
* `#Bottom` - `\bottom`
* `#Not` - `\not`
* `#Ceil` - `\ceil`
* `#Floor` - `\floor`
* `#Equals` - `\equals`
* `#And` - `\and`
* `#Or` - `\or`
* `#Implies` - `\implies`
* `#Exists` - `\exists`
* `#Forall` - `\forall`
* `#AG` - `allPathGlobally`
* `#wEF` - `weakExistsFinally`
* `#wAF` - `weakAlwaysFinally`

```k
module ML-SYNTAX
  imports SORT-K

  syntax {Sort} Sort ::= "#Top" [klabel(#Top), symbol, mlUnary]
                       | "#Bottom" [klabel(#Bottom), symbol, mlUnary]
                       | "#True" [klabel(#Top), symbol, mlUnary, unparseAvoid]
                       | "#False" [klabel(#Bottom), symbol, mlUnary, unparseAvoid]
                       | "#Not" "(" Sort ")" [klabel(#Not), symbol, mlOp, mlUnary]

  syntax {Sort1, Sort2} Sort2 ::= "#Ceil" "(" Sort1 ")" [klabel(#Ceil), symbol, mlOp, mlUnary]
                                | "#Floor" "(" Sort1 ")" [klabel(#Floor), symbol, mlOp, mlUnary]
                                | "{" Sort1 "#Equals" Sort1 "}" [klabel(#Equals), symbol, mlOp, mlEquals, format(%1%i%n%2%d%n%3%i%n%4%d%n%5)]

  syntax priorities mlUnary > mlEquals > mlAnd

  syntax {Sort} Sort ::= Sort "#And" Sort [klabel(#And), symbol, assoc, left, comm, unit(#Top), mlOp, mlAnd, format(%i%1%d%n%2%n%i%3%d)]
                       > Sort "#Or" Sort [klabel(#Or), symbol, assoc, left, comm, unit(#Bottom), mlOp, format(%i%1%d%n%2%n%i%3%d)]
                       > Sort "#Implies" Sort [klabel(#Implies), symbol, mlOp, mlImplies, format(%i%1%d%n%2%n%i%3%d)]

  syntax priorities mlImplies > mlQuantifier

  syntax {Sort1, Sort2} Sort2 ::= "#Exists" Sort1 "." Sort2 [klabel(#Exists), symbol, mlOp, mlBinder, mlQuantifier]
                                | "#Forall" Sort1 "." Sort2 [klabel(#Forall), symbol, mlOp, mlBinder, mlQuantifier]

  syntax {Sort} Sort ::= "#AG" "(" Sort ")" [klabel(#AG), symbol, mlOp]
                       | "#wEF" "(" Sort ")" [klabel(weakExistsFinally), symbol, mlOp]
                       | "#wAF" "(" Sort ")" [klabel(weakAlwaysFinally), symbol, mlOp]
endmodule
```

Variables in K
--------------

Provided below is the syntax of variables in K. There are four types of
variables in K:

1. Regular variables. These are denoted by variables that begin with an
   underscore or a capital letter. These variables match exactly one value
   and can be used to refer to it on the right-hand-side.
2. Fresh constants. These are denoted by variables that begin with an `!`. This
   is a convenience syntax which can be used on the right-hand-side only, and
   refer to a unique value of the specified sort which is distinct from any
   other value that has been generated or will be generated by the `!X` syntax.
   Note that this may not be distinct from values produced via other means.
3. Existential variables. This refers to variables that are existentially
   quantified and begin with a `?`. They are not required to appear on the
   left-hand-side prior to appearing on the right-hand-side, and generally
   refer to symbolic quantities that are introduced during rewriting. Refer to
   K's documentation for more details.
4. Set variables. These are denoted by variables that begin with a `@`.
   These variables refer to a set of values and are generally used when writing
   simplification rules in the Haskell Backend. For more information, refer to
   K's documentation.

There is also a fifth type of "variable", although it is not technically a
variable. This refers to configuration variables, which are used to insert
values into the initial configuration that come from outside the semantics.
The most common of these is the `$PGM` variable, which conventionally contains
the program being executed and is placed in the `<k>` cell in the configuration
declaration. These "variables" begin with a `$` and their values are populated
by the frontend prior to symbolic or concrete execution of a program.

```k
module KVARIABLE-SYNTAX
  syntax #KVariable
endmodule

// To be used when parsing/pretty-printing symbolic configurations
module KSEQ-SYMBOLIC
  imports KSEQ
  imports ML-SYNTAX
  imports KVARIABLE-SYNTAX

  syntax #KVariable ::= r"(?<![A-Za-z0-9_\\$!\\?@])(\\!|\\?|@)?([A-Z][A-Za-z0-9'_]*|_|_[A-Z][A-Za-z0-9'_]*)"   [token, prec(1)]
                      | #UpperId                                                          [token]
  syntax KConfigVar ::= r"(?<![A-Za-z0-9_\\$!\\?@])(\\$)([A-Z][A-Za-z0-9'_]*)"            [token]
  syntax KBott      ::= #KVariable
  syntax KBott      ::= KConfigVar
  syntax KLabel     ::= #KVariable
endmodule
```

Syntax of Cells
---------------

While the backend treats cells as regular productions like any other, the
frontend provides a significant amount of convenience notation for dealing with
groups of cells, in order to make writing modular definitions easier. As a
result, we need a syntax for groups of cells and for referring to cells within
rules, configuration declarations, and functions.

For historical reasons, the `Bag` sort is used to refer to groups of cells.
This may change in a future release. Users can combine cells in any order
by concatenating them together, and can refer to the absence of any cells with
the `.Bag` symbol. You can also refer to cells within a function by placing
the cell context symbol, `[[ K ]]` at the top of a rule, placing a function
symbol inside, and referring to cells afterwards. This implicitly inserts
a reference to the configuration at the time prior to the currently-applied
rule being applied which can be matched on within the function. Functions with
such context cannot be referred to in the initial configuration, because the
prior configuration does not yet exist.

```k
module KCELLS
  imports KAST

  syntax Cell
  syntax Bag ::= Bag Bag  [left, assoc, klabel(#cells), symbol, unit(#cells)]
               | ".Bag"   [klabel(#cells), symbol]
               | ".::Bag" [klabel(#cells), symbol]
               | Cell
  syntax Bag ::= "(" Bag ")" [bracket]
  syntax KItem ::= Bag
  syntax #RuleBody ::= "[" "[" K "]" "]" Bag    [klabel(#withConfig), symbol, avoid]
  syntax non-assoc #withConfig
  syntax Bag ::= KBott
endmodule
```

Users can also refer to cells in rules. When doing so, an optional `...` can
be placed immediately after the start of the cell or immediately before the
end. In a cell whose contents are commutative, these are equivalent to one
another and are also equivalent to placing `...` in both places. This means
that what is placed in the cell will be combined with the cell contents'
concatenation operator with an unnamed variable. In other words, you match on
some number of elements in the collection and do not care about the rest of
the collection.

In a cell whose contents are not commutative, the `...` operators correspond
to a variable on the respective side of the contents of the cell that the
`...` appears. For example, `<foo>... L </foo>`, if `L` is a list, means
some number of elements followed by L. Note that not all combinations are
supported. Cells whose contents are sort `K` can only have `...` appear at the
tail of the cell, and cells whose contents are sort `List` can only have `...`
appear on at most one side in a single rule.

```k
module RULE-CELLS
  imports KCELLS
  imports RULE-LISTS
  // if this module is imported, the parser automatically
  // generates, for all productions that have the attribute 'cell' or 'maincell',
  // a production like below:
  //syntax Cell ::= "<top>" #OptionalDots K #OptionalDots "</top>" [klabel(<top>)]

  syntax #OptionalDots ::= "..." [klabel(#dots), symbol]
                         | ""    [klabel(#noDots), symbol]
endmodule
```

Users can also declare cells in a configuration declaration. This generates a
specific set of productions that is used internally to implement the cell. The
most important of these is the cell itself, and attributes on this production
can be specified in an xml-attribute-like syntax.

You can also use an xml-short-tag-like syntax to compose configuration cells
together which were defined in different modules. However, it is a requirement
that any K definition have at most one fully-composed configuration; thus, all
other configuration declarations must appear composed within another
configuration declaration.

```k
module CONFIG-CELLS
  imports KCELLS
  imports RULE-LISTS
  syntax #CellName ::= r"[a-zA-Z][a-zA-Z0-9\\-]*"  [token, prec(1)]
                     | #LowerId            [token]
                     | #UpperId            [token]

  syntax Cell ::= "<" #CellName #CellProperties ">" K "</" #CellName ">" [klabel(#configCell), symbol]
  syntax Cell ::= "<" #CellName "/>" [klabel(#externalCell), symbol]

  syntax #CellProperties ::= #CellProperty #CellProperties [klabel(#cellPropertyList), symbol]
                           | ""                            [klabel(#cellPropertyListTerminator), symbol]
  syntax #CellProperty ::= #CellName "=" KString           [klabel(#cellProperty), symbol]
endmodule
```

Syntax of Rules
---------------

Rules can have an optional requires clause or an ensures clause. For backwards-
compatibility, you can refer to the requires clause with both the `requires`
and `when` keywords; The latter, however, is deprecated and may be removed in
a future release.

The requires clause specifies the preconditions that must be true in order
for the rule to apply. The ensures clause specifies the information which
becomes true after the rule has applied. It is a requirement that information
present in the `ensures` clause refer to existential variables only.

When doing concrete execution, you can think of the `requires` clause as a
side-condition. In other words, even if the rule matches, it will not apply
unless the `requires` clause, which must be of sort `Bool`, evaluates to
`true`.

```k
module REQUIRES-ENSURES
  imports BASIC-K

  syntax #RuleBody ::= K

  syntax #RuleContent ::= #RuleBody                                 [klabel("#ruleNoConditions"), symbol]
                        | #RuleBody "requires" Bool                 [klabel("#ruleRequires"), symbol]
                        | #RuleBody "when" Bool                     [klabel("#ruleRequires"), symbol]
                        | #RuleBody "ensures"  Bool                 [klabel("#ruleEnsures"), symbol]
                        | #RuleBody "requires" Bool "ensures" Bool  [klabel("#ruleRequiresEnsures"), symbol]
                        | #RuleBody "when" Bool "ensures" Bool      [klabel("#ruleRequiresEnsures"), symbol]
endmodule
```

Miscellaneous modules
---------------------

The below modules are used in various ways as indicators to the implementation
that certain automatically generated syntax should be created by the parser.
These modules should not be imported directly by the user.

```k
module K-TOP-SORT
  imports SORT-KBOTT
  syntax KItem ::= KBott
  syntax {Sort} KItem ::= Sort
endmodule

module K-BOTTOM-SORT
  imports SORT-KBOTT
  syntax KItem ::= KBott
  syntax {Sort} Sort ::= KBott
endmodule

module K-SORT-LATTICE
  imports K-TOP-SORT
  imports K-BOTTOM-SORT
endmodule

module AUTO-CASTS
  // if this module is imported, the parser automatically
  // generates, for all sorts, productions of the form:
  // Sort  ::= Sort "::Sort"
  // Sort  ::= Sort ":Sort"
  // KBott ::= Sort "<:Sort"
  // Sort  ::= K    ":>Sort"
  // this is part of the mechanism that allows concrete user syntax in K
endmodule

module AUTO-FOLLOW
  // if this module is imported, the parser automatically
  // generates a follow restriction for every terminal which is a prefix
  // of another terminal. This is useful to prevent ambiguities such as:
  // syntax K ::= "a"
  // syntax K ::= "b"
  // syntax K ::= "ab"
  // syntax K ::= K K
  // #parse("ab", "K")
  // In the above example, the terminal "a" is not allowed to be followed by a "b"
  // because it would turn the terminal into the terminal "ab".
endmodule

module PROGRAM-LISTS
  imports SORT-K
  // if this module is imported, the parser automatically
  // replaces the default productions for lists:
  // Es ::= E "," Es [userList("*"), klabel('_,_)]
  //      | ".Es"    [userList("*"), klabel('.Es)]
  // into a series of productions more suitable for programs:
  // Es#Terminator ::= ""      [klabel('.Es)]
  // Ne#Es ::= E "," Ne#Es     [klabel('_,_)]
  //         | E Es#Terminator [klabel('_,_)]
  // Es ::= Ne#Es
  //      | Es#Terminator      // if the list is *
endmodule

module RULE-LISTS
  // if this module is imported, the parser automatically
  // adds the subsort production to the parsing module only:
  // Es ::= E        [userList("*")]

endmodule

module RECORD-PRODUCTIONS
  // if this module is imported, prefix productions of the form
  // syntax Sort ::= name(Args)
  // will be able to be parsed with don't-care variables according
  // to their nonterminal's names
endmodule

module SORT-PREDICATES
  // if this module is imported, the Bool sort will be annotated with
  // syntax Bool ::= isSort(K) [function]
  // and all sorts will be annotated with
  // syntax Sort ::= project:Sort(K) [function]
endmodule
```

Additional Syntax for K Terms in Rules
--------------------------------------

Certain additional features are available when parsing the contents of rules
and contexts. For more information on each of these, refer to K's 
documentation.

```k
module KREWRITE
  syntax {Sort} Sort ::= Sort "=>" Sort [klabel(#KRewrite), symbol]
  syntax non-assoc #KRewrite
  syntax priority #KRewrite > #withConfig
endmodule

// To be used to parse semantic rules
module K
  imports KSEQ-SYMBOLIC
  imports REQUIRES-ENSURES
  imports RECORD-PRODUCTIONS
  imports SORT-PREDICATES
  imports K-SORT-LATTICE
  imports AUTO-CASTS
  imports AUTO-FOLLOW
  imports KREWRITE

  syntax {Sort} Sort ::= Sort "#as" Sort [klabel(#KAs), symbol]
  // functions that preserve sorts and can therefore have inner rewrites
  syntax {Sort} Sort ::= "#fun" "(" Sort ")" "(" Sort ")" [klabel(#fun2), symbol, prefer]
  // functions that do not preserve sort and therefore cannot have inner rewrites
  syntax {Sort1, Sort2} Sort1 ::= "#fun" "(" Sort2 "=>" Sort1 ")" "(" Sort2 ")" [klabel(#fun3), symbol]

  /*@ Set membership over terms. In addition to equality over
      concrete patterns, K also supports computing equality
      between a concrete pattern and a symbolic pattern.
      This is compiled efficiently down to pattern matching,
      and can be used by putting a term with unbound variables
      in the left child of :=K or =/=K. Note that this does not
      bind variables used on the lhs however (although this may
      change in the future).*/

  syntax Bool ::= left:
                  K ":=K" K           [function, functional, klabel(_:=K_), symbol, equalEqualK]
                | K ":/=K" K          [function, functional, klabel(_:/=K_), symbol, notEqualEqualK]
endmodule

// To be used to parse terms in full K
module K-TERM
  imports KSEQ-SYMBOLIC
  imports RECORD-PRODUCTIONS
  imports SORT-PREDICATES
  imports K-SORT-LATTICE
  imports AUTO-CASTS
  imports AUTO-FOLLOW
  imports KREWRITE
endmodule
```

Layout Information
------------------

When constructing a scanner for use during parsing, often you wish to ignore
certain types of text, such as whitespace and comments. However, the specific
syntax which each language must ignore is a little different from language
to language, and thus you wish to specify it manually. You can do this by
defining productions of the `#Layout` sort. For more information, refer to
K's documentation. However, this module will be implicitly imported if no 
productions are declared of sort `#Layout`. This module will also be used
for the purposes of parsing K rules. If you wish to declare a language with
no layout productions, simply create a sort declaration for the `#Layout` sort
in your code (e.g. `syntax #Layout`).

```k
module DEFAULT-LAYOUT
    syntax #Layout ::= r"(\\/\\*([^\\*]|(\\*+([^\\*\\/])))*\\*+\\/)"
                     | r"(\\/\\/[^\\n\\r]*)"
                     | r"([\\ \\n\\r\\t])"
endmodule
```

Default Configuration
---------------------

If the user has no configuration declaration in their seamantics, the below
configuration declaration will be implicitly imported.

```k
module DEFAULT-CONFIGURATION
  imports BASIC-K

  configuration <k> $PGM:K </k>
endmodule
```

Parsing Ambiguous Languages
---------------------------

On occasion, it may be desirable to parse a language with an ambiguous grammar
when parsing a program, and perform additional semantic analysis at a later
time in order to resolve the ambiguities. A good example of this is as a
substitute for the lexer hack in parsers of the `C` programming language.

The following module contains a declaration for ambiguities in K. Usually,
an ambiguous parse is an error. However, when you use the `--gen-glr-parser`
flag to `kast`, or the `--gen-glr-bison-parser` flag to `kompile`, ambiguities
instead become instances of the below parametric production, which you can use
regular K rules to disambiguate as necessary.

```k
module K-AMBIGUITIES

  syntax {Sort} Sort ::= amb(Sort, Sort) [symbol]

endmodule
```

Annotating Parses with Locations
--------------------------------

Another feature of K's Bison parser is the ability to annotate terms parsed
with location information about the file and line where they occurred. For
more information about how to use this, refer to K's documentation. However,
the below module exists to provide a user syntax for the annotations that
are generated by the parser.

```k
module K-LOCATIONS
  imports STRING-SYNTAX
  imports INT-SYNTAX

  // filename, startLine, startCol, endLine, endCol
  syntax {Sort} Sort ::= #location(Sort, String, Int, Int, Int, Int) [symbol, format(%3)]

endmodule
```
