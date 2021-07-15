// Copyright (c) 2015-2019 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.kframework.RewriterResult;
import org.kframework.attributes.Att;
import org.kframework.backend.java.compile.KOREtoBackendKIL;
import org.kframework.backend.java.kil.BuiltinList;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.backend.java.kil.KList;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.util.HookProvider;
import org.kframework.backend.java.util.Profiler2;
import org.kframework.backend.java.util.RuleSourceUtil;
import org.kframework.backend.java.util.StateLog;
import org.kframework.builtin.KLabels;
import org.kframework.compile.ExpandMacros;
import org.kframework.compile.ResolveSemanticCasts;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KORE;
import org.kframework.kprove.KProveOptions;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.api.io.FileSystem;
import org.kframework.main.GlobalOptions;
import org.kframework.rewriter.Rewriter;
import org.kframework.rewriter.SearchType;
import org.kframework.unparser.KPrint;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.options.SMTOptions;
import scala.Function1;
import scala.Tuple2;
import scala.collection.JavaConversions;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.kframework.kore.KORE.*;

/**
 * Created by dwightguth on 5/6/15.
 */
public class InitializeRewriter implements Function<org.kframework.definition.Definition, Rewriter> {

    private final FileSystem fs;
    private final GlobalOptions globalOptions;
    private final KExceptionManager kem;
    private final SMTOptions smtOptions;
    private final Map<String, MethodHandle> hookProvider;
    private final List<String> transitions;
    private final KRunOptions krunOptions;
    private final KProveOptions kproveOptions;
    private final FileUtil files;
    private final InitializeDefinition initializeDefinition;
    private static final int NEGATIVE_VALUE = -1;
    private final KompileOptions kompileOptions;
    private final KPrint kprint;
    private final Profiler2 profiler;
    private final JavaExecutionOptions javaExecutionOptions;

    @Inject
    public InitializeRewriter(
            FileSystem fs,
            GlobalOptions globalOptions,
            KExceptionManager kem,
            SMTOptions smtOptions,
            KRunOptions krunOptions,
            KProveOptions kproveOptions,
            KompileOptions kompileOptions,
            JavaExecutionOptions javaExecutionOptions,
            FileUtil files,
            InitializeDefinition initializeDefinition,
            KPrint kprint,
            Profiler2 profiler) {
        this.fs = fs;
        this.globalOptions = globalOptions;
        this.kem = kem;
        this.smtOptions = smtOptions;
        this.hookProvider = HookProvider.get(kem);
        this.transitions = kompileOptions.transition;
        this.krunOptions = krunOptions;
        this.kproveOptions = kproveOptions;
        this.kompileOptions = kompileOptions;
        this.javaExecutionOptions = javaExecutionOptions;
        this.files = files;
        this.initializeDefinition = initializeDefinition;
        this.kprint = kprint;
        this.profiler = profiler;
        chainOptions();
    }

    public void chainOptions() {
        javaExecutionOptions.logRules |= javaExecutionOptions.logImplications;
        javaExecutionOptions.debugZ3Queries |= javaExecutionOptions.debugFormulas;
        javaExecutionOptions.debugZ3 |= javaExecutionOptions.debugZ3Queries;
        javaExecutionOptions.logBasic |= javaExecutionOptions.log || javaExecutionOptions.debugZ3;
        globalOptions.verbose |= javaExecutionOptions.logBasic;
    }

    @Override
    public synchronized Rewriter apply(org.kframework.definition.Definition def) {
        GlobalContext initializingContext = newGlobalContext(def, Stage.INITIALIZING);
        Definition definition = initializeDefinition.invoke(def.mainModule(), kem, initializingContext);
        GlobalContext rewritingContext = newGlobalContext(def, Stage.REWRITING);
        rewritingContext.setDefinition(definition);

        return new SymbolicRewriterGlue(definition, def.mainModule(), rewritingContext);
    }

    public GlobalContext newGlobalContext(org.kframework.definition.Definition def, Stage stage) {
        return new GlobalContext(fs, globalOptions, krunOptions, kproveOptions, javaExecutionOptions, kem, smtOptions,
                hookProvider, files, stage, profiler, kprint, def);
    }

    public static Rule transformFunction(Function<K, K> f, Rule r) {
        return Rule.apply(f.apply(r.body()), f.apply(r.requires()), f.apply(r.ensures()), r.att());
    }

