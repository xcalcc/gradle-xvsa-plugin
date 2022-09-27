package io.xc5.plugin.gradle;

import org.gradle.api.Project;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;


/**
 * Configuration options for the Xvsa plugin. All options have sensible defaults.
 * See README.md for more information
 *
 * <p>Below is a full configuration example. Since all properties have sensible defaults,
 * typically only selected properties will be configured.
 * <p>
 * apply plugin: "java"
 * apply plugin: "io.xc5.plugin.gradle"
 * <p>
 * xvsa {
 * xvsaHome  = "<Absolute path for xvsa tools>"
 * outputDir = "$project/build/target"
 * rtObject  = "<Absolute path for rt object>"
 * runVsa    = false
 * jfeArgs   = "-allow-phantom-refs=true"
 * jvmArgs   = "-Xms512m -Xmx5120m"
 * srcListFilePath = "<Absolute path sources.json>"
 * }
 */
public class XvsaExtension {
  public static String name = "xvsa";
  // Input/Output Locations
  private String xvsaHome;
  private String outputDir;
  private String rtObject = "<null>";
  private String srcListFilePath;

  private boolean runVsa = false;
  private boolean ignoreError = false;

  /**
   * Whether the plugin should generate all the libraries
   */
  private boolean libGeneration = false;

  /***
   * Whether we should exclude all libraries from generation for V-Table by-default
   */
  private boolean excludeAllLibraryByDefault = false;

  /***
   * Skip running JFE, instead, output the dependency list only
   * For running JFE later in remote environments.
   */
  private boolean skipJfe = false;

  // Run options
  private Collection<String> jfeArgs = new ArrayList<>();
  private Collection<String> jvmArgs = new ArrayList<>();
  private Collection<String> vsaArgs = new ArrayList<>();
  private Collection<String> libJarFilter = new ArrayList<>();
  private Collection<String> libClassFilter = new ArrayList<>();

  // Set methods
  public void setXvsaHome(String homeDir) {
    xvsaHome = homeDir;
  }

  public void setOutputDir(String outDir) {
    outputDir = outDir;
  }

  public void setRtObject(String rtObj) {
    rtObject = rtObj;
  }

  public void setRunVsa(boolean v) {
    runVsa = v;
  }

  public void setIgnoreError(boolean ignoreError) {
    this.ignoreError = ignoreError;
  }

  public void setJfeArgs(Collection<String> args) {
    jfeArgs = args;
  }

  public void setJvmArgs(Collection<String> args) {
    jvmArgs = args;
  }

  public void setVsaArgs(Collection<String> args) {
    vsaArgs = args;
  }

  public void setSrcListFilePath(String s) {
    srcListFilePath = s;
  }

  public void setExcludeAllLibraryByDefault(boolean excludeAllLibraryByDefault) {
    this.excludeAllLibraryByDefault = excludeAllLibraryByDefault;
  }

  public void setLibGeneration(boolean libGeneration) {
    this.libGeneration = libGeneration;
  }

  public void setLibJarFilter(Collection<String> libJarFilter) {
    this.libJarFilter = libJarFilter;
  }

  public void setLibClassFilter(Collection<String> libClassFilter) {
    this.libClassFilter = libClassFilter;
  }

  public void setSkipJfe(boolean skipJfe) {
    this.skipJfe = skipJfe;
  }

  // Get methods
  public String getXvsaHome() {
    return xvsaHome;
  }

  public String getOutputDir() {
    return outputDir;
  }

  public String getRtObject() {
    return rtObject;
  }

  public boolean isRunVsa() {
    return runVsa;
  }

  public boolean isIgnoreError() {
    return ignoreError;
  }

  public boolean isLibGeneration() {
    return libGeneration;
  }

  public Collection<String> getLibJarFilter() {
    return libJarFilter;
  }

  public Collection<String> getLibClassFilter() {
    return libClassFilter;
  }

  public boolean isExcludeAllLibraryByDefault() {
    return excludeAllLibraryByDefault;
  }

  public Collection<String> getJfeArgs() {
    return jfeArgs;
  }

  public Collection<String> getJvmArgs() {
    return jvmArgs;
  }

  public Collection<String> getVsaArgs() {
    return vsaArgs;
  }

  public String getSrcListFilePath() {
    return srcListFilePath;
  }

  public boolean isSkipJfe() {
    return skipJfe;
  }

  // set Default value
  public void setDefValue(Project project) {
    if (project.hasProperty("XVSA_HOME")) {
      xvsaHome = (String) project.property("XVSA_HOME");
    }
    if (project.hasProperty("XVSA_GRADLE_OUTPUT")) {
      outputDir = (String) project.property("XVSA_GRADLE_OUTPUT");
    } else {
      outputDir = Paths.get(project.getBuildDir().getAbsolutePath(), "target").toString();
    }
    if (project.hasProperty("XVSA_SRC_LIST") && project.property("XVSA_SRC_LIST") != null) {
      srcListFilePath = new File((String) project.property("XVSA_SRC_LIST")).getAbsolutePath();
    } else {
      srcListFilePath = Paths.get(project.getBuildDir().getAbsolutePath(), "sources.json").toString();
    }
    if (project.hasProperty("XVSA_JFE_OPT")) {
      String[] splitted = ((String) Objects.requireNonNull(project.property("XVSA_JFE_OPT"))).split(",");
      jfeArgs.addAll(Arrays.asList(splitted));
    }
    if (project.hasProperty("XVSA_JVM_OPT")) {
      String[] splitted = ((String) Objects.requireNonNull(project.property("XVSA_JVM_OPT"))).split(",");
      jvmArgs.addAll(Arrays.asList(splitted));
    }
    if (project.hasProperty("XVSA_LIB_GEN")) {
      String property = Objects.requireNonNull(project.property("XVSA_LIB_GEN")).toString();
      libGeneration = ("true".equals(property) || "1".equals(property));
    }
    if (project.hasProperty("XVSA_LIB_JAR_FILTER")) {
      String property = Objects.requireNonNull(project.property("XVSA_LIB_JAR_FILTER")).toString();
      libJarFilter.addAll(Arrays.asList(property.split(",")));
    }
    if (project.hasProperty("XVSA_LIB_CLASS_FILTER")) {
      String property = Objects.requireNonNull(project.property("XVSA_LIB_CLASS_FILTER")).toString();
      libClassFilter.addAll(Arrays.asList(property.split(",")));
    }
    if (project.hasProperty("XVSA_IGNORE_ERROR")) {
      String property = Objects.requireNonNull(project.property("XVSA_IGNORE_ERROR")).toString();
      ignoreError = ("true".equals(property) || "1".equals(property));
    }
    if (project.hasProperty("XVSA_JFE_SKIP")) {
      String property = Objects.requireNonNull(project.property("XVSA_JFE_SKIP")).toString();
      skipJfe = ("true".equals(property) || "1".equals(property));
    }
  }
}
