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
package eu.eubrazilcloudconnect.lvl;

import com.connexience.server.workflow.BlockEnvironment;
import com.connexience.server.workflow.BlockInputs;
import com.connexience.server.workflow.BlockOutputs;
import com.connexience.server.workflow.WorkflowBlock;
import com.connexience.server.workflow.cloud.services.CloudDataProcessorService;
import org.pipeline.core.data.Data;
import org.pipeline.core.data.DataException;
import org.pipeline.core.data.columns.StringColumn;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;


public class FilterDuplicates implements WorkflowBlock
{
    private final static String Prop_NORMALIZE = "Normalize Sequence Names";
    private final static String Prop_DUPLICATES_ONLY = "Normalize Duplicates Only";

    private final static String Input_FASTA_FILES = "fasta-files";
    
    private final static String Output_FILTERED_FASTA_FILES = "filtered-fasta-files";
    private final static String Output_REMOVED_SEQ = "removed-sequences";
    private final static String Output_SEQ_MAP = "sequence-map";


    private final static String _NameFormat = "SEQ%04d";
    private final static Pattern _NamePattern = Pattern.compile("^SEQ\\d{4,}");


    @Override
    public void preExecute(BlockEnvironment env) throws Exception
    { }


    @Override
    public void execute(BlockEnvironment env, BlockInputs inputs, BlockOutputs outputs) throws Exception
    {
        List<File> inputFiles = inputs.getInputFiles(Input_FASTA_FILES);
        if (inputFiles.size() < 1) {
            throw new Exception("Missing input files");
        }

        ArrayList<File> outputFiles = new ArrayList<>();
        StringColumn removedSeqsCol = new StringColumn("Removed Sequences");

        // Configure the output sequence map and name normalizer
        StringColumn origNames = new StringColumn("Original Name");
        StringColumn normNames = new StringColumn("Normalized Name");
        NameNormalizer normalizer;
        //
        if (env.getBooleanProperty(Prop_NORMALIZE, false)) {
            if (env.getBooleanProperty(Prop_DUPLICATES_ONLY, false)) {
                normalizer = new NormalizeDuplicates(_NameFormat, origNames, normNames);
            } else {
                normalizer = new NormalizeAll(_NameFormat, origNames, normNames);
            }
        } else {
            normalizer = new DoNotNormalize();
        }

        // Filter and normalize sequences in all input files
        for (File inputFile : inputFiles) {
            normalizer.clear();
            normalizer.setCollidingNames(_scanSequenceNames(inputFile));
            _filterAndNormalizeSequences(inputFile, normalizer, outputFiles, removedSeqsCol);
        }

        outputs.setOutputFiles(Output_FILTERED_FASTA_FILES, outputFiles);

        Data sequenceMap = new Data();
        sequenceMap.addColumn(origNames);
        sequenceMap.addColumn(normNames);
        outputs.setOutputDataSet(Output_SEQ_MAP, sequenceMap);

        Data removedSeqs = new Data();
        removedSeqs.addColumn(removedSeqsCol);
        outputs.setOutputDataSet(Output_REMOVED_SEQ, removedSeqs);
    }

    @Override
    public void postExecute(BlockEnvironment env) throws Exception { }


    /**
     * Scans the FASTA input file for the names that match the normalization pattern.
     * If the file includes some names matching the pattern (a name collision), then the normalizer needs to
     * take that into account and avoid generating these names for duplicated names.
     *
     * @param inputFasta Input file to be scanned.
     * @return A set of colliding sequence names that match NamePattern
     * @throws IOException
     */
    private HashSet<String> _scanSequenceNames(File inputFasta) throws IOException
    {
        HashSet<String> collidingNames = new HashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(inputFasta.toPath(), StandardCharsets.US_ASCII)) {
            String line;
            int lineNo = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                lineNo++;

                if (!"".equals(line)) {
                    if (line.startsWith(">")) {
                        String[] tokens = line.substring(1).split("\\s+", 2);
                        if (tokens.length == 0 || tokens[0].length() == 0) {
                            throw new IllegalArgumentException("Invalid sequence identifier: " + line + "; line no.: " + lineNo);
                        }

                        if (_NamePattern.matcher(tokens[0]).matches()) {
                            collidingNames.add(tokens[0]);
                        }
                    }
                }
            }
        }

        return collidingNames;
    }


    /**
     * A simple sequence filtering for small FASTA files (a rewrite of the 1st pipeline perl script).
     * For larger files a more sophisticated filtering is needed, e.g. one that would store
     * hashes instead of raw sequences.
     *
     * @param inputFasta
     * @param normalizer
     * @param outputFiles
     * @param removedSeqs
     * @throws IOException
     * @throws DataException
     */
    private void _filterAndNormalizeSequences(File inputFasta, NameNormalizer normalizer, ArrayList<File> outputFiles, StringColumn removedSeqs)
            throws IOException, DataException
    {
        // The CloudDataProcessorService.createTempFile method should probably be put into some utility library.
        File outputFile = CloudDataProcessorService.createTempFile("output.fasta", new File("."));
        HashSet<String> sequences = new HashSet<>();

        try (
                BufferedReader reader = new BufferedReader(new FileReader(inputFasta));
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            String line;
            String lastSeq = "";
            String lastSeqName = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!"".equals(line)) {
                    if (line.startsWith(">")) {
                        // A new sequence -> store the previous one if needed.
                        if (!"".equals(lastSeq)) {
                            if (!sequences.contains(lastSeq)) {
                                sequences.add(lastSeq);
                                writer.write(">" + normalizer.normalize(lastSeqName));
                                writer.newLine();

                                _unpack(writer, lastSeq, 80);
                            } else {
                                removedSeqs.appendStringValue(lastSeqName);
                            }
                            lastSeq = "";
                        }

                        // A new sequence -> read and normalize its name
                        lastSeqName = line.substring(1).trim();
                    } else {
                        lastSeq += line.toUpperCase();
                    }
                }
            }

            if (!"".equals(lastSeq)) {
                if (!sequences.contains(lastSeq)) {
                    sequences.add(lastSeq);
                    writer.write(">" + normalizer.normalize(lastSeqName));
                    writer.newLine();

                    _unpack(writer, lastSeq, 80);
                } else {
                    removedSeqs.appendStringValue(lastSeqName);
                }
            }
        }

        outputFiles.add(outputFile);
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
