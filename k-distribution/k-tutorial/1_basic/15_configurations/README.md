# Lesson 1.15: Configuration Declarations and Cell Nesting

The purpose of this lesson is to explain how to store additional information
about the state of your interpreter by declaring **cells** using the
`configuration` sentence, as well as how to add additional inputs to your
definition.

## Cells and Configuration Declarations

We have already covered the absolute basics of cells in K by looking at the
`<k>` cell. As explained in [Lesson 1.13](../13_rewrite_rules/README.md), the
`<k>` cell is available without being explicitly declared. It turns out this is
because, if the user does not explicitly specify a `configuration` sentence
anywhere in the main module of their definition, the `configuration` sentence
from the `DEFAULT-CONFIGURATION` module of
[kast.md](../../../include/kframework/builtin/kast.md) is imported
automatically. Here is what that sentence looks like:

```
  configuration <k> $PGM:K </k>
```

This configuration declaration declares a single cell, the `<k>` cell. It also
declares that at the start of rewriting, the contents of that cell should be
initialized with the value of the `$PGM` **configuration variable**.
Configuration variables function as inputs to `krun`. These terms are supplied
to `krun` in the form of ASTs parsed using a particular module. By default, the
`$PGM` configuration variable uses the main syntax module of the definition.
The cast on the configuration variable also specifies the sort that is used as
the entry point to the parser, in this case the `K` sort.

Note that we did not explicitly specify the `$PGM` configuration variable when
we invoked `krun` on a file. This is because `krun` handles the `$PGM` variable
specially, and allows you to pass the term for that variable via a file passed
as a positional argument to `krun`. We did, however, specify the `PGM` name
explicitly when we called `krun` with the `-cPGM` command line argument in
[Lesson 1.2](../02_basics/README.md). This is the other, explicit, way of
specifying an input to krun.

This explains the most basic use of configuration declarations in K. We can,
however, declare multiple cells and multiple configuration variables. We can
also specify the initial values of cells statically, rather than dynamically
via krun.

For example, consider the following definition (`lesson-15-a.k`):

```k
module LESSON-15-A-SYNTAX
  imports INT-SYNTAX

  syntax Ints ::= List{Int,","}
endmodule

module LESSON-15-A
  imports LESSON-15-A-SYNTAX
  imports INT

  configuration <k> $PGM:Ints </k>
                <sum> 0 </sum>

  rule <k> I:Int, Is:Ints => Is ...</k>
       <sum> SUM:Int => SUM +Int I </sum>
endmodule
```

This simple definition takes a list of integers as input and sums them
together. Here we have declared two cells: `<k>` and `<sum>`. Unlike `<k>`, 
`<sum>` does not get initialized via a configuration variable, but instead 
is initialized statically with the value `0`.

Note the rule in the second module: we have explicitly specified multiple
cells in a single rule. K will expect each of these cells to match in order for
the rule to apply.

Here is a second example (`lesson-15-b.k`):

```k
module LESSON-15-B-SYNTAX
  imports INT-SYNTAX
endmodule

module LESSON-15-B
  imports LESSON-15-B-SYNTAX
  imports INT
  imports BOOL

  configuration <k> . </k>
                <first> $FIRST:Int </first>
                <second> $SECOND:Int </second>

  rule <k> . => FIRST >Int SECOND </k>
       <first> FIRST </first>
       <second> SECOND </second>
endmodule
```

This definition takes two integers as command-line arguments and populates the
`<k>` cell with a Boolean indicating whether the first integer is greater than
the second. Notice that we have specified no `$PGM` configuration variable
here. As a result, we cannot invoke `krun` via the syntax `krun $file`.
Instead, we must explicitly pass values for each configuration variable via the
`-cFIRST` and `-cSECOND` command line flags. For example, if we invoke
`krun -cFIRST=0 -cSECOND=1`, we will get the value `false` in the K cell.

You can also specify both a `$PGM` configuration variable and other
configuration variables in a single configuration declaration, in which case
you would be able to initialize `$PGM` with either a positional argument or the
`-cPGM` command line flag, but the other configuration variables would need
to be explicitly initialized with `-c`.

### Exercise

Modify your solution to Lesson 1.14, Problem 2 to add a new cell with a
configuration variable of sort `Bool`. This variable should determine whether
the `/` operator is evaluated using `/Int` or `divInt`. Test that by specifying
different values for this variable, you can change the behavior of rounding on
division of negative numbers.

## Cell Nesting

