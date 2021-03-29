package pt.tecnico.hdlt.T25.timeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class TimelineGenerator {

    private static void generateTimeline(int gridSize, int numberOfUsers, int step, int maxEp) throws IOException {

        ArrayList<Integer> positions = new ArrayList<>();

        for (int i = 0; i <= gridSize; i += step) {
            positions.add(i);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode arrayNode = objectMapper.createArrayNode();

        for (int ep = 0; ep < maxEp; ep++) {
            for (int user = 0; user < numberOfUsers; user++) {
                int lat = new Random().nextInt(positions.size());
                int lon = new Random().nextInt(positions.size());

                ObjectNode node = objectMapper.createObjectNode();
                node.put("userId", user);
                node.put("ep", ep);
                node.put("latitude", lat);
                node.put("longitude", lon);

                arrayNode.add(node);
            }
        }

        objectMapper.writeValue(new File("grid.json"), arrayNode);

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