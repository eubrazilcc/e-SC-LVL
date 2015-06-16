/**
 * e-Science Central Copyright (C) 2008-2013 School of Computing Science,
 * Newcastle University
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation at: http://www.gnu.org/licenses/gpl-2.0.html
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, 5th Floor, Boston, MA 02110-1301, USA.
 */
package eu.eubrazilcloudconnect.lvl.Phylip;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Random;

import com.connexience.server.workflow.cloud.services.CloudDataProcessorService;
import com.connexience.server.workflow.engine.datatypes.FileWrapper;

import uk.ac.ncl.eSC.CommonTools;
import uk.ac.ncl.eSC.CommonTools.DumperThread;


public class Seqboot extends CloudDataProcessorService
{
    private static final String _LibraryName = "Phylip-3.695";
    private static final String _CommandName = "seqboot";

    private final static String Prop_NUMBER_OF_REPLICATES = "Number of replicates";
    private final static String Prop_RANDOM_NUMBER_SEED = "Random number seed";

    private final static String Input_INPUT_FILES = "input-phylip";
    
    private final static String Output_OUTPUT_FILES = "output-phylip";


    @Override
    public void execute() throws Exception
    {
        FileWrapper inputFiles = (FileWrapper)getInputData(Input_INPUT_FILES);
        if (inputFiles.getFileCount() < 1) {
            throw new Exception("Missing input files");
        }

        ArrayList<String> args = new ArrayList<>();

        File cmdPath = CommonTools.GetCommandPath(this, _LibraryName, _CommandName);
        args.add(cmdPath.getPath());

        int seed = getProperties().intValue(Prop_RANDOM_NUMBER_SEED, -1);
        if (seed == -1) {
            seed = new Random().nextInt(Integer.MAX_VALUE - 1);
            if (seed % 2 == 1) {
                seed++;
            }
        } else if (seed % 2 == 0) {
            System.out.println("Warning! Even random number seed detected: incremented automatically.");
            seed++;
        }
        System.out.println("Using random number seed: " + seed);

        int replNo = getProperties().intValue(Prop_NUMBER_OF_REPLICATES, -1);
        if (replNo <= 0) {
            throw new IllegalArgumentException("Invalid number of replicates provided.");
        }

        FileWrapper outputFiles = new FileWrapper();
        execute_N_way(args, replNo, seed, inputFiles, outputFiles);
        setOutputData(Output_OUTPUT_FILES, outputFiles);
    }


    private void execute_N_way(ArrayList<String> args, int replicatesNo, int randomSeed, FileWrapper inputFiles, FileWrapper outputFiles)
    throws Exception
    {
        System.out.println("Running in the N-way mode");
        ProcessBuilder pb = new ProcessBuilder(args);

        for (File inputFile : inputFiles) {
            // For each file start a separate seqboot process
            final Process child = pb.start();

            // add a shutdown hook to kill the child in the case the block has been terminated.
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override public void run() {
                    child.destroy();
                }
            });

            Thread stdOutTh = new DumperThread(child.getInputStream(), System.out, false);
            Thread stdErrTh = new DumperThread(child.getErrorStream(), System.err, false);

            // Start dumpers
            stdOutTh.start();
            stdErrTh.start();

            // Use auto-flush to make sure the child process receives outputs straight after println
            PrintStream stdIn = new PrintStream(child.getOutputStream(), true);

            // Send input file name
            stdIn.println(inputFile.toString());
            
            // Send replicates number
            stdIn.println('R');
            stdIn.println(replicatesNo);

            // Confirm selected options
            stdIn.println('Y');

            // Check whether default outfile exists because that
            // changes the interaction with the tool.
            File defaultOutput = new File("outfile");
            File outputFile = createTempFile("out.MP.boot", new File("."), CreateOption.AddNameSuffix);
            boolean moveOutput;

            if (defaultOutput.exists()) {
                // Send random number seed
                stdIn.println(randomSeed);

                // Send new file name
                stdIn.println('F');
                stdIn.println(outputFile.toString());
                moveOutput = false;
            } else {
                // Send random number seed
                stdIn.println(randomSeed);
                moveOutput = true;
            }

            // ... and wait for a result
            int exitCode = child.waitFor();

            // Wait for dumpers. This should be quick once the process has finished.
            stdOutTh.join();
            stdErrTh.join();

            if (exitCode != 0) {
                System.out.println("=====================================================");
                System.out.println(getClass().getSimpleName() + " exited with code = " + exitCode);
                throw new Exception(getClass().getSimpleName() + " failed; see output message for more details.");
            }

            if (moveOutput) {
                try {
                    Files.move(defaultOutput.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException x) {
                    throw new Exception("Cannot move " + defaultOutput + " to " + outputFile + ": " + x);
                }
            }

            outputFiles.addFile(outputFile);
        }
    }
}
