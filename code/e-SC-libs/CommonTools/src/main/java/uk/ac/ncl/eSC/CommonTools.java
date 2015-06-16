package uk.ac.ncl.eSC;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import org.pipeline.core.data.Data;
import org.pipeline.core.data.DataException;
import org.pipeline.core.data.columns.StringColumn;

import com.connexience.server.workflow.cloud.library.CloudWorkflowServiceLibraryItem;
import com.connexience.server.workflow.cloud.library.LibraryWrapper;
import com.connexience.server.workflow.cloud.library.types.BinaryLibrary;
import com.connexience.server.workflow.cloud.services.CloudDataProcessorService;
import com.connexience.server.workflow.engine.datatypes.LibraryItemWrapper;
import com.connexience.server.workflow.service.DataProcessorException;


public abstract class CommonTools
{
    /**
     * Returns a class path string constructed from all jars included in the given library.
     *
     * @param library
     * @return
     * @throws MalformedURLException
     * @throws DataProcessorException
     */
    public static String GetLibraryClassPath(CloudWorkflowServiceLibraryItem library)
    throws MalformedURLException, DataProcessorException
    {
        ArrayList<URL> classPath = new ArrayList<URL>();
        library.addLibraryJarsToClasspath(classPath);

        StringBuilder sb = new StringBuilder();
        for (URL u : classPath) {
            sb.append(u.getPath());
            sb.append(File.pathSeparatorChar);
        }

        // Get rid of the trailing pathSeparatorChar
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }


    /**
     * Prepares a command line path to command <code>cmdName</code> from library
     * <code>libName</code> on which the given service depends on.
     * 
     * @return
     * @throws Exception
     */
    public static File GetCommandPath(CloudDataProcessorService service, String libName, String cmdName)
    throws Exception
    {
        CloudWorkflowServiceLibraryItem library = service.getDependencyItem(libName);

        if(library == null)
            throw new Exception("Can't locate dependency library: " + libName);

        LibraryWrapper libWrapper = library.getWrapper();

        return GetCommandPath(libWrapper, cmdName);
    }


    /**
     * This is a convinience method equivalent to calling {@link #GetCommandPath(LibraryWrapper, String) getCommandPath(inputWrapper.getLibrary(0).getWrapper(), cmdName)}.
     * Gets the command path of a command with name <code>cmdName</code> from the first library in <code>inputWrapper</code>.
     *  
     * @param inputWrapper
     * @param cmdName
     * @return
     * @throws Exception in the case when <code>inputWrapper</code> is empty
     */
    public static File GetCommandPath(LibraryItemWrapper inputWrapper, String cmdName)
    throws Exception
    {
        if (inputWrapper.size() < 1) {
            throw new Exception("Missing input library");
        }

        return GetCommandPath(inputWrapper.getLibrary(0).getWrapper(), cmdName);
    }


    public static File GetCommandPath(LibraryWrapper libWrapper, String cmdName)
    throws Exception
    {
        if(!(libWrapper instanceof BinaryLibrary)) {
            throw new Exception("Invalid library: " + libWrapper.getDocument().getName() + "; a binary library expected");
        }

        BinaryLibrary binLibrary = (BinaryLibrary)libWrapper;
        BinaryLibrary.Executable exeCmd = binLibrary.getExecutable(cmdName);
        if (exeCmd == null) {
            throw new Exception("Invalid command; library: " + libWrapper.getDocument().getName() + " does not declare command: " + cmdName);
        }

        File cmdPath;

        // Get the right command path depending whether or not it is an absolute path
        if (exeCmd.isAbsolute()) {
            cmdPath = new File(exeCmd.getRelativeCmd());
        } else {
            cmdPath = libWrapper.getFile(exeCmd.getRelativeCmd());
        }

        if (!cmdPath.exists()) {
            throw new FileNotFoundException(
                    "Command " + cmdName + 
                    " declares a file that does not exists in library: " + libWrapper.getDocument().getName());
        }

        return cmdPath;
    }


