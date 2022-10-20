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
import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

public class Util {

    private static final String XL2WS = "ds2ws";
    private static final String ON = "adm_ds_on";
    private static final String JOB_STATUS = "job_status";
    private Configuration configuration;

    private String getBaseURL(JsonObject object)
    {
        try {
            if (object.has ("baseUrl"))
                return object.get ("baseUrl").getAsString ();
            String host = object.get ("host").getAsString ();
            String port = object.get ("port").getAsString ();
            String base = "/";
                if (object.has ("html-context")) {
                    base = object.get ("html-base").getAsString ();
                if (!base.endsWith ("/"))
                    base += "/";
            }
            if ("0.0.0.0".equals (host))
                host = "127.0.0.1";
            return "http://" + host + ":" + port + base;
        } catch (Exception x) {
            throw new RuntimeException ("Failed to determine server URL", x);
        }
    }

    public Util(Map<String,String> args) {
        try (FileReader fileReader = new FileReader(args.get("config"))) {
            JsonObject object = new JsonParser().parse(fileReader).getAsJsonObject();
            String login = null;
            if (object.has ("login"))
                login = object.get("login").getAsString();
            String password = null;
            if (object.has ("password"))
                password = object.get("password").getAsString();

            this.configuration = new Configuration(getBaseURL (object),
                    args.get("parent"),
                    args.get("rule"),
                    args.get ("ds"),
                    login,
                    password
            );
            System.out.printf("Yours configuration is %s\n", configuration);
        } catch (IOException e) {
            System.out.println("File not found! ");
            throw new RuntimeException(e);
        }
    }

    public boolean activateWorkspace() {
            try {
               String basicAuth = configuration.getBasicAuth ();
                URL url = new URL(configuration.getBaseUrl() + ON);
                String urlParameters = String.format("?ds=%s", encode(configuration.getDatasetName ()));
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
            String basicAuth = configuration.getBasicAuth ();
            URL url = new URL(configuration.getBaseUrl() + XL2WS);
            String urlParameters = String.format("ds=%s&dtree=%s&ws=%s&force=1",
                    encode(configuration.getParentDatasetName ()),
                    encode(configuration.getTreeName()),
                    encode(configuration.getDatasetName ()));
            HttpURLConnection connection = getHttpURLConnection(url, basicAuth, urlParameters);
            System.out.printf("Request url is '%s?%s\n", connection.getURL(), urlParameters);
            if (HttpURLConnection.HTTP_OK == connection.getResponseCode()) {
                InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
                JsonObject obj = new JsonParser().parse(inputStreamReader).getAsJsonObject();
                inputStreamReader.close();
                connection.disconnect();
                String taskId = obj.get("task_id").getAsString();
                System.out.println("Sent a request to create derived dataset. Task id:" + taskId);
                while (!isCreated(taskId, basicAuth)) {
                    System.out.printf("Checking task %s status.\n", taskId);
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
                int n = jsonArray.size ();
                if (n != 2) {
                    ArrayList<String> msg = new ArrayList<> ();
                    for (JsonElement e: jsonArray) {
                        if (e == null)
                            msg.add ("null");
                        else
                            msg.add (e.toString ());
                    }
                    throw new IllegalStateException (
                        "Unexpected response from server: "
                        + "[" + String.join (", ", msg) + "]"
                    );
                }
                JsonElement result = jsonArray.get (0);
                String status = jsonArray.get (1).getAsString ();
                System.out.println ("Task status: " + status);
                if (result.isJsonPrimitive ()) {
                    boolean active = result.getAsBoolean ();
                    return active;
                } else if (result.isJsonNull ()) {
                    System.out.println ("Task returned no result");
                } else if (result.isJsonObject ()) {
                    JsonObject js = result.getAsJsonObject ();
                    Set<Map.Entry<String, JsonElement>> entries =
                        js.entrySet ();
                    for (Map.Entry<String, JsonElement> e: entries) {
                        if ("ws".equalsIgnoreCase (e.getKey ())) {
                            System.out.println (
                                "Created derived dataset: "
                                + e.getValue ()
                            );
                        } else {
                            System.out.printf (
                                "Result: %s: %s\n",
                                e.getKey (), e.getValue ()
                            );
                        }
                    }
                }
                return true;
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
        if (basicAuth != null)
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

        String getUserCredentials()
        {
            if (login != null && password != null)
                return login + ":" + password;
            return null;
        }

        String getBasicAuth()
        {
            String  userCredentials = getUserCredentials ();
            if (userCredentials == null) {
                return null;
            }
            String basicAuth = "Basic " + new String(
                Base64.getEncoder().encode(userCredentials.getBytes())
            );
            return basicAuth;
        }
    }
}
