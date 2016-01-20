/**
 * e-Science Central Copyright (C) 2008-2016 School of Computing Science,
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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import com.connexience.server.workflow.*;
import com.connexience.server.workflow.service.DataProcessorService;
import uk.ac.ncl.eSC.CommonTools.DumperThread;
import uk.ac.ncl.eSC.CommonTools.Pair;


public class Mega_MP implements WorkflowBlock
{
    private final static String Prop_TEST_METHOD = "Test of Phylogeny";
    private final static String Prop_BOOTSTRAP_NO = "No. of Bootstrap Replications";
    private final static String Prop_GAPS = "Gaps/Missing Data Treatment";
    private final static String Prop_CUTOFF = "Site Coverage Cutoff (%)";
    private final static String Prop_MP_METHOD = "MP Search Method";
    private final static String Prop_INIT_TREES = "No. of Initial Trees (random addition)";
    private final static String Prop_SEARCH_LEVEL = "MP Search level";
    private final static String Prop_TREES_TO_RETAIN = "Max No. of Trees to Retain";
    private final static String Prop_STOP_ON_ERROR = "Stop on error";

    private final static String Opt_MAXMIN_BNB_ESC = "Max-mini Branch-and-Bound";
    private final static String Opt_MAXMIN_BNB_MEGA = "Max-mini Branch-&-bound";
    private final static String Opt_SPR_ESC = "Subtree-Pruning-Regrafting";
    private final static String Opt_SPR_MEGA = "Subtree-Pruning-Regrafting (SPR)";
    private final static String Opt_TBR_ESC = "Tree-Bisection-Reconnection";
    private final static String Opt_TBR_MEGA = "Tree-Bisection-Reconnection (TBR)";

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
        Path templateFile = prepareTemplate(env);

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


    private Path prepareTemplate(BlockEnvironment env)
    throws Exception
    {
        ArrayList<Pair<String, String>> options = new ArrayList<>();
        options.add(new Pair<>(Prop_TEST_METHOD, env.getStringProperty(Prop_TEST_METHOD, "")));
        options.add(new Pair<>(Prop_BOOTSTRAP_NO, Integer.toString(env.getIntProperty(Prop_BOOTSTRAP_NO, -1))));
        options.add(new Pair<>(Prop_CUTOFF, Integer.toString(env.getIntProperty(Prop_CUTOFF, -1))));
        options.add(new Pair<>(Prop_INIT_TREES, Integer.toString(env.getIntProperty(Prop_INIT_TREES, -1))));
        options.add(new Pair<>(Prop_GAPS, env.getStringProperty(Prop_GAPS, "")));
        options.add(new Pair<>(Prop_SEARCH_LEVEL, Integer.toString(env.getIntProperty(Prop_SEARCH_LEVEL, -1))));
        options.add(new Pair<>(Prop_TREES_TO_RETAIN, Integer.toString(env.getIntProperty(Prop_TREES_TO_RETAIN, -1))));

        // Correct issues with ampersand and brackets in the option value
        String mp_method = env.getStringProperty(Prop_MP_METHOD, "");
        switch (mp_method) {
            case Opt_MAXMIN_BNB_ESC:
                mp_method = Opt_MAXMIN_BNB_MEGA;
                break;
            case Opt_SPR_ESC:
                mp_method = Opt_SPR_MEGA;
                break;
            case Opt_TBR_ESC:
                mp_method = Opt_TBR_MEGA;
                break;
            default:
                // For other options do nothing
                break;
        }
        options.add(new Pair<>(Prop_MP_METHOD, mp_method));

        Path readerPath = Paths.get(env.getExecutionService().getLibraryWrapper().getFile(MAO_TEMPLATE).toString());
        Path writerPath = Paths.get(DataProcessorService.createTempFile("mao", new File(".")).toString());

        try (
                BufferedReader reader = Files.newBufferedReader(readerPath, StandardCharsets.UTF_8);
                BufferedWriter writer = Files.newBufferedWriter(writerPath, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                boolean lineWritten = false;

                Iterator<Pair<String, String>> it = options.iterator();
                while (it.hasNext()) {
                    Pair<String, String> option = it.next();
                    if (line.startsWith(option.item1 + "=")) {
                        writer.write(option.item1 + "=" + option.item2);
                        writer.newLine();
                        it.remove();
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

        return writerPath;
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
