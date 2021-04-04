package pt.tecnico.hdlt.T25.timeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.org.apache.xpath.internal.operations.Bool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TimelineGenerator {

    private static void displayGrid(int gridSize, int step, int maxEp, Map<ObjectNode, Integer> positionsToUserId) throws FileNotFoundException {
        ObjectMapper objectMapper = new ObjectMapper();
        PrintStream fileStream = new PrintStream("grid_timeline.txt");
        System.setOut(fileStream);

        for (int ep = 0; ep < maxEp; ep++) {
            System.out.println("Epoch: " + ep);
            System.out.println();

            for (int i = 0; i < gridSize; i+= step) {
                for (int j = 0; j < gridSize; j+= step) {
                    ObjectNode displayNode = objectMapper.createObjectNode();
                    displayNode.put("lat", j);
                    displayNode.put("lon", i);
                    displayNode.put("ep", ep);

                    String pos = positionsToUserId.get(displayNode) != null ? String.valueOf(positionsToUserId.get(displayNode)) : "";

                    System.out.print(String.format("|%3s  ", pos));
                }
                System.out.println("|");
            }
            System.out.println();
            System.out.println();
        }

    }

    private static void generateTimeline(int gridSize, int numberOfUsers, int step, int maxEp) throws IOException {
        ArrayList<Integer> positions = new ArrayList<>();
        Map<ObjectNode, Integer> positionsToUserId = new HashMap<>();

        for (int i = 0; i < gridSize; i += step) {
            positions.add(i);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode arrayNode = objectMapper.createArrayNode();

        for (int ep = 0; ep < maxEp; ep++) {
            for (int user = 0; user < numberOfUsers; user++) {
                int latIndex = new Random().nextInt(positions.size());
                int lonIndex = new Random().nextInt(positions.size());

                ObjectNode displayNode = objectMapper.createObjectNode();


                displayNode.put("lat", positions.get(latIndex));
                displayNode.put("lon", positions.get(lonIndex));
                displayNode.put("ep", ep);
                positionsToUserId.put(displayNode, user);
                System.out.println("User" + user + " at " + positions.get(latIndex) + ", " + positions.get(lonIndex) + " at Epoch " + ep);


                ObjectNode gridNode = objectMapper.createObjectNode();
                gridNode.put("userId", user);
                gridNode.put("ep", ep);
                gridNode.put("latitude", positions.get(latIndex));
                gridNode.put("longitude", positions.get(lonIndex));

                arrayNode.add(gridNode);
            }
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.set("grid", arrayNode);
        node.put("numberOfUsers", numberOfUsers);
        node.put("step", step);
        node.put("maxEp", maxEp);

        objectMapper.writeValue(new File("grid.json"), node);

        // Display grid for epoch 0
        displayGrid(gridSize, step, maxEp, positionsToUserId);

        // Convert JSON -> Object
        /*List<Location> location = objectMapper.readValue(new File("test.json"), new TypeReference<List<Location>>(){});

        for (int i = 0; i < location.size(); i += 1) {
            System.out.println(location.get(i).getUserId());
        }*/
    }

    public static void main(String[] args) throws Exception {

        if (args.length != 4) {
            System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s grid_size number_of_users step maxEp%n", TimelineGenerator.class.getName());
        }

        final int gridSize = Integer.parseInt(args[0]);
        final int numberOfUsers = Integer.parseInt(args[1]);
        final int step = Integer.parseInt(args[2]);
        final int maxEp = Integer.parseInt(args[3]);

        generateTimeline(gridSize, numberOfUsers, step, maxEp);
    }
}