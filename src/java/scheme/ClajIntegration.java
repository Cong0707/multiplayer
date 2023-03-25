package scheme;

import arc.Events;
import arc.func.Cons;
import arc.net.Client;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import arc.struct.Seq;
import arc.util.Reflect;
import arc.util.Threads;
import mindustry.game.EventType.*;
import mindustry.gen.Call;
import mindustry.io.TypeIO;
import mindustry.net.ArcNetProvider.*;

import static mindustry.Vars.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.github.bsideup.jabel.Desugar;

/** https://github.com/xzxADIxzx/Scheme-Size/blob/main/src/java/scheme/ClajIntegration.java */
public class ClajIntegration {

    public static Seq<Client> clients = new Seq<>();
    public static NetListener serverListener;

    public static void load() {
        Events.run(HostEvent.class, ClajIntegration::clear);
        Events.run(ClientPreConnectEvent.class, ClajIntegration::clear);

        var provider = Reflect.get(net, "provider");
        if (steam) provider = Reflect.get(provider, "provider"); // thanks

        var server = Reflect.get(provider, "server");
        serverListener = Reflect.get(server, "dispatchListener");
    }

    // region room management

    public static Client createRoom(String ip, int port, Cons<String> link, Runnable disconnected) throws IOException {
        Client client = new Client(8192, 8192, new Serializer());
        Threads.daemon("CLaJ Room", client::run);

        client.addListener(new NetListener() {

            /** Used when creating redirectors. */
            public String key;

            @Override
            public void connected(Connection connection) {
                client.sendTCP("new");
            }

            @Override
            public void disconnected(Connection connection, DcReason reason) {
                disconnected.run();
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof String message) {
                    if (message.startsWith("CLaJ")) {
                        this.key = message;
                        link.get(key + "#" + ip + ":" + port);
                    } else if (message.equals("new")) {
                        try {
                            createRedirector(ip, port, key);
                        } catch (Exception ignored) {}
                    } else
                        Call.sendMessage(message);
                }
            }
        });

        client.connect(5000, ip, port, port);
        clients.add(client);

        return client;
    }

    public static void createRedirector(String ip, int port, String key) throws IOException {
        Client client = new Client(8192, 8192, new Serializer());
        Threads.daemon("CLaJ Redirector", client::run);

        client.addListener(serverListener);
        client.addListener(new NetListener() {
            @Override
            public void connected(Connection connection) {
                client.sendTCP("host" + key);
            }
        });

        client.connect(5000, ip, port, port);
        clients.add(client);
    }

    public static void joinRoom(String ip, int port, String key, Runnable success) {
        logic.reset();
        net.reset();

        netClient.beginConnecting();
        net.connect(ip, port, () -> {
            if (!net.client()) return;
            success.run();

            ByteBuffer buffer = ByteBuffer.allocate(8192);
            buffer.put(Serializer.linkID);
            TypeIO.writeString(buffer, "join" + key);

            buffer.limit(buffer.position()).position(0);
            net.send(buffer, true);
        });
    }

    public static void clear() {
        clients.each(Client::close);
        clients.clear();
    }

    // endregion

    public static Link parseLink(String link) throws IOException {
        link = link.trim();
        if (!link.startsWith("CLaJ")) throw new IOException("@join.missing-prefix");

        int hash = link.indexOf('#');
        if (hash != 42 + 4) throw new IOException("@join.wrong-key-length");

        int semicolon = link.indexOf(':');
        if (semicolon == -1) throw new IOException("@join.semicolon-not-found");

        int port;
        try {
            port = Integer.parseInt(link.substring(semicolon + 1));
        } catch (Throwable ignored) {
            throw new IOException("@join.failed-to-parse");
        }

        return new Link(link.substring(0, hash), link.substring(hash + 1, semicolon), port);
    }

    @Desugar
    public record Link(String key, String ip, int port) {}

    public static class Serializer extends PacketSerializer {

        public static final byte linkID = -3;

        @Override
        public void write(ByteBuffer buffer, Object object) {
            if (object instanceof String link) {
                buffer.put(linkID);
                TypeIO.writeString(buffer, link);
            } else
                super.write(buffer, object);
        }

        @Override
        public Object read(ByteBuffer buffer) {
            if (buffer.get() == linkID) return TypeIO.readString(buffer);

            buffer.position(buffer.position() - 1);
            return super.read(buffer);
        }
    }
}
