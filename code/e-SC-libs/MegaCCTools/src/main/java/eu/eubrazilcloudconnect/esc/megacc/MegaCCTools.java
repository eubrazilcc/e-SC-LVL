package eu.eubrazilcloudconnect.esc.megacc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;


public abstract class MegaCCTools
{
    public static String GetMegaCCErrorMessage(File summaryInfo)
    throws MegaCCFailureException, IOException
    {
        // TODO: This method might be optimized if the file is read from the end upwards.

        try (BufferedReader reader = Files.newBufferedReader(summaryInfo.toPath(), StandardCharsets.UTF_8)) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.length() > 0 && line.charAt(0) == ';') {
                    // Get rid of the initial semicolon
                    line = line.substring(1).trim();
                    if (line.startsWith("Error:")) {
                        return line.substring("Error:".length());
                    }
                }
            }
        }

        return null;
    }


    public static void PrepareMAO(Path maoTemplate, ArrayList<String[]> optionNVPairs, Path maoOutput)
    throws Exception
    {
        try (
                BufferedReader reader = Files.newBufferedReader(maoTemplate, StandardCharsets.UTF_8);
                BufferedWriter writer = Files.newBufferedWriter(maoOutput, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                boolean lineWritten = false;

                Iterator<String[]> iter = optionNVPairs.iterator();
                while (iter.hasNext()) {
                    String[] option = iter.next();
                    if (line.startsWith(option[0] + "=")) {
                        writer.write(option[0] + "=" + option[1]);
                        writer.newLine();
                        iter.remove();
                        lineWritten = true;
                        break;
                    }
                }

                if (!lineWritten) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }
    }
}
