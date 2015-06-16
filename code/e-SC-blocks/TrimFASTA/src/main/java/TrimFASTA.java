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
import com.connexience.server.workflow.*;
import org.pipeline.core.data.*;
import org.pipeline.core.data.columns.IntegerColumn;
import org.pipeline.core.data.columns.StringColumn;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;


public class TrimFASTA implements WorkflowBlock
{
    private final static String Prop_TRIM_INFO = "Trimming Info";
    private final static String Prop_FILTER_MISSING = "Filter Missing";

    private final static String Input_TRIM_INFO = "trimming-info";
    private final static String Input_FASTA = "input-fasta";
    
    private final static String Output_FASTA = "trimmed-fasta";
    private final static String Output_FILTERED = "filtered-sequences";


    /**
     * This method is called when block execution is first started. It should be
     * used to setup any data structures that are used throughout the execution
     * lifetime of the block.
     */
    @Override
    public void preExecute(BlockEnvironment env) throws Exception
    { }


    /**
     * This code is used to perform the actual block operation. It may be called
     * multiple times if data is being streamed through the block. It is, however, 
     * guaranteed to be called at least once and always after the preExecute
     * method and always before the postExecute method;
     */
    @Override
    public void execute(BlockEnvironment env, BlockInputs inputs, BlockOutputs outputs) throws Exception
    {
        String[][] trimList = env.getStringMatrixProperty(Prop_TRIM_INFO);
        Integer[][] trimValues;

        if (trimList != null && trimList.length > 0) {
            if (env.getExecutionService().isInputConnected(Input_TRIM_INFO)) {
                System.out.println("WARNING: Both the " + Prop_TRIM_INFO + " property and " + Input_TRIM_INFO + " input are set. Using only " + Prop_TRIM_INFO);
            }

            trimValues = new Integer[trimList.length][2];

            for (int i = 0; i < trimList.length; i++) {
                trimValues[i][0] = Integer.parseInt(trimList[i][0]);
                trimValues[i][1] = Integer.parseInt(trimList[i][1]);
            }
            throw new UnsupportedOperationException("Using property " + Prop_TRIM_INFO + " has not been implemented.");
        } else {
            Data trimData = inputs.getInputDataSet(Input_TRIM_INFO);
            if (trimData.getColumnCount() != 3) {
                throw new Exception("Invalid column number in the " + Input_TRIM_INFO + " input: " + trimData.getColumnCount() + " Expected exactly 3: [sequence id, trim start, trim end]");
            }
            if (trimData.getLargestRows() == 0) {
                throw new Exception("Missing trimming information in the " + Input_TRIM_INFO + " input.");
            }

            ArrayList<File> outputFiles = new ArrayList<>(inputs.getInputFiles(Input_FASTA).size());
            StringColumn filteredSeq_Col = new StringColumn("Filtered Sequences");
            int i = 0;
            for (File inputFile : inputs.getInputFiles(Input_FASTA)) {
                outputFiles.add(_trimFile(env, inputFile, trimData, filteredSeq_Col));
            }

            outputs.setOutputFiles(Output_FASTA, outputFiles);

            Data filteredSeq = new Data();
            filteredSeq.addColumn(filteredSeq_Col);
            outputs.setOutputDataSet(Output_FILTERED, filteredSeq);
        }
    }
    
    /*
     * This code is called once when all of the data has passed through the block. 
     * It should be used to cleanup any resources that the block has made use of.
     */
    @Override
    public void postExecute(BlockEnvironment env) throws Exception
    { }


    private File _trimFile(BlockEnvironment env, File inputFASTA, Data trimmingInfo, StringColumn filteredSeq)
    throws IOException, DataException
    {
        boolean filterMissing = env.getBooleanProperty(Prop_FILTER_MISSING, false);
        File outputFile = env.getExecutionService().createTempFile("output.fasta", new File("."));
        HashSet<String> sequences = new HashSet<>();

        try (
                BufferedReader reader = new BufferedReader(new FileReader(inputFASTA));
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            String line;
            String lastSeq = "";
            String lastSeqLine = null;
            String lastSeqId = null;
            int lineNo = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                lineNo++;
                if ("".equals(line)) {
                    // Ignore empty lines
                    continue;
                } else if (line.startsWith(">")) {
                    // Remember the original sequence name removing the initial '>' character.
                    String[] tokens = line.substring(1).split("\\s+", 2);
                    if (tokens.length == 0 || tokens[0].length() == 0) {
                        throw new IllegalArgumentException("Invalid sequence identifier: " + line + "; line no.: " + lineNo);
                    }

                    // Trim and store the last sequence
                    if (!"".equals(lastSeq)) {
                        lastSeq = _trimSequence(lastSeqId, lastSeq, trimmingInfo, filterMissing);
                        if (lastSeq == null) {
                            filteredSeq.appendStringValue(lastSeqId);
                        } else {
                            writer.write(lastSeqLine);
                            writer.newLine();
                            _unpack(writer, lastSeq, 80);
                        }

                        lastSeq = "";
                    }

                    lastSeqId = tokens[0];
                    lastSeqLine = line;
                } else {
                    if (lastSeqId == null) {
                        throw new IllegalArgumentException("Missing sequence identifier in line: " + lineNo);
                    }
                    lastSeq += line.toUpperCase();
                }
            }

            if (!"".equals(lastSeq)) {
                lastSeq = _trimSequence(lastSeqId, lastSeq, trimmingInfo, filterMissing);
                if (lastSeq == null) {
                    filteredSeq.appendStringValue(lastSeqId);
                } else {
                    writer.write(lastSeqLine);
                    writer.newLine();
                    _unpack(writer, lastSeq, 80);
                }
            }
        }

        return outputFile;
    }


    private String _trimSequence(String sequenceId, String sequence, Data trimmingInfo, boolean nullIfMissing)
    throws DataException
    {
        StringColumn seqId_Col = (StringColumn) trimmingInfo.column(0);
        IntegerColumn start_Col = (IntegerColumn) trimmingInfo.column(1);
        IntegerColumn end_Col = (IntegerColumn) trimmingInfo.column(2);

        for(int i = 0; i < seqId_Col.getRows(); i++) {
            if (sequenceId.equals(seqId_Col.getStringValue(i))) {
                return sequence.substring(start_Col.getIntValue(i) - 1, end_Col.getIntValue(i));
            }
        }

        if (nullIfMissing) {
            return null;
        } else {
            return sequence;
        }
    }


    private void _unpack(BufferedWriter writer, String text, int outputTextLength)
            throws IOException
    {
        int offset = 0;
        while (offset + outputTextLength < text.length()) {
            writer.write(text, offset, outputTextLength);
            writer.newLine();
            offset += outputTextLength;
        }
        if (offset < text.length()) {
            writer.write(text, offset, text.length() - offset);
            writer.newLine();
        }
    }
}
