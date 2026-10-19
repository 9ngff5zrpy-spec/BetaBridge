/* SPDX-License-Identifier: GPL-3.0-or-later */
package cl.nostalgia.betabridge;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.Protocol;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;

/** ViaProxy has two UserConnections; only its canonical backend owns ViaLegacy. */
public final class BetaBackendRoute {
    private BetaBackendRoute() { }
    public static UserConnection canonical(UserConnection candidate, Class<? extends Protocol> anchor) {
        if(candidate==null || candidate.getChannel()==null)return null;
        ProxyConnection proxy=ProxyConnection.fromChannel(candidate.getChannel());
        if(proxy==null)return null;
        UserConnection backend=proxy.getUserConnection();
        if(backend==null || backend.getChannel()==null || !backend.getChannel().isActive()
                || !backend.getProtocolInfo().getPipeline().contains(anchor))return null;
        return backend;
    }
}
