// Copyright (c) 2014-2019 K Team. All Rights Reserved.
package org.kframework.backend.haskell;

import com.google.inject.Inject;
import org.kframework.attributes.Att;
import org.kframework.backend.kore.KoreBackend;
import org.kframework.backend.kore.ModuleToKORE;
import org.kframework.builtin.KLabels;
import org.kframework.compile.AddSortInjections;
import org.kframework.compile.ExpandMacros;
import org.kframework.compile.RewriteToTop;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.kbmc.KBMCOptions;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.kore.KORE;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;
import org.kframework.kore.TransformK;
import org.kframework.kore.VisitK;
import org.kframework.kprove.KProveOptions;
import org.kframework.krun.RunProcess;
import org.kframework.main.GlobalOptions;
import org.kframework.main.Main;
import org.kframework.main.Tool;
import org.kframework.parser.KoreParser;
import org.kframework.parser.kore.parser.ParseError;
import org.kframework.RewriterResult;
import org.kframework.rewriter.Rewriter;
import org.kframework.rewriter.SearchType;
import org.kframework.unparser.KPrint;
import org.kframework.unparser.OutputModes;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.DefinitionScoped;
import org.kframework.utils.inject.RequestScoped;
import org.kframework.utils.options.BackendOptions;
import org.kframework.utils.options.SMTOptions;

import scala.Tuple2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import static org.kframework.builtin.BooleanUtils.*;

@RequestScoped
public class HaskellRewriter implements Function<Definition, Rewriter> {

    private final GlobalOptions globalOptions;
    private final SMTOptions smtOptions;
    private final KompileOptions kompileOptions;
    private final KProveOptions kProveOptions;
    private final KBMCOptions kbmcOptions;
    private final HaskellKRunOptions haskellKRunOptions;
    private final BackendOptions backendOptions;
    private final FileUtil files;
    private final CompiledDefinition def;
    private final KExceptionManager kem;
    private final KPrint kprint;
    private final Tool tool;

    @Inject
    public HaskellRewriter(
            GlobalOptions globalOptions,
            SMTOptions smtOptions,
            KompileOptions kompileOptions,
            KProveOptions kProveOptions,
            KBMCOptions kbmcOptions,
            HaskellKRunOptions haskellKRunOptions,
            BackendOptions backendOptions,
            FileUtil files,
            CompiledDefinition def,
            KExceptionManager kem,
            KPrint kprint,
            Tool tool) {
        this.globalOptions = globalOptions;
        this.smtOptions = smtOptions;
        this.haskellKRunOptions = haskellKRunOptions;
        this.backendOptions = backendOptions;
        this.kompileOptions = kompileOptions;
        this.kProveOptions = kProveOptions;
        this.kbmcOptions = kbmcOptions;
        this.files = files;
        this.def = def;
        this.kem = kem;
        this.kprint = kprint;
        this.tool = tool;
    }

