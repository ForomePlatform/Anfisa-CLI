/*
 * Copyright (c) 2019. Partners HealthCare and other members of
 * Forome Association
 *
 *  Developed by Andrei Pestov and Michael Bouzinier, based on contributions by
 *  members of Division of Genetics, Brigham and Women's Hospital
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.forome.cli.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class Util {

    private static final String XL2WS = "xl2ws";
    private static final String ON = "adm_ds_on";
    private static final String JOB_STATUS = "job_status";
    private Configuration configuration;

    public Util(Map<String,String> args) {
        try (FileReader fileReader = new FileReader(args.get("config"))) {
            JsonObject object = new JsonParser().parse(fileReader).getAsJsonObject();
            this.configuration = new Configuration(object.get("baseUrl").getAsString(),
                    args.get("parent"),
                    args.get("rule"),
                    args.get ("ds"),
                    object.get("login").getAsString(),
                    object.get("password").getAsString()
            );
            System.out.printf("Yours configuration is %s\n", configuration);
        } catch (Exception e) {
            System.out.println("File not found! ");
            throw new RuntimeException();
        }
    }

    public boolean activateWorkspace() {
            try {
                String userCredentials = configuration.getLogin() + ":" + configuration.getPassword();
                String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userCredentials.getBytes()));
                URL url = new URL(configuration.getBaseUrl() + ON);
                String urlParameters = String.format("ds=%s", encode(configuration.getDatasetName ()));
                HttpURLConnection connection = getHttpURLConnection(url, basicAuth, urlParameters);
                System.out.printf("Request url is '%s'\n", connection.getURL() + urlParameters);
                if (HttpURLConnection.HTTP_OK == connection.getResponseCode()) {
                    InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
                    inputStreamReader.close();
                    connection.disconnect();
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

    public boolean createWorkspace() {
        try {
            String userCredentials = configuration.getLogin() + ":" + configuration.getPassword();
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userCredentials.getBytes()));
            URL url = new URL(configuration.getBaseUrl() + XL2WS);
            String urlParameters = String.format("ds=%s&std_name=%s&ws=%s&force=1",
                    encode(configuration.getParentDatasetName ()),
                    encode(configuration.getTreeName()),
                    encode(configuration.getDatasetName ()));
            HttpURLConnection connection = getHttpURLConnection(url, basicAuth, urlParameters);
            System.out.printf("Request url is '%s'\n", connection.getURL() + urlParameters);
            if (HttpURLConnection.HTTP_OK == connection.getResponseCode()) {
                InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
                JsonObject obj = new JsonParser().parse(inputStreamReader).getAsJsonObject();
                inputStreamReader.close();
                connection.disconnect();
                String taskId = obj.get("task_id").getAsString();
                System.out.println("Sending a request for workspace creation. Task id:" + taskId);
                while (isCreated(taskId, basicAuth)) {
                    System.out.println("Checking the workspace creation status.  Task id:" + taskId);
                    Thread.sleep(5000);
                }
                return true;
            }
            if (HttpURLConnection.HTTP_NOT_FOUND == connection.getResponseCode()) {
                System.out.println("The URL is incorrect. Please correct your \"baseUrl\" in a configuration file" +
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
        System.out.println("Workspace has not been created yet, please wait");
        return false;
    }

    private static HttpURLConnection getHttpURLConnection (URL url, String basicAuth, String urlParameters) throws IOException {
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

    private static String encode (String str) {
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
        private String parentDatasetName;
        private String treeName;
        private String datasetName;
        private String login;
        private String password;

        @Override
        public String toString() {
            return String.format("baseUrl: '%s', login: '%s', password: '%s', parent dataset: '%s', treeName: '%s', " +
                    "dataset: '%s'", baseUrl, login, password,
                parentDatasetName, treeName,
                datasetName);
        }
    }
}
