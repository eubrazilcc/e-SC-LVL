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


public class Consense extends CloudDataProcessorService
{
    private static final String _LibraryName = "Phylip-3.695";
    private static final String _CommandName = "consense";

    private final static String Prop_OUTGROUP_ROOT = "Outgroup root";

    private final static String Input_INPUT_FILES = "input-tree-files";
    
    private final static String Output_ANALYSIS_FILES = "analysis-files";
    private final static String Output_TREE_FILES = "tree-files";


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

        FileWrapper analysisFiles = new FileWrapper();
        FileWrapper treeFiles = new FileWrapper();
        execute_N_way(
                args,
                getProperties().intValue(Prop_OUTGROUP_ROOT, -1),
                inputFiles,
                analysisFiles,
                treeFiles);
        setOutputData(Output_ANALYSIS_FILES, analysisFiles);
        setOutputData(Output_TREE_FILES, treeFiles);
    }


    private void execute_N_way(ArrayList<String> args, int outgroupRoot, FileWrapper inputFiles, FileWrapper outAnalysisFiles, FileWrapper outTreeFiles)
    throws Exception
    {
        System.out.println("Running in the N-way mode");
        ProcessBuilder pb = new ProcessBuilder(args);

        for (File inputFile : inputFiles) {
            // For each file start a separate dnapars process
            final Process child = pb.start();

            // Add a shutdown hook to kill the child in the case the block has been terminated.
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override public void run() {
                    child.destroy();
                }
            });

            // Prepare connectors for std-in, -out and -err
            Thread stdOutTh = new DumperThread(child.getInputStream(), System.out, false);
            Thread stdErrTh = new DumperThread(child.getErrorStream(), System.err, false);

            // Start dumpers
            stdOutTh.start();
            stdErrTh.start();

            // Use auto-flush to make sure the child process receives outputs straight after println
            PrintStream stdIn = new PrintStream(child.getOutputStream(), true);

            // Check whether default outfile exists because that
            // changes the interaction with the tool.
            File defaultAnalysis = new File("outfile");
            File analysisFile = createTempFile("out.MP.consense.analysis", new File("."), CreateOption.AddNameSuffix);
            boolean moveAnalysisOut;

            if (defaultAnalysis.exists()) {
                // Send input file name
                stdIn.println(inputFile.toString());

                // Send new file name
                stdIn.println('F');
                stdIn.println(analysisFile.toString());
                moveAnalysisOut = false;
            } else {
                // Send input file name
                stdIn.println(inputFile.toString());
                moveAnalysisOut = true;
            }

            if (outgroupRoot > 0) {
                // Send outgroup root information
                stdIn.println('O');
                stdIn.println(outgroupRoot);
            }

            // Check whether default outtree file exists because that
            // changes the interaction with the tool.
            File defaultTree = new File("outtree");
            File treeFile = createTempFile("out.MP.consense.tree", new File("."), CreateOption.AddNameSuffix);
            boolean moveTreeOut;

            if (defaultTree.exists()) {
                // Confirm selected options
                stdIn.println('Y');

                // Send new file name
                stdIn.println('F');
                stdIn.println(treeFile.toString());
                moveTreeOut = false;
            } else {
                // Confirm selected options
                stdIn.println('Y');
                moveTreeOut = true;
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

            try {
                if (moveAnalysisOut) {
                    Files.move(defaultAnalysis.toPath(), analysisFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                if (moveTreeOut) {
                    Files.move(defaultTree.toPath(), treeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException x) {
                throw new Exception("Cannot move output files: " + x);
            }

            outAnalysisFiles.addFile(analysisFile);
            outTreeFiles.addFile(treeFile);
        }
    }
}
