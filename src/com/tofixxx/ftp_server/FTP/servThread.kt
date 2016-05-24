package com.tofixxx.ftp_server

import com.tofixxx.ftp_server.FileOperations
import java.io.*
import java.net.Socket
import java.util.*

/**
 * Created by TofixXx on 30.04.2016.
 */
class servThread : Runnable {

    protected var addr: String? = null;
    protected var nextPort: Int = 55000;

    private var USERS: MutableList<String>? = null;
    private var PASSWORDS: MutableList<String>? = null;

    protected var clientSock: Socket? = null;

    private var reader: BufferedReader? = null;
    private var writer: PrintWriter? = null;

    private var FileOperations: FileOperations? = null;

    private var type: Char? = 'I';

    private var username: String? = null;
    private var isAuth: Boolean = false;

    private val baseDirectory: String = "D:\\FTPServerRoot";
    private var currentDir: String? = baseDirectory;

    constructor( clientSocket: Socket, addr: String, cPort: Int)
    {
        try
        {
            this.clientSock = clientSocket;
            this.reader = BufferedReader(InputStreamReader((clientSock as Socket).getInputStream()));
            this.writer = PrintWriter(OutputStreamWriter((clientSock as Socket).getOutputStream()), true);
            this.FileOperations = FileOperations(this);

            this.addr = addr;

            var BFreader: BufferedReader = BufferedReader(FileReader("passwords.txt"));
            USERS = ArrayList<String>();
            PASSWORDS = ArrayList<String>();
            var line: String;
            var st: StringTokenizer;
            for(line in BFreader.readLines()){
                st = StringTokenizer(line);
                if(st.countTokens() != 2)
                    continue;
                this.USERS?.add(st.nextToken());
                this.PASSWORDS?.add(st.nextToken());
            }
        } catch ( e: IOException)
        {
            System.out.println("FTP server creation failed");
        }
    }

    override fun run() {

        System.out.println("Base directory is " + baseDirectory);

        reply(220, "Server ready");
        try
        {
            this.loop();
        } catch (e: Exception )
        {
            System.out.println("Server control channel: commands loop failed");
            e.printStackTrace();
        } finally
        {
            try
            {
                this.clientSock?.close();
                System.out.println("Client connection closed");
            } catch( e: IOException)
            {
                e.printStackTrace();
            }
        }
    }

