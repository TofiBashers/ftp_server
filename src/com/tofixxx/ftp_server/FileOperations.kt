package com.tofixxx.ftp_server

import java.util.List
import java.io.*
import java.net.ConnectException
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by TofixXx on 30.04.2016.
 */
class FileOperations {
    private var SERVER_THREAD: servThread? = null;
    private var DATA_PORT: Int = 20;
    private var DATA_HOST: String? = null ;
    private var isPasv: Boolean = true;
    var dataSocket: Socket? = null;
    var servSocket: ServerSocket? = null;

    constructor(SRVTHREAD: servThread)
    {
        this.SERVER_THREAD = SRVTHREAD;
    }

    fun setDataPort( port: Int ){
        this.isPasv = true;
        this.DATA_PORT = port;
    }

    fun openPort(){
        if (isPasv){
            servSocket = ServerSocket(this.DATA_PORT);
            dataSocket = (servSocket as ServerSocket).accept();
        }else{
            dataSocket = Socket(this.DATA_HOST, this.DATA_PORT);
        }
    }

    fun setDataPort(host: String?, port: Int)
    {
        this.DATA_PORT = port;
        this.DATA_HOST = host;
        this.isPasv = false;
        System.out.println("Connection settings for data transmittion:");
        System.out.println("\tHost = " + host + "\n\tPort = " + port);
    }

    fun sendList(path: String?): Int
    {
        try
        {
            var dir: File = File(path);
            var filenames: String[] = dir.list();
            var filesCount: Int = (filenames == null ? 0 : filenames.length);

            var writer: PrintWriter = PrintWriter(OutputStreamWriter(dataSocket?.getOutputStream()), true);

            this.SERVER_THREAD?.reply(150, "Creating data connection successfull - starting list transmittion");

            for(Int i = 0 : filesCount)
            {
                String fName = filenames[i];
                File file = new File(dir, fName);
                this.listFile(file, writer);
                System.out.println("LIST processing: " + dir + "\\" + fName);
            }
            writer.flush();
            System.out.println("LIST [" + filesCount + " file(s)] transfered");
            this.SERVER_THREAD?.reply(226, "Transfer complete");
        } catch ( e: ConnectException)
        {
            this.SERVER_THREAD?.reply(425, "Can't open data connection. Pleasy try again");
            return 1;
        }catch ( e: Exception)
        {
            e.printStackTrace();
            this.SERVER_THREAD.reply(550, "No such directory");
            return 1;
        } finally
        {
            try
            {
                if(dataSocket != null)
                    (dataSocket as Socket).close();
                if(servSocket != null)
                    (servSocket as ServerSocket).close();
            } catch ( e: IOException)
            {}
        }
        return 0;
    }
    fun receiveFile(path: String? )
    {
        var fos: FileOutputStream?  = null;
        try
        {
            var inpStream: InputStream?  = dataSocket?.getInputStream();

            var f: File  = File(path);
            if(f.exists())
            {
                this.SERVER_THREAD?.reply(550, "File already exist: " + path);
                return;
            }
            fos = FileOutputStream(f);
            if(this.DATA_PORT == -1)
            {
                this.SERVER_THREAD?.reply(500, "Send a PORT cmd first");
                return;
            }
            this.SERVER_THREAD.reply(150, "Starting to receive file " + path);
            //Здесь непосредственно прием файла
            List<Byte> buf = ArrayList();
            Int nread;
            while((nread = inpStream.read(buf, 0, 1024)) > 0)
            {
                fos.write(buf, 0, nread);
            }
            inpStream?.close();

            this.SERVER_THREAD?.reply(226, "Transfer completed successfuly");
        } catch( e: ConnectException)
        {
            this.SERVER_THREAD?.reply(420, "Connection error");
            return;
        } catch ( e: FileNotFoundException)
        {
            this.SERVER_THREAD?.reply(500, "File not exist");
            return;
        } catch ( e: UnknownHostException)
        {
            System.out.println("Host unknown");
            this.SERVER_THREAD?.reply(500, "Host unknown");
            return;
        } catch ( e: Exception)
        {
            System.out.println("Unknown exception");
            this.SERVER_THREAD?.reply(500, "exception unknown");
            return;
        } finally
        {
            try
            {
                if(fos != null)
                    fos.close();
                if(dataSocket != null)
                    (dataSocket as Socket).close();
                if(servSocket != null)
                    (servSocket as ServerSocket).close();

            } catch( e: IOException)
            {}
        }
    }

    fun sendFile(path: String? )
    {
        var fis: FileInputStream? = null;
        try
        {
            var out: OutputStream?  = dataSocket?.getOutputStream();

            var f: File = File(path);
            if(!f.isFile())
            {
                this.SERVER_THREAD?.reply(550, "Not a file");
                return;
            }
            fis = FileInputStream(f);
            if(this.DATA_PORT == -1)
            {
                this.SERVER_THREAD?.reply(500, "Send a PORT cmd first");
                return;
            }
            this.SERVER_THREAD?.reply(150, "Starting to transfer file " + path);
            //Здесь непосредственно передача файла
            var buf: Byte[] = Byte[1024];
            var nread: Int;
            while((nread = fis.read(buf)) > 0)
            {
                out?.write(buf, 0, nread);
            }
            fis.close();

            this.SERVER_THREAD?.reply(226, "Transfer completed");
        } catch ( e: FileNotFoundException)
        {
            this.SERVER_THREAD?.reply(500, "File not exist");
            return;
        } catch ( e: UnknownHostException)
        {
            System.out.println("Host unknown");
            this.SERVER_THREAD?.reply(500, "Host unknown");
            return;
        } catch ( e: Exception)
        {
            System.out.println("Unknown exception");
            this.SERVER_THREAD?.reply(500, "exception unknown");
            return;
        } finally
        {
            try
            {
                if(fis != null)
                    fis.close();
                if(dataSocket != null)
                    (dataSocket as Socket).close();
                if(servSocket != null)
                    (servSocket as ServerSocket).close();
            } catch( e: IOException)
            {
                //ignore
            }
        }
    }
    private fun listFile(f: File, writer: PrintWriter)
    {
        var date: Date = Date(f.lastModified());
        var dateFormat: SimpleDateFormat = SimpleDateFormat("MMM dd hh:mm", Locale.ENGLISH);
        var dateStr: String = dateFormat.format(date);

        var size: Long  = f.length();
        var sizeStr: String = Long.toString(size);
        var sizePadLength: Int = Math.max(13 - sizeStr.length, 0);
        var sizeField: String = pad(sizePadLength) + sizeStr;

        if (f.isDirectory())
            writer.print("d");
        else
            writer.print("-");
        writer.print("rwxrwxrwx   1 ftp      ftp ");
        writer.print(sizeField);
        writer.print(" ");
        writer.print(dateStr);
        writer.print(" ");
        writer.println(f.getName());
    }

    private fun pad(length: Int): String?
    {
        var buf: StringBuffer = StringBuffer();
        for (Int i = 0; i < length; i++)
        {
            buf.append((Char)' ');
        }
        return buf.toString();
    }
}