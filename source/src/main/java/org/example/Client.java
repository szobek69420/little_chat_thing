package main.java.org.example;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
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

    public class Message{
        public final String sender,receiver,text;
        public Message(String sender, String receiver, String text){this.sender=sender; this.receiver=receiver; this.text=text;}
    }

    private final ArrayList<Message> messages=new ArrayList<>();
    private String currentPartner=null;
    private boolean shouldClose=false;
    private final Object syncObject=new Object();

    //graphics
    private final JFrame window;
    private final JSplitPane windowContent1, windowContent2;
    private final JPanel contacts;
    private final JPanel chat;
    private final JTextArea console;

    public Client(String name)
    {
        this.name=name;

        this.contacts=new JPanel();
        this.contacts.setBackground(new Color(30,30,30));
        this.contacts.setMinimumSize(new Dimension(200,50));

        this.chat=new JPanel();
        this.chat.setBackground(new Color(0,0,0));
        this.chat.setMinimumSize(new Dimension(200,50));

        this.console=new JTextArea();
        this.console.setBackground(new Color(20,20,20));
        this.console.setCaretColor(new Color(0,255,0));
        this.console.setMinimumSize(new Dimension(100,50));

        this.windowContent2=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,this.contacts,this.chat);
        this.windowContent1=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,this.windowContent2,this.console);

        this.window=new JFrame("roblox client");
        this.window.setLayout(new GridLayout(1,1));
        this.window.add(this.windowContent1);

        this.window.addWindowListener(new WindowListener() {
            public void windowOpened(WindowEvent e) {}
            public void windowClosing(WindowEvent e) {handleConsole("0");}
            public void windowClosed(WindowEvent e) {handleConsole("0");}
            public void windowIconified(WindowEvent e) {}
            public void windowDeiconified(WindowEvent e) {}
            public void windowActivated(WindowEvent e) {}
            public void windowDeactivated(WindowEvent e) {}
        });

        this.window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.window.pack();
        this.window.setVisible(true);
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
            console.append("Could not acquire the server's address\n");
            console.append(ex.getMessage()+"\n");
            return;
        }
        console.append("Server address: "+(256+serverAddress[0])%256+"."+(256+serverAddress[1])%256+"."+(256+serverAddress[2])%256+"."+(256+serverAddress[3])%256+":50069\n");
        //remaining things
        try {
            socket=new Socket(InetAddress.getByAddress(serverAddress),50069);
            out=new PrintWriter(socket.getOutputStream(),true);
            in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (Exception e) {
            console.append("Something is fucked:\n");
            console.append(e.getMessage()+"\n");
            return;
        }

        out.println("COMMAND\tNAME\t"+this.name);

        console.append("connection successful, alive: "+(socket.isConnected()&&!socket.isClosed())+"\n");


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
                    messages.add(new Message(words[1],name,message.substring(9+words[1].length())));
                    refreshChat();
                    break;

                case "EXIT":
                    shouldClose=true;
                    console.append("Connection closed remotely\n");
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
                    if(out==null)
                        break;
                    shouldClose=true;
                    out.println("COMMAND\tEXIT");
                    console.append("Connection ended manually\n");
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
                    messages.add(new Message(name,addressee,text));
                    console.append("Message sent\n");
                    refreshChat();
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

    private void refreshChat()
    {
        this.contacts.removeAll();
        this.chat.removeAll();

        ArrayList<String> people=new ArrayList<>();
        for(int i=0;i<messages.size();i++)
        {
            boolean found=false;
            for(int j=0;j<people.size();j++)
            {
                if(messages.get(i).sender.equals(people.get(j))&&!messages.get(i).sender.equals(this.name))
                {
                    people.remove(j);
                    people.add(messages.get(i).sender);
                    found=true;
                    break;
                }
                if(messages.get(i).receiver.equals(people.get(j))&&!messages.get(i).receiver.equals(this.name))
                {
                    people.remove(j);
                    people.add(messages.get(i).receiver);
                    found=true;
                    break;
                }
            }
            if(found)
                continue;

            if(!messages.get(i).sender.equals(this.name))
                people.add(messages.get(i).sender);
            else if(!messages.get(i).receiver.equals(this.name))
                people.add(messages.get(i).receiver);
        }

        if(currentPartner!=null&&!people.contains(currentPartner))
            people.add(currentPartner);

        JPanel scrollableContent=new JPanel();
        scrollableContent.setLayout(new BoxLayout(scrollableContent,BoxLayout.Y_AXIS));
        scrollableContent.setBackground(new Color(30,30,30));
        for(int i=people.size()-1;i>=0;i--)
        {
            JButton button=new JButton();
            button.setBackground(new Color(40,40,40));
            button.setForeground(new Color(0,255,255));
            button.setMaximumSize(new Dimension(3000,50));
            button.setText(people.get(i));
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
            int finalI = i;
            button.addActionListener((e)->{
                currentPartner=people.get(finalI);
                refreshChat();
            });
            scrollableContent.add(button);
        }
        JScrollPane scrollable=new JScrollPane(scrollableContent);

        this.contacts.setLayout(new GridLayout(1,1));
        this.contacts.add(scrollable);

        console.append("chat repainted\n");
        scrollable.setVisible(true);
        scrollable.revalidate();

        //chat content
        JPanel inputPart=new JPanel();
        inputPart.setLayout(new BoxLayout(inputPart,BoxLayout.X_AXIS));
        inputPart.setMaximumSize(new Dimension(3000,20));
        inputPart.setBackground(new Color(0,0,0));
        JTextField inputField=new JTextField();
        inputField.setMaximumSize(new Dimension(3000,20));
        JButton sendButton=new JButton(">");
        sendButton.setMaximumSize(new Dimension(30,20));
        sendButton.setBackground(new Color(0,255,255));
        sendButton.setForeground(new Color(0,0,0));
        sendButton.addActionListener((e)->{
            if(out!=null&&currentPartner!=null&&inputField.getText()!=null&&inputField.getText().trim().equals("")==false)
            {
                out.println("MESSAGE\t"+currentPartner+"\t"+inputField.getText());
                messages.add(new Message(this.name,currentPartner,inputField.getText()));
                refreshChat();
            }
        });
        inputPart.add(inputField);
        inputPart.add(sendButton);


        JPanel scrollableContentChat=new JPanel();
        scrollableContentChat.setBackground(new Color(0,0,0));
        scrollableContentChat.setLayout(new BoxLayout(scrollableContentChat,BoxLayout.Y_AXIS));
        for(int i=0;i<messages.size();i++)
        {
            if(messages.get(i).sender.equals(currentPartner)||messages.get(i).receiver.equals(currentPartner))
            {
                JLabel label=new JLabel(messages.get(i).sender+" > "+messages.get(i).receiver+": "+messages.get(i).text);
                label.setBackground(new Color(0,0,0));
                label.setForeground(new Color(0,255,0));
                label.setHorizontalAlignment(SwingConstants.LEFT);
                scrollableContentChat.add(label);
            }
        }

        JScrollPane scrollableChat=new JScrollPane(scrollableContentChat);

        this.chat.setLayout(new BoxLayout(this.chat,BoxLayout.Y_AXIS));
        this.chat.add(scrollableChat);
        this.chat.add(inputPart);


        this.windowContent2.repaint();
        this.windowContent2.revalidate();
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
