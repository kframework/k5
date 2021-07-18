// Copyright (c) 2015-2019 Runtime Verification, Inc. (RV-Match team). All Rights Reserved.
package org.kframework.backend.ocaml;

import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.kframework.RewriterResult;
import org.kframework.builtin.KLabels;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.kore.KVariable;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.RunProcess;
import org.kframework.main.GlobalOptions;
import org.kframework.main.Main;
import org.kframework.parser.binary.BinaryParser;
import org.kframework.rewriter.Rewriter;
import org.kframework.rewriter.SearchType;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.DefinitionScoped;
import org.kframework.utils.inject.RequestScoped;
import scala.Tuple2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.kframework.kore.KORE.*;

@RequestScoped
public class OcamlRewriter implements Function<Definition, Rewriter> {

    private final FileUtil files;
    private final CompiledDefinition def;
    private final DefinitionToOcaml converter;
    private final KRunOptions options;
    private final OcamlKRunOptions ocamlKRunOptions;

    @Inject
    public OcamlRewriter(
            KExceptionManager kem,
            FileUtil files,
            GlobalOptions globalOptions,
            KompileOptions kompileOptions,
            CompiledDefinition def,
            InitializeDefinition init,
            KRunOptions options,
            OcamlKRunOptions ocamlKRunOptions) {
        this.files = files;
        this.def = def;
        this.ocamlKRunOptions = ocamlKRunOptions;
        this.converter = new DefinitionToOcaml(kem, files, globalOptions, kompileOptions, null);
        converter.initialize(init.serialized, def);
        this.options = options;
    }

    OcamlRewriter(FileUtil files, DefinitionToOcaml converter, KRunOptions options) {
        this.files = files;
        this.def = null;
        this.converter = converter;
        this.options = options;
        this.ocamlKRunOptions = null;
    }

