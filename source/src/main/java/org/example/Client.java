package main.java.org.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;

public class Client extends Thread {
    private String name;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private final ArrayList<String> messages=new ArrayList<>();
    private boolean shouldClose=false;
    private final Object syncObject=new Object();

    public Client(String name)
    {
        this.name=name;
    }

    public void run()
    {
        //get servers ip address
        byte[] serverAddress=new byte[4];
        try{
            DatagramSocket s=new DatagramSocket();
            s.connect(InetAddress.getByAddress(new byte[]{4,2,2,2}), 60000);
            byte[] address=s.getLocalAddress().getAddress();
            s.close();

            DatagramSocket ipSocket=new DatagramSocket(50072);
            ipSocket.setSoTimeout(5000);

            DatagramPacket packet=new DatagramPacket(address,4);
            packet.setAddress(InetAddress.getByAddress(new byte[]{(byte)255,(byte)255,(byte)255,(byte)255}));
            packet.setPort(50070);
            ipSocket.send(packet);

            packet.setData(serverAddress);
            ipSocket.receive(packet);

            ipSocket.close();
        }
        catch (Exception ex)
        {
            System.out.println("Could not acquire the server's address");
            System.out.println(ex.getMessage());
            return;
        }
        System.out.println("Server address: "+(256+serverAddress[0])%256+"."+(256+serverAddress[1])%256+"."+(256+serverAddress[2])%256+"."+(256+serverAddress[3])%256+":50069");
        //remaining things
        try {
            socket=new Socket(InetAddress.getByAddress(serverAddress),50069);
            out=new PrintWriter(socket.getOutputStream(),true);
            in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (Exception e) {
            System.out.println("Something is fucked:");
            System.out.println(e.getMessage());
            return;
        }

        out.println("COMMAND\tNAME\t"+this.name);

        System.out.println("connection successful, alive: "+(socket.isConnected()&&!socket.isClosed()));


        Thread inputWaiter=new Thread(()->{
            while(shouldClose==false)
            {
                String line=null;
                try{ line=in.readLine();}catch(Exception ex){ continue; }

                if(line==null) line="EXIT";
                handleIncoming(line);
            }
        });
        inputWaiter.start();

        BufferedReader br=new BufferedReader(new InputStreamReader(System.in));
        while(shouldClose==false)
        {
            System.out.println("\n\n\nSelect an option:\n(0) exit\n(1) send message\n(2) view messages");

            String line=null;
            try{ line=br.readLine();}catch(Exception ex){ continue; }
            handleConsole(line);
        }

        try{
            out.close();
            in.close();
            socket.close();
            inputWaiter.join();
        }
        catch (Exception ex){}
    }

    private void handleIncoming(String message)
    {
        synchronized (syncObject){
            String[] words=message.split("\t");
            switch(words[0])
            {
                case "MESSAGE":
                    System.out.println("MESSAGE FROM "+words[1]+": "+message.substring(9+words[1].length()));
                    messages.add(words[1]+" > "+name+": "+message.substring(9+words[1].length()));
                    break;

                case "EXIT":
                    shouldClose=true;
                    System.out.println("Connection closed remotely");
                    break;
            }
        }
    }

    private void handleConsole(String input)
    {
        synchronized (syncObject)
        {
            switch(input)
            {
                case "0":
                    shouldClose=true;
                    out.println("COMMAND\tEXIT");
                    System.out.println("Connection ended manually");
                    break;

                case "1":
                    System.out.println("\n\n\nReceiver: ");
                    String addressee=null;
                    try{addressee=new BufferedReader(new InputStreamReader(System.in)).readLine();}catch(Exception ex){}
                    if(addressee==null)
                        break;
                    System.out.println("Text: ");
                    String text=null;
                    try{text=new BufferedReader(new InputStreamReader(System.in)).readLine();}catch(Exception ex){}
                    if(text==null)
                        break;
                    out.println("MESSAGE\t"+addressee+"\t"+text);
                    messages.add(name+" > "+addressee+": "+text);
                    System.out.println("\n\nMessage sent");
                    break;

                case "2":
                    System.out.println("\n\n\n");
                    for(int i=0;i<messages.size();i++)
                        System.out.println(messages.get(i));
                    break;

                default:
                    System.out.println("\n\n\nInvalid input");
                    break;
            }
        }
    }

    public static void main(String[] args)
    {
        String name=null;
        System.out.print("What's your name: ");
        try{name=new BufferedReader(new InputStreamReader(System.in)).readLine();}catch(Exception ex){}
        if(name==null) name="i'm gay";

        Thread thread=new Client(name);
        thread.start();
        try{thread.join();}catch(Exception ex){}
    }
}