    private fun loop()
    {
        var cmd: String?;
        var line: String?;
        var fileFR: String? = null;
        while(true)
        {
            line = this.ReadAndPrintMsg("--> Client says : ");
            if(line == null){
                break;
            }
            var st: StringTokenizer? = StringTokenizer(line);
            cmd = st?.nextToken();
            if (cmd.equals("user", true) && !this.isAuth)
            {
                var ans: Int = this.setUser(st?.nextToken());
                System.out.println("UserName: stringTokenizer = "+ans);
                if(ans==0)
                {
                    reply(331, "Username successfully changed-need password");
                } else if (ans == 1)
                {
                    reply(230, "Username successfully changed");
                }
            } else if (cmd.equals("pass", true) && !this.isAuth)
            {
                this.checkLogin();
                var result: Int = this.checkPassword(st?.nextToken().toString());

                if(result == 0)
                    reply(220, "Password successfully taken");
                else if (result == 1)
                    reply(531, "Incorrect password");
                else
                    reply(532, "No such user");
            } else if (cmd.equals("quit", true))
            {
                reply(221, "Goodbye!!!");
                break;
            } else if (!this.isAuth)
            {
                reply(530, "Please login with USER and PASS.");
                continue;
            } else if (cmd.equals("rnfr"))
            {
                checkLogin();
                fileFR = st?.nextToken();
                reply(200, "RNFR accepted");
            } else if (cmd.equals("rnto"))
            {
                checkLogin();
                var fileTO: String? = st?.nextToken();
                var f: File  = File(this.currentDir + fileFR);
                if (!f.isDirectory()){
                    f.renameTo(File(this.currentDir + fileTO));
                    reply(200, "RNTO accepted");
                } else {
                    reply(550, "RNTO it is directory!!!");
                }

            } else if (cmd.equals("type"))
            {
                checkLogin();
                var typeStr: String?  = st?.nextToken();
                if(typeStr?.length != 1)
                {
                    reply(500, "Cant use this type");
                } else
                {
                    reply(200, "Type accepted");
                    this.type = typeStr?.get(0);
                    System.out.println("Type accepted: " + this.type);
                }
            } else if (cmd.equals("syst"))
            {
                reply(210, "Windows 7");
            } else if (cmd.equals("noop"))
            {
                reply(200, "noop accepted");
            }else if (cmd.equals("pasv"))
            {
                checkLogin();
                var addrStr: String? = this.addr;
                st = StringTokenizer(addrStr, ".");
                var h1: String = st.nextToken();
                var h2: String = st.nextToken();
                var h3: String = st.nextToken();
                var h4: String = st.nextToken();
                var port: Int = nextPort();
                var p1: Int = port/256;
                var p2: Int = port%256;
                this.FileOperations?.setDataPort(p1*256+p2);
                reply(227, "Entering Passive Mode (" +
                        h1 + "," + h2 + "," + h3 + "," + h4 + "," + p1 + "," + p2 + ")");
                System.out.println("Entering Passive Mode");
                this.FileOperations?.openPort();
            } else if (cmd.equals("cwd") || cmd.equals("xcwd"))
            {
                checkLogin();
                var newDir: String? = this.getFullName(st);

                System.out.println("CWD cmd dir: " + newDir);
                if(newDir?.length == 0)
                    newDir = this.baseDirectory;

                newDir = this.resolvePath(newDir);
                var f: File = File(newDir);
                if(!f.exists()) {
                    reply(550, "No such directory: " + newDir);
                } else if(!f.isDirectory()) {
                    reply(550, "Not a directory: " + newDir);
                } else {
                    currentDir = newDir;
                    System.out.println("current dir: " + currentDir);
                    reply(250, "cwd command successfull");
                }
            } else if (cmd.equals("rmd") || cmd.equals("xrmd"))
            {
                checkLogin();
                var dirName: String? = this.getFullName(st);
                var path: String? = this.resolvePath(dirName);

                var dir: File = File(path);
                if(!dir.exists())
                    reply(550, "Directory doesn't exist: " + dirName);
                else if (!dir.isDirectory())
                    reply(550, dirName + " is not directory");
                else if (!dir.delete())
                    reply(550, "Error deleting directory " + dirName);
                else{
                    reply(250, "Directory successfuly removed: " + path);
                    System.out.println("Directory deleted successfuly: " + path);
                }
            } else if (cmd.equals("dele"))
            {
                checkLogin();
                var fName: String? = this.getFullName(st);
                var path: String? = this.resolvePath(fName);

                var f: File = File(path);

                if(!f.exists())
                    reply(550, "File doesn't exist: " + path);
                else if (!f.delete())
                    reply(550, "Error deleting file: " + path);
                else
                {
                    reply(250, "File deleted successfuly: " + path);
                    System.out.println("File deleted successfuly: " + path);
                }
            } else if (cmd.equals("mkd") || cmd.equals("xmkd"))
            {
                checkLogin();
                var dirName: String? = this?.getFullName(st);
                var path: String? = this?.resolvePath(dirName);

                var dir: File  = File(path);
                if(dir.exists())
                    reply(550, "Directory already exist: " + dirName);
                else if(!dir.mkdir())
                    reply(550, "Error creating directory "+ dirName);
                else{
                    this.reply(257, "Directory created: " + path );
                    System.out.println("Directory created: " + path);
                }

            } else if (cmd.equals("cdup"))
            {
                checkLogin();
                var newDir: String?  = this.resolvePath("..");
                System.out.println("resolvePath result: " + newDir);
                currentDir = newDir;
                reply(250, "cdup command successfull");
            } else if (cmd.equals("retr"))
            {
                checkLogin();
                var path: String? = this.resolvePath(this.getFullName(st));
                this.FileOperations?.sendFile(path);
            } else if (cmd.equals("stor"))
            {
                checkLogin();

                var path: String? = this.resolvePath(this.getFullName(st));
                this.FileOperations?.receiveFile(path);
            } else if (cmd.equals("port"))
            {
                checkLogin();
                var portStr: String?  = st?.nextToken();
                st = StringTokenizer(portStr, ",");
                var h1: String = st.nextToken();
                var h2: String = st.nextToken();
                var h3: String = st.nextToken();
                var h4: String = st.nextToken();
                var p1: Int = Integer.parseInt(st.nextToken());
                var p2: Int = Integer.parseInt(st.nextToken());

                var dataHost: String = h1 + "." + h2 + "." + h3 + "." + h4;
                var dataPort: Int = (p1 << 8) | p2;

                this.FileOperations?.setDataPort(dataHost, dataPort);

                reply(200, "Port cmd succedeed");

            } else if (cmd.equals("list") || cmd.equals("nlst") )
            {
                checkLogin();
                var path: String? = null;
                if(st?.hasMoreTokens()){
                    path = st?.nextToken();
                    if (path?.get(0) == '-'){
                        path = currentDir;
                    }
                } else {
                    path = currentDir;
                }

                System.out.println("Sending list in : " + path);
                //reply(200, "Port cmd succedeed");
                this.FileOperations?.sendList(path);
            } else if (cmd.equals("pwd") || cmd.equals("xpwd"))
            {
                this.checkLogin();
                reply(257, "\"" + this.currentDir + "\"" + " is current directory");
                System.out.println("pwd cmd anser : " + "\"" + this.currentDir + "\"" + " is current directory");
            } else
            {
                System.out.println("cmd unknown: " + cmd);
                reply(500, "Command not supported: " + cmd);
            }
        }
    }

