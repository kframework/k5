// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore.generator;

import org.apache.commons.collections4.trie.PatriciaTrie;
import org.apache.commons.io.FileUtils;
import org.kframework.Collections;
import org.kframework.attributes.Att;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.definition.NonTerminal;
import org.kframework.definition.Production;
import org.kframework.definition.ProductionItem;
import org.kframework.definition.RegexTerminal;
import org.kframework.definition.Sentence;
import org.kframework.definition.SyntaxSort;
import org.kframework.definition.Terminal;
import org.kframework.kil.Attribute;
import org.kframework.kore.Sort;
import org.kframework.kore.convertors.KOREtoKIL;
import org.kframework.utils.StringUtil;
import scala.collection.immutable.List;
import scala.collection.immutable.Seq;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.Att;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

/**
 * Generator for rule and ground parsers.
 * Takes as input a reference to a definition containing all the base syntax of K
 * and uses it to generate a grammar by connecting all users sorts in a lattice with
 * the top sort KItem#Top and the bottom sort KItem#Bottom.
 * <p/>
 * The instances of the non-terminal KItem is renamed in KItem#Top if found in the right
 * hand side of a production, and into KItem#Bottom if found in the left hand side.
 */
public class RuleGrammarGenerator {

    private final Definition baseK;
    private static final Set<Sort> kSorts = new HashSet<>();

    static {
        kSorts.add(Sorts.KBott());
        kSorts.add(Sorts.K());
        kSorts.add(Sorts.KLabel());
        kSorts.add(Sorts.KList());
        kSorts.add(Sorts.KItem());
        kSorts.add(Sort("RuleContent"));
        kSorts.add(Sort("KConfigVar"));
        kSorts.add(Sorts.KString());
    }

    public Set<Sort> kSorts() {
        return java.util.Collections.unmodifiableSet(kSorts);
    }
    /// modules that have a meaning:
    public static final String RULE_CELLS = "RULE-CELLS";
    public static final String CONFIG_CELLS = "CONFIG-CELLS";
    public static final String K = "K";
    public static final String AUTO_CASTS = "AUTO-CASTS";
    public static final String K_TOP_SORT = "K-TOP-SORT";
    public static final String K_BOTTOM_SORT = "K-BOTTOM-SORT";
    public static final String AUTO_FOLLOW = "AUTO-FOLLOW";

    public RuleGrammarGenerator(Definition baseK) {
        this.baseK = baseK;
    }

    private Set<Module> renameKItem2Bottom(Set<Module> def) {
        // TODO: do renaming of KItem and K in the LHS to KBott?
        return def;
    }

    public Module getRuleGrammar(Module mod) {
        // import RULE-CELLS in order to parse cells specific to rules
        Module newM = new Module(mod.name() + "-" + RULE_CELLS, Set(mod, baseK.getModule(K).get(), baseK.getModule(RULE_CELLS).get()), Set(), null);
        return getCombinedGrammar(newM);
    }

    public Module getConfigGrammar(Module mod) {
        // import CONFIG-CELLS in order to parse cells specific to configurations
        Module newM = new Module(mod.name() + "-" + CONFIG_CELLS, Set(mod, baseK.getModule(K).get(), baseK.getModule(CONFIG_CELLS).get()), Set(), null);
        return getCombinedGrammar(newM);
    }

