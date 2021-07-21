// Copyright (c) 2014-2019 K Team. All Rights Reserved.
package org.kframework.backend.llvm;

import com.google.inject.Inject;
import org.kframework.backend.kore.ModuleToKORE;
import org.kframework.compile.AddSortInjections;
import org.kframework.compile.ExpandMacros;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.RunProcess;
import org.kframework.main.GlobalOptions;
import org.kframework.main.Main;
import org.kframework.parser.KoreParser;
import org.kframework.parser.kore.parser.ParseError;
import org.kframework.RewriterResult;
import org.kframework.rewriter.Rewriter;
import org.kframework.rewriter.SearchType;
import org.kframework.unparser.KPrint;
import org.kframework.unparser.OutputModes;
import org.kframework.utils.OS;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.DefinitionScoped;
import org.kframework.utils.inject.RequestScoped;
import scala.Tuple2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;


@RequestScoped
public class LLVMRewriter implements Function<Definition, Rewriter> {

    private final GlobalOptions globalOptions;
    private final FileUtil files;
    private final CompiledDefinition def;
    private final KRunOptions krunOptions;
    private final KompileOptions kompileOptions;
    private final KPrint kprint;

    @Inject
    public LLVMRewriter(
            GlobalOptions globalOptions,
            FileUtil files,
            CompiledDefinition def,
            KRunOptions krunOptions,
            KompileOptions kompileOptions,
            KPrint kprint) {
        this.globalOptions = globalOptions;
        this.files = files;
        this.def = def;
        this.krunOptions = krunOptions;
        this.kompileOptions = kompileOptions;
        this.kprint = kprint;
    }

    @Override
    public Rewriter apply(Definition definition) {
        Module module = definition.mainModule();
        if (!module.equals(def.executionModule())) {
            throw KEMException.criticalError("Invalid module specified for rewriting. LLVM backend only supports rewriting over" +
                    " the definition's main module.");
        }
        return new Rewriter() {
            @Override
            public RewriterResult execute(K k, Optional<Integer> depth) {
                Module mod = def.executionModule();
                ExpandMacros macroExpander = ExpandMacros.forNonSentences(mod, files, kompileOptions, false);
                ModuleToKORE converter = new ModuleToKORE(mod, def.topCellInitializer, kompileOptions);
                K withMacros = macroExpander.expand(k);
                K kWithInjections = new AddSortInjections(mod).addInjections(withMacros);
                StringBuilder sb = new StringBuilder();
                converter.convert(kWithInjections, sb);
                String koreOutput = sb.toString();
                files.saveToTemp("pgm.kore", koreOutput);
                String pgmPath = files.resolveTemp("pgm.kore").getAbsolutePath();
                File koreOutputFile = files.resolveTemp("result.kore");
                List<String> args = new ArrayList<String>();
                if (krunOptions.debugger) {
                  if (OS.current() == OS.OSX) {
                    args.add("lldb");
                    args.add("--");
                  } else {
                    args.add("gdb");
                    args.add("--args");
                  }
                }
                args.add(files.resolveKompiled("interpreter").getAbsolutePath());
                args.add(pgmPath);
                args.add(Integer.toString(depth.orElse(-1)));
                args.add(koreOutputFile.getAbsolutePath());
                if (krunOptions.statistics) {
                  args.add("--statistics");
                }
                String[] command = new String[args.size()];
                if (krunOptions.backend.dryRun) {
                    System.out.println(String.join(" ", args.toArray(command)));
                    kprint.options.output = OutputModes.NONE;
                    return new RewriterResult(Optional.empty(), Optional.empty(), k);
                }
                try {
                    int exit = executeCommandBasic(files.resolveWorkingDirectory("."), args);
                    if (!koreOutputFile.exists()) {
                      throw KEMException.criticalError("LLVM Backend crashed during rewriting.");
                    }
                    int line = 0;
                    if (krunOptions.statistics) {
                      line = 1;
                      BufferedReader br = new BufferedReader(new FileReader(koreOutputFile));
                      long steps = Long.parseLong(br.readLine());
                      System.err.println("[" + steps + " steps]");
                    }
                    K outputK = new KoreParser(mod.sortAttributesFor()).parseFile(koreOutputFile, line);
                    return new RewriterResult(Optional.empty(), Optional.of(exit), outputK);
                } catch (IOException e) {
                    throw KEMException.criticalError("I/O Error while executing", e);
                } catch (InterruptedException e) {
                    throw KEMException.criticalError("Interrupted while executing", e);
                } catch (ParseError parseError) {
                    throw KEMException.criticalError("Error parsing llvm backend output", parseError);
                }
            }

            @Override
            public K match(K k, Rule rule) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Tuple2<RewriterResult, K> executeAndMatch(K k, Optional<Integer> depth, Rule rule) {
                throw new UnsupportedOperationException();
            }

            @Override
            public K search(K initialConfiguration, Optional<Integer> depth, Optional<Integer> bound, Rule pattern, SearchType searchType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RewriterResult prove(Module rules, Rule boundaryPattern, Boolean reuseDef) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RewriterResult bmc(Module rules) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean equivalence(Rewriter firstDef, Rewriter secondDef, Module firstSpec, Module secondSpec) {
                throw new UnsupportedOperationException();
            }
        };
    }


    /**
     * Runs a command in the given directory,
     * @param workingDir directory to run in
     * @param command  commandline to run
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private int executeCommandBasic(File workingDir, List<String> command) throws IOException, InterruptedException {
        int exit;
        if (globalOptions.verbose) {
            System.err.println("Executing command: " + String.join(" ", command));
        }
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

