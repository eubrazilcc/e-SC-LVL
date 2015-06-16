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
import com.connexience.server.model.document.DocumentRecord;
import com.connexience.server.workflow.*;
import com.connexience.server.workflow.cloud.library.CloudWorkflowServiceLibraryItem;
import com.connexience.server.workflow.cloud.library.LibraryWrapper;
import com.connexience.server.workflow.cloud.library.installer.Installer;
import com.connexience.server.workflow.cloud.library.types.BinaryLibrary;
import com.connexience.server.workflow.cloud.services.CloudDataProcessorService;
import com.connexience.server.workflow.engine.datatypes.DataWrapper;
import com.connexience.server.workflow.engine.datatypes.FileWrapper;
import com.connexience.server.workflow.service.DataProcessorCallMessage;
import com.connexience.server.workflow.service.DataProcessorException;
import com.connexience.server.workflow.service.DataProcessorIODefinition;
import org.pipeline.core.data.*;
import org.pipeline.core.data.io.*;
import org.pipeline.core.data.manipulation.BestGuessDataTyper;
import org.pipeline.core.drawing.DrawingException;
import org.pipeline.core.drawing.TransferData;
import org.pipeline.core.xmlstorage.XmlDataObject;
import org.pipeline.core.xmlstorage.XmlDataStore;
import org.pipeline.core.xmlstorage.XmlStorageException;
import org.pipeline.core.xmlstorage.xmldatatypes.*;
import uk.ac.ncl.eSC.CommonTools;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class CmdLineWrapper extends CloudDataProcessorService
{
    @Override
    public void execute() throws Exception
    {
        ProcessBuilder pb = new ProcessBuilder();

        _prepareProperties(pb.environment());
        _prepareInputs(pb.environment());
        Map<String, File> outputMap = _prepareOutputs(pb.environment());
        _prepareDependencies(pb.environment());

        Path mainScript = getLibraryItem().getOriginalUnpackedDir().toPath().resolve("scripts");
        switch (Installer.getOsName()) {
            case "windows":
                mainScript = mainScript.resolve("main.cmd");
                break;
            case "linux":
            case "macos":
                mainScript = mainScript.resolve("main.sh");
                break;
            default:
                System.out.println("WARNING: Unrecognised OS type: " + Installer.getOsName() + ". Using the 'main.sh' script to run the tool.");
                mainScript = mainScript.resolve("main.sh");
        }
        mainScript.toFile().setExecutable(true, true);

        pb.command(mainScript.toString());
        final Process child = pb.start();

        // Add a shutdown hook to kill the child in the case the block has been terminated.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() {
                child.destroy();
            }
        });

        // Prepare connectors for std-in, -out and -err
        Thread stdOutTh = new CommonTools.DumperThread(child.getInputStream(), System.out, false);
        Thread stdErrTh = new CommonTools.DumperThread(child.getErrorStream(), System.err, false);

        // Start dumpers
        stdOutTh.start();
        stdErrTh.start();

        int exitCode = child.waitFor();

        stdOutTh.join();
        stdErrTh.join();

        // Process outputs even if the tool returned erroneous exit status. There still might be some valuable
        // information in the output files.
        _processOutputs(outputMap);

        if (exitCode != 0) {
            throw new Exception("Service terminated with exit status: " + exitCode);
        }
    }


    /**
     * This method generates a name that is acceptable by Windows Batch and Linux Shell as an environment variable.
     *
     * @param name
     * @return
     */
    private String _toSimpleVariableName(String name)
    {
        StringBuilder sb = new StringBuilder();
        boolean illegal = false;

        for (char c : name.toCharArray()) {
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_') {
                sb.append(c);
                illegal = false;
            } else if (c >= '0' && c <= '9') {
                if (sb.length() == 0) {
                    sb.append('_');
                }
                sb.append(c);
                illegal = false;
            } else {
                if (illegal) {
                    // Produce only a single underscore for more than one consecutive illegal character
                    continue;
                }
                sb.append('_');
                illegal = true;
            }
        }

        return sb.toString();
    }


    private void _prepareProperties(Map<String, String> processEnvironment)
    throws XmlStorageException
    {
        XmlDataStore properties = getCallMessage().getProperties();

        for (Object name : properties.getNames()) {
            XmlDataObject propValue = properties.get(name.toString());

            if (propValue instanceof XmlStringDataObject) {
                processEnvironment.put("PROPS__" + _toSimpleVariableName(name.toString()), ((XmlStringDataObject) propValue).stringValue());
            } else if (propValue instanceof XmlDoubleDataObject) {
                processEnvironment.put("PROPS__" + _toSimpleVariableName(name.toString()), Double.toString(((XmlDoubleDataObject)propValue).doubleValue()));
            } else if (propValue instanceof XmlIntegerDataObject) {
                processEnvironment.put("PROPS__" + _toSimpleVariableName(name.toString()), Integer.toString(((XmlIntegerDataObject) propValue).intValue()));
            } else if (propValue instanceof XmlLongDataObject) {
                processEnvironment.put("PROPS__" + _toSimpleVariableName(name.toString()), Long.toString(((XmlLongDataObject) propValue).longValue()));
            } else if (propValue instanceof XmlBooleanDataObject) {
                processEnvironment.put("PROPS__" + _toSimpleVariableName(name.toString()), Boolean.toString(((XmlBooleanDataObject) propValue).booleanValue()));
            } else {
                // TODO: More work needed, esp. to convert StringList and StringPairList
                throw new UnsupportedOperationException("Property conversion for type: " + propValue.getClass().getSimpleName() + " has not been implemented.");
            }
        }
    }


    /**
     * This method exposes commands from all dependencies of this service which ar BinaryLibraries.
     *
     * @param processEnvironment
     */
    private void _prepareDependencies(Map<String, String> processEnvironment)
    {
        processEnvironment.put("BLOCK_HOME", getLibraryItem().getOriginalUnpackedDir().toString());

        Iterator<CloudWorkflowServiceLibraryItem> iter = getLibraryItem().resolvedDependencies();
        while (iter.hasNext()) {
            String depPrefix = null; // Prefix used in the variable name. Initialized on the first command of
                                     // the binary library to avoid unnecessary calls for other types of library.
            CloudWorkflowServiceLibraryItem depLib = iter.next();
            LibraryWrapper wrapper = depLib.getWrapper();
            if (wrapper instanceof BinaryLibrary) {
                BinaryLibrary binLib = (BinaryLibrary)wrapper;
                for (String execName : binLib.getExecutableNames()) {
                    BinaryLibrary.Executable execCmd = binLib.getExecutable(execName);
                    File execPath;
                    if (execCmd.isAbsolute()) {
                        execPath = new File(execCmd.getRelativeCmd());
                    } else {
                        execPath = binLib.getFile(execCmd.getRelativeCmd());
                    }
                    if (depPrefix == null) {
                        depPrefix = "DEPS__" + _toSimpleVariableName(depLib.getLibraryName()) + "__";
                    }
                    processEnvironment.put(depPrefix + execName, execPath.toString());
                }
            }
        }
    }


    private void _prepareInputs(Map<String, String> processEnvironment)
    throws IOException, DataExportException, DataProcessorException
    {
        DataProcessorCallMessage message = getCallMessage();
        String[] inputNames = message.getDataSources();
        String[] inputModes = message.getDataSourceModes();
        TransferData inputDataObject;
        Data data;

        for (int i = 0; i < inputNames.length; i++) {
            inputDataObject = getInputData(inputNames[i]);
            if (inputDataObject instanceof DataWrapper && inputModes[i].equals(DataProcessorIODefinition.NON_STREAMING_CONNECTION)) {
                _prepareDataFile(processEnvironment, inputNames[i], getInputDataSet(inputNames[i]));
                //processEnvironment.put("INPUTS__" + _toSimpleVariableName(inputNames[i]), _prepareDataFile(getInputData(inputNames[i])));
            } else if (inputDataObject instanceof FileWrapper) {
                _prepareFileWrapperInput(processEnvironment, inputNames[i], (FileWrapper) inputDataObject);
            }
        }
    }


    private void _prepareFileWrapperInput(Map<String, String> env, String inputName, FileWrapper inputData)
    {
        if (inputData.getFileCount() < 1) {
            // Do nothing. Input is empty.
            return;
        }

        // TODO: This is currently implemented only for linux bash shell
        String varName = "INPUTS__" + _toSimpleVariableName(inputName);

        // For convenience the first file from the list is added under a simple env variable name (not an array).
        env.put(varName, inputData.getFile(0).toString());

        // The actual file wrapper is added as simple name + "__LIST"
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (File f : inputData) {
            sb.append(f.toString() + ", ");
        }
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        sb.append(")");
        env.put(varName + "__LIST", sb.toString());
    }


    private void _prepareDataFile(Map<String, String> env, String inputName, Data inputData)
    throws IOException, DataExportException
    {
        // TODO: This can potentially be optimized because the inputData is unnecessarily read from the source and stored here
        File tmpFile = createTempFile("tmp-" + inputName, new File("."));
        CSVDataExporter exporter = null;
        try {
            exporter = new CSVDataExporter();
            exporter.openFile(tmpFile);
            exporter.appendData(inputData);
        } finally {
            if (exporter != null) {
                exporter.closeFile();
            }
        }
        env.put("INPUTS__" + _toSimpleVariableName(inputName), tmpFile.toString());
    }


    private Map<String, File> _prepareOutputs(Map<String, String> processEnvironment) throws IOException, DrawingException
    {
        DataProcessorCallMessage message = getCallMessage();
        String[] outputNames = message.getDataOutputs();

        HashMap<String, File> outputs = new HashMap<>();

        for (String outputName : outputNames) {
            File outfile = createTempFile("output.tmp", new File("."));
            System.out.println("Outfile: " + outputName + " = '" + outfile.getAbsolutePath() + (outfile.exists() ? "' exists." : "' not exists."));
            if ("file-wrapper".equals(message.getDataOutputType(outputName))) {
                // For file-wrapper outputs prepare the output file with the base directory.
                // TODO: could be improved with some minor changes to the FileWrapper class
                FileWrapper wrapper = new FileWrapper();
                try (OutputStream outStream = Files.newOutputStream(outfile.toPath())) {
                    wrapper.saveToOutputStream(outStream);
                }
            }
            outputs.put(outputName, outfile);
            processEnvironment.put("OUTPUTS__" + _toSimpleVariableName(outputName), outfile.toString());
        }

        return outputs;
    }


    private void _processOutputs(Map<String, File> outputMap) throws Exception
    {
        DataProcessorCallMessage message = getCallMessage();

        for (Map.Entry<String, File> entry : outputMap.entrySet()) {
            String type = message.getDataOutputType(entry.getKey());
            switch (type) {
                case "file-wrapper":
                    // The output file should include a list of files which can directly be fed into the file-wrapper output
                    FileWrapper wrapper = new FileWrapper();
                    try (InputStream inStream = Files.newInputStream(entry.getValue().toPath())) {
                        wrapper.loadFromInputStream(inStream);
                    }
                    setOutputData(entry.getKey(), wrapper);
                    break;
                case "data-wrapper":
                    // The output file should be a csv which needs to be parsed into a Data object
                    _readCSV(entry.getKey(), entry.getValue());
                    break;
                default:
                    throw new UnsupportedOperationException("CmdLineWrapper cannot handle output-type: " + type);
            }
        }
    }


    private void _readCSV(String outputName, File csvFile)
    throws IOException, DataImportException, DataException, XmlStorageException, DataProcessorException
    {
        DelimitedTextDataStreamImporter importer = new DelimitedTextDataStreamImporter();
        importer.setChunkSize(5000);
        importer.setForceTextImport(true);

        try (FileInputStream inStream = new FileInputStream(csvFile)) {
            importer.resetWithInputStream(inStream);

            int chunkCount = 0;
            int rowCount = 0;
            String dataName = csvFile.getName();

            while (!importer.isFinished()) {
                Data data = importer.importNextChunk();
                data.createEmptyProperties();
                data.setName(dataName);

                setOutputDataSet(outputName, data);

                chunkCount++;
                rowCount += data.getLargestRows();
            }

            System.out.println(String.format("File %s imported: %d rows of data in %d chunk(s)", dataName, rowCount, chunkCount));
        } finally {
            importer.terminateRead();
        }
    }
}
