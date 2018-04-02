package org.apache.cordova.cifs;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.netbios.NbtAddress;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.io.BufferedInputStream;

/**
 * This class access file over cifs.
 */
public class Cifs extends CordovaPlugin {
  
  private static final int MAX_LENGTH = 1024 * 50;

  private static final String IPADDRESS_PATTERN = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
    + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
    + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
    + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

  private Pattern pattern;

  public Cifs() {
    super();
    pattern = Pattern.compile(IPADDRESS_PATTERN);
  }

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    if(action.equals("exist")) {
      String url = args.getString(0);
      this.exist(url, callbackContext);
      return true;
    } else if(action.equals("dir")) {
      String url = args.getString(0);
      this.dir(url, callbackContext);
      return true;
    } else if(action.equals("getfiles")) {
      String url = args.getString(0);
      this.getfiles(url, callbackContext);
      return true;
    } else if(action.equals("download")) {
      String url = args.getString(0);
      this.download(url, callbackContext);
      return true;
    }
    return false;
  }

  private String resolveHostname(String url) throws Exception {
    int atIdx = url.indexOf("@");
    String name = null;
    if (atIdx > -1) {
      name = url.substring(atIdx + 1, url.indexOf("/", atIdx));
    } else {
      int doubleSlashIdx = url.indexOf("//");
      if (doubleSlashIdx == -1) throw new MalformedURLException("url is invalid");
      name = url.substring(doubleSlashIdx + 2, url.indexOf("/", doubleSlashIdx + 2));
    }
    Matcher matcher = pattern.matcher(name);
    if (matcher.matches()) { // IP address
      return url;
    }
    // hostname
    InetAddress addr = NbtAddress.getByName(name).getInetAddress();
    return url.replace(name, addr.getHostAddress());
  }

  /**
   * exist (true or false) 
   */
  private void exist(final String url, final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          url = resolveHostname(url);
          SmbFile smbFile = new SmbFile(url);
          callbackContext.success(Boolean.toString(smbFile.exists()));
        } catch (Exception e) {
          callbackContext.error("Exception: " + e.getMessage());
        }
      }
    });
  }

  /**
   * file lists (JSONArray)
   */
  private void dir(final String url, final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          url = resolveHostname(url);
          SmbFile smbFile = new SmbFile(url);
          if (smbFile.isFile()) {
            callbackContext.error("Only Directory can be processed.");
            return;
          }
          JSONArray jsonfiles = new JSONArray();
          SmbFile[] files = smbFile.listFiles();
          for (SmbFile file : files) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", file.getName());
            jsonObject.put("length", file.length());
            jsonObject.put("url", file.getCanonicalPath());
            jsonObject.put("directory", file.isDirectory());
            jsonfiles.put(jsonObject);
          }
          callbackContext.success(jsonfiles);
        } catch (Exception e) {
          callbackContext.error("Exception: " + e.getMessage());
        }
      }
    });
  }

  /**
   * get all files include subfolder (JSONArray)
   */
  private void getfiles(final String url, final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          url = resolveHostname(url);
          SmbFile smbFile = new SmbFile(url);
          if (smbFile.isFile()) {
            callbackContext.error("Only Directory can be processed.");
            return;
          }
          iterator(smbFile, callbackContext);
          JSONObject resp = new JSONObject();
          resp.put("status", "finished");
          callbackContext.success(resp);
        } catch (Exception e) {
          callbackContext.error("Exception: " + e.getMessage());
        }
      }

      private void iterator(SmbFile smbFile, CallbackContext callbackContext) throws Exception {
        JSONObject resp = new JSONObject();
        resp.put("status", "processing");
        JSONArray jsonfiles = new JSONArray();
        SmbFile[] files = smbFile.listFiles();
        for (SmbFile file : files) {
          if (file.isDirectory() && !file.getName().endsWith("$/")) {
            iterator(file, callbackContext);
          }
          if (file.isFile()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", file.getName());
            jsonObject.put("length", file.length());
            jsonObject.put("url", file.getCanonicalPath());
            jsonObject.put("directory", file.isDirectory());
            jsonfiles.put(jsonObject);
          }
        }
        resp.put("files", jsonfiles);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, resp);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
      }
    });
  }

  /**
   * download file (filename/percent/status)
   */
  private void download(final String url, final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          url = resolveHostname(url);
          final SmbFile smbFile = new SmbFile(url);
          if(!smbFile.exists()) {
            callbackContext.error("File not existed.");
            return;
          }
          if(smbFile.isDirectory()) {
            callbackContext.error("Directory can not be downloaded.");
            return;
          }
          long fileSize = smbFile.length();
          // System.out.println("File size:" + fileSize);
          BufferedInputStream inputStream = new BufferedInputStream(smbFile.getInputStream());
          File tempFile = File.createTempFile("cifs", "");
          JSONObject jsonObject = new JSONObject();
          jsonObject.put("status", "downloading");
          jsonObject.put("filename", tempFile.getName());
          FileOutputStream outputStream = new FileOutputStream(tempFile);
          byte[] bytes = new byte[MAX_LENGTH];
          long loadedSize = 0;
          int count = -1;
          while((count = inputStream.read(bytes, 0, MAX_LENGTH)) > -1) {
            outputStream.write(bytes, 0, count);
            loadedSize += count;
            String percent = String.format("%.1f%%", loadedSize * 100.0 / fileSize);
            jsonObject.put("percent", percent);
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, jsonObject);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
            // System.out.printf("Download Rate: %.1f%%\n", loadedSize * 100.0 / fileSize);
          }
          inputStream.close();
          outputStream.close();
    
          jsonObject.put("status", "finished");
          callbackContext.success(jsonObject);
        } catch (Exception e) {
          callbackContext.error("Exception: " + e.getMessage());
        }
      }
    });
  }
}
