package io.xc5.plugin.gradle;


import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.util.GradleVersion;
import org.json.JSONArray;

import javax.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class XvsaTask extends ConventionTask {
  private Logger logger = getLogger();
  static final String TASK_NAME = "xvsa";
  static final GradleVersion NEW_VERSION = GradleVersion.version("4.0");
  private static final String LOG_PREFIX = "[XVSA] ";
  // mapping values, do not use them directly
  private String xvsaHome;
  private SourceSetOutput sourceInfo;
  private FileCollection compClassPath;
  private FileCollection rtClassPath;
  private FileCollection sourcePaths;
  private String srcListFilePath;
  private String outputFolder;
  private ArrayList<String> jfeArgs;
  private ArrayList<String> jvmArgs;
  private ArrayList<String> vsaArgs;
  private boolean ignoreError;
  private boolean skipJfe;

  /**
   * Library Generation Options
   * */
  private boolean libGeneration = false;
  private ArrayList<String> libJarFilter = new ArrayList<>();
  private boolean excludeAllLibraryByDefault = true;
  private ArrayList<String> libClassFilter  = new ArrayList<>();
  private boolean libClassBlackList = true;

  private Project project;
  private HashSet<String> classPath;
  private File jfeFile;
  private HashSet<String> inputPath;
  private File outputFile;
  public static boolean verified = false;


  @Inject
  public XvsaTask() {
    jfeArgs = new ArrayList<>();
    jvmArgs = new ArrayList<>();
    vsaArgs = new ArrayList<>();
    outputFolder = "<null>";
    xvsaHome = "<null>";
    srcListFilePath = "<null>";
    ignoreError = false;
  }

  public void setProject(Project proj) {
    project = proj;
  }

  @Input
  public String getXvsaHome() {
    return xvsaHome;
  }

  @Input
  public SourceSetOutput getSourceInfo() {
    return sourceInfo;
  }

  @Input
  public FileCollection getRtClassPath() {
    return rtClassPath;
  }

  @Input
  public FileCollection getCompClassPath() {
    return compClassPath;
  }

  @Input
  public FileCollection getSourcePaths() {
    return sourcePaths;
  }

  @Input
  public String getOutputFolder() {
    return outputFolder;
  }

  @Input
  public ArrayList<String> getJfeArgs() {
    return jfeArgs;
  }

  @Input
  public String getSrcListFilePath() {
    return srcListFilePath;
  }

  @Input
  public ArrayList<String> getJvmArgs() {
    return jvmArgs;
  }

  @Input
  public ArrayList<String> getVsaArgs() {
    return vsaArgs;
  }

  @Input
  public boolean isIgnoreError() {
    return ignoreError;
  }

  @Input
  public boolean isLibGeneration() {
    return libGeneration;
  }

  @Input
  public ArrayList<String> getLibJarFilter() {
    return libJarFilter;
  }

  @Input
  public boolean isExcludeAllLibraryByDefault() {
    return excludeAllLibraryByDefault;
  }

  @Input
  public ArrayList<String> getLibClassFilter() {
    return libClassFilter;
  }

  @Input
  public boolean isLibClassBlackList() {
    return libClassBlackList;
  }

  @Input
  public boolean isSkipJfe() {
    return skipJfe;
  }

  private ArrayList<File> getClassesDir() {
    ArrayList<File> ret = new ArrayList<>();
    if (GradleVersion.current().compareTo(NEW_VERSION) < 0) {
      ret.add(getSourceInfo().getClassesDir());
    } else {
      ret.addAll(getSourceInfo().getClassesDirs().getFiles());
    }
    return ret;
  }

  private String getInputPathString() {
    String ret = new String();
    for (String path : inputPath) {
      ret += " " + path;
    }
    return ret;
  }

  private boolean prepareBasic() {
    if (!prepareInput()) {
      return false;
    }
    if (!prepareOutput()) {
      return false;
    }
    prepareClassPath();
    return true;
  }

  private boolean prepare() {
    // Check whether mapfej exists
    if (!prepareBinary()) {
      return false;
    }
    // Verify if jfeArgs are working
    if (!verified && !verifyArgs()) {
      return false;
    }
    return true;
  }

  private boolean verifyArgs() {
    String dummyInput = Paths.get(project.getProjectDir().getAbsolutePath(), "xvsa.verify.class")
                             .toFile().getAbsolutePath();
    String dummyObj = Paths.get(project.getProjectDir().getAbsolutePath() , "xvsa.dummy.o")
                          .toFile().getAbsolutePath();
    File tempClass = new File(dummyInput);
    if (!tempClass.exists()) {
      try {
        tempClass.createNewFile();
      } catch (IOException e) {
        logInfo("Unable to create tempClass file");
        return false;
      }
    }

    List<String> cmd = genVerifyCmd(dummyInput, dummyObj);
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      ExecResult rs = getProject().exec(new Action<ExecSpec>() {
        @Override
        public void execute(ExecSpec execSpec) {
          execSpec.setCommandLine(cmd.toArray());
          execSpec.setStandardOutput(outputStream);
        }
      });
    } catch (Exception e) {
      String cmdStr = " ";
      if (cmd != null) {
        cmdStr = String.join(cmdStr, cmd);
      }
      throw new GradleException(
        "[XVSA PLUGIN]: JFE arg verify failed, check xvsa.jfeArgs property, exception : " + e.toString() + ", cmd : " + cmdStr
      );
    } finally {
      if (tempClass.exists()) {
        tempClass.delete();
      }
    }
    verified = true;
    return true;
  }

  private boolean prepareBinary() {
    logInfo("Prepare Binary");
    String binroot = getXvsaHome();
    if (binroot == null) {
      throw new GradleException("[XVSA PLUGIN]: xvsa home not set, check extension xvsa.xvsaHome");
    }

    jfeFile = Paths.get(binroot, "lib", "1.0", "mapfej").toFile();
    if (!jfeFile.exists()) {
      throw new GradleException("[XVSA PLUGIN] xvsa file " + jfeFile.getAbsolutePath() +
          " does not exists, check extension xvsa.xvsaHome");
    }
    return true;
  }

  private boolean prepareInput() {
    logInfo("Prepare Input");
    inputPath = new HashSet<>();
    for (File classDir : getClassesDir()) {
      if (classDir.exists()) {
        inputPath.add(classDir.getAbsolutePath());
      }
    }
    if (inputPath.size() <= 0) {
      logInfo("class inputPath does not exists");
      return false;
    }
    return true;
  }

  private boolean prepareOutput() {
    logInfo("Prepare output");
    String outputDir = getOutputFolder();
    File outputDirFile = new File(outputDir);
    if (!outputDirFile.exists()) {
      outputDirFile.mkdirs();
    }
    if (!outputDirFile.exists()) {
      logInfo("OutputDir " + outputDir + "does not exists");
      return false;
    }
    String projectName = project.getName();
    if (projectName == null) {
      projectName = "output";
    }
    outputFile = new File(outputDir , projectName + ".o");
    return true;
  }

  private void prepareClassPath() {
    logInfo("Prepare classpath");
    classPath = new HashSet<>();
    for (File cp : getCompClassPath().getFiles()) {
      if (cp.exists()) {
        classPath.add(cp.getAbsolutePath());
      }
    }
    for (File rtcp : getRtClassPath().getFiles()) {
      if (rtcp.exists()) {
        classPath.add(rtcp.getAbsolutePath());
      }
    }
  }

  private void addBasicOptions(List<String> cmdList) {
    logInfo("addBasicOptions");
    cmdList.add("-allow-phantom-refs=true");
    for (String arg : getJfeArgs()) {
      cmdList.add(arg);
    }
  }

  /***
   * Dump the source file list directly by gradle, to allow agent to pick them up.
   */
  private void dumpSourceFileList() {
    logger.info("Dumping source file");

    JSONArray jsonArray = new JSONArray();
    for (File oneSrcOrDir : getSourcePaths().getFiles()) {
      if (oneSrcOrDir.exists() && oneSrcOrDir.isFile()) {
        jsonArray.put(oneSrcOrDir);
      }
    }

    // Use the existing way of writing the list to a file.
    SourceFileRecorder sfr = SourceFileRecorder.i(srcListFilePath, logger);
    sfr.existingFiles.add(jsonArray);
    File outputDir = new File(srcListFilePath);
    if (!outputDir.isDirectory()) {
      outputDir = outputDir.getParentFile();
    }
    File outputSrcListFile = new File(outputDir, project.getName() + ".src.list");

    logger.info("Dump the source list to file: {}, with a list of size = {}", outputSrcListFile, jsonArray.length());
    sfr.recoverPreviousListFile(outputSrcListFile);
  }

  /***
   * This function used to work to list up all the possible source code directory for JFE to match.
   * @deprecated
   * @param cmdList the list to append to
   */
  private void addSourceDirs(List<String> cmdList) {
    getLogger().info("all src path: " + getSourcePaths().getFiles().size());
    Set<String> allPossibleRoots = new HashSet<>();
    for (File srcDir : getSourcePaths().getFiles()) {
      if (srcDir.exists() && srcDir.isFile()) {
        // Add all parents
        File possibleRoot = srcDir.getParentFile();
        while (possibleRoot != null) {
          if (possibleRoot.exists() &&
                  possibleRoot.isDirectory() &&
                  !allPossibleRoots.contains(possibleRoot.getAbsolutePath())) {
            allPossibleRoots.add(possibleRoot.getAbsolutePath());
            cmdList.add("-srcdir=" + possibleRoot.getAbsolutePath());
          }
          possibleRoot = possibleRoot.getParentFile();
        }
      } else if (srcDir.exists() && srcDir.isDirectory() && !allPossibleRoots.contains(srcDir.getAbsolutePath())) {
        getLogger().info("one src path: " + srcDir);
        cmdList.add("-srcdir=" + srcDir.getAbsolutePath());
      } else {
        getLogger().info("invalid src path: " + srcDir);
      }
    }
    //cmdList.add(SourceFileRecorder.i(getSrcListFilePath(), getLogger()).getJfeOption());
  }

  private List<String> genVerifyCmd(String input, String output) {
    logInfo("genVerifyCmd");
    List<String> cmdList = new ArrayList<>();
    cmdList.add(jfeFile.getAbsolutePath());
    cmdList.add("-fC," + input);
    cmdList.add("-fB," + output);
    addBasicOptions(cmdList);
    addSourceDirs(cmdList);
    cmdList.add("-h");
    return cmdList;
  }

  private List<String> genJFEApplicationGenCmd() {
    logInfo("genJFEApplicationGenCmd");
    List<String> cmdList = new ArrayList<>();
    cmdList.add(jfeFile.getAbsolutePath());
    for (String input : inputPath) {
      cmdList.add("-fD," + input);
      cmdList.add("-cp=" + input);
    }
    cmdList.add("-fB," + outputFile.getAbsolutePath());
    for (String cp : classPath) {
      cmdList.add("-cp=" + cp);
    }
    addBasicOptions(cmdList);
    addSourceDirs(cmdList);
    return cmdList;
  }

  private List<String> genJFELibraryGenCmd() {
    logInfo("genJFELibraryGenCmd");
    List<String> cmdList = new ArrayList<>();
    cmdList.add(jfeFile.getAbsolutePath());
    addBasicOptions(cmdList);
    cmdList.add("-VTABLE=true");
    cmdList.add("-libGenOnly=true");
    cmdList.add("-libFilterBlackList=" + (libClassBlackList ? "true" : "false"));
    for (String oneCriteria : libClassFilter) {
      cmdList.add("-libFilter=" + oneCriteria);
    }
    return cmdList;
  }

  private void logInfo(String message) {
    getLogger().info(LOG_PREFIX + message);
  }

  private void dumpProperties() {
    logInfo("Dump properties.");
    logger.info("\t\txvsaHome : " + getXvsaHome());
    logger.info("\t\tignoreError : " + isIgnoreError());
  }

  @TaskAction
  void doAction() {
    logInfo("Action start for " + project.getName());
    dumpProperties();

    if (!prepareBasic()) {
      logger.warn("Skip project due to preparation exception: " + project.getName());
      return;
    }

    // Dump basic info for agent that works without JFE.
    // Or for verifier use even with JFE
    dumpProjectInfoToProperties();
    dumpSourceFileList();

    // Return if we do not go further to run JFE.
    if (isSkipJfe()) {
      logger.warn("Finishing project {} for no-JFE mode.", project.getName());
      return;
    }

    // The following line will try to invoke JFE to verify if user's options are acceptable
    if (!prepare()) {
      logger.warn("Skip project " + project.getName());
      return;
    }

    List<String> cmd = genJFEApplicationGenCmd();
    executeCmd(cmd);

    // Generate all libraries.
    genLibraryVTables();
    logInfo(project.getName() + " output: " + outputFile.getAbsolutePath());
  }

  /**
   *  Generate JAR libraries' VTable objects, under the output directory with a
   *  transformed name of the library jar file name.
   * */
  private void genLibraryVTables() {
    if (!libGeneration) {
      // Skipping library generation
      logger.warn("Skipping V-Table generation for libraries");
      return;
    }
    List<String> libraryGenCmd = genJFELibraryGenCmd();
    File outputFolder = outputFile.getParentFile();
    List<String> generatedLibraries = new LinkedList<>();
    for (String cp : classPath) {
      File jarFile = new File(cp);
      // Only apply when library is jar file and not folder.
      if(jarFile.exists() && !jarFile.isDirectory()) {
        // Apply the library jar file / dir name filter
        boolean librarySelected = isLibrarySelected(jarFile);
        if (!librarySelected)
          continue;
        File libraryOutputLocation = new File(outputFolder,
                jarFile.getName().replaceAll("\\.", "-").replaceAll(":", "-") + ".o");
        generatedLibraries.add(libraryOutputLocation.getName());

        // Skip the library if we have already generated it in other modules
        if (libraryOutputLocation.exists()) {
          logger.warn("Library dependency file generated before: {}, using pre-exisiting one under : {}", jarFile.getAbsolutePath(),
                  libraryOutputLocation.getAbsolutePath());
          continue;
        }

        logger.info("Generating library for {}", jarFile.getAbsolutePath());
        logger.info("Output under {}", libraryOutputLocation);
        libraryGenCmd.add("-fC," + jarFile.getAbsolutePath());
        libraryGenCmd.add("-fB," + libraryOutputLocation.getAbsolutePath());
        executeCmd(libraryGenCmd);
        libraryGenCmd.remove(libraryGenCmd.size() - 1);
        libraryGenCmd.remove(libraryGenCmd.size() - 1);
        logger.info("Complete generation for library for {}", jarFile.getAbsolutePath());
      } else {
        logger.warn("Cannot find one library file {}", cp);
      }
    }

    // Write the involved libraries for such target to a separate properties file.
    File propertiesFile = new File(getOutputFolder(), project.getName() + ".lib.output.list").getAbsoluteFile();
    writeListToFile(generatedLibraries, propertiesFile, "\n");
  }

  private void dumpProjectInfoToProperties() {
    // Write the Jar used for generating this module.
    File projectDependenciesFile = new File(getOutputFolder(), project.getName() + ".lib.list").getAbsoluteFile();
    List<String> projectDependenciesList = classPath.stream().filter(x -> {
      // making sure that the file exist and is a valid file, not a directory in some cases
      File clazzPathFile = new File(x);
      return clazzPathFile.exists();
    }).map(x -> new File(x).getAbsolutePath()).collect(Collectors.toList());
    writeListToFile(projectDependenciesList, projectDependenciesFile, "\n");

    File projectFolderListFile = new File(getOutputFolder(), project.getName() + ".dir.list").getAbsoluteFile();
    List<String> projectFolders = inputPath.stream().filter(x -> {
      // making sure that the file exist and is a valid file, not a directory in some cases
      File clazzPathFile = new File(x);
      return clazzPathFile.exists();
    }).map(x -> new File(x).getAbsolutePath()).collect(Collectors.toList());
    writeListToFile(projectFolders, projectFolderListFile, "\n");
  }

  /***
   * Write a list of strings to a file.
   */
  private void writeListToFile(List<String> stringList, File fileName, String separator) throws GradleException {
    FileWriter writer = null;
    boolean preprendColon = false;
    try {
      logger.info("Writing property file under " + fileName.getAbsolutePath());
      writer = new FileWriter(fileName);
      for (String one: stringList) {
        // write a comma starting from the second file name
        if (preprendColon)
          writer.write(separator);
        else
          preprendColon = true;
        // write the name
        writer.write(one);
      }
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
      throw new GradleException("Writing properties file failed under " + fileName.getAbsolutePath() );
    }
  }

  /***
   * This function is used for selecting jars to generate,
   * we could use libJarFilter and excludeAllLibraryByDefault
   * to specify which of the libraries we would like to generate
   * @param jarFile file to be checked
   * @return true if we should visit this library
   */
  private boolean isLibrarySelected(File jarFile) {
    boolean librarySelected = excludeAllLibraryByDefault;
    for (String oneCriteria : libJarFilter) {
      if (jarFile.getName().startsWith(oneCriteria)) {
        // matching item, skip this if we are in black list mode
        librarySelected = !excludeAllLibraryByDefault;
        break;
      }
    }
    return librarySelected;
  }

  private void executeCmd(List<String> cmd) {
    logInfo("JFE commands:" + Arrays.toString(cmd.toArray()));
    try {
      // SourceFileRecorder.i(getSrcListFilePath(), getLogger()).preRunGatherSourceList();
      ExecResult rs = getProject().exec(new Action<ExecSpec>() {
        @Override
        public void execute(ExecSpec execSpec) {
          execSpec.setCommandLine(cmd.toArray());
        }
      });
      // SourceFileRecorder.i(getSrcListFilePath(), getLogger()).postRunCollectSourceList();
    } catch (Exception e) {
      e.printStackTrace();
      String info = "Project " + project.getName() + "Failed in JFE " + getInputPathString() + " {}";
      outputFile.delete();
      if (isIgnoreError()) {
        logger.error(info, e);
      } else {
        throw new GradleException(info);
      }
    }
  }
}