    public Module getProgramsGrammar(Module mod) {
        Set<Sentence> prods = new HashSet<>();
        // if no start symbol has been defined in the configuration, then use K
        for (Sort srt : iterable(mod.definedSorts())) {
            if (!kSorts.contains(srt) && !mod.listSorts().contains(srt)) {
                // K ::= Sort
                prods.add(Production(Sorts.K(), Seq(NonTerminal(srt)), Att()));
            }
        }
        // find all productions annotated with 'userList'
        // expecting to always find 3 of them of the form:
        // Es ::= Es "," Es [right, userList, klabel(...)]
        // Es ::= ".Es"     [userList, klabel(...)]
        // Es ::= E         [userList]
        // for each triple, generate a new pattern which works better for parsing lists in programs.
        Module newM = new Module(mod.name() + "-FOR-PROGRAMS", Set(mod), immutable(prods), null);
        Map<Boolean, java.util.List<Sentence>> separatedProds
                = mutable(newM.sentences()).stream().collect(Collectors.groupingBy(p -> p instanceof Production && p.att().contains(KOREtoKIL.USER_LIST_ATTRIBUTE)));
        Map<String, java.util.List<Sentence>> listsMap = separatedProds.get(true).stream().collect(Collectors.groupingBy(s -> ((Production) s).sort().name()));

        java.util.Set<Sentence> res = new HashSet<>();
        for (Map.Entry<String, java.util.List<Sentence>> x : listsMap.entrySet()) {
            String sort = x.getKey();
            String childSort = null;
            String separator = null;
            String terminatorKLabel = null;
            String klabel = null;
            boolean nonEmpty = false;
            org.kframework.attributes.Att attrs = null;
            assert x.getValue().size() == 3;
            for (Sentence s : x.getValue()) {
                Production p = (Production) s;
                if (p.isSyntacticSubsort()) {
                    childSort = ((NonTerminal) p.items().head()).sort().name();
                } else if (p.items().size() == 3) {
                    Terminal t = (Terminal) p.items().tail().head();
                    separator = t.value();
                    klabel = p.klabel().get().name();
                    attrs = p.att();
                    nonEmpty = attrs.get(KOREtoKIL.USER_LIST_ATTRIBUTE).get().equals("+");
                } else if (p.items().size() == 1 && p.items().head() instanceof Terminal) {
                    terminatorKLabel = p.klabel().get().name();
                } else
                    throw new AssertionError("Didn't expect this type of production when recognizing userList patterns!");
            }
            assert attrs != null; assert klabel != null; assert terminatorKLabel != null; assert childSort != null;
            org.kframework.definition.Production prod1, prod2, prod3, prod4, prod5;
            // Es#Terminator ::= "" [klabel('.Es)]
            prod1 = Production(terminatorKLabel, Sort(sort + "#Terminator"), Seq(Terminal("")),
                    attrs.add("klabel", terminatorKLabel));
            // Ne#Es ::= E "," Ne#Es [klabel('_,_)]
            prod2 = Production(klabel, Sort("Ne#" + sort),
                    Seq(NonTerminal(Sort(childSort)), Terminal(separator), NonTerminal(Sort("Ne#" + sort))),
                    attrs.add("klabel", klabel));
            // Ne#Es ::= E Es#Terminator [klabel('_,_)]
            prod3 = Production(klabel, Sort("Ne#" + sort),
                    Seq(NonTerminal(Sort(childSort)), NonTerminal(Sort(sort + "#Terminator"))),
                    attrs.add("klabel", klabel));
            // Es ::= Ne#Es
            prod4 = Production(Sort(sort), Seq(NonTerminal(Sort("Ne#" + sort))));
            // Es ::= Es#Terminator // if the list is *
            prod5 = Production(Sort(sort), Seq(NonTerminal(Sort(sort + "#Terminator"))));

            res.add(prod1);
            res.add(prod2);
            res.add(prod3);
            res.add(prod4);
            res.add(SyntaxSort(Sort(sort + "#Terminator")));
            res.add(SyntaxSort(Sort("Ne#" + sort)));
            if (!nonEmpty) {
                res.add(prod5);
            }
        }
        res.addAll(separatedProds.get(false));

        Module newM2 = new Module(mod.name() + "-FOR-PROGRAMS", Set(), immutable(res), null);
        return newM2;
    }

