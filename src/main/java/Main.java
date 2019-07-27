import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.stream.Collectors;

class User{
    WebSocket sock;
    String name;
}
class AppState{
    ArrayList<User> usersInServer = new ArrayList<User>();
    WebSocketServer webSocketServer;
    WebSocketClient webSocketClient;
    JButton connectButton;
    JButton setNameButton;
    JComboBox<Draft> draftComboBox;
    JTextField uriField;
    JTextArea messageView;
    JButton closeButton;
    JTextField chatField;
    JFrame jFrame;
//    String myName;
}

class ChatMessage{
    String message;
    String fromUser;
    String messageify(){
        return new Gson().toJson(this);
    }
}

class SetNameMessage{
    String desiredName;
}


public class Main {
    private static WebSocketClient makeClient(AppState appState){
        Draft draft = (Draft) appState.draftComboBox.getSelectedItem();
        if(draft==null)return null;
        URI uri = null;
        try {uri = new URI(appState.uriField.getText());}catch (Exception uriex){System.out.println(uriex.toString());}
        if(uri==null)return null;
        return new WebSocketClient(uri, draft) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                appState.messageView.append("You are connected to ChatServer: " + getURI() + "\n");
                appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
            }

            @Override
            public void onMessage(String message) {
                JsonObject jsonObject = new Gson().fromJson(message,JsonObject.class);
                if(jsonObject.has("message")){
                    ChatMessage cm = new Gson().fromJson(message,ChatMessage.class);
                    appState.messageView.append( cm.message );
                    appState.messageView.append("\n");
                    appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
                }
//                appState.messageView.append(message);
//                ChatMessage chatMessage = new Gson().fromJson(message, ChatMessage.class);
//                appState.messageView.append(chatMessage.fromUser +" says: "+ chatMessage.message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                appState.messageView.append("You have been disconnected from: " + getURI() + "; Code: " + code + " " + reason + "\n");
                appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
                appState.connectButton.setEnabled(true);
                appState.uriField.setEditable(true);
                appState.draftComboBox.setEditable(true);
                appState.closeButton.setEnabled(false);
            }

            @Override
            public void onError(Exception ex) {
                appState.messageView.append("Exception occured ...\n$ex\n");
                appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
                ex.printStackTrace();
                appState.connectButton.setEnabled(true);
                appState.uriField.setEditable(true);
                appState.draftComboBox.setEditable(true);
                appState.closeButton.setEnabled(false);
            }
        };
    }

    private static WebSocketServer makeServer(AppState appState){
        int port = Integer.parseInt(appState.uriField.getText().split(":")[2]);
        return new WebSocketServer(new InetSocketAddress(port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                User user = new User();
                user.name = conn.getRemoteSocketAddress().getAddress().toString();
                user.sock = conn;
                appState.usersInServer.add(user);
                ChatMessage cm = new ChatMessage();
                cm.fromUser = "server";
                cm.message = "welcome to the server";
                conn.send(cm.messageify());

                ChatMessage bcm = new ChatMessage();
                bcm.fromUser = "server";

                ArrayList<String> ids = (ArrayList<String>) appState.usersInServer.stream().map((it)->it.name ).collect(Collectors.toList());
                bcm.message = "new userlist is "+ids.toString();
                broadcast(bcm.messageify());
                System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room!");
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                ChatMessage cm = new ChatMessage();
                cm.fromUser = "server";
                cm.message = conn + " has left the room!";
                broadcast(cm.messageify());
                System.out.println(conn+" has left the room!");
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                JsonObject jsonObject = new Gson().fromJson(message,JsonObject.class);
                User auser=null;
                for(User user : appState.usersInServer){
                    if(user.sock==conn){
                        auser = user;
                        break;
                    }
                }
                if(auser==null)return;
                if(jsonObject.has("desiredName")){
                    auser.name = jsonObject.get("desiredName").toString();
                    ArrayList<String> ids = (ArrayList<String>) appState.usersInServer.stream().map((it)->it.name ).collect(Collectors.toList());
                    ChatMessage bcm = new ChatMessage();
                    bcm.fromUser = "server";
                    bcm.message = "new userlist is "+ids.toString();
                    broadcast(bcm.messageify());
                }else if (jsonObject.has("message")){
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.message = auser.name +" says "+jsonObject.getAsJsonPrimitive("message").getAsString().toString();
                    System.out.println(chatMessage.message);
                    System.out.println(chatMessage.messageify());
                    broadcast(chatMessage.messageify());
                }

            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                ex.printStackTrace();
            }

            @Override
            public void onStart() {
                System.out.println("Server started!");
                setConnectionLostTimeout(0);
                setConnectionLostTimeout(100);
            }
        };
    }

    private static ActionListener connectButtonPressed(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketClient = makeClient(appState);
                appState.closeButton.setEnabled(true);
                appState.connectButton.setEnabled(false);
                appState.uriField.setEditable(false);
                appState.draftComboBox.setEditable(false);
                appState.webSocketClient.connect();
            }
        };
    }
    private static ActionListener makeStartServerActionListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketServer = makeServer(appState);
                appState.webSocketServer.start();
            }
        };
    }
    private static ActionListener closeButtonListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketClient.close();
                try{ appState.webSocketServer.stop(); }catch (Exception exStop){}
            }
        };
    }
    private static WindowListener windowCloseListener(AppState appState){
        return new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                try{appState.webSocketServer.stop();}catch (Exception exso){}
                appState.webSocketClient.close();
                appState.jFrame.dispose();
            }
        };
    }
    private static ActionListener chatFieldListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ChatMessage cm = new ChatMessage();
