package org.forome.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Util {

    private static final String XL2WS = "xl2ws";
    private static final String JOB_STATUS = "job_status";
    private Configuration configuration;

    public Util(String[] args) {
        try (FileReader fileReader = new FileReader(args[0])) {
            JsonObject object = new JsonParser().parse(fileReader).getAsJsonObject();
            this.configuration = new Configuration(object.get("baseUrl").getAsString(),
                    args[1],
                    args[2],
                    args[3],
                    object.get("login").getAsString(),
                    object.get("password").getAsString()
            );
            System.out.printf("Yours configuration is %s\n", configuration);
        } catch (Exception e) {
            System.out.println("File not found! ");
            throw new RuntimeException();
        }
    }

    public boolean createWorkspace() {
        try {
            String userCredentials = configuration.getLogin() + ":" + configuration.getPassword();
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userCredentials.getBytes()));
            URL url = new URL(configuration.getBaseUrl() + XL2WS);
            String urlParameters = String.format("ds=%s&std_name=%s&ws=%s&force=1",
                    encode(configuration.getXlDatasetName()),
                    encode(configuration.getTreeName()),
                    encode(configuration.getWorkspaceName()));
            HttpURLConnection connection = getHttpURLConnection(url, basicAuth, urlParameters);
            System.out.printf("Request url is '%s'\n", connection.getURL() + urlParameters);
            if (HttpURLConnection.HTTP_OK == connection.getResponseCode()) {
                InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
                JsonObject obj = new JsonParser().parse(inputStreamReader).getAsJsonObject();
                inputStreamReader.close();
                connection.disconnect();
                String taskId = obj.get("task_id").getAsString();
                System.out.println("Request for creating workspace was sending. Task id:" + taskId);
                while (isCreated(taskId, basicAuth)) {
                    System.out.println("Checking the workspace creation status.  Task id:" + taskId);
                    Thread.sleep(5000);
                }
                return true;
            }
            if (HttpURLConnection.HTTP_NOT_FOUND == connection.getResponseCode()) {
                System.out.println("The URL is not correct. Please correct your \"baseUrl\" in a configuration file" +
                        "and try again.\n");
            }
            if (HttpURLConnection.HTTP_INTERNAL_ERROR == connection.getResponseCode()) {
                System.out.println("Server error. " + connection.getResponseMessage());
            }
            return false;
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
        }
        return false;
    }

    private boolean isCreated(String taskId, String basicAuth) {
        try {
            URL url = new URL(configuration.getBaseUrl() + JOB_STATUS);
            String urlParameters = String.format("task=%s", taskId);
            HttpURLConnection connection = getHttpURLConnection(url, basicAuth, urlParameters);
            if (HttpURLConnection.HTTP_OK == connection.getResponseCode()) {
                InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
                JsonArray jsonArray = new JsonParser().parse(inputStreamReader).getAsJsonArray();
                inputStreamReader.close();
                connection.disconnect();
                return "Done".equals(jsonArray.get(1).getAsString());
            }
        } catch (IOException e) {
            System.out.println("ERROR: " + e);
        }
        System.out.println("Workspace has not created yet, please wait");
        return false;
    }

    private HttpURLConnection getHttpURLConnection(URL url, String basicAuth, String urlParameters) throws IOException {
        byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("charset", "utf-8");
        connection.setRequestProperty("Content-Length", String.valueOf(postData.length));
        connection.addRequestProperty("Authorization", basicAuth);
        connection.setUseCaches(false);
        try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
            wr.write(postData);
        }
        return connection;
    }

    private String encode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private static class Configuration {
        private String baseUrl;
        private String xlDatasetName;
        private String treeName;
        private String workspaceName;
        private String login;
        private String password;

        @Override
        public String toString() {
            return String.format("baseUrl: '%s', login: '%s', password: '%s', xlDatasetName: '%s', treeName: '%s', " +
                    "workspace name: '%s'", baseUrl, login, password, xlDatasetName, treeName, workspaceName);
        }
    }
}
