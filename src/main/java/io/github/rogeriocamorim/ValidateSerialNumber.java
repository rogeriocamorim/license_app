package io.github.rogeriocamorim;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;
import oshi.util.Constants;

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
        String unknownHash = String.format("%08x", Constants.UNKNOWN.hashCode());
        String computerIdentifier = getComputerIdentifier();
        System.out.println("If any field is " + unknownHash
                + " then I couldn't find a serial number or uuid, and running as sudo might change this.");

        logger.traceEntry("ComputerIdentifier: {}", computerIdentifier);
        validateSerialNumber.sendPost();
        logger.trace("Exiting application.");
    }

    public static String getComputerIdentifier() {
        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
        HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
        CentralProcessor centralProcessor = hardwareAbstractionLayer.getProcessor();
        ComputerSystem computerSystem = hardwareAbstractionLayer.getComputerSystem();

        String vendor = operatingSystem.getManufacturer();
        String processorSerialNumber = computerSystem.getSerialNumber();
        String uuid = computerSystem.getHardwareUUID();
        String processorIdentifier = centralProcessor.getProcessorIdentifier().getIdentifier();
        int processors = centralProcessor.getLogicalProcessorCount();

        String delimiter = "-";

        return String.format("%08x", vendor.hashCode()) + delimiter
                + String.format("%08x", processorSerialNumber.hashCode()) + delimiter
                + String.format("%08x", uuid.hashCode()) + delimiter
                + String.format("%08x", processorIdentifier.hashCode()) + delimiter + processors;
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
