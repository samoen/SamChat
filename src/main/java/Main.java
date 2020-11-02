import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
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
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;

class User {
    WebSocket sock;
    String name;
}

class AppState {
    ArrayList<User> usersInServer = new ArrayList<User>();
    WebSocketServer webSocketServer;
    WebSocketClient webSocketClient;
    Samform1 samform1;
    JFrame jFrame;
    DefaultListModel<String> listModel;
}

class ChatMessage {
    String message;
}

class SetNameMessage {
    String desiredName;
}

class UserListMessage {
    ArrayList<String> userList;
    String myName;
}

class ElasticEntry {
    @SerializedName("@timestamp")
    String timestamp;

    String message;
    ElasticUser user;
}

class ElasticUser {
    String id;
}

public class Main {
    private static WebSocketClient makeClient(AppState appState) {
        Draft draft = new Draft_6455();
        URI uri = null;
        try {
            uri = new URI(appState.samform1.uriField.getText());
        } catch (Exception uriex) {
            System.out.println(uriex.toString());
        }
        if (uri == null) return null;
        return new WebSocketClient(uri, draft) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                appState.samform1.textArea1.append("You are connected to ChatServer: " + getURI() + "\n");
                appState.samform1.textArea1.setCaretPosition(appState.samform1.textArea1.getDocument().getLength());
                setUINotConnected(appState, true);
            }

