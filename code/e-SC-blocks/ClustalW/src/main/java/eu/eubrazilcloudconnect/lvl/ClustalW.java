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
package eu.eubrazilcloudconnect.lvl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.connexience.server.workflow.BlockEnvironment;
import com.connexience.server.workflow.BlockInputs;
import com.connexience.server.workflow.BlockOutputs;
import com.connexience.server.workflow.WorkflowBlock;
import com.connexience.server.workflow.cloud.services.CloudDataProcessorService;
import uk.ac.ncl.eSC.CommonTools;
import uk.ac.ncl.eSC.CommonTools.DumperThread;


public class ClustalW implements WorkflowBlock
{
    private static final String _LibraryName = "ClustalW-2.1";
    private static final String _CommandName = "clustalw";

    private final static String Prop_OUTPUT_TYPE = "Output-Type";
    private final static String Prop_ALIGN = "Align";

    private final static String Input_INPUT_SEQS = "input-sequences";
    
    private final static String Output_ALIGNED_SEQS = "aligned-sequences";


    @Override
    public void preExecute(BlockEnvironment env) throws Exception
    { }

    @Override
    public void execute(BlockEnvironment env, BlockInputs inputs, BlockOutputs outputs) throws Exception
    {
        List<File> inputFiles = inputs.getInputFiles(Input_INPUT_SEQS);
        if (inputFiles.size() < 1) {
            throw new Exception("Missing input files");
        }

        ArrayList<String> args = new ArrayList<>();

        File cmdPath = CommonTools.GetCommandPath(env.getExecutionService(), _LibraryName, _CommandName);
        args.add(cmdPath.getPath());
        // Leave place for input and output file names
        args.add(null);
        args.add(null);

        if (env.getBooleanProperty(Prop_ALIGN, false)) {
            args.add("-align");
        }

        String sValue = env.getStringProperty(Prop_OUTPUT_TYPE, "").trim();
        if ("".equals(sValue)) {
            throw new IllegalArgumentException("Property " + Prop_OUTPUT_TYPE + " has not been set correctly.");
        }
        // To be set during execute_N_way
        args.add("-output=" + sValue);

        ArrayList<File> outputFiles = _execute_N_way(args, 1, 2, sValue, inputFiles);
        outputs.setOutputFiles(Output_ALIGNED_SEQS, outputFiles);
    }

    @Override
    public void postExecute(BlockEnvironment env) throws Exception
    { }

    private ArrayList<File> _execute_N_way(ArrayList<String> args, int inputFileNameArg, int outputFileNameArg, String outputType, List<File> inputFiles)
            throws Exception
    {
        System.out.println("Running in the N-way mode");

        ArrayList<File> outputFiles = new ArrayList<>();
        ProcessBuilder pb = new ProcessBuilder(args);

        // ClustalW doesn't like absolute paths!
        for (File inputFile : inputFiles) {
            // Prepare the INPUT arguments
            args.set(inputFileNameArg, "-infile=" + inputFile.toString());

            // Prepare the OUTPUT argument
            File outputFile = CloudDataProcessorService.createTempFile("out." + outputType, new File("."));
            args.set(outputFileNameArg, "-outfile=" + outputFile.toString());

            System.out.println("The command line arguments: " + CommonTools.ToString(", ", args));

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

            outputFiles.add(outputFile);
        }

        return outputFiles;
    }
}
