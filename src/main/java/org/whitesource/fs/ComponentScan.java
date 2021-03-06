package org.whitesource.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.fs.configuration.ConfigurationSerializer;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.util.*;


/**
 * Created by anna.rozin
 */
public class ComponentScan {
    public static final String DIRECTORY = "d";

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(ComponentScan.class);
    public static final String DIRECTORY_NOT_SET = "Directory parameter 'd' is not set" + StatusCode.ERROR;
    public static final String EMPTY_PROJECT_TOKEN = "";
    public static final String FOLDER_DELIMITER = ",";
    public static final String DOT = ".";

    /* --- Members --- */

    private  Properties config;

    /* --- Constructors --- */

    public ComponentScan(Properties config) {
        this.config = config;
    }

    /* --- Methods --- */

    public String scan() {
        logger.info("Starting Analysis - component scan has started");
        String directory = config.getProperty(DIRECTORY);
        String[] directories = directory.split(FOLDER_DELIMITER);
        ArrayList<String> scannerBaseDirs = new ArrayList<>(Arrays.asList(directories));
        if (!scannerBaseDirs.isEmpty()) {
            logger.info("Getting properties");
            // configure properties
            FSAConfiguration fsaConfiguration = new FSAConfiguration(config);
            // set default values in case of missing parameters
            ResolverConfiguration resolverConfiguration = fsaConfiguration.getResolver();
            String[] includes = config.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY) != null ?
                    config.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX) : ExtensionUtils.INCLUDES;
            String[] excludes = config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY) != null ?
                    config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX) : ExtensionUtils.EXCLUDES;
            String[] acceptExtensionsList = (String[]) config.get(ConfigPropertyKeys.ACCEPT_EXTENSIONS_LIST);
            boolean globCaseSensitive = config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY) != null ?
                    Boolean.valueOf(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY)) : false;
            boolean followSymlinks = config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY) != null ?
                    Boolean.valueOf(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY)) : false;
            Collection<String> excludedCopyrights = fsaConfiguration.getAgent().getExcludedCopyrights();
            excludedCopyrights.remove("");
            //todo hasScmConnectors[0] in future - no need for cx
            // Resolving dependencies
            logger.info("Resolving dependencies");
            // via should not run for componentScan
            Collection<AgentProjectInfo> projects = new FileSystemScanner(resolverConfiguration, fsaConfiguration.getAgent(), false).createProjects(
                    scannerBaseDirs, false, includes, excludes, globCaseSensitive, fsaConfiguration.getAgent().getArchiveExtractionDepth(),
                    fsaConfiguration.getAgent().getArchiveIncludes(), fsaConfiguration.getAgent().getArchiveExcludes(), fsaConfiguration.getAgent().isArchiveFastUnpack(),
                    followSymlinks, excludedCopyrights, fsaConfiguration.getAgent().isPartialSha1Match(), fsaConfiguration.getAgent().isCalculateHints(),
                    fsaConfiguration.getAgent().isCalculateMd5(), fsaConfiguration.getResolver().getNpmAccessToken()).keySet();
            logger.info("Finished dependency resolution");
            for (AgentProjectInfo project : projects) {
                project.setProjectToken(EMPTY_PROJECT_TOKEN);
                if (acceptExtensionsList != null && acceptExtensionsList.length > 0) {
                    project.setDependencies(getDependenciesFromExtensionsListOnly(project.getDependencies(), acceptExtensionsList));
                }
            }
//             Return dependencies
            String jsonString = new ConfigurationSerializer().getAsString(projects, true);
            return jsonString;
        } else {
            return "";// new ConfigurationSerializer<>().getAsString(new Collection<AgentProjectInfo>);
        }
    }

    private List<DependencyInfo> getDependenciesFromExtensionsListOnly(Collection<DependencyInfo> dependencies, String[] acceptExtensionsList) {
        LinkedList<DependencyInfo> filteredDependencies = new LinkedList<>();
        for (DependencyInfo dependency : dependencies) {
            for (String extension : acceptExtensionsList) {
                if (dependency.getDependencyType() != null || dependency.getArtifactId().endsWith(DOT + extension) || checkFileName(dependency, extension)) {
                    filteredDependencies.add(dependency);
                    dependency.setChildren(getDependenciesFromExtensionsListOnly(dependency.getChildren(), acceptExtensionsList));
                    break;
                }
            }
        }
        return filteredDependencies;
    }

    private boolean checkFileName(DependencyInfo dependency, String extension) {
        boolean fileNameEndsWithExtension = false;
        if (dependency.getFilename() != null) {
            fileNameEndsWithExtension = dependency.getFilename().endsWith(DOT + extension);
        }
        return fileNameEndsWithExtension;
    }
}

