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
}

class ChatMessage{
    String message;
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
                cm.message = "welcome to the server";
                conn.send(new Gson().toJson(cm));

                ChatMessage bcm = new ChatMessage();

                ArrayList<String> ids = (ArrayList<String>) appState.usersInServer.stream().map((it)->it.name ).collect(Collectors.toList());
                bcm.message = "new userlist is "+ids.toString();
                broadcast(new Gson().toJson(bcm));
                System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room!");
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                ChatMessage cm = new ChatMessage();
                cm.message = conn + " has left the room!";
                broadcast(new Gson().toJson(cm));
                System.out.println(conn+" has left the room!");
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                System.out.println("server recieved: "+message);
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
                    SetNameMessage setNameMessage = new Gson().fromJson(message,SetNameMessage.class);
                    auser.name = setNameMessage.desiredName;
                    ArrayList<String> ids = (ArrayList<String>) appState.usersInServer.stream().map((it)->it.name ).collect(Collectors.toList());
                    ChatMessage bcm = new ChatMessage();
                    bcm.message = "new userlist is "+ids.toString();
                    broadcast(new Gson().toJson(bcm));
                }else if (jsonObject.has("message")){
                    ChatMessage chatMessage = new Gson().fromJson(message,ChatMessage.class);
                    chatMessage.message = auser.name +" says "+chatMessage.message;
                    broadcast(new Gson().toJson(chatMessage));
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
                cm.message = appState.chatField.getText();
                appState.webSocketClient.send(new Gson().toJson(cm));
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
                cm.desiredName = appState.chatField.getText();
                appState.chatField.setText("");
                appState.webSocketClient.send(new Gson().toJson(cm));
                appState.chatField.setText("");
                appState.chatField.requestFocus();
            }
        };
    }
    private static void startApp(){
        AppState appState = new AppState();
        Samform1 sf = new Samform1();

        appState.jFrame = new JFrame("WebSocket Chat Client");
        appState.jFrame.setContentPane(sf.panel1);
        appState.uriField = sf.uriField;
        appState.connectButton = sf.connectToServerButton;
        appState.setNameButton = sf.setNameButton;
        appState.closeButton = sf.closeConnectionsButton;
        appState.messageView = sf.textArea1;
        appState.chatField= sf.chatField;
        JScrollPane scroll = sf.chatScrollPane;
        scroll.setViewportView(appState.messageView);
        JButton startServer = sf.startServerButton;

        appState.draftComboBox = sf.comboBox1;
        appState.draftComboBox.addItem(new Draft_6455());
        appState.uriField.setText("ws://localhost:8887");
        appState.closeButton.setEnabled(false);
        appState.chatField.setText("");

        appState.setNameButton.addActionListener(setNameButtonListener(appState));
        appState.connectButton.addActionListener(connectButtonPressed(appState));
        startServer.addActionListener(makeStartServerActionListener(appState));
        appState.closeButton.addActionListener(closeButtonListener(appState));
        appState.chatField.addActionListener(chatFieldListener(appState));

        Dimension d = new Dimension(700, 800);
        appState.jFrame.setPreferredSize(d);
        appState.jFrame.setSize(d);
        appState.jFrame.addWindowListener(windowCloseListener(appState));
        appState.jFrame.setVisible(true);
    }

    public static void main(String[] args){
        System.out.println("hihi");
        startApp();
        startApp();
    }
}
