package org.whitesource.agent.dependency.resolver.docker;

import org.whitesource.agent.api.model.DependencyInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;

import static org.whitesource.agent.dependency.resolver.docker.DockerResolver.*;

/**
 * @author chen.luigi
 */
public abstract class AbstractParser {

    /* --- Constructors --- */

    public AbstractParser() {

    }

    /* --- Public methods --- */

    static void closeStream(BufferedReader br, FileReader fr) {
        try {
            if (br != null)
                br.close();
            if (fr != null)
                fr.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /* --- Abstract methods --- */

    public abstract Collection<DependencyInfo> parse(File file);

    public abstract File findFile(String[] files, String filename, String operatingSystem);

    public static void findFolder(File dir, String folderName, Collection<String> folder, String operatingSystem) {
        if (!operatingSystem.startsWith(WINDOWS)){
            folderName = folderName.replace(WINDOWS_SEPARATOR, LINUX_SEPARATOR);
        }
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file :files) {
                    if (file.isDirectory()) {
                        findFolder(file, folderName, folder, operatingSystem);
                        if (file.getName().equals(folderName)) {
                            folder.add(file.getPath());
                        }
                    }
                }
            }
        }
    }
}