    fun getFullName(tok: StringTokenizer?): String?
    {
        var elem: String?;
        var fullName: String? = null;
        while(tok?.hasMoreTokens() as Boolean)
        {
            elem = tok?.nextToken().toString();
            if(fullName != null)
                fullName = fullName + " " + elem;
            else
                fullName = elem;
        }
        System.out.println("FullName function result: " + fullName);
        return fullName;
    }

    private fun setUser(user: String?): Int
    {

        this.username = user;
        if(user.equals("anonymous")){
            this.isAuth = true;
            return 1;
        }
        System.out.println("username successfully changed to " + user);
        return 0;
    }
    private fun checkPassword(pass: String? ): Int
    {
        var it_u: MutableIterator<String>?  = USERS?.iterator();
        var it_p: MutableIterator<String>? = PASSWORDS?.iterator();
        while(it_u?.hasNext()!!)
        {
            var p: String? = it_p?.next();
            if(this.username.equals(it_u?.next()))
            {
                //checkPassword
                if(pass.equals(p))
                {
                    System.out.println("password successfully changed");
                    this.isAuth = true;
                    return 0;
                }
                else
                {
                    System.out.println("password wrong");
                    return 1;
                }
            }
        }
        System.out.println("No such user: " + this.username);
        return 2;
    }

    private fun checkLogin()
    {
        if(this.username == null)
        {
            reply(400, "Please login first");
        }
    }

    fun reply(code: Int, msg: String)
    {
        System.out.println("MSG to client <-- " + code + " " + msg);
        this.writer?.println(Integer.toString(code) + " " + msg);
    }

    fun ReadMsg(): String?
    {
        var ans: String? = null;
        try
        {
            ans = this.reader?.readLine();
        } catch ( e: IOException)
        {
            System.out.println("Error while reading msg from client");
        }

        return ans;
    }
    fun ReadAndPrintMsg( prefix: String?): String?
    {
        var ans: String? = ReadMsg();
        System.out.println(prefix + " " + ans);
        return ans;
    }
    fun resolvePath(path: String? ): String?
    {
        if (path?.length == 1){
            if (path?.get(0) == '\\' || path?.get(0) == '/'){
                path = this.currentDir?.get(0);
            } else
                path = this.currentDir + "\\" + path;
        }else if (path?.get(0) == '/'){
            path = this.baseDirectory;
            System.out.println("123: " + path );
        }else if (path?.get(1) != ':')
            path = this.currentDir + "\\" + path;

        var pathSt: StringTokenizer = StringTokenizer(path, "\\");
        var segments: Stack = Stack();
        while(pathSt.hasMoreTokens())
        {
            var segment: String = pathSt.nextToken();
            if(segment.equals(".."))
            {
                if(segments.size()!=1)
                    segments.pop();
            } else if (segment.equals("."))
            {//Пропускаем
            } else
            {
                segments.push(segment);
            }
        }
        var pathBuf: StringBuffer = StringBuffer();
        var segmentsEn: Enumeration = segments.elements();
        while (segmentsEn.hasMoreElements())
        {
            pathBuf.append(segmentsEn.nextElement());
            if (segmentsEn.hasMoreElements())
                pathBuf.append("\\");
        }

        return pathBuf.toString() + "\\";
    }

    fun nextPort(): Int {
        if (nextPort != 65500){
            nextPort +=1;
        } else {
            nextPort = 55000;
        }
        return nextPort;
    }
}