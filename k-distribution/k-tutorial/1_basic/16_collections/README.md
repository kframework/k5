# Lesson 1.16: Maps, Semantic Lists, and Sets

The purpose of this lesson is to explain how to use the data structure sorts
provided by K: maps, lists, and sets.

## Maps

The most frequently used type of data structure in K is the map. The sort
provided by K for this purpose is the `Map` sort, and it is provided in
[domains.md](../../../include/kframework/builtin/domains.md) in the `MAP`
module. This type is not (currently) polymorphic. All `Map` terms are maps that
map terms of sort `KItem` to other terms of sort `KItem`. Of course, a `KItem`
can contain any other sort except a `K` sequence. If you need to store such
a term in a map, you can always use a wrapper such as
`syntax KItem ::= kseq(K)`.

A `Map` pattern consists of zero or more map elements (as represented by the
symbol `syntax Map ::= KItem "|->" KItem`), mixed in any order, separated by
whitespace, with zero or one variables of sort `Map`. The empty map is
represented by `.Map`. If all of the bindings for the variables in the keys
of the map can be deterministically chosen, these patterns can be matched in
`O(1)` time. If they cannot, then each map element that cannot be
deterministically constructed contributes a single dimension of polynomial
time to the cost of the matching. In other words, a single such element is
linear, two are quadratic, three are cubic, etc.

Patterns like the above are the only type of `Map` pattern that can appear
on the left-hand-side of a rule. In other words, you are not allowed to write
a `Map` pattern on the left-hand-side with more than one variable of sort `Map`
in it. You are, however, allowed to write such patterns on the right-hand-side
of a rule. You can also write a function pattern in the key of a map element
so long as all the variables in the function pattern can be deterministically
chosen.

Note the meaning of matching on a `Map` pattern: a map pattern with no
variables of sort `Map` will match if the map being matched has exactly as
many bindings as `|->` symbols in the pattern. It will then match if each
binding in the map pattern matches exactly one distinct binding in the map
being matched. A map pattern with one `Map` variable will also match any map
that contains such a map as a subset. The variable of sort `Map` will be bound
to whatever bindings are left over (`.Map` if there are no bindings left over).

Here is an example of a simple definition that implements a very basic
variable declaration semantics using a `Map` to store the value of variables
(`lesson-16-a.k`):

```k
module LESSON-16-A-SYNTAX
  imports INT-SYNTAX
  imports ID-SYNTAX

  syntax Exp ::= Id | Int
  syntax Decl ::= "int" Id "=" Exp ";" [strict(2)]
  syntax Pgm ::= List{Decl,""}
endmodule

module LESSON-16-A
  imports LESSON-16-A-SYNTAX
  imports BOOL

  configuration <T>
                  <k> $PGM:Pgm </k>
                  <state> .Map </state>
                </T>

  // declaration sequence
  rule <k> D:Decl P:Pgm => D ~> P ...</k>
  rule <k> .Pgm => . ...</k>

  // variable declaration
  rule <k> int X:Id = I:Int ; => . ...</k>
       <state> STATE => STATE [ X <- I ] </state>

  // variable lookup
  rule <k> X:Id => I ...</k>
       <state>... X |-> I ...</state>

  syntax Bool ::= isKResult(K) [symbol, function]
  rule isKResult(_:Int) => true
  rule isKResult(_) => false [owise]
endmodule
```

There are several new features in this definition. First, note we import
the module `ID-SYNTAX`. This module is defined in `domains.md` and provides a
basic syntax for identifiers. This syntax is only imported when parsing
programs, not when parsing rules. Later in this lesson we will see how to
reference specific concrete identifiers in a rule.

Second, we introduce a single new function over the `Map` sort. This function,
which is represented by the symbol
`syntax Map ::= Map "[" KItem "<-" KItem "]"`, represents the map update
operation. Other functions over the `Map` sort can be found in `domains.md`.

Finally, we have used the `...` syntax on a cell containing a Map. In this
case, the meaning of `<state>... Pattern ...</state>`,
`<state>... Pattern </state>`, and `<state> Pattern ...</state>` are the same:
it is equivalent to writing `<state> (Pattern) _:Map </state>`.

Consider the following program (`a.decl`):

```
int x = 0;
int y = 1;
int a = x;
```

