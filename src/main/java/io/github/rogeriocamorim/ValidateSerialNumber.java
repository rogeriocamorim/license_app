package io.github.rogeriocamorim;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ValidateSerialNumber {
    private static final Logger logger = LogManager.getLogger(ValidateSerialNumber.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    public static void main(String[] args){

        logger.traceEntry("Entering application.");
        ValidateSerialNumber validateSerialNumber = new ValidateSerialNumber();
        validateSerialNumber.getMachineID();
        validateSerialNumber.sendPost();
        logger.trace("Exiting application.");
    }

    private void getMachineID(){
        logger.traceEntry("Getting MachineID.");
        String operationSystem = System.getProperty("os.name").toLowerCase();
        String machineId = "";
        logger.traceEntry("Machine: {}", operationSystem);
        if (operationSystem.contains("win")) {
            StringBuilder output = new StringBuilder();
            String[] cmd = {"wmic", "csproduct", "get", "UUID"};
            machineId = getUUIDFromMachine(output, cmd);
        } else if (operationSystem.contains("nix") || operationSystem.contains("nux") || operationSystem.indexOf("aix") > 0) {

            StringBuilder output = new StringBuilder();
            String[] cmd = {"/bin/sh", "-c", "echo <password for superuser> | sudo -S cat /sys/class/dmi/id/product_uuid"};
            machineId = getUUIDFromMachine(output, cmd);
        }
        System.out.println(machineId);
    }

    private String getUUIDFromMachine(StringBuilder output, String[] cmd) {
        Process process;
        String machineId;
        try {
            process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        machineId = output.toString();
        return machineId;
    }

    private void sendPost() {
        Properties prop = new Properties();

        InputStream inputStream = ValidateSerialNumber.class.getClassLoader().getResourceAsStream("config.properties");

        if (inputStream != null) {
            try {
                prop.load(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Map<Object, Object> data = new HashMap<>();
        data.put("serialNumber",  prop.getProperty("app.serialNumber"));

        HttpRequest request = HttpRequest.newBuilder()
                .POST(buildFormDataFromMap(data))
                .uri(URI.create("http://" + prop.getProperty("api.url") + ":" + prop.getProperty("api.port") + "/api/license/product/check-serial-number"))
                .setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            logger.error("There is a problem on the connection");
        }
        catch (InterruptedException e) {
            logger.error("The connection was interrupted");
        }
        if(response!=null) {
            logger.trace("Connection OK. Response code: {}", response.statusCode());

            // print response body
            System.out.println(response.body());
        }else{
            logger.error("Response for server was null");
        }
    }

    private static HttpRequest.BodyPublisher buildFormDataFromMap(Map<Object, Object> data) {
        var builder = new StringBuilder();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }

}