    public static Rule transform(Function1<K, K> f, Rule r) {
        return Rule.apply(f.apply(r.body()), f.apply(r.requires()), f.apply(r.ensures()), r.att());
    }

    public static org.kframework.backend.java.kil.Rule convertToJavaPattern(KOREtoBackendKIL converter, Rule pattern) {
        return pattern == null ? null : converter.convert(
                null, transformFunction(JavaBackend::convertKSeqToKApply, pattern));
    }

    public class SymbolicRewriterGlue implements Rewriter {

        public final Definition definition;
        public final Module module;
        private final long initCounterValue;
        public final GlobalContext rewritingContext;

        public SymbolicRewriterGlue(Definition definition, Module module, GlobalContext rewritingContext) {
            this.definition = definition;
            this.module = module;
            this.initCounterValue = 0L;
            this.rewritingContext = rewritingContext;
        }

        @Override
        public RewriterResult execute(K k, Optional<Integer> depth) {
            rewritingContext.stateLog.open("execute-" + Integer.toString(Math.abs(k.hashCode())));
            rewritingContext.setExecutionPhase(false);
            TermContext termContext = TermContext.builder(rewritingContext).freshCounter(initCounterValue).build();
            KOREtoBackendKIL converter = new KOREtoBackendKIL(module, definition, termContext.global(), false);
            ResolveSemanticCasts resolveCasts = new ResolveSemanticCasts(true);
            ExpandMacros macroExpander = ExpandMacros.forNonSentences(module, files, kompileOptions, false);
            termContext.setKOREtoBackendKILConverter(converter);
            Term backendKil = converter.convert(macroExpander.expand(resolveCasts.resolve(k))).evaluate(termContext);
            rewritingContext.stateLog.log(StateLog.LogEvent.EXECINIT, backendKil, KApply(KLabels.ML_TRUE));
            rewritingContext.setExecutionPhase(true);
            SymbolicRewriter rewriter = new SymbolicRewriter(rewritingContext, transitions, converter);
            if (javaExecutionOptions.skipInvokingBackend) {
                System.err.println("Skipping invoking the backend!");
                return new RewriterResult(Optional.empty(), Optional.of(0), KORE.KApply(KLabels.ML_TRUE));
            }
            RewriterResult result = rewriter.rewrite(new ConstrainedTerm(backendKil, termContext), depth.orElse(-1));
            rewritingContext.stateLog.close();
            return result;
        }

        @Override
        public K match(K k, Rule rule) {
            return search(k, Optional.of(0), Optional.empty(), rule, SearchType.STAR);
        }


        @Override
        public K search(K initialConfiguration, Optional<Integer> depth, Optional<Integer> bound, Rule pattern, SearchType searchType) {
            rewritingContext.stateLog.open("search-" + Integer.toString(Math.abs(initialConfiguration.hashCode())));
            TermContext termContext = TermContext.builder(rewritingContext).freshCounter(initCounterValue).build();
            KOREtoBackendKIL converter = new KOREtoBackendKIL(module, definition, termContext.global(), false);
            ResolveSemanticCasts resolveCasts = new ResolveSemanticCasts(true);
            ExpandMacros macroExpander = ExpandMacros.forNonSentences(module, files, kompileOptions, false);
            termContext.setKOREtoBackendKILConverter(converter);
            Term javaTerm = converter.convert(macroExpander.expand(resolveCasts.resolve(initialConfiguration))).evaluate(termContext);
            rewritingContext.stateLog.log(StateLog.LogEvent.SEARCHINIT, javaTerm, KApply(KLabels.ML_TRUE));
            org.kframework.backend.java.kil.Rule javaPattern = convertToJavaPattern(converter, pattern);
            SymbolicRewriter rewriter = new SymbolicRewriter(rewritingContext, transitions, converter);
            if (javaExecutionOptions.skipInvokingBackend) {
                System.err.println("Skipping invoking the backend!");
                return KORE.KApply(KLabels.ML_TRUE);
            }
            K result = rewriter.search(javaTerm, javaPattern, bound.orElse(NEGATIVE_VALUE), depth.orElse(NEGATIVE_VALUE), searchType, termContext);
            rewritingContext.stateLog.log(StateLog.LogEvent.SEARCHREACH, result);
            rewritingContext.stateLog.close();
            return result;
        }


