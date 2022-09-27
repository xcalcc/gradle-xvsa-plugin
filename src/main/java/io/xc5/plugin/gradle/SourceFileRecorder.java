package io.xc5.plugin.gradle;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


public class SourceFileRecorder {

  private static final Logger logger = Logging.getLogger(SourceFileRecorder.class);
  static SourceFileRecorder singleton = null;

  public static SourceFileRecorder i(String srcListFilePath, Logger logger) {
    if (singleton == null)
      singleton = new SourceFileRecorder(srcListFilePath, logger);
    return singleton;
  }

  String srcListFilePath = null;

  public SourceFileRecorder(String pSrcListFilePath, Logger pLogger) {
    srcListFilePath = pSrcListFilePath;
  }

  void preRunGatherSourceList() throws XvsaPluginException {
    if (srcListFilePath == null) return;
    synchronized (this) {
      if (new File(srcListFilePath).getAbsoluteFile().exists()) {
        // Save the file content to a list, and remove it.
        logger.info("Before mapfej, save preexist source list");
        saveExistingListFile(new File(srcListFilePath).getAbsoluteFile());
      }
      if (!new File(srcListFilePath).getAbsoluteFile().getParentFile().canWrite()) {
        throw new XvsaPluginException("Cannot write to the source_files json: " + srcListFilePath);
      }
    }
  }

  void postRunCollectSourceList() throws XvsaPluginException {
    if (srcListFilePath == null) return;
    synchronized (this) {
      if (new File(srcListFilePath).getAbsoluteFile().exists()) {
        logger.info("After mapfej, before saving generated sources list stack size = " + existingFiles.size());
        saveExistingListFile(new File(srcListFilePath).getAbsoluteFile());
      } else {
        logger.warn("After mapfej, the source list file {} does not exist.", new File(srcListFilePath).getAbsolutePath());
      }
      logger.info("Recovering generated sources list, stack size = " + existingFiles.size());
      recoverPreviousListFile(new File(srcListFilePath).getAbsoluteFile());
    }
  }

  List<JSONArray> existingFiles = new LinkedList<>();

  void recoverPreviousListFile(File absoluteFile) throws XvsaPluginException {
    try {
      // Remove duplicates.
      Set<String> totalSet = new HashSet<>();
      for (JSONArray i : existingFiles){
        for (Object obj : i){
          if (obj instanceof String) {
            totalSet.add((String) obj);
          }
        }
      }
      // Transform into JSONArray
      JSONArray totalList = new JSONArray();
      for (String one : totalSet) {
        totalList.put(one);
      }
      // Clear the list
      existingFiles.clear();
      // Save back to file
      FileWriter fileWriter = new FileWriter(absoluteFile);
      fileWriter.write(totalList.toString());
      fileWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
      throw new XvsaPluginException("Cannot save back source files list file: " + absoluteFile.getPath());
    }
  }

  void saveExistingListFile(File absoluteFile) throws XvsaPluginException{
    if (!absoluteFile.exists() || !absoluteFile.canRead()) {
      throw new XvsaPluginException("Cannot read srclist file : " + absoluteFile.getPath());
    }
    try {
      FileReader reader = new FileReader(absoluteFile);
      JSONTokener tokener = new JSONTokener(reader);
      JSONArray sourceList = new JSONArray(tokener);
      existingFiles.add(sourceList);
      logger.info("Added one existing source list json to list, file count = " + sourceList.length() +
              ", stack size = " + existingFiles.size());
      reader.close();
      if (!absoluteFile.delete()) {
        throw new XvsaPluginException("Cannot delete srclist file : " + absoluteFile.getPath());
      }
    } catch (JSONException e) {
      logger.warn("Previous json file is empty or not valid JSON format, skipping loading");
    } catch (IOException e) {
      e.printStackTrace();
      throw new XvsaPluginException("Cannot read file into JSONArray : " + absoluteFile.getPath());
    }
  }

  public String getJfeOption() {
    return "-srcPathOutput," + new File(srcListFilePath).getAbsoluteFile();
  }
}
