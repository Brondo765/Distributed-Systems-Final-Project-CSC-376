import java.io.*;
import com.google.gson.*;
import com.google.gson.internal.LinkedTreeMap;
import java.net.*;
import java.util.*;

public class graphServer {
    // Used for exit in child procress to get return code for error or success
    public enum ReturnValue {
        SUCCESS(0),
        FAILURE(-1);

        private final int returnCode;

        ReturnValue(int returnCode) {
            this.returnCode = returnCode;
        }

        public int getReturnCode() {
            return returnCode;
        }
    }

    // Parses the URL and stores the JSON into an ArrayList of LinkedTreeMaps for later parsing
    private static ArrayList<LinkedTreeMap<String, Object>> readJsonFromURL(String url) throws IOException {
        URL u = new URL(url.replaceAll("\"", ""));
        InputStreamReader in = new InputStreamReader(u.openStream());
        Gson gson = new Gson();
        ArrayList<LinkedTreeMap<String, Object>> json = gson.fromJson(in, ArrayList.class);
        in.close();
        return json;
    }

    // Parses the JSON we stored for the preferred X and Y points in graph
    public static void parseJsonForXAndYValues(ArrayList<LinkedTreeMap<String, Object>> json, String xAxisString, String yAxisString,
                                               ArrayList<Double> xNoNull, ArrayList<Double> yNoNull) {
        // Get data from stored from JSON map
        ArrayList<Double> xVals = new ArrayList<>();
        ArrayList<Double> yVals = new ArrayList<>();
        for(LinkedTreeMap<String, Object> map : json) {
            for (String key : map.keySet()) {
                if (key.equals(xAxisString)) {
                    xVals.add((Double) map.get(key));
                }
                if (key.equals(yAxisString)) {
                    yVals.add((Double) map.get(key));
                }
            }
        }

        // Store only X,Y vals that do not contain null in either category
        int i = 0;
        while (i < xVals.size()) {
            if (xVals.get(i) != null) {
                if (yVals.get(i) != null) {
                    xNoNull.add(xVals.get(i));
                    yNoNull.add(yVals.get(i));
                }
            }
            i++;
        }
    }

    // Writes the X,Y values in stored ArrayLists to data file to be read from gnuplot
    public static void writeDataToTextFile(ArrayList<Double> xNoNull, ArrayList<Double> yNoNull) throws IOException {
        // Write to text file the data
        File path = new File(System.getProperty("user.dir"));
        // This version is for Linux VM
        // FileWriter data = new FileWriter(path  + "/" + "data.txt");

        FileWriter data = new FileWriter(path + "\\" + "output" + "\\" + "data.txt");
        for (int i = 0; i < xNoNull.size(); i++) {
            data.write(xNoNull.get(i) + " " + yNoNull.get(i) + "\r\n");
        }
        data.flush();
    }

    // Allows to sleep threads for write to finish before running exec on gnuplot
    public static void pause(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(ReturnValue.FAILURE.getReturnCode());
        }
    }

    // Creates a subprocess which runs gnuplot with our string args, returns a .png in the current directory
    public static void runGnuplotExec(String xAxisString, String yAxisString) {
        // Must run on a Linux machine with gnuplot installed for this to work
        String[] s = {"/usr/bin/gnuplot",
                "-e",
                "set term png size 640,480;"
                        + "set output\"scatterPlot.png\";"
                        + "set title "
                        + "\"" + xAxisString + " as a fnc of " + yAxisString + "\"" + ";"
                        + "plot \"./data.txt\" with points pt 1;"};
        try {
            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec(s);
            InputStream stdin = proc.getErrorStream();
            InputStreamReader isr = new InputStreamReader(stdin);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                System.err.println("gnuplot: " + line);
                System.err.flush();
            }
            int exitVal = proc.waitFor();
            if (exitVal != 0) {
                System.err.println("gnuplot Process exitValue: " + exitVal);
                System.err.flush();
            }
            proc.getInputStream().close();
            proc.getOutputStream().close();
            proc.getErrorStream().close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(ReturnValue.FAILURE.getReturnCode());
        }
    }

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        String url = scanner.nextLine();
        // On Linux machine URL passed as argument in child process
        // String url = args[0];
        ArrayList<LinkedTreeMap<String, Object>> json = readJsonFromURL(url);
        boolean stopLooping = false;
        try {
            StringBuilder str = new StringBuilder();
            while (!stopLooping) {
                // Read in the command received from standard in and turn into JsonObject for processing
                str.append(scanner.nextLine());
                Gson gson = new Gson();
                JsonObject command = null;

                try {
                    command = gson.fromJson(str.toString(), JsonObject.class);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(ReturnValue.FAILURE.getReturnCode());
                }

                if (command.toString().equals("{" + "\"command\"" + ":" + "\"quit\"" + "}")) {
                    JsonObject responseToCommand = new JsonObject();
                    responseToCommand.addProperty("status", "quitting");
                    System.out.println(responseToCommand);
                    System.out.flush();
                    stopLooping = true;
                } else {
                    ArrayList<Double> xNoNull = new ArrayList<>();
                    ArrayList<Double> yNoNull = new ArrayList<>();
                    String xAxisString = command.get("xAxis").toString()
                            .replaceAll("\"", "");
                    String yAxisString = command.get("yAxis").toString()
                            .replaceAll("\"", "");

                    parseJsonForXAndYValues(json, xAxisString, yAxisString, xNoNull, yNoNull);
                    writeDataToTextFile(xNoNull, yNoNull);
                    pause(5);
                    runGnuplotExec(xAxisString, yAxisString);

                    String exitString = "{"
                            + "\"status\""
                            + ":"
                            + "\"success\""
                            + ","
                            + "\"imageFilename\""
                            + ":"
                            + "\"scatterPlot.png\""
                            + ","
                            + "\"height\""
                            + ":"
                            + "360"
                            + ","
                            + "\"width\""
                            + ":"
                            + "480"
                            + "}";

                    JsonObject graphCreated = gson.fromJson(exitString, JsonObject.class);
                    System.out.println(graphCreated);
                    System.out.flush();

                    // Erase String for next graph command
                    str.delete(0, str.length());
                }
            }
        } catch (IllegalStateException | NoSuchElementException e) {
            e.printStackTrace();
            System.exit(ReturnValue.FAILURE.getReturnCode());
        }
        scanner.close();
        System.exit(ReturnValue.SUCCESS.getReturnCode());
    }
}