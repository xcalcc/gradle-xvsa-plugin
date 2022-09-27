package io.xc5.plugin.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

public class XvsaFakeTask extends DefaultTask {
  @TaskAction
  void doAction() {
    getLogger().lifecycle("[XVSA Plugin WARN]: Skip xvsa task, no file generated for project " + getProject().getName());
  }
}