            @Override
            public void onMessage(String message) {
                JsonObject jsonObject = new Gson().fromJson(message, JsonObject.class);
                if (jsonObject.has("message")) {
                    ChatMessage cm = new Gson().fromJson(message, ChatMessage.class);
                    appState.samform1.textArea1.append(cm.message);
                    appState.samform1.textArea1.append("\n");
                    appState.samform1.textArea1.setCaretPosition(appState.samform1.textArea1.getDocument().getLength());
                } else if (jsonObject.has("userList")) {
//                    appState.listModel.clear();
                    appState.samform1.userscrollpanel.removeAll();
                    appState.samform1.userscrollpanel.revalidate();
                    appState.samform1.userscrollpanel.repaint();
                    UserListMessage userListMessage = new Gson().fromJson(message, UserListMessage.class);
                    for (String usr : userListMessage.userList) {
                        UserListItem uli = new UserListItem();
                        uli.textArea1.setText(usr);
//                        appState.listModel.addElement(usr);
                        appState.samform1.userscrollpanel.add(uli.panel1);
                    }
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                appState.samform1.textArea1.append("websocketclient onClose... " + getURI() + "; Code: " + code + " " + reason + "\n");
            }

            @Override
            public void onError(Exception ex) {
                appState.samform1.textArea1.append("websocketclient onError...\n$ex\n");
                appState.samform1.textArea1.setCaretPosition(appState.samform1.textArea1.getDocument().getLength());
                ex.printStackTrace();
                setUINotConnected(appState, false);
            }
        };
    }

    private static void setUINotConnected(AppState appState, boolean connected) {
        if (!connected) {
            appState.listModel.clear();
            appState.samform1.userscrollpanel.removeAll();
            appState.samform1.userscrollpanel.revalidate();
            appState.samform1.userscrollpanel.repaint();
        }
        appState.samform1.connectToServerButton.setEnabled(!connected);
        appState.samform1.uriField.setEditable(!connected);
        appState.samform1.closeConnectionsButton.setEnabled(connected);
        appState.samform1.chatField.setEnabled(connected);
        appState.samform1.setNameButton.setEnabled(connected);
    }

    private static void sendUserList(AppState appState) {
        UserListMessage bcm = new UserListMessage();
        ArrayList<String> ids = new ArrayList<String>();
        for (User usr : appState.usersInServer) {
            ids.add(usr.name);
        }
        bcm.userList = ids;
        for (User user : appState.usersInServer) {
            bcm.myName = user.name;
            if (user.sock.isOpen())
                user.sock.send(new Gson().toJson(bcm));
        }
    }

    private static WebSocketServer makeServer(AppState appState) {
        int port = Integer.parseInt(appState.samform1.uriField.getText().split(":")[2]);
        return new WebSocketServer(new InetSocketAddress(port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                User user = new User();
                user.name = "Unnamed User";
                user.sock = conn;
                appState.usersInServer.add(user);
                ChatMessage cm = new ChatMessage();
                cm.message = "welcome to the server";
                conn.send(new Gson().toJson(cm));
                sendUserList(appState);
                System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room!");
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                for (User user : appState.usersInServer) {
                    if (conn == user.sock) {
                        appState.usersInServer.remove(user);
                        break;
                    }
                }
                sendUserList(appState);
                System.out.println(conn + " disconnected");
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                System.out.println("server recieved: " + message);
                JsonObject jsonObject = new Gson().fromJson(message, JsonObject.class);
                User auser = null;
                for (User user : appState.usersInServer) {
                    if (user.sock == conn) {
                        auser = user;
                        break;
                    }
                }
                if (auser == null) return;
                if (jsonObject.has("desiredName")) {
                    SetNameMessage setNameMessage = new Gson().fromJson(message, SetNameMessage.class);
                    auser.name = setNameMessage.desiredName;
                    addDocumentToElastic(setNameMessage.desiredName);
                    sendUserList(appState);
                } else if (jsonObject.has("message")) {
                    ChatMessage chatMessage = new Gson().fromJson(message, ChatMessage.class);
                    chatMessage.message = auser.name + " says " + chatMessage.message;
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

    private static ActionListener connectButtonPressed(AppState appState) {
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketClient = makeClient(appState);
                appState.samform1.closeConnectionsButton.setEnabled(true);
                appState.samform1.connectToServerButton.setEnabled(false);
                appState.samform1.uriField.setEditable(false);
                appState.webSocketClient.connect();
            }
        };
    }

    private static ActionListener makeStartServerActionListener(AppState appState) {
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketServer = makeServer(appState);
                appState.webSocketServer.start();
            }
        };
    }

    private static ActionListener closeButtonListener(AppState appState) {
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketClient.close();
                try {
                    appState.webSocketServer.stop();
                } catch (Exception exStop) {
                    exStop.printStackTrace();
                }
                appState.samform1.textArea1.setCaretPosition(appState.samform1.textArea1.getDocument().getLength());
                setUINotConnected(appState, false);
            }
        };
    }

    private static WindowListener windowCloseListener(AppState appState) {
        return new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                try {
                    if (appState.webSocketServer != null) {
                        appState.webSocketServer.stop();
                    }
                } catch (Exception exso) {
                    exso.printStackTrace();
                }
                if (appState.webSocketClient != null) {
                    appState.webSocketClient.close();
                }

                appState.jFrame.dispose();
            }
        };
    }

    private static ActionListener chatFieldListener(AppState appState) {
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ChatMessage cm = new ChatMessage();
                cm.message = appState.samform1.chatField.getText();
                appState.webSocketClient.send(new Gson().toJson(cm));
                appState.samform1.chatField.setText("");
                appState.samform1.chatField.requestFocus();
            }
        };
    }

    private static ActionListener setNameButtonListener(AppState appState) {
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SetNameMessage cm = new SetNameMessage();
                cm.desiredName = appState.samform1.chatField.getText();
                appState.samform1.chatField.setText("");
                appState.webSocketClient.send(new Gson().toJson(cm));
                appState.samform1.chatField.setText("");
                appState.samform1.chatField.requestFocus();
            }
        };
    }

    private static void startApp() {
        AppState appState = new AppState();
        appState.samform1 = new Samform1();
        appState.jFrame = new JFrame("WebSocket Chat Client");
        appState.jFrame.setContentPane(appState.samform1.panel1);
        appState.samform1.chatScrollPane.setViewportView(appState.samform1.textArea1);

        appState.samform1.scrollpane.setViewportView(appState.samform1.userscrollpanel);
        appState.samform1.userscrollpanel.setPreferredSize(new Dimension(100, 500));

        appState.listModel = new DefaultListModel<String>();
        appState.samform1.list1.setModel(appState.listModel);
        appState.samform1.uriField.setText("ws://localhost:8887");
        appState.samform1.closeConnectionsButton.setEnabled(false);
        appState.samform1.chatField.setText("");

        appState.samform1.setNameButton.addActionListener(setNameButtonListener(appState));
        appState.samform1.connectToServerButton.addActionListener(connectButtonPressed(appState));
        appState.samform1.startServerButton.addActionListener(makeStartServerActionListener(appState));
        appState.samform1.closeConnectionsButton.addActionListener(closeButtonListener(appState));
        appState.samform1.chatField.addActionListener(chatFieldListener(appState));
        appState.samform1.usersearch.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
//                    URL url = new URL("http://localhost:9200/my-index-000001/_search?q=user.id:kimchy");
                    URL url = new URL("http://localhost:9200/my-index-000001/_search?pretty=true");

                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");
                    con.setRequestProperty("Content-Type", "application/json; utf-8");
                    con.setRequestProperty("Accept", "application/json");
                    con.setDoOutput(true);
                    String jsonInputString = "{\"query\" : { \"match_all\" : {} } }";
                    try (OutputStream os = con.getOutputStream()) {
                        byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();
                    con.disconnect();
                    JsonObject jsob = new Gson().fromJson(content.toString(), JsonObject.class);
//                    System.out.println(content.toString());
                    JsonArray innerHits = jsob.get("hits").getAsJsonObject().get("hits").getAsJsonArray();
                    appState.listModel.clear();
                    for (JsonElement hit : innerHits) {
                        String source = hit.getAsJsonObject().get("_source").toString();
                        System.out.println(source);
                        ElasticEntry ee = new Gson().fromJson(source, ElasticEntry.class);
                        appState.listModel.addElement(ee.user.id);
                    }

                } catch (Exception exception) {
                    System.out.println("failed search req " + exception);
                }
            }
        });

        setUINotConnected(appState, false);

        Dimension d = new Dimension(700, 800);
        appState.jFrame.setPreferredSize(d);
        appState.jFrame.setSize(d);
        appState.jFrame.addWindowListener(windowCloseListener(appState));
        appState.jFrame.setVisible(true);

    }

    public static void addDocumentToElastic(String userid) {
        try {
//                    URL url = new URL("http://localhost:9200/my-index-000001/_search?q=user.id:kimchy");
            URL url = new URL("http://localhost:9200/my-index-000001/_doc?pretty=true");

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);

            ElasticEntry ee = new ElasticEntry();
            ee.message = "mess";

            Instant instant = Instant.now();

            ee.timestamp = instant.truncatedTo(ChronoUnit.SECONDS).toString();
            ee.user = new ElasticUser();
            ee.user.id = userid;
            String jsonInputString = new Gson().toJson(ee);
            System.out.println(jsonInputString);
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            con.disconnect();
            System.out.println(content.toString());

        } catch (Exception exception) {
            System.out.println("failed to add user to elastic " + exception);
        }
    }

    public static void main(String[] args) {
        startApp();
//        startApp();
    }

}
