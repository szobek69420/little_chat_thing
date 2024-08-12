package org.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Main {
    public static void main(String[] args) {
        String url="2762c27e037025b3.247ctf.com";
        int port=50364;

        Socket socket=null;
        PrintWriter in=null;
        BufferedReader out=null;
        try{
            socket=new Socket(url,port);
            in =new PrintWriter(socket.getOutputStream(),true);
            out=new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }
        catch(Exception e){}

        if(socket==null)
        {
            System.out.println("scheisse");
            return;
        }

        try{
            System.out.println(out.readLine());

            in.close();
            out.close();
            socket.close();
        }
        catch(Exception e){}
    }
}