// Copyright (c) 2015-2019 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore;

import com.google.common.collect.Lists;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kframework.attributes.Source;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.definition.RegexTerminal;
import org.kframework.kompile.DefinitionParsing;
import org.kframework.kompile.Kompile;
import org.kframework.kore.K;
import org.kframework.kore.Sort;
import org.kframework.kore.convertors.KILtoKORE;
import org.kframework.main.GlobalOptions;
import org.kframework.main.GlobalOptions.Warnings;
import org.kframework.parser.concrete2kore.generator.RuleGrammarGenerator;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import scala.Tuple2;
import scala.util.Either;

import java.io.File;
import java.util.Set;

import static org.kframework.kore.KORE.*;

public class RuleGrammarTest {
    private final static Sort startSymbol = DefinitionParsing.START_SYMBOL;
    private RuleGrammarGenerator gen;

    @Before
    public void setUp() throws Exception {
        gen = makeRuleGrammarGenerator();
    }

    public RuleGrammarGenerator makeRuleGrammarGenerator() {
        String definitionText;
        FileUtil files = FileUtil.testFileUtil();
        ParserUtils parser = new ParserUtils(files::resolveWorkingDirectory, new KExceptionManager(new GlobalOptions()));
        File definitionFile = new File(Kompile.BUILTIN_DIRECTORY.toString() + "/kast.k");
        definitionText = files.loadFromWorkingDirectory(definitionFile.getPath());

        Definition baseK =
                parser.loadDefinition("K", "K", definitionText,
                        definitionFile,
                        definitionFile.getParentFile(),
                        Lists.newArrayList(Kompile.BUILTIN_DIRECTORY),
                        false, false);

        return new RuleGrammarGenerator(baseK);
    }

    private void parseRule(String input, String def, int warnings, boolean expectedError) {
        Module test = ParserUtils.parseMainModuleOuterSyntax(def, Source.apply("generated by RuleGrammarTest"), "TEST");
        ParseInModule parser = RuleGrammarGenerator.getCombinedGrammar(gen.getRuleGrammar(test), true);
        Tuple2<Either<Set<KEMException>, K>, Set<KEMException>> rule = parser.parseString(input, startSymbol, Source.apply("generated by RuleGrammarTest"));
        printout(rule, warnings, expectedError);
    }

    private void parseRule(String input, String def, int warnings, K expectedResult) {
        Module test = ParserUtils.parseMainModuleOuterSyntax(def, Source.apply("generated by RuleGrammarTest"), "TEST");
        ParseInModule parser = RuleGrammarGenerator.getCombinedGrammar(gen.getRuleGrammar(test), true);
        Tuple2<Either<Set<KEMException>, K>, Set<KEMException>> rule
                = parser.parseString(input, startSymbol, Source.apply("generated by RuleGrammarTest"));
        printout(rule, warnings, false);
        Assert.assertEquals(expectedResult, rule._1().right().get());
    }

    private void parseConfig(String input, String def, int warnings, boolean expectedError) {
        Module test = ParserUtils.parseMainModuleOuterSyntax(def, Source.apply("generated by RuleGrammarTest"), "TEST");
        ParseInModule parser = RuleGrammarGenerator.getCombinedGrammar(gen.getConfigGrammar(test), true);
        Tuple2<Either<Set<KEMException>, K>, Set<KEMException>> rule = parser.parseString(input, startSymbol, Source.apply("generated by RuleGrammarTest"));
        printout(rule, warnings, expectedError);
    }

    private void parseProgram(String input, String def, String startSymbol, int warnings, boolean expectedError) {
        Module test = ParserUtils.parseMainModuleOuterSyntax(def, Source.apply("generated by RuleGrammarTest"), "TEST");
        ParseInModule parser = RuleGrammarGenerator.getCombinedGrammar(gen.getProgramsGrammar(test), true);
        Tuple2<Either<Set<KEMException>, K>, Set<KEMException>> rule = parser.parseString(input, Sort(startSymbol), Source.apply("generated by RuleGrammarTest"));
        printout(rule, warnings, expectedError);
    }

    private void parseProgram(String input, String def, String startSymbol, int warnings, K expectedResult) {
        Module test = ParserUtils.parseMainModuleOuterSyntax(def, Source.apply("generated by RuleGrammarTest"), "TEST");
        ParseInModule parser = RuleGrammarGenerator.getCombinedGrammar(gen.getProgramsGrammar(test), true);
        Tuple2<Either<Set<KEMException>, K>, Set<KEMException>> rule = parser.parseString(input, Sort(startSymbol), Source.apply("generated by RuleGrammarTest"));
        printout(rule, warnings, false);
        Assert.assertEquals(expectedResult, rule._1().right().get());
    }