    /**
     * Create the rule parser for the given module.
     * It creates a module which includes the given module and the base K module given to the
     * constructor. The new module contains syntax declaration for Casts and the diamond
     * which connects the user concrete syntax with K syntax.
     *
     * @param mod module for which to create the parser.
     * @return parser which applies disambiguation filters by default.
     */
    public Module getCombinedGrammar(Module mod) {
        Set<Sentence> prods = new HashSet<>();

        if (baseK.getModule(AUTO_CASTS).isDefined() && mod.importedModules().contains(baseK.getModule(AUTO_CASTS).get())) { // create the diamond
            for (Sort srt : iterable(mod.definedSorts())) {
                if (!kSorts.contains(srt) && !srt.name().startsWith("#")) {
                    // K ::= K "::Sort" | K ":Sort" | K "<:Sort" | K ":>Sort"
                    prods.addAll(makeCasts(Sorts.KBott(), Sorts.K(), srt));
                }
            }
            prods.addAll(makeCasts(Sorts.KLabel(), Sorts.KLabel(), Sorts.KLabel()));
            prods.addAll(makeCasts(Sorts.KList(), Sorts.KList(), Sorts.KList()));
            prods.addAll(makeCasts(Sorts.KBott(), Sorts.K(), Sorts.KItem()));
            prods.addAll(makeCasts(Sorts.KBott(), Sorts.K(), Sorts.K()));
        }
        if (baseK.getModule(K_TOP_SORT).isDefined() && mod.importedModules().contains(baseK.getModule(K_TOP_SORT).get())) { // create the diamond
            for (Sort srt : iterable(mod.definedSorts())) {
                if (!kSorts.contains(srt) && !srt.name().startsWith("#")) {
                    // K ::= Sort
                    prods.add(Production(Sorts.K(), Seq(NonTerminal(srt)), Att()));
                }
            }
        }

        if (baseK.getModule(K_BOTTOM_SORT).isDefined() && mod.importedModules().contains(baseK.getModule(K_BOTTOM_SORT).get())) { // create the diamond
            for (Sort srt : iterable(mod.definedSorts())) {
                if (!kSorts.contains(srt) && !srt.name().startsWith("#")) {
                    // Sort ::= KBott
                    prods.add(Production(srt, Seq(NonTerminal(Sorts.KBott())), Att()));
                }
            }
        }
        scala.collection.immutable.Set<Sentence> prods2;
        if (baseK.getModule(RULE_CELLS).isDefined() && mod.importedModules().contains(baseK.getModule(RULE_CELLS).get())) { // prepare cell productions for rule parsing
            prods2 = Stream.concat(prods.stream(), stream(mod.sentences())).flatMap(s -> {
                if (s instanceof Production && (s.att().contains("cell"))) {
                    Production p = (Production) s;
                    // assuming that productions tagged with 'cell' start and end with terminals, and only have non-terminals in the middle
                    assert p.items().head() instanceof Terminal || p.items().head() instanceof RegexTerminal;
                    assert p.items().last() instanceof Terminal || p.items().last() instanceof RegexTerminal;
                    Seq<ProductionItem> pi = Seq(p.items().head(), NonTerminal(Sort("#OptionalDots")), NonTerminal(Sort("K")), NonTerminal(Sort("#OptionalDots")), p.items().last());
                    Production p1 = Production(p.klabel().get().name(), Sort("Cell"), pi, p.att());
                    Production p2 = Production(Sort("Cell"), Seq(NonTerminal(p.sort())));
                    return Stream.of(p1, p2);
                }
                return Stream.of(s);
            }).collect(Collections.toSet());
        } else
            prods2 = Stream.concat(prods.stream(), stream(mod.sentences())).collect(Collections.toSet());

        if (baseK.getModule(AUTO_FOLLOW).isDefined() && mod.importedModules().contains(baseK.getModule(AUTO_FOLLOW).get())) {
            Object PRESENT = new Object();
            PatriciaTrie<Object> terminals = new PatriciaTrie<>(); // collect all terminals so we can do automatic follow restriction for prefix terminals
            stream(prods2).filter(sent -> sent instanceof Production).forEach(p -> stream(((Production) p).items()).forEach(i -> {
                if (i instanceof Terminal) terminals.put(((Terminal) i).value(), PRESENT);
            }));
            prods2 = stream(prods2).map(s -> {
                if (s instanceof Production) {
                    Production p = (Production) s;
                    if (p.sort().name().startsWith("#"))
                        return p; // don't do anything for such productions since they are advanced features
                    // rewrite productions to contin follow restrictions for prefix terminals
                    // example _==_ and _==K_ can produce ambiguities. Rewrite the first into _(==(?![K])_
                    // this also takes care of casting and productions that have ":"
                    List<ProductionItem> items = stream(p.items()).map(pi -> {
                        if (pi instanceof Terminal) {
                            Terminal t = (Terminal) pi;
                            Set<String> follow = new HashSet<>();
                            terminals.prefixMap(t.value()).keySet().stream().filter(biggerString -> !t.value().equals(biggerString))
                                    .forEach(biggerString -> {
                                        String ending = biggerString.substring(t.value().length());
                                        follow.add(ending);
                                    });
                            // add follow restrictions for the characters that might produce ambiguities
                            if (!follow.isEmpty()) {
                                String restriction = follow.stream().map(StringUtil::escapeAutomatonRegex).reduce((s1, s2) -> "(" + s1 + ")|(" + s2 + ")").get();
                                return Terminal.apply(t.value(), restriction);
                            }
                        }
                        return pi;
                    }).collect(Collections.toList());
                    if (p.klabel().isDefined())
                        p = Production(p.klabel().get().name(), p.sort(), Seq(items), p.att());
                    else
                        p = Production(p.sort(), Seq(items), p.att());
                    return p;
                }
                return s;
            }).collect(Collections.toSet());
        }

        Module newM = new Module(mod.name() + "-PARSER", Set(), prods2, mod.att());
        return newM;
    }

    private Set<Sentence> makeCasts(Sort outerSort, Sort innerSort, Sort castSort) {
        Set<Sentence> prods = new HashSet<>();
        Att attrs1 = Att().add(Attribute.SORT_KEY, castSort.name());
        prods.add(Production("#SyntacticCast", castSort, Seq(NonTerminal(castSort), Terminal("::" + castSort.name())), attrs1));
        prods.add(Production("#SemanticCastTo" + castSort.name(),  castSort, Seq(NonTerminal(castSort), Terminal(":"  + castSort.name())), attrs1));
        prods.add(Production("#InnerCast",     outerSort, Seq(NonTerminal(castSort), Terminal("<:" + castSort.name())), attrs1));
        prods.add(Production("#OuterCast",     castSort, Seq(NonTerminal(innerSort), Terminal(":>" + castSort.name())), attrs1));
        return prods;
    }
}
