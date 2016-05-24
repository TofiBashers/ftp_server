package com.tofixxx.ftp_server.FTP

import com.tofixxx.ftp_server.servThread
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

/**
 * Created by TofixXx on 30.04.2016.
 */
class FTPServer : Runnable {

    protected var addr: String  = "192.168.88.11";
    protected var SERVER_PORT: Int = 21;

    protected var servSock: ServerSocket? = null;

    constructor (){}

    constructor(addr: String, server_port: Int){
        this.addr = addr;
        System.out.println(this.addr);
        this.SERVER_PORT = server_port;
    }

    override fun run() {
        openServSocket();
        while(true)
        {
            var clientSock: Socket;
            try
            {
                clientSock = this.servSock?.accept() as Socket;
                Thread(servThread(clientSock, this.addr, this.SERVER_PORT)).start();
            } catch ( e: IOException)
            {
                System.out.println("Catched exception while waiting for client connections on server");
            }
        }
    }

    private fun openServSocket()
    {
        System.out.println("Opening server socket :\nSERVER PORT = " + this.SERVER_PORT);
        try
        {
            this.servSock = ServerSocket(this.SERVER_PORT);
        } catch ( e: IOException)
        {
            throw RuntimeException("Error while opening port " + this.SERVER_PORT, e);
        }
    }
}