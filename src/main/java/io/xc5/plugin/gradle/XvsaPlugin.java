package io.xc5.plugin.gradle;

import org.gradle.api.*;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileFilter;

public class XvsaPlugin implements Plugin<Project> {
  private static final Logger logger = Logging.getLogger(XvsaPlugin.class);
  static final GradleVersion SUPPORTED_VERSION = GradleVersion.version("2.2");
  private XvsaExtension extension;
  private SourceSet sourceSet;

  @Override
  public void apply(final Project project) {
    // create extension
    extension = project.getExtensions().create(XvsaExtension.name, XvsaExtension.class);

    if(verifyProject(project)) {
      logger.lifecycle("[XVSA plugin]: Apply to project " + project.getName());
      // create xvsaTask
      XvsaTask xvsaTask = project.getTasks().create(XvsaTask.TASK_NAME, XvsaTask.class);
      xvsaTask.setProject(project);
      configureExtension(xvsaTask, project);
      // apply java plugin
      project.getPlugins().apply("java");
      configureSourceSet(xvsaTask, project);
      configureTask(xvsaTask, project);
    } else {
      // create fake xvsaTask
      project.getTasks().create(XvsaTask.TASK_NAME, XvsaFakeTask.class);
    }
  }

  private File[] containsJavaSource(File folder) {
    return folder.listFiles(new FileFilter() {
      @Override
      public boolean accept(File file) {
        if (file.isDirectory()) {
          File[] cands = containsJavaSource(file);
          if (cands != null && cands.length > 0) {
            return true;
          } else {
            return false;
          }
        }
        return file.getName().toLowerCase().endsWith(".java");
      }
    });
  }

  boolean isAndroidProject(Project project) {
    return project.getPlugins().hasPlugin("com.android.application") ||
      project.getPlugins().hasPlugin("com.android.library") ||
      project.getPlugins().hasPlugin("com.android.test") ||
      project.getPlugins().hasPlugin("com.android.feature");
  }

  boolean verifyGradleVersion(GradleVersion version) {
    if (version.compareTo(SUPPORTED_VERSION) < 0) {
      String message = String.format("Gradle version %s is unsupported. Please use %s or later.", version,
          SUPPORTED_VERSION);
      throw new IllegalArgumentException(message);
    }
    return true;
  }

  private boolean verifyProject(Project project) {
    boolean ret = true;
    if(isAndroidProject(project)) {
      logger.lifecycle("[XVSA plugin]: Skip android project " + project.getName());
      ret = false;
    } else {
      verifyGradleVersion(GradleVersion.current());
      File projDir = project.getProjectDir();
      File[] subFolders = containsJavaSource(projDir);
      ret = subFolders != null && subFolders.length != 0;
      if(!ret) {
        logger.lifecycle("[XVSA plugin]: Skip project " + project.getName() + " no java sources");
      }
    }
    return ret;
  }

  private void configureExtension(XvsaTask task, Project project) {
    logger.debug("Configure extension to project :" + project.getName());
    extension.setDefValue(project);

    ConventionMapping taskMapping = task.getConventionMapping();
    taskMapping.map("xvsaHome", extension::getXvsaHome);
    taskMapping.map("srcListFilePath", extension::getSrcListFilePath);
    taskMapping.map("outputFolder", extension::getOutputDir);
    taskMapping.map("ignoreError", extension::isIgnoreError);
    taskMapping.map("jfeArgs", extension::getJfeArgs);
    taskMapping.map("jvmArgs", extension::getJvmArgs);
    taskMapping.map("libJarFilter", extension::getLibJarFilter);
    taskMapping.map("libClassFilter", extension::getLibClassFilter);
    taskMapping.map("libGeneration", extension::isLibGeneration);
    taskMapping.map("excludeAllLibraryByDefault", extension::isExcludeAllLibraryByDefault);
    taskMapping.map("skipJfe", extension::isSkipJfe);
  }

  private void configureSourceSet(XvsaTask task, Project project) {
    logger.debug("Configure sourceset to project :" + project.getName());

    sourceSet = project.getConvention()
        .getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main");
    ConventionMapping taskMapping = task.getConventionMapping();

    taskMapping.map("sourceInfo", sourceSet::getOutput);
    taskMapping.map("sourcePaths", sourceSet::getJava);
    taskMapping.map("compClassPath", sourceSet::getCompileClasspath);
    taskMapping.map("rtClassPath", sourceSet::getRuntimeClasspath);
  }

  private void configureTask(XvsaTask task, Project project) {
    logger.debug("Configure task to project :" + project.getName());
    // set debug debug flag for javac
    // the doFirst may not be called, project may set actions to null(sprint-framework/spring-aspect)
    JavaCompile compileTask = (JavaCompile) project.getTasks().getByName(sourceSet.getCompileJavaTaskName());

    compileTask.doFirst(
      new Action<Task>() {
        @Override
        public void execute(Task task) {
          ((JavaCompile) task.getProject().getTasks()
                  .getByName("compileJava")).getOptions().setIncremental(false).setDebug(true);
        }
      }
    );

    //task.dependsOn(project.getTasks().getByName("clean"));
    task.dependsOn(sourceSet.getCompileJavaTaskName());
  }
}