    private void printout(Tuple2<Either<Set<KEMException>, K>, Set<KEMException>> rule, int warnings, boolean expectedError) {
        if (false) { // true to print detailed results
            KExceptionManager kem = new KExceptionManager(new GlobalOptions(true, Warnings.ALL, true));
            if (rule._1().isLeft()) {
                for (KEMException x : rule._1().left().get()) {
                    kem.addKException(x.getKException());
                }
            } else {
                System.err.println("rule = " + rule._1().right().get());
            }
            for (KEMException x : rule._2()) {
                kem.addKException(x.getKException());
            }
            kem.print();
        }
        if (expectedError)
            Assert.assertTrue("Expected error here: ", rule._1().isLeft());
        else
            Assert.assertTrue("Expected no errors here: ", rule._1().isRight());
        Assert.assertEquals("Expected " + warnings + " warnings: ", warnings, rule._2().size());
    }

    // test proper associativity for rewrite, ~> and cast
    @Test
    public void test2() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= Exp \"+\" Exp [klabel('Plus), left] " +
                "| r\"[0-9]+\" [token] " +
                "syntax left 'Plus " +
                "endmodule";
        parseRule("1+2=>A:Exp~>{B}:>Exp", def, 0, false);
    }

    // test variable disambiguation when a variable is declared by the user
    @Test
    public void test3() {
        String def = "" +
                "module TEST " +
                "syntax Exps ::= Exp \",\" Exps [klabel('Exps)] " +
                "| Exp " +
                "syntax Exp ::= Id " +
                "syntax Stmt ::= \"val\" Exps \";\" Stmt [klabel('Decl)] " +
                "syntax {Sort} Sort ::= \"(\" Sort \")\" [bracket] " +
                "syntax KItem ::= (Id, Stmt) [klabel('tuple)] " +
                "syntax Id " +
                "syntax K " +
                "endmodule";
        parseRule("val X ; S:Stmt => (X, S)", def, 0, false);
    }

    // test variable disambiguation when all variables are being inferred
    @Test
    public void test4() {
        String def = "" +
                "module TEST " +
                "syntax Exps ::= Exp \",\" Exps [klabel('Exps)] " +
                "| Exp " +
                "syntax Exp ::= Id " +
                "syntax Stmt ::= \"val\" Exps \";\" Stmt [klabel('Decl)] " +
                "syntax {Sort} Sort ::= \"(\" Sort \")\" [bracket] " +
                "syntax KItem ::= (Id, Stmt) [klabel('tuple)] " +
                "syntax Id " +
                "syntax K " +
                "endmodule";
        parseRule("val X ; S => (X, S)", def, 0, false);
    }

    // test error reporting when + is non-associative
    @Test
    public void test5() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= Exp \"+\" Exp [klabel('Plus), non-assoc] " +
                "| r\"[0-9]+\" [token] " +
                "syntax non-assoc 'Plus " +
                "endmodule";
        parseRule("1+2+3", def, 0, true);
    }

    // test AmbFilter which should report ambiguities as errors
    @Test
    public void test6() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= Exp \"+\" Exp [klabel('Plus)] " +
                "| r\"[0-9]+\" [token] " +
                "endmodule";
        parseRule("1+2+3", def, 0, true);
    }

    // test error reporting when rewrite priority is not met
    @Test
    public void test7() {
        String def = "" +
                "module TEST " +
                "syntax A ::= \"foo\" A [klabel('foo)] " +
                "syntax B ::= \"bar\"   [klabel('bar)] " +
                "endmodule";
        parseRule("foo bar => X", def, 0, true);
    }

    // test prefer and avoid
    @Test
    public void test8() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= Exp \"+\" Exp [klabel('Plus), prefer] " +
                "| Exp \"*\" Exp [klabel('Mul)] " +
                "| r\"[0-9]+\" [token] " +
                "endmodule";
        parseRule("1+2*3", def, 0, false);
    }

    // test cells
    @Test
    public void test9() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= Exp \"+\" Exp [klabel('Plus), prefer] " +
                "| Exp \"*\" Exp [klabel('Mul)] " +
                "| r\"[0-9]+\" [token] " +
                "syntax K " +
                "syntax TopCell ::= \"<T>\" KCell StateCell \"</T>\" [klabel(<T>), cell] " +
                "syntax KCell ::= \"<k>\" K \"</k>\" [klabel(<k>), cell] " +
                "syntax StateCell ::= \"<state>\" K \"</state>\" [klabel(<state>), cell] " +
                "endmodule";
        parseRule("<T> <k>...1+2*3...</k> (<state> A => .::K ...</state> => .::Bag) ...</T>", def, 0, false);
    }

    // test rule cells
    @Test
    public void test10() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= Exp \",\" Exp [klabel('Plus), prefer] " +
                "| Exp \"*\" Exp [klabel('Mul)] " +
                "| r\"[0-9]+\" [token] " +
                "endmodule";
        parseRule("A::KLabel(B::K, C::K, D::K)", def, 0, false);
    }

    // test config cells
    @Test
    public void test11() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= Exp \"+\" Exp [klabel('Plus), prefer] " +
                "| Exp \"*\" Exp [klabel('Mul)] " +
                "| r\"[0-9]+\" [token] " +
                "syntax K " +
                "endmodule";
        parseConfig("<T multiplicity=\"*\"> <k> 1+2*3 </k> (<state> A => .::K </state> => .::Bag) </T>", def, 0, false);
    }

    // test variable disambiguation when all variables are being inferred
    @Test
    public void test12() {
        String def = "" +
                "module TEST " +
                "syntax Stmt ::= \"val\" Exp \";\" Stmt [klabel('Decl)] " +
                "syntax Exp " +
                "syntax Stmt " +
                "endmodule";
        parseRule("val _:Exp ; _", def, 0, false);
    }

    // test priority exceptions (requires and ensures)
    @Test
    public void test13() {
        String def = "" +
                "module TEST " +
                "endmodule";
        parseRule(".::K => .::K requires .::K", def, 0, false);
    }

    // test automatic follow restriction for terminals
    @Test
    public void test14() {
        String def = "" +
                "module TEST " +
                "syntax Stmt ::= Stmt Stmt [klabel('Stmt)] " +
                "syntax Exp ::= K \"==\" K [klabel('Eq)] " +
                "syntax Exp ::= K \":\" K [klabel('Colon)] " +
                "syntax K " +
                "syntax Exp ::= K \"==K\" K [klabel('EqK)] " +
                "syntax Exp ::= K \"?\" K \":\" K " +
                "endmodule";
        parseRule("A::K ==K A", def, 0, false);
        parseRule("A::K == K A", def, 0, true);
        parseRule("A:K", def, 0, false);
        parseRule("A: K", def, 0, false);
        parseRule("A:Stmt ?F : Stmt", def, 0, false);
        parseRule("A:Stmt ? F : Stmt", def, 0, false);
    }

    // test whitespace
    @Test
    public void test15() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= Divide(K, K) [klabel('Divide)] " +
                "syntax Exp ::= K \"/\" K [klabel('Div)] " +
                "syntax K " +
                "endmodule";
        parseRule("Divide(K1:K, K2:K) => K1:K / K2:K", def, 0, false);
    }

    // test lexical to RegexTerminal extractor
    @Test
    public void test16() {
        assertPatterns("", "#", "", "#");
        assertPatterns("abc", "#", "abc", "#");
        assertPatterns("(?<!abc)", "abc", "", "#");
        assertPatterns("(?<!abc)def", "abc", "def", "#");
        assertPatterns("(?<!abcdef", "#", "(?<!abcdef", "#");
        assertPatterns("(?!abc)", "#", "", "abc");
        assertPatterns("\\(?!abc)", "#", "\\(?!abc)", "#");
    }

    private static void assertPatterns(String original, String precede, String pattern, String follow) {
        RegexTerminal re1 = KILtoKORE.getRegexTerminal(original);
        Assert.assertEquals(precede, re1.precedeRegex());
        Assert.assertEquals(pattern, re1.regex());
        Assert.assertEquals(follow, re1.followRegex());
    }

    // test the new regex engine
    @Test
    public void test17() {
        Automaton a = new RegExp("[\\\"](([^\\\"\n\r\\\\])|([\\\\][nrtf\\\"\\\\])|([\\\\][x][0-9a-fA-F]{2})|([\\\\][u][0-9a-fA-F]{4})|([\\\\][U][0-9a-fA-F]{8}))*[\\\"]").toAutomaton();
        RunAutomaton ra = new RunAutomaton(a, false);
        Assert.assertTrue(ra.run("\"n\\\\\\\"\""));
    }

    // test unicode chars
    @Test
    public void test18() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= \"\u2022\" " +
                "endmodule";
        parseRule("\u2022 => .K", def, 0, false);
    }

    // test special priority
    @Test
    public void test19() {
        String def = "" +
                "module TEST " +
                "endmodule";
        parseRule("`foo`(`bar`(.KList) ~> .K , .KList)", def, 0, false);
        parseRule("`foo`(`bar`(.KList) => .K , .KList)", def, 0, false);
    }

    // test user lists
    @Test
    public void test20() {
        String def = "" +
                "module TEST " +
                "syntax Exp ::= Int \"(\" Ints  \")\" " +
                "syntax Exp ::= Int \"[\" Ints2 \"]\" " +
                "syntax Ints  ::=   List{Int, \",\"} " +
                "syntax Ints2 ::= NeList{Int, \".\"} " +
                "syntax Int ::= r\"[0-9]+\" [token] " +
                "endmodule";
        parseRule("0, 1, 2", def, 0, false);
        parseProgram("0, 1, 2", def, "Ints", 0, false);
        parseProgram("0()", def, "Exp", 0, false);
        parseProgram("0[]", def, "Exp", 0, true);
    }

    // test inference with overloading.
    // regression test for issue #1586
    @Test
    public void test21() {
        String def = "" +
                "module TEST " +
                "syntax A ::= B | \"a\" " +
                "syntax B ::= \"b\" " +
                "syntax TA ::= TB | t(A) [klabel(t)] " +
                "syntax TB ::= t(B) [klabel(t)] " +
                "endmodule";
        parseRule("t(Y) => .K", def, 0, false);
        parseRule("t(_) => .K", def, 0, false);
    }

    // test inference with complicated ambiguity
    // regression test for issue #1603
    @Test
    public void test22() {
        String def = "" +
                "module TEST\n" +
                "syntax T ::= \"wrap\" M [klabel(wrapM)] | \"wrap\" X [klabel(wrapX)]\n" +
                "syntax A\n" +
                "syntax B\n" +
                "syntax M ::= A | B\n" +
                "syntax N ::= A | B | X // make A and B both maximal in the intersection of N and M\n" +
                "syntax X\n" +
                "syntax KItem ::= label(N,T)\n" +
                "endmodule";
        parseRule("X => label(X,wrap X)", def, 0, true);
    }

    // automatic follow restriction should not be added for empty terminals
    // regression test for issue #1575
    @Test
    public void test23() {
        String def = "" +
                "module TEST " +
                "syntax Stmts ::= Stmt \"\" Stmts\n" +
                "                 | \".Stmts\"\n" +
                "  syntax Stmt  ::= \"a\"" +
                "endmodule";
        parseRule("a .Stmts", def, 0, false);
    }

    // Should not infer sort KList for variable in arity-1 application of a klabel
    @Test
    public void test24() {
        String def = "" +
                "module TEST\n" +
                "syntax A\n" +
                "syntax A ::= f(A) [klabel(l)]\n" +
                "endmodule";
        parseRule("l(_) => .K", def, 0,
                KApply(KLabel("#ruleNoConditions"),KApply(KLabel("#KRewrite"),
                        KApply(KLabel("#KApply"),
                            KToken("l",Sort("KLabel")), KApply(KLabel("#SemanticCastToK"), KToken("_",Sort("#KVariable")))),
                KApply(KLabel("#EmptyK"))
                )));
    }

    @Test
    public void testPriorityAssoc() throws Exception {
        String def = "module TEST " +
                "syntax Exp ::= Exp \"*\" Exp [left, klabel('Mul)] " +
                "> Exp \"+\" Exp [left, klabel('Plus)] " +
                "| r\"[0-9]+\" [token] " +
                "syntax left 'Plus " +
                "syntax left 'Mul " +
                "endmodule";
        parseProgram("1+2",   def, "Exp", 0, false);
        //System.out.println("out1 = " + out1);
        parseProgram("1+2*3", def, "Exp", 0, false);
        //System.out.println("out2 = " + out2);
        parseProgram("1+2+3", def, "Exp", 0, false);
        //System.out.println("out3 = " + out3);
    }

    // test default/custom layout
    @Test
    public void testLayout() {
        String defaultLayout = "" +
                "module TEST " +
                "syntax Int ::= Int \"+\" Int " +
                "syntax Int ::= r\"[0-9]+\" [token] " +
                "endmodule";
        parseProgram("0 + 3 // some text"   , defaultLayout, "Int", 0, false);
        parseProgram("0 + 3 /* some text */", defaultLayout, "Int", 0, false);
        parseProgram("0 /* some text */ + 3", defaultLayout, "Int", 0, false);
        parseProgram("0 + 3 ;; some text"   , defaultLayout, "Int", 0, true);

        String customLayout = "" +
                "module TEST " +
                "syntax #Layout ::= r\"(\\\\(;([^;]|(;+([^;\\\\)])))*;\\\\))\"" +
                                 "| r\"(;;[^\\\\n\\\\r]*)\"" +
                                 "| r\"([\\\\ \\\\n\\\\r\\\\t])\"" +
                "// -------------------------------------\n" +      // make sure standard layout still works in K defn
                "syntax Int ::= Int \"+\" Int " +
                "syntax Int ::= r\"[0-9]+\" [token] " +
                "endmodule";
        parseProgram("0 + 3 ;; some text"   , customLayout, "Int", 0, false);
        parseProgram("0 + 3 (; some text ;)", customLayout, "Int", 0, false);
        parseProgram("0 (; some text ;) + 3", customLayout, "Int", 0, false);
        parseProgram("0 + 3 // some text"   , customLayout, "Int", 0, true);
    }
}