If we run this program with `krun`, we will get the following result:

```
<T>
  <k>
    .
  </k>
  <state>
    a |-> 0
    x |-> 0
    y |-> 1
  </state>
</T>
```

Note that `krun` has automatically sorted the collection for you. This doesn't
happen at runtime, so you still get the performance of a hash map, but it will
help make the output more readable.

### Exercise

Create a sort `Stmt` that is a subsort of `Decl`. Create a production of sort
`Stmt` for variable assignment. Feel free to use the syntax
`syntax Stmt ::= Id "=" Exp ";"`. Write a rule that implements variable
assignment using a map update function. Then write the same rule using a
map pattern. Test your implementations with some programs to ensure they behave
as expected.

## Semantic Lists

In a previous lesson, we explained how to represent lists in the AST of a
program. However, this is not the only context where lists can be used. We also
frequently use lists in the configuration of an interpreter in order to
represent certain types of program state. For this purpose, it is generally
useful to have an associative-list sort, rather than the cons-list sorts
provided in [Lesson 1.12](../12_syntactic_lists/README.md).

The type provided by K for this purpose is the `List` sort, and it is also 
provided in `domains.md`, in the `LIST` module. This type is also not
(currently) polymorphic. Like `Map`, all `List` terms are lists of terms of the
`KItem` sort.

A `List` pattern in K consists of zero or more list elements (as represented by
the `ListItem` symbol), followed by zero or one variables of sort `List`,
followed by zero or more list elements. An empty list is represented by
`.List`. These patterns can be matched in `O(log(N))` time. This is the only
type of `List` pattern that can appear on the left-hand-side of a rule. In
other words, you are not allowed to write a `List` pattern on the
left-hand-side with more than one variable of sort `List` in it. You are,
however, allowed to write such patterns on the right-hand-side of a rule.

Note the meaning of matching on a `List` pattern: a list pattern with no
variables of sort `List` will match if the list being matched has exactly as
many elements as `ListItem` symbols in the pattern. It will then match if each
element in sequence matches the pattern contained in the `ListItem` symbol. A
list pattern with one variable of sort `List` operates the same way, except
that it can match any list with at least as many elements as `ListItem`
symbols, so long as the prefix and suffix of the list match the patterns inside
the `ListItem` symbols. The variable of sort `List` will be bound to whatever
elements are left over (`.List` if there are no elements left over).

The `...` syntax is allowed on cells containing lists as well. In this case,
the meaning of `<cell>... Pattern </cell>` is the same as
`<cell> _:List (Pattern) </cell>`, the meaning of `<cell> Pattern ...</cell>`
is the same as `<cell> (Pattern) _:List</cell>`. Because list patterns with
multiple variables of sort `List` are not allowed, it is an error to write
`<cell>... Pattern ...</cell>`.

Here is an example of a simple definition that implements a very basic
function-call semantics using a `List` as a function stack (`lesson-16-a.k`):

```k
module LESSON-16-B-SYNTAX
  imports INT-SYNTAX
  import ID-SYNTAX

  syntax Exp ::= Id "(" ")" | Int
  syntax Stmt ::= "return" Exp ";" [strict]
  syntax Decl ::= "fun" Id "(" ")" "{" Stmt "}"
  syntax Pgm ::= List{Decl,""}
  syntax Id ::= "main" [token]
endmodule

module LESSON-16-B
  imports LESSON-16-B-SYNTAX
  imports BOOL

  configuration <T>
                  <k> $PGM:Pgm ~> main () </k>
                  <functions> .Map </functions>
                  <fstack> .List </fstack>
                </T>

  // declaration sequence
  rule <k> D:Decl P:Pgm => D ~> P ...</k>
  rule <k> .Pgm => . ...</k>

  // function definitions
  rule <k> fun X:Id () { S } => . ...</k>
       <functions>... .Map => X |-> S ...</functions>

  // function call
  syntax KItem ::= stackFrame(K)
  rule <k> X:Id () ~> K => S </k>
       <functions>... X |-> S ...</functions>
       <fstack> .List => ListItem(stackFrame(K)) ...</fstack>

  // return statement
  rule <k> return I:Int ; ~> _ => I ~> K </k>
       <fstack> ListItem(stackFrame(K)) => .List ...</fstack>

  syntax Bool ::= isKResult(K) [function, symbol]
  rule isKResult(_:Int) => true
  rule isKResult(_) => false [owise]
endmodule
```