        public Tuple2<RewriterResult, K> executeAndMatch(K k, Optional<Integer> depth, Rule rule) {
            RewriterResult res = execute(k, depth);
            return Tuple2.apply(res, match(res.k(), rule));
        }

        @Override
        public RewriterResult prove(Module specModule, @Nullable Rule boundaryPattern, Boolean reuseDef) {
            rewritingContext.stateLog.open("prove-" + Integer.toString(Math.abs(specModule.hashCode())));
            rewritingContext.setExecutionPhase(false);

            ProcessProofRules processProofRules = new ProcessProofRules(specModule);
            KOREtoBackendKIL converter = processProofRules.converter;
            TermContext termContext = processProofRules.termContext;
            org.kframework.backend.java.kil.Rule javaBoundaryPattern = convertToJavaPattern(converter, boundaryPattern);

            // rename all variables to avoid any potential conflicts with the rules in the semantics
            List<org.kframework.backend.java.kil.Rule> proofObligationRules = processProofRules.specRules.stream()
                    .map(org.kframework.backend.java.kil.Rule::renameVariables)
                    .collect(Collectors.toList());

            SymbolicRewriter rewriter = new SymbolicRewriter(rewritingContext, transitions, converter);

            if (javaExecutionOptions.skipInvokingBackend) {
                System.err.println("Skipping invoking the backend!");
                return new RewriterResult(Optional.empty(), Optional.of(0), KORE.KApply(KLabels.ML_TRUE));
            }

            rewritingContext.setExecutionPhase(true);
            List<ConstrainedTerm> proofResults = proofObligationRules.stream()
                    .filter(r -> !r.att().contains(Att.TRUSTED()))
                    .map(r -> {
                        //Build LHS with fully evaluated constraint. Then expand patterns.
                        ConjunctiveFormula constraint = processProofRules.getEvaluatedConstraint(r);
                        ConstrainedTerm lhs = new ConstrainedTerm(r.leftHandSide(), constraint, termContext);
                        termContext.setTopConstraint(constraint);
                        lhs = lhs.expandPatterns(true);

                        //Build RHS with fully evaluated ensures. RHS term is already evaluated.
                        ConjunctiveFormula ensures = (ConjunctiveFormula) processProofRules.evaluate(
                                ConjunctiveFormula.of(termContext.global()).addAll(r.ensures()), constraint, termContext);
                        ConstrainedTerm rhs = new ConstrainedTerm(
                                r.rightHandSide(), ensures, TermContext.builder(termContext.global()).build());

                        termContext.setInitialLhsVariables(lhs.variableSet());
                        termContext.setTopConstraint(null);
                        if (rewritingContext.javaExecutionOptions.cacheFunctionsOptimized) {
                            rewritingContext.functionCache.clear();
                        }
                        rewritingContext.stateLog.log(StateLog.LogEvent.REACHINIT,   lhs.term(), lhs.constraint());
                        rewritingContext.stateLog.log(StateLog.LogEvent.REACHTARGET, rhs.term(), rhs.constraint());
                        return rewriter.proveRule(r, lhs, rhs, kem, javaBoundaryPattern);
                    })
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            for (ConstrainedTerm res: proofResults) {
                rewritingContext.stateLog.log(StateLog.LogEvent.REACHUNPROVED, res.term(), res.constraint());
            }

            K result = proofResults.stream()
                    .map(constrainedTerm -> (K) constrainedTerm.term())
                    .reduce(((k1, k2) -> KORE.KApply(KLabels.ML_AND, k1, k2))).orElse(KORE.KApply(KLabels.ML_TRUE));
            int exit;
            if (result instanceof KApply) {
                KApply kapp = (KApply) result;
                exit = kapp.klabel().name().equals(KLabels.ML_TRUE.name()) ? 0 : 1;
            } else {
                exit = 1;
            }
            rewritingContext.stateLog.close();
            return new RewriterResult(Optional.empty(), Optional.of(exit), result);
        }