//                cm.fromUser = appState.myName;
                cm.message = appState.chatField.getText();
                appState.webSocketClient.send(cm.messageify());
                appState.chatField.setText("");
                appState.chatField.requestFocus();
            }
        };
    }
    private static ActionListener setNameButtonListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SetNameMessage cm = new SetNameMessage();
                cm.desiredName = "changedit";
                appState.webSocketClient.send(new Gson().toJson(cm));
                appState.chatField.setText("");
                appState.chatField.requestFocus();
            }
        };
    }
    private static void startApp(){
        AppState appState = new AppState();
//        appState.myName = "someone";
        appState.uriField = new JTextField();
        appState.connectButton = new JButton("connect to server");
        appState.setNameButton = new JButton("set name");
        appState.closeButton = new JButton("close connections");
        appState.messageView = new JTextArea();
        appState.chatField= new JTextField();
        appState.jFrame = new JFrame("WebSocket Chat Client");
        JButton startServer = new JButton("Start Server");
        Draft[] drafts = new Draft[]{new Draft_6455()};
        appState.draftComboBox = new JComboBox<Draft>(drafts);
        appState.uriField.setText("ws://localhost:8887");
        appState.closeButton.setEnabled(false);
        JScrollPane scroll = new JScrollPane();
        scroll.setViewportView(appState.messageView);
        appState.chatField.setText("");
        appState.setNameButton.addActionListener(setNameButtonListener(appState));
        appState.connectButton.addActionListener(connectButtonPressed(appState));
        startServer.addActionListener(makeStartServerActionListener(appState));
        appState.closeButton.addActionListener(closeButtonListener(appState));
        appState.chatField.addActionListener(chatFieldListener(appState));
        Dimension d = new Dimension(300, 400);
        appState.jFrame.setPreferredSize(d);
        appState.jFrame.setSize(d);
        appState.jFrame.addWindowListener(windowCloseListener(appState));
        Container c = appState.jFrame.getContentPane();
        GridLayout layout = new GridLayout();
        layout.setColumns(2);
        layout.setHgap(5);
        layout.setRows(7);
        c.setLayout(layout);
        c.add(appState.draftComboBox);
        c.add(appState.uriField);
        c.add(appState.connectButton);
        c.add(startServer);
        c.add(appState.closeButton);
        c.add(scroll);
        c.add(appState.chatField);
        c.add(appState.setNameButton);
        appState.jFrame.setLocationRelativeTo(null);
        appState.jFrame.setVisible(true);
    }

    public static void main(String[] args){
        System.out.println("hihi");
//        ChatClientKt.alloh();
//        ChatClientKt.alloh();
        startApp();
        startApp();
    }
}
