
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
import com.connexience.server.model.document.DocumentRecord;
import com.connexience.server.workflow.*;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.pipeline.core.data.*;
import org.pipeline.core.data.columns.StringColumn;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

public class MyService implements WorkflowBlock
{
    private final static String Prop_SSH_SERVER_ADDR = "SSH Server Address";
    private final static String Prop_SSH_SERVER_PORT = "SSH Server Port";
    private final static String Prop_USER_NAME = "User Name";
    private final static String Prop_USER_PASS = "User/Key Password";
    private final static String Prop_KNOWN_HOSTS = "Known Hosts";
    private final static String Prop_PRIV_KEY = "Private Key";
    private final static String Prop_QSUB_ARGS = "QSub Arguments";
    private final static String Prop_SCRIPT_FILE = "Script File";

    /**
     * This field refers to output port 'output-1' defined in service.xml
     */
    private final static String Output_INVOCATION_ID = "oge-invocation-id";


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
        JSch ssh_client =  new JSch();
        // Only for testing purposes
        //JSch.setConfig("StrictHostKeyChecking", "no");
        String userName = env.getStringProperty(Prop_USER_NAME, "");
        if ("".equals(userName)) {
            throw new IllegalArgumentException("Property: " + Prop_USER_NAME + " has not been set.");
        }
        String host = env.getStringProperty(Prop_SSH_SERVER_ADDR, "");
        if ("".equals(host)) {
            throw new IllegalArgumentException("Property: " + Prop_SSH_SERVER_PORT + " has not been set.");
        }
        int port = env.getIntProperty(Prop_SSH_SERVER_PORT, -1);
        if (port == -1) {
            throw new IllegalArgumentException("Property: " + Prop_SSH_SERVER_PORT + " has not been set.");
        }

        DocumentRecord hostsDoc = env.getDocumentProperty(Prop_KNOWN_HOSTS);
        if (hostsDoc.getId() != null) {
            ssh_client.setKnownHosts(env.downloadFile(hostsDoc).toString());
        }

        Session session;

        // Set password and private key if available
        String password = env.getStringProperty(Prop_USER_PASS, (String)null);
        DocumentRecord keyDoc = env.getDocumentProperty(Prop_PRIV_KEY);

        if (password == null && keyDoc == null) {
            throw new Exception("Neither password nor private key has been set.");
        }

        if (keyDoc != null) {
            ssh_client.addIdentity(env.downloadFile(keyDoc).toString(), password);
            session = ssh_client.getSession(userName, host, port);
        } else {
            session = ssh_client.getSession(userName, host, port);
            session.setPassword(password);
        }

        session.connect();
        ChannelExec channel = (ChannelExec)session.openChannel("exec");

        StringColumn invocationId = new StringColumn("QSub Invocation Id");

        try {
            channel.setCommand("qsub " + env.getStringProperty(Prop_QSUB_ARGS, ""));
            channel.setInputStream(null);

            channel.setOutputStream(System.out);
            channel.setErrStream(System.err);
            InputStream input = channel.getInputStream();

            channel.connect();
            byte[] buffer = new byte[1024];

            while (true) {
                while (input.available() > 0) {
                    if (input.read(buffer, 0, buffer.length) < 0) {
                        break;
                    }
                    String cmdOut = new String(buffer);
                    System.out.println("cmdout: " + cmdOut);
                }

                if (channel.isClosed()) {
                    if (input.available() > 0) {
                        continue;
                    } else {
                        int status = channel.getExitStatus();
                        if (status != 0) {
                            throw new RuntimeException("The qsub command error: exit status: " + status);
                        }
                        break;
                    }
                }

                Thread.sleep(500);
            }
        } finally {
            channel.disconnect();
            session.disconnect();
        }

        Data outputData = new Data();
        outputData.addColumn(invocationId);
        outputs.setOutputDataSet(Output_INVOCATION_ID, outputData);
    }

    /*
     * This code is called once when all of the data has passed through the block. 
     * It should be used to cleanup any resources that the block has made use of.
     */
    public void postExecute(BlockEnvironment env) throws Exception
    { }
}