It is possible to nest cells inside one another. A cell that contains other
cells must contain **only** other cells, but in doing this, you are able to
create a hierarchical structure to the configuration. Consider the following
definition which is equivalent to the one in `LESSON-15-B` (`lesson-15-c.k`):

```k
module LESSON-15-C-SYNTAX
  imports INT-SYNTAX
endmodule

module LESSON-15-C
  imports LESSON-15-C-SYNTAX
  imports INT
  imports BOOL

  configuration <T>
                  <k> . </k>
                  <state>
                    <first> $FIRST:Int </first>
                    <second> $SECOND:Int </second>
                  </state>
                </T>

  rule <k> . => FIRST >Int SECOND </k>
       <first> FIRST </first>
       <second> SECOND </second>
endmodule
```

Note that we have added some new cells to the configuration declaration:
the `<T>` cell wraps the entire configuration, and the `<state>` cell is
introduced around the `<first>` and `<second>` cells.

However, we have not changed the rule in this definition. This is because of
a concept in K called **configuration abstraction**. K allows you to specify
any number of cells in a rule (except zero) in any order you want, and K will
compile the rules into a form that matches the structure of the configuration
specified by the configuration declaration.

Here then, is how this rule would look after the configuration abstraction
has been resolved:

```
  rule <T>
         <k> . => FIRST >Int SECOND </k>
         <state>
           <first> FIRST </first>
           <second> SECOND </second>
         </state>
       </T>
```

In other words, K will complete cells to the top of the configuration by
inserting parent cells where appropriate based on the declared structure of
the configuration. This is useful because as a definition evolves, the
configuration may change, but you don't want to have to modify every single
rule each time. Thus, K follows the principle that you should only mention the
cells in a rule that are actually needed in order to accomplish its specific
goal. By following this best practice, you can significantly increase the
modularity of the definition and make it easier to maintain and modify.

### Exercise

Modify your definition from the previous exercise in this lesson to wrap the
two cells you have declared in a top cell `<T>`. You should not have to change
any other rules in the definition.

## Cell Variables

Sometimes it is desirable to explicitly match a variable against certain
fragments of the configuration. Because K's configuration is hierarchical,
we can grab subsets of the configuration as if they were just another term.
However, configuration abstraction applies here as well.
In particular, for each cell you specify in a configuration declaration, a
unique sort is assigned for that cell with a single constructor (the cell
itself). The sort name is taken by removing all special characters,
capitalizing the first letter and each letter after a hyphen, and adding the
word `Cell` at the end. For example, in the above example, the cell sorts are
`TCell`, `KCell`, `StateCell`, `FirstCell`, and `SecondCell`. If we had declared
a cell as `<first-number>`, then the cell sort name would be `FirstNumberCell`.

You can explicitly reference a variable of one of these sorts anywhere you
might instead write that cell. For example, consider the following rule:

```
  rule <k> true => S </k>
       (S:StateCell => <state>... .Bag ...</state>)
```

Here we have introduced two new concepts. The first is the variable of sort
`StateCell`, which matches the entire `<state>` part of the configuration. The
second is that we have introduced the concept of `...` once again. When a cell
contains other cells, it is also possible to specify `...` on either the left,
right or both sides of the cell term. Each of these three syntaxes are
equivalent in this case. When they appear on the left-hand side of a rule, they
indicate that we don't care what value any cells not explicitly named might
have. For example, we might write `<state>... <first> 0 </first> ...</state>` on
the left-hand side of a rule in order to indicate that we want to match the
rule when the `<first>` cell contains a zero, regardless of what the `<second>`
cell contains. If we had not included this ellipsis, it would have been a
syntax error, because K would have expected you to provide a value for each of
the child cells.

However, if, as in the example above, the `...` appeared on the right-hand side
of a rule, this instead indicates that the cells not explicitly mentioned under
the cell should be initialized with their default value from the configuration
declaration. In other words, that rule will set the value of `<first>` and
`<second>` to zero.

You may note the presence of the phrase `.Bag` here. You can think of this as
the empty set of cells. It is used as the child of a cell when you want to
indicate that no cells should be explicitly named. We will cover other uses
of this term in later lessons.

## Exercises

1. Modify the definition from the previous exercise in this lesson so that the
Boolean cell you created is initialized to false. Then add a production
`syntax Stmt ::= Bool ";" Exp`, and a rule that uses this `Stmt` to set the
value of the Boolean flag. Then add another production
`syntax Stmt ::= "reset" ";" Exp` which sets the value of the Boolean flag back
to its default value via a `...` on the right-hand side. You will need to add
an additional cell around the Boolean cell to make this work.
