package org.kframework.kprovex;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.io.FilenameUtils;
import org.kframework.compile.Backend;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.Kompile;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.file.FileUtil;
import scala.Option;
import scala.Tuple2;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Denis Bogdanas
 * Created on 07-Nov-19.
 */
public class ProofDefinitionBuilder {

    private static Map<Definition, Definition>
            cache = Collections.synchronizedMap(new LinkedHashMap<Definition, Definition>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry entry) {
            return size() > 10;
        }
    });


    private final CompiledDefinition compiledDefinition;
    private final Backend backend;
    private final Kompile kompile;
    private final FileUtil files;
    @Inject(optional = true)
    @Named("extraConcreteRuleLabels")
    private List<String> extraConcreteRuleLabels = null;

    @Inject
    public ProofDefinitionBuilder(CompiledDefinition compiledDefinition, Backend backend, Kompile kompile,
                                  FileUtil files) {
        this.compiledDefinition = compiledDefinition;
        this.backend = backend;
        this.kompile = kompile;
        this.files = files;
    }

    /**
     * @param specFile       File containing specification rules to prove. Not part of definition.
     * @param defModuleName  Name of main module of extended definition - that is compiled definition + extra
     *                       modules required by proofs, usually abstractions for symbolic execution and lemmas.
     * @param specModuleName Module containing specifications to prove
     */
    public Tuple2<Definition, Module> build(File specFile, String defModuleName, String specModuleName) {
        return build(specFile, defModuleName, specModuleName, false);
    }

    public Tuple2<Definition, Module> build(File specFile, String defModuleName, String specModuleName, boolean readOnlyCache) {
        String defModuleNameUpdated =
                defModuleName == null ? compiledDefinition.kompiledDefinition.mainModule().name() : defModuleName;
        String specModuleNameUpdated =
                specModuleName == null ? FilenameUtils.getBaseName(specFile.getName()).toUpperCase() : specModuleName;
        File absSpecFile = files.resolveWorkingDirectory(specFile).getAbsoluteFile();

        Set<Module> modules = kompile.parseModules(compiledDefinition, defModuleNameUpdated, specModuleNameUpdated, absSpecFile,
                backend.excludedModuleTags(), readOnlyCache);
        Map<String, Module> modulesMap = modules.stream().collect(Collectors.toMap(Module::name, m -> m));
        Definition parsedDefinition = compiledDefinition.getParsedDefinition();
        Module specModule = getModule(specModuleNameUpdated, modulesMap, parsedDefinition);
        specModule = kompile.proverChecks(specModule, modulesMap.get(defModuleNameUpdated));
        kompile.structuralChecks(scala.collection.JavaConverters.asScalaSet(modules),
                specModule, Option.empty(), backend.excludedModuleTags());
        Module defModule = getModule(defModuleNameUpdated, modulesMap, parsedDefinition);
        Definition rawExtendedDef = Definition.apply(defModule, parsedDefinition.entryModules(),
                parsedDefinition.att());
        Definition compiledExtendedDef = compileDefinition(backend, rawExtendedDef); //also resolves imports
        compiledExtendedDef = backend.proofDefinitionNonCachedSteps(extraConcreteRuleLabels).apply(compiledExtendedDef);

        specModule = backend.specificationSteps(compiledDefinition.kompiledDefinition).apply(specModule);

        return Tuple2.apply(compiledExtendedDef, specModule);
    }

    private static Module getModule(String defModule, Map<String, Module> modules, Definition parsedDefinition) {
        if (modules.containsKey(defModule))
            return modules.get(defModule);
        Option<Module> mod = parsedDefinition.getModule(defModule);
        if (mod.isDefined()) {
            return mod.get();
        }
        throw KEMException.criticalError("Module " + defModule + " does not exist.");
    }

    private static Definition compileDefinition(Backend backend, Definition combinedDef) {
        Definition compiled = cache.get(combinedDef);
        if (compiled == null) {
            compiled = backend.steps().apply(combinedDef);
            cache.put(combinedDef, compiled);
        }
        return compiled;
    }
}
