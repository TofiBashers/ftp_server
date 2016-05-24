package com.tofixxx.ftp_server

import com.tofixxx.ftp_server.FTP.FTPServer
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Created by TofixXx on 23.05.2016.
 */
fun main(args: Array<String>) {
    var server: FTPServer ;

    if (args.size == 2){
        try{
            server = FTPServer(args[0], Integer.parseInt(args[1]));
        } catch (e: NumberFormatException  ){
            System.out.println("Bad arguments!!!");
            server = FTPServer();
        }
    }
    else{
        server = FTPServer();
    }

    var ftpThread: Thread = Thread(server);
    ftpThread.start();

    var isQuit: Boolean = true;
    while(isQuit)
    {
        try
        {
            Thread.sleep(1000);
            try
            {
                if(System.`in`.read().equals('q'))
                {
                    System.out.println("Quit command catched");

                    ftpThread.interrupt();
                    isQuit = false;
                }
            } catch (e: IOException)
            {
                e.printStackTrace();
            }
        } catch (e: InterruptedException)
        {
            Logger.getLogger("main: ").log(Level.SEVERE, null, e);
        }
    }

    System.out.println("Server finished");
    System.exit(0);
}