        @Override
        public RewriterResult bmc(Module mod) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equivalence(Rewriter firstDef, Rewriter secondDef, Module firstSpec, Module secondSpec) {
            if (!(firstDef instanceof SymbolicRewriterGlue) || !(secondDef instanceof SymbolicRewriterGlue)) {
                throw KEMException.criticalError("All three definitions must be compiled with --backend java in order" +
                        "to check program equivalence.");
            }
            SymbolicRewriterGlue glue1 = (SymbolicRewriterGlue) firstDef;
            SymbolicRewriterGlue glue2 = (SymbolicRewriterGlue) secondDef;
            EquivalenceState state1 = new EquivalenceState(glue1, firstSpec);
            EquivalenceState state2 = new EquivalenceState(glue2, secondSpec);

            java.util.List<ConjunctiveFormula> startEnsures = new ArrayList<>();
            assert state1.startEnsures.size() == state2.startEnsures.size();
            for (int i = 0; i < state1.startEnsures.size(); i++) {
                startEnsures.add(getConjunctiveFormula(state1.startEnsures.get(i), state2.startEnsures.get(i), rewritingContext));
            }

            java.util.List<ConjunctiveFormula> targetEnsures = new ArrayList<>();
            assert state1.targetEnsures.size() == state2.targetEnsures.size();
            for (int i = 0; i < state1.targetEnsures.size(); i++) {
                targetEnsures.add(getConjunctiveFormula(state1.targetEnsures.get(i), state2.targetEnsures.get(i), rewritingContext));
            }

            return EquivChecker.equiv(
                    state1.startSyncNodes, state2.startSyncNodes,
                    state1.targetSyncNodes, state2.targetSyncNodes,
                    startEnsures, //info1.startEnsures, info2.startEnsures,
                    targetEnsures, //info1.targetEnsures, info2.targetEnsures,
                    state1.trusted, state2.trusted,
                    state1.rewriter, state2.rewriter);
        }

        private ConjunctiveFormula getConjunctiveFormula(ConjunctiveFormula e1, ConjunctiveFormula e2, GlobalContext global) {

            ConjunctiveFormula ensure = ConjunctiveFormula.of(global);

            ImmutableList<Term> l1 = getChildren(e1);
            ImmutableList<Term> l2 = getChildren(e2);

            assert l1.size() == l2.size();
            for (int j = 0; j < l1.size(); j++) {
                // TODO: make it better
                ensure = ensure.add(
                        ((KList) ((KItem) l1.get(j)).kList()).getContents().get(0),
                        ((KList) ((KItem) l2.get(j)).kList()).getContents().get(0));
            }

            return ensure;
        }

        private ImmutableList<Term> getChildren(ConjunctiveFormula e) {
            // TODO: make it better
            assert e.equalities().size() == 1;
            assert e.equalities().get(0).leftHandSide() instanceof KItem;
            assert ((KItem) e.equalities().get(0).leftHandSide()).kLabel() instanceof KLabelConstant;
            assert ((KLabelConstant) ((KItem) e.equalities().get(0).leftHandSide()).kLabel()).label().equals("vars");
            assert ((KItem) e.equalities().get(0).leftHandSide()).kList() instanceof KList;
            assert ((KList) ((KItem) e.equalities().get(0).leftHandSide()).kList()).getContents().size() == 1;
            assert ((KList) ((KItem) e.equalities().get(0).leftHandSide()).kList()).getContents().get(0) instanceof BuiltinList;

            return ((BuiltinList) ((KList) ((KItem) e.equalities().get(0).leftHandSide()).kList()).getContents().get(0)).children;
        }

        private List<String> getTransitions() {
            return transitions;
        }

        private class ProcessProofRules {
            private final KOREtoBackendKIL converter;
            private final TermContext termContext;
            private final List<org.kframework.backend.java.kil.Rule> specRules;

            public ProcessProofRules(Module specModule) {
                converter = new KOREtoBackendKIL(module, definition, rewritingContext, false);
                termContext = TermContext.builder(rewritingContext).freshCounter(initCounterValue).build();
                termContext.setKOREtoBackendKILConverter(converter);
                specRules = definition.addKoreClaims(specModule, converter, this::evaluateRule);
            }

            private org.kframework.backend.java.kil.Rule evaluateRule(org.kframework.backend.java.kil.Rule rule) {
                if (rewritingContext.javaExecutionOptions.logBasic) {
                    System.err.println("Pre-processing rule:");
                    RuleSourceUtil.printRuleAndSource(rule);
                    System.err.println("==================================");
                }
                ConjunctiveFormula constraint = getEvaluatedConstraint(rule);
                if (constraint.isFalseExtended()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Rule requires clause evaluates to false:\n");
                    RuleSourceUtil.appendRuleAndSource(rule, sb);
                    throw KEMException.criticalError(sb.toString());
                }

                return new org.kframework.backend.java.kil.Rule(
                        rule.label(),
                        evaluate(rule.leftHandSide(), constraint, termContext),
                        evaluate(rule.rightHandSide(), constraint, termContext),
                        rule.requires(),
                        rule.ensures(),
                        rule.freshConstants(),
                        rule.freshVariables(),
                        rule.lookups(),
                        rule.att());
            }