    @Override
    public Rewriter apply(Definition definition) {
        Module module = definition.mainModule();
        return new Rewriter() {
            @Override
            public RewriterResult execute(K k, Optional<Integer> depth) {
                Module mod = getExecutionModule(module);
                ModuleToKORE converter = new ModuleToKORE(mod, def.topCellInitializer, kompileOptions);
                String koreOutput = getKoreString(k, mod, converter);
                String defPath = files.resolveKompiled("definition.kore").getAbsolutePath();
                String moduleName = mod.name();

                files.saveToTemp("execute-initial.kore", koreOutput);
                String pgmPath = files.resolveTemp("execute-initial.kore").getAbsolutePath();
                String[] koreCommand = haskellKRunOptions.haskellBackendCommand.split("\\s+");
                String koreDirectory = haskellKRunOptions.haskellBackendHome;
                File koreOutputFile = files.resolveTemp("execute-result.kore");
                List<String> args = new ArrayList<String>();
                args.addAll(Arrays.asList(koreCommand));
                args.addAll(Arrays.asList(
                        defPath,
                        "--module", moduleName,
                        "--pattern", pgmPath,
                        "--output", koreOutputFile.getAbsolutePath()));
                if (depth.isPresent()) {
                    args.add("--depth");
                    args.add(Integer.toString(depth.get()));
                }
                if (smtOptions.smtPrelude != null) {
                    args.add("--smt-prelude");
                    args.add(smtOptions.smtPrelude);
                }
                koreCommand = args.toArray(koreCommand);
                if (backendOptions.dryRun) {
                    System.out.println(String.join(" ", koreCommand));
                    kprint.options.output = OutputModes.NONE;
                    return new RewriterResult(Optional.empty(), Optional.empty(), k);
                }
                try {
                    File korePath = koreDirectory == null ? null : new File(koreDirectory);
                    int execStatus = executeCommandBasic(korePath, koreCommand);
                    checkOutput(koreOutputFile, execStatus);
                    K outputK = new KoreParser(mod.sortAttributesFor()).parseFile(koreOutputFile);
                    return new RewriterResult(Optional.empty(), Optional.of(execStatus), outputK);
                } catch (IOException e) {
                    throw KEMException.criticalError("I/O Error while executing", e);
                } catch (InterruptedException e) {
                    throw KEMException.criticalError("Interrupted while executing", e);
                } catch (ParseError parseError) {
                    throw KEMException.criticalError("Error parsing haskell backend output", parseError);
                }
            }

            @Override
            public K match(K k, Rule rule) {
                return search(k, Optional.of(0), Optional.empty(), rule, SearchType.STAR);
            }

            @Override
            public Tuple2<RewriterResult, K> executeAndMatch(K k, Optional<Integer> depth, Rule rule) {
                RewriterResult res = execute(k, depth);
                return Tuple2.apply(res, match(res.k(), rule));
            }

            @Override
            public K search(K initialConfiguration, Optional<Integer> depth, Optional<Integer> bound, Rule pattern, SearchType searchType) {
                Module mod = getExecutionModule(module);
                String koreOutput = getKoreString(initialConfiguration, mod, new ModuleToKORE(mod, def.topCellInitializer, kompileOptions));
                Sort initializerSort = mod.productionsFor().get(def.topCellInitializer).get().head().sort();
                K patternTerm = RewriteToTop.toLeft(pattern.body());
                if (patternTerm instanceof  KVariable) {
                    patternTerm = KORE.KVariable(((KVariable) patternTerm).name(), Att.empty().add(Sort.class, initializerSort));
                }
                K patternCondition = pattern.requires();
                String patternTermKore = getKoreString(patternTerm, mod, new ModuleToKORE(mod, def.topCellInitializer, kompileOptions));
                String patternConditionKore;
                if (patternCondition.equals(TRUE)) {
                    patternConditionKore = "\\top{Sort" + initializerSort.name() + "{}}()";
                } else {
                    patternConditionKore =
                            "\\equals{SortBool{},Sort" + initializerSort.name() + "{}}("
                            + getKoreString(patternCondition, mod, new ModuleToKORE(mod, def.topCellInitializer, kompileOptions))
                            + ", \\dv{SortBool{}}(\"true\")"
                            + ")";
                }
                String korePatternOutput = "\\and{Sort" + initializerSort.name() + "{}}("
                        + patternTermKore
                        + ", " + patternConditionKore
                        + ")";
                String defPath = files.resolveKompiled("definition.kore").getAbsolutePath();
                String moduleName = mod.name();

                files.saveToTemp("search-initial.kore", koreOutput);
                String pgmPath = files.resolveTemp("search-initial.kore").getAbsolutePath();
                files.saveToTemp("search-pattern.kore", korePatternOutput);
                String patternPath = files.resolveTemp("search-pattern.kore").getAbsolutePath();
                String[] koreCommand = haskellKRunOptions.haskellBackendCommand.split("\\s+");
                String koreDirectory = haskellKRunOptions.haskellBackendHome;
                File koreOutputFile = files.resolveTemp("search-result.kore");
                List<String> args = new ArrayList<String>();
                args.addAll(Arrays.asList(koreCommand));
                args.addAll(Arrays.asList(
                        defPath,
                        "--module", moduleName,
                        "--pattern", pgmPath,
                        "--output", koreOutputFile.getAbsolutePath(),
                        "--searchType", searchType.toString(),
                        "--search", patternPath
                        )

                );
                if (depth.isPresent()) {
                    args.add("--depth");
                    args.add(depth.get().toString());
                }
                if (bound.isPresent()) {
                    args.add("--bound");
                    args.add(bound.get().toString());
                }
                if (smtOptions.smtPrelude != null) {
                    args.add("--smt-prelude");
                    args.add(smtOptions.smtPrelude);
                }
                koreCommand = args.toArray(koreCommand);
                if (backendOptions.dryRun) {
                    System.out.println(String.join(" ", koreCommand));
                    kprint.options.output = OutputModes.NONE;
                    return initialConfiguration;
                }
                try {
                    File korePath = koreDirectory == null ? null : new File(koreDirectory);
                    if (executeCommandBasic(korePath, koreCommand) != 0) {
                        throw KEMException.criticalError("Haskell backend returned non-zero exit code");
                    }
                    K outputK = new KoreParser(mod.sortAttributesFor()).parseFile(koreOutputFile);
                    outputK = addAnonymousAttributes(outputK, pattern);
                    return outputK;
                } catch (IOException e) {
                    throw KEMException.criticalError("I/O Error while executing", e);
                } catch (InterruptedException e) {
                    throw KEMException.criticalError("Interrupted while executing", e);
                } catch (ParseError parseError) {
                    throw KEMException.criticalError("Error parsing haskell backend output", parseError);
                }
            }

            private K addAnonymousAttributes(K input, Rule pattern) {
              Map<KVariable, KVariable> anonVars = new HashMap<>();
              VisitK visitor = new VisitK() {
                @Override
                public void apply(KVariable var) {
                  anonVars.put(var, var);
                }
              };
              visitor.apply(pattern.body());
              visitor.apply(pattern.requires());
              visitor.apply(pattern.ensures());
              return new TransformK() {
                @Override
                public K apply(KVariable var) {
                  return anonVars.getOrDefault(var, var);
                }
              }.apply(input);
            }

            private Module getExecutionModule(Module module) {
                Module mod = def.executionModule();
                if (!module.equals(mod)) {
                    throw KEMException.criticalError("Invalid module specified for rewriting. Haskell backend only supports rewriting over" +
                            " the definition's main module.");
                }
                return mod;
            }


            private String saveKoreDefinitionToTemp(ModuleToKORE converter) {
                String kompiledString = KoreBackend.getKompiledString(converter, files, false, tool);
                files.saveToTemp("vdefinition.kore", kompiledString);
                String defPath = files.resolveTemp("vdefinition.kore").getAbsolutePath();
                return defPath;
            }

            private String saveKoreSpecToTemp(ModuleToKORE converter, Module rules) {
                StringBuilder sb = new StringBuilder();
                String koreOutput = converter.convertSpecificationModule(module, rules,
                        haskellKRunOptions.defaultClaimType, sb);
                files.saveToTemp("spec.kore", koreOutput);
                String specPath = files.resolveTemp("spec.kore").getAbsolutePath();
                return specPath;
            }

            private List<String> buildCommonProvingCommand(String defPath, String specPath, String outPath,
                                                           String defModuleName, String specModuleName){
                String[] koreCommand;
                if (kProveOptions.debugger && !haskellKRunOptions.haskellBackendCommand.equals("kore-exec")) {
                    throw KEMException.criticalError("Cannot pass --debugger with --haskell-backend-command.");
                } else if (kProveOptions.debugger) {
                    koreCommand = "kore-repl".split("\\s+");
                } else {
                    koreCommand = haskellKRunOptions.haskellBackendCommand.split("\\s+");
                }

                List<String> args = new ArrayList<>();
                args.addAll(Arrays.asList(koreCommand));
                args.addAll(Arrays.asList(
                        defPath,
                        "--module", defModuleName,
                        "--prove", specPath,
                        "--spec-module", specModuleName,
                        "--output", outPath));
                if (smtOptions.smtPrelude != null) {
                    args.add("--smt-prelude");
                    args.add(smtOptions.smtPrelude);
                }
                if (kProveOptions.debugScript != null) {
                    if (!kProveOptions.debugger) {
                        throw KEMException.criticalError("Can only use --debug-script with --debugger.");
                    }
                    args.add("--repl-script");
                    args.add(files.resolveWorkingDirectory(kProveOptions.debugScript).getAbsolutePath());
                }
                return args;
            }

            private RewriterResult executeKoreCommands(Module rules, String[] koreCommand,
                                                       String koreDirectory, File koreOutputFile) {
                int exit;
                try {
                    File korePath = koreDirectory == null ? null : new File(koreDirectory);
                    exit = executeCommandBasic(korePath, koreCommand);
                    checkOutput(koreOutputFile, exit);
                } catch (IOException e) {
                    throw KEMException.criticalError("I/O Error while executing", e);
                } catch (InterruptedException e) {
                    throw KEMException.criticalError("Interrupted while executing", e);
                }
                K outputK;
                try {
                    outputK = new KoreParser(rules.sortAttributesFor())
                            .parseFile(koreOutputFile);
                } catch (ParseError parseError) {
                    kem.registerCriticalWarning(ExceptionType.PROOF_LINT, "Error parsing haskell backend output", parseError);
                    outputK = KORE.KApply(KLabels.ML_FALSE);
                }
                return new RewriterResult(Optional.empty(), Optional.of(exit), outputK);
            }

            @Override
            public RewriterResult prove(Module rules, Rule boundaryPattern, Boolean reuseDef) {
                Module kompiledModule = KoreBackend.getKompiledModule(module);
                ModuleToKORE converter = new ModuleToKORE(kompiledModule, def.topCellInitializer, kompileOptions);
                String defPath = reuseDef ? files.resolveKompiled("definition.kore").getAbsolutePath() : saveKoreDefinitionToTemp(converter);
                String specPath = saveKoreSpecToTemp(converter, rules);
                File koreOutputFile = files.resolveTemp("result.kore");

                String koreDirectory = haskellKRunOptions.haskellBackendHome;

                String defModuleName =
                        kProveOptions.defModule == null ? def.executionModule().name() : kProveOptions.defModule;
                String specModuleName = kProveOptions.specModule == null ? rules.name() : kProveOptions.specModule;

                List<String> args = buildCommonProvingCommand(defPath, specPath, koreOutputFile.getAbsolutePath(),
                        defModuleName, specModuleName);

                if (kProveOptions.depth != null) {
                    args.addAll(Arrays.asList(
                        "--depth", kProveOptions.depth.toString()));
                }
                String[] koreCommand = args.toArray(new String[args.size()]);
                if (backendOptions.dryRun) {
                    globalOptions.debugWarnings = true; // sets this so the kprove directory is not removed.
                    System.out.println(String.join(" ", koreCommand));
                    kprint.options.output = OutputModes.NONE;
                    return new RewriterResult(Optional.empty(), Optional.of(0),KORE.KApply(KLabels.ML_FALSE));
                }
                if (globalOptions.verbose) {
                    System.err.println("Executing " + args);
                }

                RewriterResult result = executeKoreCommands(rules, koreCommand, koreDirectory, koreOutputFile);
                return result;
            }

            public RewriterResult bmc (Module rules) {
                Module kompiledModule = KoreBackend.getKompiledModule(module);
                ModuleToKORE converter = new ModuleToKORE(kompiledModule, def.topCellInitializer, kompileOptions);
                String defPath = saveKoreDefinitionToTemp(converter);
                String specPath = saveKoreSpecToTemp(converter, rules);
                File koreOutputFile = files.resolveTemp("result.kore");

                String koreDirectory = haskellKRunOptions.haskellBackendHome;

                String defModuleName =
                        kbmcOptions.defModule == null ? def.executionModule().name() : kbmcOptions.defModule;
                String specModuleName = kbmcOptions.specModule == null ? rules.name() : kbmcOptions.specModule;

                List<String> args = buildCommonProvingCommand(defPath, specPath, koreOutputFile.getAbsolutePath(),
                        defModuleName, specModuleName);

                if (kbmcOptions.depth != null) {
                    args.addAll(Arrays.asList(
                            "--depth", kbmcOptions.depth.toString()));
                }
                if (kbmcOptions.graphSearch != null) {
                    args.addAll(Arrays.asList(
                            "--graph-search", kbmcOptions.graphSearch.toString()));
                }
                args.add("--bmc");

                String[] koreCommand = args.toArray(new String[args.size()]);
                if (backendOptions.dryRun) {
                    globalOptions.debugWarnings = true; // sets this so the kprove directory is not removed.
                    System.out.println(String.join(" ", koreCommand));
                    kprint.options.output = OutputModes.NONE;
                    return new RewriterResult(Optional.empty(), Optional.of(0),KORE.KApply(KLabels.ML_FALSE));
                }
                if (globalOptions.verbose) {
                    System.err.println("Executing " + args);
                }

                RewriterResult result = executeKoreCommands(rules, koreCommand, koreDirectory, koreOutputFile);
                return result;
            }

            @Override
            public boolean equivalence(Rewriter firstDef, Rewriter secondDef, Module firstSpec, Module secondSpec) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private void checkOutput(File koreOutputFile, int execStatus) {
        if (execStatus != 0) {
            if (!koreOutputFile.isFile()) {
                throw KEMException.criticalError("Haskell Backend execution failed with code " + execStatus
                        + " and produced no output.");
            }
        }
    }

    private String getKoreString(K initialConfiguration, Module mod, ModuleToKORE converter) {
        ExpandMacros macroExpander = ExpandMacros.forNonSentences(mod, files, kompileOptions, false);
        K withMacros = macroExpander.expand(initialConfiguration);
        K kWithInjections = new AddSortInjections(mod).addInjections(withMacros);
        StringBuilder sb = new StringBuilder();
        converter.convert(kWithInjections, sb);
        return sb.toString();
    }


    /**
     * Runs a command in the given directory,
     * @param workingDir directory to run in
     * @param command  commandline to run
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private int executeCommandBasic(File workingDir, String... command) throws IOException, InterruptedException {
        if (globalOptions.verbose) {
            System.err.println("Executing command: " + String.join(" ", Arrays.asList(command)));
        }
        int exit;
        ProcessBuilder pb = files.getProcessBuilder()
                .command(command);
        if (workingDir != null) {
            pb.directory(workingDir);
        }
        if (Main.isNailgun()) {

            Process p2 = pb.start();

            Thread in = new Thread(() -> {
                int count;
                byte[] buffer = new byte[8192];
                try {
                    while (true) {
                        count = System.in.read(buffer);
                        if (count < 0)
                            break;
                        p2.getOutputStream().write(buffer, 0, count);
                        p2.getOutputStream().flush();
                    }
                } catch (IOException e) {}
            });
            Thread out = RunProcess.getOutputStreamThread(p2::getInputStream, System.out);
            Thread err = RunProcess.getOutputStreamThread(p2::getErrorStream, System.err);
            in.start();
            out.start();
            err.start();

            exit = p2.waitFor();
            in.interrupt();
            in.join();
            out.join();
            err.join();
            System.out.flush();
            System.err.flush();
            return exit;
        } else {
            // if we're not nailgun, we can't do the above because System.in won't be interruptible,
            // and we don't really want or need to anyway.
            return pb.inheritIO().start().waitFor();
        }
    }
}