    /**
     * Splits given file name or path into name and extension part.
     * The returned array always includes two elements: first is the name,
     * second is the extension (with the leading dot). In the cases when 
     * no extension was found the second element contains an empty string "".
     */
    public static String[] SplitFileNameAndExtension(String fileName)
    {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            return new String[] { fileName.substring(0, dot), fileName.substring(dot) };
        } else {
            return new String[] { fileName, "" };
        }
    }


    /**
     * Changes the extension of the given file name to the one provided in the second argument.
     * If the file name has no extension, it is added only if <code>addIfMissing</code> is set.
     * 
     * String <code>newExtension</code> cannot start with dot character (<code>'.'</code>) and cannot
     * be null, empty or blank string. Otherwise, <code>IllegalArgumentException</code> is thrown.
     * 
     * @param fileName
     * @param newExtension
     * @param addIfMissing 
     * @return
     */
    public static String ChangeExtension(String fileName, String newExtension, boolean addIfMissing)
    {
        if (StringHelper.IsNullOrBlank(newExtension) || newExtension.charAt(0) == '.') {
            throw new IllegalArgumentException("Invalid argument 'newExtension'.");
        }

        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            return fileName.substring(0, dot + 1) + newExtension; 
        } else if (addIfMissing) {
            if (fileName.charAt(fileName.length() - 1) == '.') {
                return fileName + newExtension;
            } else {
                return fileName + "." + newExtension;
            }
        } else {
            return fileName;
        }
    }

    /**
     * Returns the greatest common prefix for the two string arguments.
     * 
     * This is a naive O(n) implementation which, perhaps, may be optimised
     * to be O(log(n)) and Ω(n).
     * 
     * However, it is supposed to be used for short strings denoting program 
     * paths, so it should be ok.
     * 
     * @param s1
     * @param s2
     * @return
     */
    public static String GreatestCommonPrefix(String s1, String s2)
    {
        int minLen = Math.min(s1.length(), s2.length());
        int i;

        for (i = 0; i < minLen; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                break;
            }
        }

        return s1.substring(0, i);
    }


    /**
     * This is a convenience method equivalent to calling:
     * {@link #ToString(String, String, String, Object...) ToString("[", "]", delim, objects)}
     * 
     * @param delim
     * @param objects
     * @return
     */
    public static String ToString(String delim, Object... objects)
    {
        return ToString("[", "]", delim, objects);
    }


    /**
     * This is a convenience method equivalent to calling:
     * {@link #ToString(String, String, String, Object...) ToString("[", "]", delim, (Object[])objects)}
     * 
     * @param delim
     * @param objects
     * @return
     */
    public static String ToString(String delim, String... objects)
    {
        return ToString("[", "]", delim, (Object[]) objects);
    }


    /**
     * This is a convenience method equivalent to calling:
     * {@link #ToString(String, String, String, Collection<?>) ToString("[", "]", delim, objects)}
     * 
     * @param delim
     * @param objects
     * @return
     */
    public static String ToString(String delim, Collection<?> objects)
    {
        return ToString("[", "]", delim, objects);
    }


    public static String ToString(String startToken, String endToken, String delim, Collection<?> objects)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(startToken);
        for (Object o : objects) {
            sb.append(o);
            sb.append(delim);
        }

        if (sb.length() > 0) {
            sb.setLength(sb.length() - delim.length());
        }
        sb.append(endToken);

        return sb.toString();
    }


    /**
     * Similarly to {@link java.util.Arrays#toString(Object[])}, this method generates
     * a string representation of an array of objects. However, it takes 
     * additional arguments to allow a bit more flexibility.
     * 
     * @param startToken
     * @param endToken
     * @param delim
     * @param objects
     * @return
     */
    public static String ToString(String startToken, String endToken, String delim, Object... objects)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(startToken);
        for (Object o : objects) {
            sb.append(o);
            sb.append(delim);
        }

        if (sb.length() > 0) {
            sb.setLength(sb.length() - delim.length());
        }
        sb.append(endToken);

        return sb.toString();
    }


    /**
     * This is a convenience method equivalent to calling:
     * {@link #ToString(String, String, String, int...) ToString("[", "]", delim, numbers)}
     * 
     * @param delim
     * @param numbers
     * @return
     */
    public static String ToString(String delim, int... numbers)
    {
        return ToString("[", "]", delim, numbers);
    }


    /**
     * Similarly to {@link java.util.Arrays#toString(Object[])}, this method generates
     * a string representation of an array of objects. However, it takes 
     * additional arguments to allow a bit more flexibility.
     * 
     * @param startToken
     * @param endToken
     * @param delim
     * @param numbers
     * @return
     */
    public static String ToString(String startToken, String endToken, String delim, int... numbers)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(startToken);
        for (int n : numbers) {
            sb.append(n);
            sb.append(delim);
        }

        if (sb.length() > 0) {
            sb.setLength(sb.length() - delim.length());
        }
        sb.append(endToken);

        return sb.toString();
    }


    /**
     * This is a helper method equivalent to calling 
     * {@link #ConciseFormat(java.util.Calendar) conciseFormat(Calendar.getInstance())}.
     * 
     * @return
     */
    public static String ConciseDateFormat()
    {
        return ConciseFormat(Calendar.getInstance());
    }

    /**
     * This is a very simple date formatter which produces a concise
     * representation of the given calendar date: {@code YYYYMMDD-HHMMSS}.
     *
     * @param date
     * @return
     */
    public static String ConciseFormat(Calendar date)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(_PadNumber(date.get(Calendar.YEAR)));
        sb.append(_PadNumber(date.get(Calendar.MONTH) + 1));
        sb.append(_PadNumber(date.get(Calendar.DAY_OF_MONTH)));
        sb.append('-');
        sb.append(_PadNumber(date.get(Calendar.HOUR_OF_DAY)));
        sb.append(_PadNumber(date.get(Calendar.MINUTE)));
        sb.append(_PadNumber(date.get(Calendar.SECOND)));

        return sb.toString();
    }

    private static String _PadNumber(int number)
    {
        if (number < 10)
            return "0" + number;
        return Integer.toString(number);
    }


    public static class Pair<T1, T2>
    {
        public T1 item1;
        public T2 item2;
        
        public Pair()
        {}
        
        public Pair(T1 item1, T2 item2)
        {
            this.item1 = item1;
            this.item2 = item2;
        }
    }


    public static class DataSize
    {
        public static class Prefix {
            public static final int SI = 1 << 0;
            public static final int JEDEC  = 1 << 1;
            public static final int IEC    = 1 << 2;
        }

        public static long ParseDataSize(String dataSize)
        {
            return ParseDataSize(dataSize, Prefix.SI | Prefix.IEC);
        }

        public static long ParseDataSize(String dataSize, int fmtFlag)
        {
            if ((fmtFlag & Prefix.JEDEC) != 0 && (fmtFlag & Prefix.SI) != 0) {
                throw new IllegalArgumentException("JEDEC and Metric data unit size formats are in conflict. Please use one of these");
            }

            NumberFormatException ex = null;

            if ((fmtFlag & Prefix.IEC) != 0) {
                try {
                    return _ParseDataSize_IEC(dataSize);
                } catch (NumberFormatException x) {
                    // Ignore for this moment but remember for later
                    ex = x;
                }
            }

            if ((fmtFlag & Prefix.SI) != 0) {
                try {
                    return _ParseDataSize_SI(dataSize);
                } catch (NumberFormatException x) {
                    // Ignore for this moment but remember for later
                    ex = x;
                }
            }

            if ((fmtFlag & Prefix.JEDEC) != 0) {
                try {
                    return _ParseDataSize_JEDEC(dataSize);
                } catch (NumberFormatException x) {
                    // Ignore for this moment but remember for later
                    ex = x;
                }
            }

            if (ex != null) {
                throw ex;
            } else {
                throw new IllegalArgumentException("Cannot parse value " + dataSize + " with data size format " + fmtFlag);
            }
        }


        /**
         * Parses the amount of data which can potentially be expressed with suffixes in accordance to the IEC standard,
         * like KiB/MiB, etc.
         * Recognizes sizes upto ExbiByte = 1EiB = 1024^6 = 2^60 as 16EiB-1 is the maximum long value.
         * 
         * If no suffix is provided, byte size is assumed. 
         * 
         * For example, strings "1024", "1024B" and "1KiB" will all result in value 1024. 
         * 
         * @param dataSize
         * @return
         */
        private static long _ParseDataSize_IEC(String dataSize)
        {
            long factor = 1;

            if (dataSize.endsWith("KiB")) {
                factor = 0x0400L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("MiB")) {
                factor = 0x0010_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("GiB")) {
                factor = 0x4000_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("TiB")) {
                factor = 0x0010_0000_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("PiB")) {
                factor = 0x0004_0000_0000_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("EiB")) {
                factor = 0x1000_0000_0000_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("B")) {
                dataSize = dataSize.substring(0, dataSize.length() - 1);
            }

            return factor * Long.parseLong(dataSize);
        }


        private static Long _ParseDataSize_SI(String dataSize)
        {
            long factor = 1;

            if (dataSize.endsWith("kB")) {
                factor = 1000L;
                dataSize = dataSize.substring(0, dataSize.length() - 2);
            } else if (dataSize.endsWith("MB")) {
                factor = 1000_000L;
                dataSize = dataSize.substring(0, dataSize.length() - 2);
            } else if (dataSize.endsWith("GB")) {
                factor = 1000_000_000L;
                dataSize = dataSize.substring(0, dataSize.length() - 2);
            } else if (dataSize.endsWith("TB")) {
                factor = 1000_000_000_000L;
                dataSize = dataSize.substring(0, dataSize.length() - 2);
            } else if (dataSize.endsWith("PB")) {
                factor = 1000_000_000_000_000L;
                dataSize = dataSize.substring(0, dataSize.length() - 2);
            } else if (dataSize.endsWith("EB")) {
                factor = 1000_000_000_000_000_000L;
                dataSize = dataSize.substring(0, dataSize.length() - 2);
            } else if (dataSize.endsWith("B")) {
                dataSize = dataSize.substring(0, dataSize.length() - 1);
            }

            return factor * Long.parseLong(dataSize);
        }


        private static Long _ParseDataSize_JEDEC(String dataSize)
        {
            long factor = 1;

            if (dataSize.endsWith("KB")) {
                factor = 0x0400L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("MB")) {
                factor = 0x0010_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("GB")) {
                factor = 0x4000_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            /*}
            else if (dataSize.endsWith("TB")) {
                factor = 0x0010_0000_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("PB")) {
                factor = 0x0004_0000_0000_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            } else if (dataSize.endsWith("EB")) {
                factor = 0x1000_0000_0000_0000L;
                dataSize = dataSize.substring(0, dataSize.length() - 3);
            */
            } else if (dataSize.endsWith("B")) {
                dataSize = dataSize.substring(0, dataSize.length() - 1);
            }

            return factor * Long.parseLong(dataSize);
        }

        public static String ToString(long dataSize, int format)
        {
            switch (format) {
            case Prefix.IEC:
                return _ToString_IEC(dataSize);
            case Prefix.JEDEC:
                return _ToString_JEDEC(dataSize);
            case Prefix.SI:
                return _ToString_SI(dataSize);
            }
            
            throw new IllegalArgumentException("Unsupported data size format provided.");
        }

        private static final String[] IEC_Units = { "", "Ki", "Mi", "Gi", "Ti", "Pi", "Ei" };
        private static final String[] SI_Units = { "", "k", "M", "G", "T", "P", "E" };
        private static final String[] JEDEC_Units = { "", "K", "M", "G" };

        private static String _ToString_SI(long dataSize)
        {
            String result = "B";
            long quotient = dataSize;
            long reminder;
            long divisor = 1000;
            int i = 0;
            
            while (quotient / divisor > 0 && i < SI_Units.length - 1) {
                reminder = quotient % divisor;
                if (reminder > 0) {
                    result = reminder + SI_Units[i] + result;
                }
                quotient = quotient / divisor;
                i++;
            }

            if (quotient > 0) {
                result = quotient + SI_Units[i] + result;
            }

            return result;
        }

        private static String _ToString_IEC(long dataSize)
        {
            String result = "B";
            long quotient = dataSize;
            long reminder;
            long divisor = 1024;
            int i = 0;
            
            while (quotient / divisor > 0 && i < IEC_Units.length - 1) {
                reminder = quotient % divisor;
                if (reminder > 0) {
                    result = reminder + IEC_Units[i] + result;
                }
                quotient = quotient / divisor;
                i++;
            }

            if (quotient > 0) {
                result = quotient + IEC_Units[i] + result;
            }

            return result;
        }


        private static String _ToString_JEDEC(long dataSize)
        {
            String result = "B";
            long quotient = dataSize;
            long reminder;
            long divisor = 1024;
            int i = 0;
            
            while (quotient / divisor > 0 && i < JEDEC_Units.length - 1) {
                reminder = quotient % divisor;
                if (reminder > 0) {
                    result = reminder + JEDEC_Units[i] + result;
                }
                quotient = quotient / divisor;
                i++;
            }

            if (quotient > 0) {
                result = quotient + JEDEC_Units[i] + result;
            }

            return result;
        }
    }


    /**
     * This method adds a string <code>value</code> to the given <code>row</code> in the given <code>data</code> set.
     * Adding starts from left (column 0) to right and the <code>value</code> is put into the first cell that
     * has no value in the given row (the column is shorter or the value is {@link org.pipeline.core.data.MissingValue}).
     * 
     * In the case <code>row</code> is greater or equal than the current number of rows in the first column
     * the method grows <code>data</code> accordingly. In the case all columns include some values in the row,
     * the method adds an additional {@link org.pipeline.core.data.columns.StringColumn} filled
     * with <code>MissingValue</code>s and adds the string value into the given row.
     * 
     * @param data
     * @param row
     * @param value
     * @throws DataException
     */
    public static void AddOrAppendToRow(Data data, int row, String value)
    throws DataException
    {
        if (data.getColumns() == 0) {
            data.addColumn(new StringColumn());
            data.column(0).appendStringValue(value);
        } else if (row < data.column(0).getRows()) {
            int col = 0;
            while (col < data.getColumns()) {
                if (data.column(col).getRows() <= row) {
                    data.column(col).padToSize(row);
                    data.column(col).appendStringValue(value);
                    return;
                } else if (data.column(col).isMissing(row)) {
                    data.column(col).setObjectValue(row, value);
                    return;
                }
                col++;
            }
            data.addColumn(new StringColumn(row));
            data.column(col).appendStringValue(value);
        } else {
            data.column(0).padToSize(row);
            data.column(0).appendStringValue(value);
        }
    }


    public static class DumperThread extends Thread
    {
        InputStream inStream;
        File outFile;
        OutputStream outStream;
        Object lockObject;
        boolean closeOutput;


        public DumperThread(InputStream inputStream, File outputFile)
        {
            this.inStream = inputStream;
            this.outFile = outputFile;
            this.closeOutput = true;
        }

        public DumperThread(InputStream inputStream, OutputStream outputStream, boolean closeOutput)
        {
            this.inStream = inputStream;
            this.outStream = outputStream;
            this.closeOutput = closeOutput;
        }

        public DumperThread(InputStream inputStream, OutputStream outputStream, Object outputLockObject)
        {
            this.inStream = inputStream;
            this.outStream = outputStream;
            this.lockObject = outputLockObject;
            // This is implicit because if a lock object is present it means that
            // the output stream is shared by some other thread.
            this.closeOutput = false;
        }


        @Override
        public void run() 
        {
            int n;
            byte[] buffer = new byte[16384];

            BufferedOutputStream out = null;

            try {
                if (outFile != null) {
                    out = new BufferedOutputStream(new FileOutputStream(outFile));
                } else if (outStream != null) {
                    out = new BufferedOutputStream(outStream);
                }

                if (lockObject != null) {
                    while ((n = inStream.read(buffer)) > 0) {
                        synchronized (lockObject) {
                            out.write(buffer, 0, n);
                        }
                    }
                    synchronized (lockObject) {
                        out.flush();
                    }
                } else {
                    while ((n = inStream.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                    out.flush();
                }
            } catch (IOException x) {
                System.out.println("IOException in the dumper thread: " + x);
            } finally {
                // Do not close the output stream if closeOutput is false
                // or lockObject is used which mean that the stream is shared
                // with some other thread
                if (out != null && closeOutput && lockObject == null) {
                    try {
                        out.close();
                    } catch (IOException x) {
                        System.out.println("Problems when closing the output file: " + x);
                    }
                }
            }
        }
    }
}