            private ConjunctiveFormula getEvaluatedConstraint(org.kframework.backend.java.kil.Rule rule) {
                termContext.setTopConstraint(null);
                //We need this ConsTerm only to evaluate the constraint. That's why we use an empty first argument.
                ConstrainedTerm constraintHolder = new ConstrainedTerm(
                        ConjunctiveFormula.of(rewritingContext),
                        rule.getRequires(),
                        termContext).expandPatterns(true);

                ConjunctiveFormula constraint = constraintHolder.constraint();
                termContext.setTopConstraint(constraint);
                //simplify the constraint in its own context, to force full evaluation of terms.
                constraint = constraint.simplify(termContext);
                return constraint;
            }

            private Term evaluate(Term term, ConjunctiveFormula constraint, TermContext context) {
                context.setTopConstraint(constraint);
                return term.fullSubstituteAndEvaluate(context);
            }
        }
    }

    static class EquivalenceState {
        final List<ConstrainedTerm> startSyncNodes;
        final List<ConstrainedTerm> targetSyncNodes;
        final List<ConjunctiveFormula> startEnsures;
        final List<ConjunctiveFormula> targetEnsures;
        final List<Boolean> trusted;
        final SymbolicRewriter rewriter;

        EquivalenceState(SymbolicRewriterGlue glue, Module specModule) {
            SymbolicRewriterGlue.ProcessProofRules processProofRules = glue.new ProcessProofRules(specModule);
            List<org.kframework.backend.java.kil.Rule> specRules = processProofRules.specRules;
            java.util.Collections.sort(specRules, new Comparator<org.kframework.backend.java.kil.Rule>() {
                @Override
                public int compare(org.kframework.backend.java.kil.Rule rule1, org.kframework.backend.java.kil.Rule rule2) {
                    return Integer.compare(rule1.getLocation().startLine(), rule2.getLocation().startLine());
                }
            });

            // rename all variables to avoid any potential conflicts with the rules in the semantics
            List<org.kframework.backend.java.kil.Rule> proofObligationRules = specRules.stream()
                    .map(org.kframework.backend.java.kil.Rule::renameVariables)
                    .collect(Collectors.toList());

            //// prove spec rules
            rewriter = new SymbolicRewriter(glue.rewritingContext, glue.getTransitions(), processProofRules.converter);

            startSyncNodes = new ArrayList<>();
            targetSyncNodes = new ArrayList<>();
            startEnsures = new ArrayList<>();
            targetEnsures = new ArrayList<>();
            trusted = new ArrayList<>();

            for (int i = 0; i < specRules.size(); i++) {
                org.kframework.backend.java.kil.Rule startRule = specRules.get(i);
                org.kframework.backend.java.kil.Rule targetRule = proofObligationRules.get(i);

                // assert rule1.getEnsures().equals(rule2.getEnsures());

                // TODO: split requires for each side and for both sides in createLhsPattern
                startSyncNodes.add(startRule.createLhsPattern(processProofRules.termContext));
                targetSyncNodes.add(targetRule.createLhsPattern(processProofRules.termContext));
                startEnsures.add(startRule.getEnsures(processProofRules.termContext.global()));
                targetEnsures.add(targetRule.getEnsures(processProofRules.termContext.global()));

                trusted.add(startRule.att().contains(Att.TRUSTED()));
            }
        }
    }

    public static class InitializeDefinition {
        public Definition invoke(Module module, KExceptionManager kem, GlobalContext global) {
            Definition definition = new Definition(module, kem);
            global.setDefinition(definition);

            JavaConversions.setAsJavaSet(module.attributesFor().keySet()).stream()
                    .map(l -> KLabelConstant.of(l, definition))
                    //sorting to eliminate non-determinism in logs
                    .sorted(Comparator.comparing(KLabelConstant::label))
                    .forEach(definition::addKLabel);
            definition.addKoreRules(module, global);
            return definition;
        }
    }
}
