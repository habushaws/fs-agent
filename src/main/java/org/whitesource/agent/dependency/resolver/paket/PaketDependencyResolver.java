package org.whitesource.agent.dependency.resolver.paket;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * @author raz.nitzan
 */
public class PaketDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private static final String PAKET_DEPENDENCIES = "paket.dependencies";
    private static final String DLL = ".dll";
    private static final String EXE = ".exe";
    private static final String NUPKG = ".nupkg";
    private static final String CS = ".cs";
    private static final String JS = ".js";
    private static final String GROUP = "group";
    private static final String NUGET = "nuget";
    private static final String SPACE = " ";
    private static final String MAIN = "Main";
    private static final String PAKET_EXE = "paket.exe";
    private static final String PAKET = "paket";
    private final String PAKET_LOCK = "paket.lock";
    private static final String FORWARD_SLASH = "/";
    private static final String PATTERN = "**/*";
    private static final String EXCLUDE_TOP_FOLDER = "packages";
    private static final String INSTALL_COMMAND = "install";

    /* --- Members --- */

    private final Logger logger = LoggerFactory.getLogger(PaketDependencyResolver.class);
    private final boolean paketIgnoreFiles;
    private final boolean paketRunPreStep;
    private String[] paketIgnoredGroups;
    private String paketPath;

    /* --- Constructor --- */

    public PaketDependencyResolver(String[] paketIgnoredGroups, boolean paketIgnoreFiles, boolean paketRunPreStep, String paketPath) {
        super();
        changePaketIgnoredScopesToLowerCase(paketIgnoredGroups);
        this.paketIgnoredGroups = paketIgnoredGroups;
        this.paketIgnoreFiles = paketIgnoreFiles;
        this.paketRunPreStep = paketRunPreStep;
        this.paketPath = paketPath;
    }

    /* --- Overridden protected methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> paketDependenciesFiles) {
        boolean installSuccess = true;
        Collection<DependencyInfo> dependencies = new ArrayList<>();
        List<String> excludes = new LinkedList<>();
        if (paketRunPreStep) {
            File paket = new File(topLevelFolder + FORWARD_SLASH + PAKET_EXE);
            if (StringUtils.isNotEmpty(this.paketPath)) {
                paket = new File(this.paketPath);
            } else if (!paket.exists()) {
                paket = null;
            }
            installSuccess = !executePreparationStep(topLevelFolder, paket);
        }
        if (installSuccess) {
            String paketLockPath = topLevelFolder + FORWARD_SLASH + PAKET_LOCK;
            File paketLockFile = new File(paketLockPath);
            if (paketLockFile.exists()) {
                logger.debug("Found paket.lock file: {}", paketLockPath);
                Map<String, List<String>> groupToDirectDependenciesMap = getGroupToDirectDependencies(paketDependenciesFiles.iterator().next());
                if (groupToDirectDependenciesMap != null && !groupToDirectDependenciesMap.keySet().isEmpty()) {
                    Collection<AbstractPaketDependencyCollector> paketDependencyCollectors = new LinkedList<>();
                    for(String groupName : groupToDirectDependenciesMap.keySet()) {
                        if (groupName.equals(MAIN)) {
                            paketDependencyCollectors.add(new MainGroupPaketDependencyCollector(groupToDirectDependenciesMap.get(MAIN), this.paketIgnoredGroups));
                        } else {
                            paketDependencyCollectors.add(new GroupPaketDependencyCollector(groupToDirectDependenciesMap.get(groupName), this.paketIgnoredGroups, groupName));
                        }
                    }
                    logger.info("Start collecting paket dependencies");
                    paketDependencyCollectors.forEach(paketDependencyCollector -> {
                        Collection<AgentProjectInfo> prjectInfo = paketDependencyCollector.collectDependencies(topLevelFolder);
                        dependencies.addAll(prjectInfo.iterator().next().getDependencies());
                    });
                }

                // ignore all the nupkg files in order to not scan them again
                if (!dependencies.isEmpty()) {
                    if (this.paketIgnoreFiles) {
                        excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, Arrays.asList(PATTERN + NUPKG, PATTERN + DLL, PATTERN + EXE,
                                PATTERN + CS, PATTERN + JS), null));
                    } else {
                        excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, Arrays.asList(PATTERN + NUPKG, PATTERN + DLL, PATTERN + EXE,
                                PATTERN + CS, PATTERN + JS), EXCLUDE_TOP_FOLDER));
                    }
                }
            } else {
                logger.warn("Could not find paket.lock file in {}. Please execute 'paket install' first.", topLevelFolder);
            }
        } else {
            logger.warn("'paket install' command failed");
        }

        return new ResolutionResult(dependencies, excludes, getDependencyType(), topLevelFolder);
    }

    private boolean executePreparationStep(String folder, File paket) {
        String[] command;
        if (paket == null) {
            command = getInstallParams(PAKET);
        } else {
            command = getInstallParams(paket.getAbsolutePath());
        }
        String commandString = String.join(" ", command);
        logger.debug("Running install command : " + commandString);
        CommandLineProcess npmInstall = new CommandLineProcess(folder, command);
        try {
            npmInstall.executeProcessWithoutOutput();
        } catch (IOException e) {
            logger.debug("Could not run " + commandString + " in folder " + folder);
            return true;
        }
        return npmInstall.isErrorInProcess();
    }

    private String[] getInstallParams(String firstParameter) {
        return new String[]{firstParameter, INSTALL_COMMAND};
    }

    @Override
    protected Collection<String> getExcludes() {
        return new ArrayList<>();
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return new ArrayList<>(Arrays.asList(DLL, EXE, NUPKG, CS));
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.NUGET;
    }

    @Override
    protected String getBomPattern() {
        return PATTERN + PAKET_DEPENDENCIES;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }

    /* --- Private methods --- */

    private Map<String, List<String>> getGroupToDirectDependencies(String paketDependenciesPath) {
        Map<String, List<String>> result = new HashMap<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(paketDependenciesPath))) {
            String line;
            String groupName = MAIN;
            // the first group of dependencies named 'Main' even though is not written in the paket.dependencies file
            List<String> groupDependencies = new LinkedList<>();
            while ((line = bufferedReader.readLine()) != null) {
                String processedLine = line.toLowerCase().trim();
                if (processedLine.startsWith(NUGET)) {
                    // nuget dependency example: nuget xunit >= 1.1.1
                    String nugetDependencyName = line.substring(NUGET.length() + 1).trim();
                    int indexOfSpace = nugetDependencyName.indexOf(SPACE);
                    if (indexOfSpace < 0) {
                        indexOfSpace = nugetDependencyName.length();
                    }
                    groupDependencies.add(nugetDependencyName.substring(0, indexOfSpace));
                }
                // starting to parse new nuget dependencies group
                if (processedLine.startsWith(GROUP)) {
                    if (!groupDependencies.isEmpty()) {
                        result.put(groupName, groupDependencies);
                    }
                    groupName = line.substring(GROUP.length() + 1);
                    groupDependencies = new LinkedList<>();
                }
            }
            // get the last group of dependencies
            if (!groupDependencies.isEmpty()) {
                result.put(groupName, groupDependencies);
            }
        } catch (IOException e) {
            logger.warn("Could not find paket.dependencies file in {}.", paketDependenciesPath);
        }
        return result;
    }

    private void changePaketIgnoredScopesToLowerCase(String[] paketIgnoredScopes) {
        if (paketIgnoredScopes != null) {
            for (int i = 0; i < paketIgnoredScopes.length; i++) {
                paketIgnoredScopes[i] = paketIgnoredScopes[i].toLowerCase();
            }
        }
    }
}