Notice that we have declared the production `syntax Id ::= "main" [token]`.
Since we use the `ID-SYNTAX` module, this declaration is necessary in order to
be able to refer to the `main` identifier directly in the configuration
declaration. Our `<k>` cell now contains a `K` sequence initially: first we
process all the declarations in the program, then we call the `main` function.

Consider the following program (`foo.func`):

```
int foo() { return 5; }
int main() { return foo(); }
```

When we `krun` this program, we should get the following output:

```
<T>
  <k>
    5 ~> .
  </k>
  <functions>
    foo |-> return 5 ;
    main |-> return foo ( ) ;
  </functions>
  <fstack>
    .List
  </fstack>
</T> 
```

Note that we have successfully put on the `<k>` cell the value returned by the
`main` function.

### Exercise

Add a term of sort `Id` to the `stackFrame` operator to keep track of the
name of the function in that stack frame. Then write a function
`syntax String ::= printStackTrace(List)` that takes the contents of the
`<fstack>` cell and pretty prints the current stack trace. Test this function
by creating a new expression that returns the current stack trace as a string.
Make sure to update `isKResult` and the `Exp` sort as appropriate to allow
strings as values.

## Sets

The final primary data structure sort in K is a set, i.e., an idempotent
unordered collection where elements are deduplicated. The sort provided by K
for this purpose is the `Set` sort and it is provided in `domains.md` in the
`SET` module. Like maps and lists, this type is not (currently) polymorphic.
Like `Map` and `List`, all `Sert` terms are sets of terms of the `KItem` sort.

A `Set` pattern has the exact same restrictions as a `Map` pattern, except that
its elements are treated like keys, and there are no values. It has the same
performance characteristics as well. However, syntactically it is more similar
to the `List` sort: An empty `Set` is represented by `.Set`, but a set element
is represented by the `SetItem` symbol.

Matching behaves similarly to the `Map` sort: a set pattern with no variables
of sort `Set` will match if the set has exactly as many bindings as `SetItem`
symbols, and if each element pattern matches one distinct element in the set.
A set with a variable of sort `Set` also matches any superset of such a set.
As with map, the elements left over will be bound to the `Set` variable (or
`.Set` if no elements are left over).

Like `Map`, the `...` syntax on a set is syntactic sugar for an anonymous
variable of sort `Set`.

Here is an example of a simple modification to `LESSON-16-A` which uses a `Set`
to ensure that variables are never declared more than once. In practice, you
would likely just use the `in_keys` symbol over maps to test for this, but
it's still useful as an example of sets in practice:

```k
module LESSON-16-C-SYNTAX
  imports LESSON-16-A-SYNTAX
endmodule

module LESSON-16-C
  imports LESSON-16-C-SYNTAX
  imports BOOL

  configuration <T>
                  <k> $PGM:Pgm </k>
                  <state> .Map </state>
                  <declared> .Set </declared>
                </T>

  // declaration sequence
  rule <k> D:Decl P:Pgm => D ~> P ...</k>
  rule <k> .Pgm => . ...</k>

  // variable declaration
  rule <k> int X:Id = I:Int ; => . ...</k>
       <state> STATE => STATE [ X <- I ] </state>
       <declared> D => D SetItem(X) </declared>
    requires notBool X in D

  // variable lookup
  rule <k> X:Id => I ...</k>
       <state>... X |-> I ...</state>
       <declared>... SetItem(X) ...</declared>

  syntax Bool ::= isKResult(K) [symbol, function]
  rule isKResult(_:Int) => true
  rule isKResult(_) => false [owise]
endmodule
```

Now if we `krun` a program containing duplicate declarations, it will get
stuck on the declaration.

## Exercises

1. Modify your solution to Lesson 1.14, Problem 2 and introduce the sorts
`Decls`, `Decl`, and `Stmt` which include variable and function declaration
(without function parameters), and return and assignment statements, as well
as call expressions. Use `List` and `Map` to implement these operators, making
sure to consider the interactions between components, such as saving and
restoring the environment of variables at each call site. Don't worry about
local function definitions or global variables for now. Make sure to test the
resulting interpreter.