    @Override
    public Rewriter apply(Definition definition) {
        Module module = definition.mainModule();
        if (!module.equals(def.executionModule())) {
            throw KEMException.criticalError("Invalid module specified for rewriting. Ocaml backend only supports rewriting over" +
                    " the definition's main module.");
        }
        return new Rewriter() {
            @Override
            public RewriterResult execute(K k, Optional<Integer> depth) {
                String ocaml = converter.execute(k, depth.orElse(-1), files.resolveTemp("run.out").getAbsolutePath());
                files.saveToTemp("pgm.ml", ocaml);
                byte[] output = compileAndExecOcaml("pgm.ml");
                return parseOcamlRewriterOutput(output);
            }

            @Override
            public K match(K k, Rule rule) {
                String ocaml = converter.match(k, rule, files.resolveTemp("run.out").getAbsolutePath());
                files.saveToTemp("match.ml", ocaml);
                byte[] output = compileAndExecOcaml("match.ml");
                return parseOcamlSearchOutput(output);
            }

            @Override
            public Tuple2<RewriterResult, K> executeAndMatch(K k, Optional<Integer> depth, Rule rule) {
                String ocaml = converter.executeAndMatch(k, depth.orElse(-1), rule, files.resolveTemp("run.out").getAbsolutePath(), files.resolveTemp("run.subst").getAbsolutePath());
                files.saveToTemp("pgm.ml", ocaml);
                byte[] output = compileAndExecOcaml("pgm.ml");
                byte[] subst = files.loadBytesFromTemp("run.subst");
                return Tuple2.apply(parseOcamlRewriterOutput(output), parseOcamlSearchOutput(subst));
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

    private RewriterResult parseOcamlRewriterOutput(byte[] output) {
        String s = new String(output);
        int steps = Integer.parseInt(s.substring(0, s.indexOf('\n')));
        if (options.statistics) {
            System.err.println("[" + steps + " steps]");
        }
        return new RewriterResult(Optional.of(steps), Optional.empty(), BinaryParser.parse(Arrays.copyOfRange(output, s.indexOf('\n') + 1, output.length)));
    }

    private K parseOcamlSearchOutput(byte[] output) {
        ByteBuffer buf = ByteBuffer.wrap(output);
        int count = buf.getInt();
        List<Map<KVariable, K>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<KVariable, K> map = new HashMap<>();
            list.add(map);
            while (true) {
                if (!buf.hasRemaining()) break;
                int len = buf.getInt();
                byte[] nameBuf = new byte[len];
                buf.get(nameBuf, 0, len);
                String line = new String(nameBuf);
                if ("|".equals(line) ) {
                    break;
                }
                KVariable key = KVariable(line);
                K res = BinaryParser.parse(buf);
                map.put(key, res);
            }
        }
        return list.stream().map(m -> m.entrySet().stream()
                    .map(e -> KApply(KLabel("_==K_"), e.getKey(), e.getValue()))
                    .reduce((k1, k2) -> KApply(KLabels.ML_AND, k1, k2)).orElse(KApply(KLabels.ML_TRUE)))
                .reduce((k1, k2) -> KApply(KLabels.ML_OR, k1, k2)).orElse(KApply(KLabels.ML_FALSE));
    }

    private static String readLine(ByteArrayInputStream inputStream) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int c;
        for (c = inputStream.read(); c != '\n' && c != -1 ; c = inputStream.read()) {
            byteArrayOutputStream.write(c);
        }
        if (c == -1 && byteArrayOutputStream.size() == 0) {
            return null;
        }
        String line = byteArrayOutputStream.toString();
        if (line.endsWith("\r")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }

    /**
     * Compile the given ocaml file and then run it,
     * returning the output if it returned with exit code 0,
     * or raising a {@code KEMException} otherwise.
     * @param name
     * @return
     */
    byte[] compileAndExecOcaml(String name) {
        int exit = compileAndExecOcamlBasic(name);
        if (exit != 0) {
            throw KEMException.criticalError("Failed to execute program in ocaml. See output for error information.");
        }
        return files.loadBytesFromTemp("run.out");
    }

    int compileAndExecOcamlBasic(String name) {
        try {
            compileOcaml(name);
            return executeCommandBasic(null,
                    files.resolveTemp("a.out").getAbsolutePath(),
                    files.resolveKompiled("realdef.cma").getAbsolutePath(),
                    ocamlKRunOptions.argv

            );
        } catch (IOException e) {
            throw KEMException.criticalError("Failed to start ocamlopt: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw KEMException.criticalError("Ocaml process interrupted.", e);
        }
    }

    /**
     * Run the given command with {@link #executeCommandBasic(File, String...)},
     * translating any exceptions it raises into {@link KEMException} reports
     * claiming the failure occurred while running an ocaml process.
     * @param directory
     * @param command
     * @return
     */
    int execOcaml(File directory, String... command) {
        try {
            return executeCommandBasic(directory, command);
        } catch (IOException e) {
            throw KEMException.criticalError("Failed to start ocamlopt: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw KEMException.criticalError("Ocaml process interrupted.", e);
        }
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

    void createResourceObject(String name) throws IOException, InterruptedException {
        ProcessBuilder pb = files.getProcessBuilder().directory(files.resolveTemp("."));
        pb.command("ld","-r","-b","binary","-o",name+".o",name); // writes to <name>.o. Symbols are always based on name.
        Process p = pb.redirectError(files.resolveTemp("ld-"+name+".err"))
                .redirectOutput(files.resolveTemp("ld-"+name+".out"))
                .start();
        int exit = p.waitFor();
        if (exit != 0) {
            System.err.println(files.loadFromTemp("ld-"+name+".err"));
            throw KEMException.criticalError("Failed to embed resource into object file. See output for error information.");
        }
    }

    /** Must be kept compatible with {@link .compileOcamlObject} and consistent with {@link .compileOcaml}. */
    void linkOcamlObject(String compiler, String name, String target, String... objectFiles) throws IOException, InterruptedException  {
        // now link
        ProcessBuilder pb = files.getProcessBuilder();
        List<String> args = new ArrayList<>(Arrays.asList(
                compiler, name, "-o", target, "-Wl,-E"));
        args.addAll(Arrays.asList(objectFiles));
        args.addAll(Arrays.asList("-lmpfr","-lgmp","-lffi","-lm","-ldl"));
        args.add("-Wl,--no-as-needed");
        args.addAll(options.nativeLibraries);
        pb.command(args);
        if (options.global.verbose) {
            System.err.println("+ " + StringUtils.join(args, " "));
        }
        Process p = pb.redirectError(files.resolveTemp("link.err"))
                .redirectOutput(files.resolveTemp("link.out"))
                .start();
        int exit = p.waitFor();
        if (exit != 0) {
            System.err.println(files.loadFromTemp("link.err"));
            throw KEMException.criticalError("Failed to link final program. See output for error information.");
        }
    }

    /** Must be kept consistent with {@link .compileOcamlObject} and {@link .linkOcamlObject} */
    void compileOcaml(String name, String... objectFiles) throws IOException, InterruptedException {
        ProcessBuilder pb = files.getProcessBuilder();
        List<String> args = new ArrayList<>(Arrays.asList("-g", "-o", files.resolveTemp("a.out").getAbsolutePath(), "-package", "gmp", "-package", "zarith", "-package", "uuidm",
                "-package", "str", "-package", "unix", "-package", "dynlink", "-linkpkg", "-linkall", "-safe-string"));
        args.addAll(0, converter.options.packages.stream().flatMap(p -> Stream.of("-package", p)).collect(Collectors.toList()));
        // -cclib -foo is used to pass -foo to gcc which is called to do linking. -cclib -Wl,-foo is used to pass
        // -foo to ld which is called by gcc.
        args.addAll(options.nativeLibraries.stream().flatMap(lib -> Stream.of("-cclib", lib)).collect(Collectors.toList()));
        args.addAll(Arrays.asList(objectFiles));
        String ocamlfind = OcamlBackend.getOcamlFind(files);
        if (converter.kompileOptions.optimize2 || converter.kompileOptions.optimize3) {
            args.add(0, ocamlfind);
            args.add(1, "ocamlopt");
            if (!converter.options.noLinkPrelude) {
                args.add(files.resolveKompiled("constants.cmx").getAbsolutePath());
                args.add(files.resolveKompiled("prelude.cmx").getAbsolutePath());
                args.add(files.resolveKompiled("plugin.cmx").getAbsolutePath());
                args.add(files.resolveKompiled("parser.cmx").getAbsolutePath());
                args.add(files.resolveKompiled("lexer.cmx").getAbsolutePath());
                args.add(files.resolveKompiled("hooks.cmx").getAbsolutePath());
                args.add(files.resolveKompiled("run.cmx").getAbsolutePath());
            }
            args.addAll(Arrays.asList("-I", files.resolveKompiled(".").getAbsolutePath(),
                    files.resolveTemp(name).getAbsolutePath()));
            args.add("-inline");
            args.add("20");
            args.add("-nodynlink");
            pb = pb.command(args);
        } else {
            args.add(0, ocamlfind);
            args.add(1, "ocamlc");
            if (!converter.options.noLinkPrelude) {
                args.add(files.resolveKompiled("constants.cmo").getAbsolutePath());
                args.add(files.resolveKompiled("prelude.cmo").getAbsolutePath());
                args.add(files.resolveKompiled("plugin.cmo").getAbsolutePath());
                args.add(files.resolveKompiled("parser.cmo").getAbsolutePath());
                args.add(files.resolveKompiled("lexer.cmo").getAbsolutePath());
                args.add(files.resolveKompiled("hooks.cmo").getAbsolutePath());
                args.add(files.resolveKompiled("run.cmo").getAbsolutePath());
            }
            args.addAll(Arrays.asList("-I", files.resolveKompiled(".").getAbsolutePath()));
            args.add(files.resolveKompiled("realdef.cmo").getAbsolutePath());
            args.add(files.resolveTemp(name).getAbsolutePath());
            pb = pb.command(args);
        }
        if (options.global.verbose) {
            System.err.println("+ " + StringUtils.join(args, " "));
        }
        Process p = pb.redirectError(files.resolveTemp("compile.err"))
                .redirectOutput(files.resolveTemp("compile.out"))
                .start();
        int exit = p.waitFor();
        if (exit != 0) {
            System.err.println(files.loadFromTemp("compile.err"));
            throw KEMException.criticalError("Failed to compile program to ocaml. See output for error information.");
        }
    }

    @DefinitionScoped
    public static class InitializeDefinition {
        final DefinitionToOcaml serialized;

        @Inject
        public InitializeDefinition(BinaryLoader loader, FileUtil files) {
            serialized = loader.loadOrDie(DefinitionToOcaml.class, files.resolveKompiled("ocaml_converter.bin"));
        }
    }
}
