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
import org.apache.commons.lang3.tuple.Pair;
import org.pipeline.core.data.*;
import org.pipeline.core.data.columns.IntegerColumn;
import org.pipeline.core.data.columns.StringColumn;
import org.pipeline.core.data.manipulation.ColumnPicker;
import org.pipeline.core.data.manipulation.ColumnPickerCollection;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

public class PrepareToTrim implements WorkflowBlock
{
    //private final static String Prop_COPY_INPUT = "Copy Input";

    private final static String Input_BLASTN_INFO = "blastn-info";
    private final static String Input_SEQ_NAMES = "sequence-names";
    private final static String Input_FASTA = "input-fasta";
    
    private final static String Output_TRIMMING_INFO = "trimming-info";


    /**
     * This method is called when block execution is first started. It should be
     * used to setup any data structures that are used throughout the execution
     * lifetime of the block.
     */
    public void preExecute(BlockEnvironment env) throws Exception
    {
        
    }

    /**
     * This code is used to perform the actual block operation. It may be called
     * multiple times if data is being streamed through the block. It is, however, 
     * guaranteed to be called at least once and always after the preExecute
     * method and always before the postExecute method;
     */
    public void execute(BlockEnvironment env, BlockInputs inputs, BlockOutputs outputs) throws Exception
    {
        /*
        List<String> seqNames;

        if (env.getExecutionService().isInputConnected(Input_SEQ_NAMES)) {
            if (env.getExecutionService().isInputConnected(Input_FASTA)) {
                System.out.println("WARNING: Both " + Input_FASTA + " and " + Input_SEQ_NAMES + " are connected. Using only " + Input_SEQ_NAMES);
            }
            throw new UnsupportedOperationException("To be implemented.");
        } else if (env.getExecutionService().isInputConnected(Input_FASTA)) {
            List<File> fastaFiles = inputs.getInputFiles(Input_FASTA);
            if (fastaFiles.size() == 0) {
                throw new IllegalArgumentException("Missing input FASTA files.");
            } else if (fastaFiles.size() > 1) {
                throw new IllegalArgumentException("This block can only work with a single FASTA file.");
            }

            seqNames = _readSequenceNames(fastaFiles.get(0));
        } else {
            throw new Exception("Missing sequence information. Connect one of " + Input_FASTA + " or " + Input_SEQ_NAMES + " inputs.");
        }
        */

        outputs.setOutputDataSet(Output_TRIMMING_INFO,
                _generateTrimmingInfo(env,
                        inputs.getInputDataSet(Input_BLASTN_INFO)));
    }
    
    /*
     * This code is called once when all of the data has passed through the block. 
     * It should be used to cleanup any resources that the block has made use of.
     */
    public void postExecute(BlockEnvironment env) throws Exception
    {
        
    }

    private static class TrimInfo {
        String sequenceId;
        int trimStart;
        int trimEnd;

        public TrimInfo()
        { }


        public TrimInfo(String seqId, int start, int end)
        {
            sequenceId = seqId;
            trimStart = start;
            trimEnd = end;
        }
    }

    private static final String Prop_QSEQID = "Column Name: Query Sequence Id";
    private static final String Prop_QSTART = "Column Name: Query Start";
    private static final String Prop_QEND = "Column Name: Query End";

    //private static final String Col_QSEQID = "qseqid";
    //private static final String Col_QSTART = "qstart";
    //private static final String Col_QEND = "qend";

    Data _generateTrimmingInfo(BlockEnvironment env, Data blastInfo) throws DataException
    {
        ColumnPickerCollection pickers = new ColumnPickerCollection();
        pickers.setPickersCopyData(false);
        pickers.addColumnPicker(env.getStringProperty(Prop_QSEQID, (String) null));
        pickers.addColumnPicker(env.getStringProperty(Prop_QSTART, (String) null));
        pickers.addColumnPicker(env.getStringProperty(Prop_QEND, (String) null));

        Vector<?> columns = pickers.extractColumnVector(blastInfo);

        StringColumn seqId_Col = (StringColumn)columns.get(0);
        // QStart and QEnd columns may be String, Integer or Double depending on the nuances of the input data parsing
        // Let's parse integers manually in this block...
        Column qStart_Col = (Column)columns.get(1);
        Column qEnd_Col = (Column)columns.get(2);

        // Check for errors in the input
        if (seqId_Col.getRows() != qStart_Col.getRows() || seqId_Col.getRows() != qEnd_Col.getRows()) {
            throw new IllegalArgumentException("Invalid blast info uneven column lengths: " + seqId_Col.getRows() + ", " + qStart_Col.getRows() + ", " + qEnd_Col.getRows());
        }

        ArrayList<TrimInfo> startEndList = new ArrayList<>();

        // To make loop simpler...
        if (seqId_Col.getRows() == 0) {
            return new Data();
        }
        // initialise the list with the first element from the blastInfo data set.
        TrimInfo info = new TrimInfo(seqId_Col.getStringValue(0), Integer.parseInt(qStart_Col.getStringValue(0)), Integer.parseInt(qEnd_Col.getStringValue(0)));
        startEndList.add(info);

        for (int i = 1; i < seqId_Col.getRows(); i++) {
            int qStart = Integer.parseInt(qStart_Col.getStringValue(i));
            int qEnd = Integer.parseInt(qEnd_Col.getStringValue(i));

            if (info.sequenceId.equals(seqId_Col.getStringValue(i))) {
                if (qStart < info.trimStart) {
                    info.trimStart = qStart;
                }
                if (qEnd > info.trimEnd) {
                    info.trimEnd = qEnd;
                }
            } else {
                info = new TrimInfo(seqId_Col.getStringValue(i), qStart, qEnd);
                startEndList.add(info);
            }
        }

        seqId_Col = new StringColumn("qSeqId");
        IntegerColumn start_Col = new IntegerColumn("qStart");
        IntegerColumn end_Col = new IntegerColumn("qEnd");

        for (TrimInfo tInfo : startEndList) {
            seqId_Col.appendStringValue(tInfo.sequenceId);
            start_Col.appendIntValue(tInfo.trimStart);
            end_Col.appendIntValue(tInfo.trimEnd);
        }

        Data output = new Data();
        output.addColumn(seqId_Col);
        output.addColumn(start_Col);
        output.addColumn(end_Col);

        return output;
    }


    /*
    List<String> _readSequenceNames(File fasta) throws IOException
    {
        ArrayList<String> names = new ArrayList<>();
        int lineNo = 0;

        try (BufferedReader reader = Files.newBufferedReader(fasta.toPath(), Charset.defaultCharset())) {
            while (true) {
                String line = reader.readLine();
                lineNo++;
                if (line == null) {
                    break;
                }

                line = line.trim();
                if (line.startsWith(">")) {
                    String[] tokens = line.substring(1).split("\\s+", 2);
                    if (tokens.length == 0 || tokens[0].length() == 0) {
                        throw new IllegalArgumentException("Invalid FASTA sequence identifier: " + line + "; line no.: " + lineNo);
                    }
                    names.add(tokens[0]);
                }
            }
        }

        return names;
    }
    */
}
