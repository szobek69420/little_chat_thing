package main.java.org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class Server extends Thread{

    private class ClientConnection extends Thread{
        private static int currentId=1;

        private Server server;
        private Socket socket;
        public PrintWriter out;
        private BufferedReader in;
        public String name;
        private boolean shouldStop=false;

        public ClientConnection(Server server, Socket _socket)
        {
            this.server=server;
            name="User "+currentId++;
            try{
                socket = _socket;
                out=new PrintWriter(socket.getOutputStream(),true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            }
            catch(Exception ex)
            {
                System.out.println("Something went wrong while trying to establish connection with the client: ");
                System.out.println(ex.getMessage()+"\n");

                try{
                    socket.close();
                    out.close();
                    in.close();
                }
                catch(Exception ex2){}
            }
        }

        public void run()
        {
            if(socket==null||in==null||out==null)
                return;

            do {
                byte[] address = socket.getInetAddress().getAddress();
                byte[] address2=socket.getLocalAddress().getAddress();
                int port = socket.getPort();
                int port2= socket.getLocalPort();
                System.out.println("Connection opened to: " + (256 + address[0]) % 256 + "." + (256 + address[1]) % 256 + "." + (256 + address[2]) % 256 + "." + (256 + address[3]) % 256 + ":" + port+" from "+(256 + address2[0]) % 256 + "." + (256 + address2[1]) % 256 + "." + (256 + address2[2]) % 256 + "." + (256 + address2[3]) % 256 + ":" + port2);
            }while(false);


            while(shouldStop==false)
            {
                String line=null;
                try {
                    line=in.readLine();
                } catch (IOException e) {
                    continue;
                }

                if(line==null)
                    break;

                String[] words=line.split("\t");
                if(words[0].equals("COMMAND"))
                    handleCommand(words);
                else if(words[0].equals("MESSAGE"))
                {
                    server.forwardMessage(this.name,words[1],line.substring(words[1].length()+9));
                }
            }

            removeConnection(this);

            try{
                out.close();
                in.close();
                socket.close();
            }
            catch(Exception ex){}

            do{
                byte[] address = socket.getInetAddress().getAddress();
                int port = socket.getPort();
                System.out.println("Connection closed: " + (256 + address[0]) % 256 + "." + (256 + address[1]) % 256 + "." + (256 + address[2]) % 256 + "." + (256 + address[3]) % 256 + ":" + port);
            }while(false);
        }

        private void handleCommand(String[] words)
        {
            switch(words[1])
            {
                case "EXIT":
                    shouldStop=true;
                    break;

                case "NAME":
                    String previousName=name;
                    name=words[2];
                    System.out.println(previousName+" changed their name to: "+name);
                    break;
            }
        }

        public void endConnection()
        {
            out.println("EXIT");
            shouldStop=true;
        }
    }

    private ArrayList<ClientConnection> connections=new ArrayList<>();


    public void run()
    {
        ServerSocket serverSocket=null;

        try{
            DatagramSocket s=new DatagramSocket();
            s.connect(InetAddress.getByAddress(new byte[]{4,2,2,2}), 60000);
            byte[] address=s.getLocalAddress().getAddress();
            s.close();

            serverSocket=new ServerSocket(50069,10,InetAddress.getByAddress(address));
        }
        catch(Exception ex)
        {
            System.out.println("Server could not be started:");
            System.out.println(ex.getMessage());
        }

        if(serverSocket==null)
            return;

        ServerSocket finalServerSocket = serverSocket;//hogy ne sirjon
        AtomicReference<Boolean> shouldStop= new AtomicReference<>(false);
        Thread socketWelcomer=new Thread(() -> {
            while(shouldStop.get() ==false)
            {
                try{
                    ClientConnection cc=new ClientConnection(this, finalServerSocket.accept());
                    cc.start();
                    connections.add(cc);
                }
                catch(Exception ex){}
            }
        });

        Thread ipQuery=new Thread(()->{

            byte[] address=new byte[4];
            DatagramPacket returnPacket=new DatagramPacket(address,4);
            try{
                byte[] ipAddress= finalServerSocket.getInetAddress().getAddress();
                address[0]=ipAddress[0];
                address[1]=ipAddress[1];
                address[2]=ipAddress[2];
                address[3]=ipAddress[3];
                returnPacket.setData(address);
                System.out.println("Address: "+(256+ipAddress[0])%256+"."+(256+ipAddress[1])%256+"."+(256+ipAddress[2])%256+"."+(256+ipAddress[3])%256+":50069");
            }catch(Exception ex){
                System.out.println("Server cannot be reached");
                System.out.println(ex.getMessage());
                return;
            }

            DatagramSocket ipSocket=null;
            try{
                ipSocket=new DatagramSocket(50070);
            }
            catch(Exception ex)
            {
                System.out.println("Server cannot be reached");
                System.out.println(ex.getMessage());
                return;
            }

            while(shouldStop.get()==false)
            {
                //receive packet into packet
                DatagramPacket packet=new DatagramPacket(new byte[4],4);
                try{ipSocket.receive(packet);} catch(Exception ex){continue;}

                //send back the servers address
                returnPacket.setPort(packet.getPort());
                try{returnPacket.setAddress(InetAddress.getByAddress(packet.getData()));}catch(Exception ex){}
                try{ipSocket.send(returnPacket);}catch (Exception ex){continue;}
            }

            try{
                ipSocket.close();
            }
            catch (Exception ex){}
        });


        socketWelcomer.start();
        ipQuery.start();

        BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
        while(true)
        {
            String line=null;
            try{line=in.readLine();}catch(Exception ex){continue;}
            if(line.equals("exit"))
            {
                shouldStop.set(true);
                try{ Socket tempSocket=new Socket(serverSocket.getInetAddress(),50069); tempSocket.close();} catch (Exception ex){}
                try{
                    DatagramSocket tempSocket=new DatagramSocket(50071);
                    DatagramPacket tempPacket=new DatagramPacket(new byte[4],0,4);
                    tempPacket.setPort(50070);
                    tempPacket.setAddress(InetAddress.getLocalHost());
                    tempSocket.send(tempPacket);
                    tempSocket.close();
                }catch(Exception ex){}
                break;
            }
        }

        try{
            socketWelcomer.join();
            ipQuery.join();
        }
        catch(Exception ex){}


        for(ClientConnection cc : connections)
            cc.endConnection();
        connections.clear();
    }

    private void removeConnection(ClientConnection connection)
    {
        connections.remove(connection);
    }

    private boolean forwardMessage(String sender, String addressee, String message)
    {
        for(int i=0;i<connections.size();i++)
        {
            if(connections.get(i).name.equals(addressee))
            {
                connections.get(i).out.println("MESSAGE\t"+sender+ "\t"+ message);
                System.out.println("message sent by "+sender+" to "+addressee);
                return true;
            }
        }

        return false;
    }



    public static void main(String[] args)
    {
        Server server=new Server();
        server.start();
        try{
            server.join();
        }
        catch(Exception ex){}

        System.out.println("server closed");
    }
}
