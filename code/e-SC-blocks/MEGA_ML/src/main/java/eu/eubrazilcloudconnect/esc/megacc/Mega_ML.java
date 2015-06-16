/**
 * e-Science Central Copyright (C) 2008-2015 School of Computing Science,
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
package eu.eubrazilcloudconnect.esc.megacc;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import com.connexience.server.workflow.*;
import com.connexience.server.workflow.service.DataProcessorService;
import uk.ac.ncl.eSC.CommonTools.DumperThread;


public class Mega_ML implements WorkflowBlock
{
    private final static String Prop_TEST_METHOD = "Test of Phylogeny";
    private final static String Prop_BOOTSTRAP_NO = "No. of Bootstrap Replications";
    private final static String Prop_MODEL = "Model/Method";
    private final static String Prop_RATES = "Rates among Sites";
    private final static String Prop_GAMMA_CATS = "No of Discrete Gamma Categories";
    private final static String Prop_GAPS = "Gaps/Missing Data Treatment";
    private final static String Prop_CUTOFF = "Site Coverage Cutoff (%)";
    private final static String Prop_ML_METHOD = "ML Heuristic Method";
    private final static String Prop_INIT_TREES = "Initial Tree for ML";
    private final static String Prop_BRANCH_FILTER = "Branch Swap Filter";
    private final static String Prop_THREADS = "Number of Threads";
    private final static String Prop_STOP_ON_ERROR = "Stop on error";

    // Mapping of option values that cause issues in e-SC: [0] -- e-SC value, [1] -- MEGACC value
    private final static String[][] Prop_RATES_Opt_Mapping = {
            { "Gamma distributed with Invariant sites", "Gamma distributed with Invariant sites (G+I)" },
            { "Gamma Distributed", "Gamma Distributed (G)" },
            { "Has Invariant sites", "Has Invariant sites (I)" }
    };

    private final static String[][] Prop_ML_METHOD_Opt_Mapping = {
            { "Nearest-Neighbor-Interchange", "Nearest-Neighbor-Interchange (NNI)" },
            { "Subtree-Pruning-Regrafting - Fast", "Subtree-Pruning-Regrafting - Fast (SPR level 3)" },
            { "Subtree-Pruning-Regrafting - Extensive", "Subtree-Pruning-Regrafting - Extensive (SPR level 5)" }
    };

    private final static String[][] Prop_INIT_TREES_Opt_Mapping = {
            { "Make initial tree automatically (Default - NJ|BioNJ)", "Make initial tree automatically (Default - NJ/BioNJ)" },
            { "Make initial tree automatically - Maximum Parsimony", "Make initial tree automatically (Maximum Parsimony)" },
            { "Make initial tree automatically - Neighbor Joining", "Make initial tree automatically (Neighbor Joining)" },
            { "Make initial tree automatically - BioNJ", "Make initial tree automatically (BioNJ)" }
    };

    private final static String Input_DATA = "input-data";
    
    private final static String Output_ANALYSIS = "analysis-output";
    private final static String Output_CONSENSUS = "consensus-output";
    private final static String Output_SUMMARY = "summary-info";

    private final static String Lib_MEGACC = "MegaCC-6.0";
    private final static String MAO_TEMPLATE = "M6CC.mao.template";


    /**
     * This code is used to perform the actual block operation. It may be called
     * multiple times if data is being streamed through the block. It is, however, 
     * guaranteed to be called at least once and always after the preExecute
     * method and always before the postExecute method;
     */
    public void execute(BlockEnvironment env, BlockInputs inputs, BlockOutputs outputs) throws Exception
    {
        Path templateFile = _prepareTemplate(env);

        ArrayList<File> outputAnalyses = new ArrayList<>();
        ArrayList<File> outputConsensus = new ArrayList<>();
        ArrayList<File> outputSummaries = new ArrayList<>();

        ArrayList<String> args = new ArrayList<>();
        args.add(env.getDependencyDirectory(Lib_MEGACC) + File.separator + env.getDependencyCommand(Lib_MEGACC, "megacc"));
        args.add("-a");
        args.add(templateFile.toString());
        args.add("-d");
        args.add("<INPUT_FILE_PLACEHOLDER>");
        args.add("-o");
        args.add("<OUTPUT_FILE_PLACEHOLDER>");

        try {
            execute_N_way(args, inputs.getInputFiles(Input_DATA), env.getBooleanProperty(Prop_STOP_ON_ERROR, true), outputAnalyses, outputConsensus, outputSummaries);

            outputs.setOutputFiles(Output_SUMMARY, outputSummaries);
            outputs.setOutputFiles(Output_ANALYSIS, outputAnalyses);
            outputs.setOutputFiles(Output_CONSENSUS, outputConsensus);
        } finally {
            // Handle the summary output in the special case ...
            if (outputSummaries.size() == 1) {
                // when there is only one input/output file, print the summary to the block std output.
                Files.copy(outputSummaries.get(0).toPath(), System.out);
            } else {
                // otherwise direct the user to inspect the summary files.
                System.out.println("To see the detailed output of megacc run please inspect files from the " + Output_SUMMARY + " output port.");
            }
        }
    }

    /**
     * This method is called when block execution is first started. It should be
     * used to setup any data structures that are used throughout the execution
     * lifetime of the block.
     */
    public void preExecute(BlockEnvironment env) throws Exception
    {

    }

    /*
     * This code is called once when all of the data has passed through the block.
     * It should be used to cleanup any resources that the block has made use of.
     */
    public void postExecute(BlockEnvironment env) throws Exception
    {

    }


    private Path _prepareTemplate(BlockEnvironment env)
    throws Exception
    {
        ArrayList<String[]> options = new ArrayList<>();
        options.add(new String[] { Prop_BOOTSTRAP_NO, Integer.toString(env.getIntProperty(Prop_BOOTSTRAP_NO, -1)) });
        options.add(new String[] { Prop_TEST_METHOD, env.getStringProperty(Prop_TEST_METHOD, "") });
        options.add(new String[] { Prop_CUTOFF, Integer.toString(env.getIntProperty(Prop_CUTOFF, -1)) });
        options.add(new String[] { Prop_INIT_TREES, env.getStringProperty(Prop_INIT_TREES, "") });
        options.add(new String[] { Prop_GAPS, env.getStringProperty(Prop_GAPS, "") });
        options.add(new String[] { Prop_MODEL, env.getStringProperty(Prop_MODEL, "") });
        options.add(new String[] { Prop_BRANCH_FILTER, env.getStringProperty(Prop_BRANCH_FILTER, "") });
        options.add(new String[] { Prop_GAMMA_CATS, Integer.toString(env.getIntProperty(Prop_GAMMA_CATS, -1)) });
        options.add(new String[] { Prop_THREADS, Integer.toString(env.getIntProperty(Prop_THREADS, -1)) });

        // Correct issues with ampersand and brackets in the option value
        String rates = env.getStringProperty(Prop_RATES, "");
        for (String[] mapping : Prop_RATES_Opt_Mapping) {
            if (mapping[0].equals(rates)) {
                rates = mapping[1];
                break;
            }
        }
        options.add(new String[] { Prop_RATES, rates });

        String ml_method = env.getStringProperty(Prop_ML_METHOD, "");
        for (String[] mapping : Prop_ML_METHOD_Opt_Mapping) {
            if (mapping[0].equals(ml_method)) {
                ml_method = mapping[1];
                break;
            }
        }
        options.add(new String[] { Prop_ML_METHOD, ml_method });

        String init_trees = env.getStringProperty(Prop_INIT_TREES, "");
        for (String[] mapping : Prop_INIT_TREES_Opt_Mapping) {
            if (mapping[0].equals(init_trees)) {
                init_trees = mapping[1];
                break;
            }
        }
        options.add(new String[] { Prop_INIT_TREES, init_trees });

        Path outputPath = Paths.get(DataProcessorService.createTempFile("mao", new File(".")).toString());
        MegaCCTools.PrepareMAO(
                Paths.get(env.getExecutionService().getLibraryWrapper().getFile(MAO_TEMPLATE).toString()), options, outputPath);

        return outputPath;
    }


    private void execute_N_way(ArrayList<String> args, List<File> inputFiles, boolean stopOnError, List<File> outAnalysisFiles, List<File> outConsensusFiles, List<File> outSummaryFiles)
    throws Exception
    {
        System.out.println("Running in the N-way mode");
        boolean allErrors = true;

        for (File inputFile : inputFiles) {
            // Set the input file name
            args.set(4, inputFile.toString());

            // Set the output file name
            File outputFile = DataProcessorService.createTempFile("output", new File("."));
            args.set(6, outputFile.toString());

            System.out.println("About to start: " + args);

            // Start a separate process for each input file.
            ProcessBuilder pb = new ProcessBuilder(args);

            // A little hack to avoid problems with wine on VM created in UPV
            Map<String, String> sysenv = pb.environment();
            if (sysenv.get("TERM") == null || "unknown".equals(sysenv.get("TERM"))) {
                System.out.println("Environment variable TERM not set correctly. Resetting to 'linux'");
                sysenv.put("TERM", "linux");
            }
            final Process child = pb.start();

            // Add a shutdown hook to kill the child in the case the block has been terminated.
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override public void run() {
                    child.destroy();
                }
            });

            // Prepare connectors for std-out and -err
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
                System.out.println("Error processing file: " + inputFile);
                System.out.println(getClass().getSimpleName() + " exited with code = " + exitCode);
                //throw new Exception(getClass().getSimpleName() + " failed; see output message for more details.");
            }

            System.out.println("Adding: " + outputFile.toString() + ".nwk" + ", " + new File(outputFile.toString() + "_consensus.nwk") + ", " + new File(outputFile.toString() + "_summary.txt"));
            outAnalysisFiles.add(new File(outputFile.toString() + ".nwk"));
            outConsensusFiles.add(new File(outputFile.toString() + "_consensus.nwk"));
            File summaryFile = new File(outputFile.toString() + "_summary.txt");
            outSummaryFiles.add(summaryFile);

            String errorMsg = MegaCCTools.GetMegaCCErrorMessage(summaryFile);
            if (errorMsg != null) {
                if (stopOnError) {
                    throw new MegaCCFailureException("MegaCC failed for file: " + inputFile + " with error: " + errorMsg);
                } else {
                    System.err.println("Error processing file: " + inputFile + ": " + errorMsg + "; inspect files from the " + Output_SUMMARY + " output port for more information.");
                }
            } else {
                allErrors = false;
            }
        }

        if (allErrors) {
            throw new MegaCCFailureException("MegaCC failed for all input files!");
        }
    }
}
