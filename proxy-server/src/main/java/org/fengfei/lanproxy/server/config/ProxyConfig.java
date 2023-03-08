package org.fengfei.lanproxy.server.config;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.Channel;
import org.fengfei.lanproxy.common.Config;
import org.fengfei.lanproxy.common.JsonUtil;
import org.fengfei.lanproxy.protocol.ProxyMessage;
import org.fengfei.lanproxy.server.ProxyChannelManager;
import org.fengfei.lanproxy.server.config.web.PortFounder;
import org.fengfei.lanproxy.server.save.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * server config
 *
 * @author fengfei
 *
 */
public class ProxyConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private static Logger logger = LoggerFactory.getLogger(ProxyConfig.class);

    /** 代理服务器绑定主机host */
    private String serverBind;

    /** 代理服务器与代理客户端通信端口 */
    private Integer serverPort;

    /** 配置服务绑定主机host */
    private String configServerBind;

    /** 配置服务端口 */
    private Integer configServerPort;

    /** 配置服务管理员用户名 */
    private String configAdminUsername;

    /** 配置服务管理员密码 */
    private String configAdminPassword;

    private String serverList;

    public String getServerList() {
        return serverList;
    }

    /** 代理客户端，支持多个客户端 */
    private ConcurrentHashMap<String ,Client> clients = new ConcurrentHashMap<>();

    /** 更新配置后保证在其他线程即时生效 */
    private static ProxyConfig instance = new ProxyConfig();;

    /** 代理服务器为各个代理客户端（key）开启对应的端口列表（value） */
    private volatile Map<String, List<Integer>> clientInetPortMapping = new HashMap<String, List<Integer>>();

    /** 代理服务器上的每个对外端口（key）对应的代理客户端背后的真实服务器信息（value） */
    private volatile Map<Integer, String> inetPortLanInfoMapping = new HashMap<Integer, String>();

    /** 配置变化监听器 */
   private OnUserStartListener onUserStartListener;

   private OnRemoveChannelListener onRemoveChannelListener;

   private static int portStart = 9999;

    private ProxyConfig() {
        this.serverList = Config.getInstance().getStringValue("server.list");
        // 代理服务器主机和端口配置初始化
        this.serverPort = Config.getInstance().getIntValue("server.port");
        this.serverBind = Config.getInstance().getStringValue("server.bind", "0.0.0.0");

        // 配置服务器主机和端口配置初始化
        this.configServerPort = Config.getInstance().getIntValue("config.server.port");
        this.configServerBind = Config.getInstance().getStringValue("config.server.bind", "0.0.0.0");

        // 配置服务器管理员登录认证信息
        this.configAdminUsername = Config.getInstance().getStringValue("config.admin.username");
        this.configAdminPassword = Config.getInstance().getStringValue("config.admin.password");

        logger.info(
                "config init serverBind {}, serverPort {}, configServerBind {}, configServerPort {}, configAdminUsername {}, configAdminPassword {}",
                serverBind, serverPort, configServerBind, configServerPort, configAdminUsername, configAdminPassword);
    }

    public void setOnRemoveChannelListener(OnRemoveChannelListener onRemoveChannelListener) {
        this.onRemoveChannelListener = onRemoveChannelListener;
    }

    public void setOnUserStartListener(OnUserStartListener onUserStartListener) {
        this.onUserStartListener = onUserStartListener;
    }

    public Integer getServerPort() {
        return this.serverPort;
    }

    public String getServerBind() {
        return serverBind;
    }

    public void setServerBind(String serverBind) {
        this.serverBind = serverBind;
    }

    public String getConfigServerBind() {
        return configServerBind;
    }

    public void setConfigServerBind(String configServerBind) {
        this.configServerBind = configServerBind;
    }

    public Integer getConfigServerPort() {
        return configServerPort;
    }

    public void setConfigServerPort(Integer configServerPort) {
        this.configServerPort = configServerPort;
    }

    public String getConfigAdminUsername() {
        return configAdminUsername;
    }

    public void setConfigAdminUsername(String configAdminUsername) {
        this.configAdminUsername = configAdminUsername;
    }

    public String getConfigAdminPassword() {
        return configAdminPassword;
    }

    public void setConfigAdminPassword(String configAdminPassword) {
        this.configAdminPassword = configAdminPassword;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public List<Client> getClients() {
        ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<>(this.clients);
        List<Client> list = new ArrayList<>();
        for (Map.Entry<String, Client> entry : clients.entrySet()) {
            list.add(entry.getValue());
        }
        Collections.sort(list, new Comparator<Client>() {
            @Override
            public int compare(Client o1, Client o2) {
                if (o1.lastActivityTime > o2.lastActivityTime) {
                    return 1;
                } else if (o1.lastActivityTime == o2.lastActivityTime) {
                    return 0;
                } else {
                    return -1;
                }
            }
        });
        return list;
    }



    private Timer timer = new Timer();

    public Client add(String clientSig, String clientName, String lan, String portName, int port) {

        //清除以前的所有通道，重新连接
        onRemoveChannelListener.onRemove(clientSig);
        removeClient(clientSig);

        Client client = new Client();
        clientSig = client.clientKey = (clientSig == null || "".equals(clientSig)) ? UUID.randomUUID().toString() : clientSig;
        client.name = clientName;
        List<ClientProxyMapping> mappings = new ArrayList<>();
        ClientProxyMapping mapping = new ClientProxyMapping();
        mapping.lan = "127.0.0.1:" + lan;
        mapping.name = "portName";
        mappings.add(mapping);
        client.setProxyMappings(mappings);
        //System.out.println("port_start=" + port);
        //服务端端口开始启动监听
        if (port <= 0) {
            port = new PortFounder().findPort();
        }

        boolean isSuccess = false;

        for (int i = 0; i < 10; i++){
            if (!onUserStartListener.onUserStart(port, client.clientKey)) {
                port++;
                if (port > 65530) {
                    port = 13000;
                }
                isSuccess = false;
            } else {
                isSuccess = true;
                break;
            }
        }

        if (!isSuccess) {
            return null;
        }

        mapping.inetPort = port;

        //启动30秒的定时器,并保存定时器
        final String tempSig = clientSig;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                //如果还是离线状态，就直接结束掉
                Channel channel = ProxyChannelManager.getCmdChannel(tempSig);
                if (channel == null) {
                    removeClient(tempSig);
                }
            }
        }, 30 * 1000);

        //添加到队列
        this.clients.put(clientSig, client);
        List<Integer> ports = new ArrayList<>();
        ports.add(port);
        this.clientInetPortMapping.put(client.clientKey, ports);
        this.inetPortLanInfoMapping.put(port, mapping.lan);
        return client;
    }

    public synchronized void removeClient(String clientSig) {
        Client client = clients.get(clientSig);
        clients.remove(clientSig);
        if (client != null &&
                client.getProxyMappings() != null &&
                client.getProxyMappings().size() > 0) {
            ClientProxyMapping mapping = client.getProxyMappings().get(0);
            if (mapping != null) {
                onUserStartListener.onUserStop(mapping.getInetPort(), clientSig);
            }
        }
    }

    public void updateRemark(String clientSig, String remark) {
        Client client = clients.get(clientSig);
        if (client != null) {
            client.setRemark(remark);
        }
        client = RedisUtils.getClient(clientSig);
        if (client != null) {
            client.setRemark(remark);
            RedisUtils.putClient(clientSig, client);
        }
    }

    public void updateWifState(String clientSig, int closeWifi) {
        Client client = clients.get(clientSig);
        if (client != null) {
            client.setIsCloseWifi(closeWifi);
        }
        client = RedisUtils.getClient(clientSig);
        if (client != null) {
            client.setIsCloseWifi(closeWifi);
            RedisUtils.putClient(clientSig, client);

            //发送消息通知
            final Channel channel = ProxyChannelManager.getCmdChannel(client.getClientKey());
            if (channel != null && channel.isActive() && client.getIsCloseWifi() == 1) {
                channel.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (channel != null && channel.isActive()) {
                            Map<String, Object> inputParams = new HashMap<>();
                            inputParams.put("type", "close-wifi");
                            ProxyMessage message = new ProxyMessage();
                            message.setType(ProxyMessage.TYPE_NOTICE);
                            message.setUri(JsonUtil.object2json(inputParams));
                            channel.writeAndFlush(message);
                        }
                    }
                });
            }
        }

    }


    /**
     * 获取代理客户端对应的代理服务器端口
     *
     * @param clientKey
     * @return
     */
    public List<Integer> getClientInetPorts(String clientKey) {
        return clientInetPortMapping.get(clientKey);
    }

    /**
     * 获取所有的clientKey
     *
     * @return
     */
    public Set<String> getClientKeySet() {
        return clientInetPortMapping.keySet();
    }

    /**
     * 根据代理服务器端口获取后端服务器代理信息
     *
     * @param port
     * @return
     */
    public String getLanInfo(Integer port) {
        return inetPortLanInfoMapping.get(port);
    }

    /**
     * 返回需要绑定在代理服务器的端口（用于用户请求）
     *
     * @return
     */
    public List<Integer> getUserPorts() {
        List<Integer> ports = new ArrayList<Integer>();
        Iterator<Integer> ite = inetPortLanInfoMapping.keySet().iterator();
        while (ite.hasNext()) {
            ports.add(ite.next());
        }

        return ports;
    }

    public static ProxyConfig getInstance() {
        return instance;
    }

    /**
     * 代理客户端
     *
     * @author fengfei
     *
     */
    public static class Client implements Serializable {

        private static final long serialVersionUID = 1L;

        private String netType;

        private String clientIp;

        private String lastClientIp;

        private String serverIp;

        private String inputCode;

        private long lastActivityTime;

        private String remark = "";

        private int isCloseWifi;

        /** 客户端备注名称 */
        private String name;

        /** 代理客户端唯一标识key */
        private String clientKey;

        /** 代理客户端与其后面的真实服务器映射关系 */
        private List<ClientProxyMapping> proxyMappings;

        public String getClientIp() {
            return clientIp;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }

        public String getLastClientIp() {
            return lastClientIp;
        }

        public void setLastClientIp(String lastClientIp) {
            this.lastClientIp = lastClientIp;
        }

        public String getServerIp() {
            return serverIp;
        }

        public void setServerIp(String serverIp) {
            this.serverIp = serverIp;
        }

        private int status;

        public String getClientKey() {
            return clientKey;
        }

        public void setClientKey(String clientKey) {
            this.clientKey = clientKey;
        }

        public List<ClientProxyMapping> getProxyMappings() {
            return proxyMappings;
        }

        public void setProxyMappings(List<ClientProxyMapping> proxyMappings) {
            this.proxyMappings = proxyMappings;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public long getLastActivityTime() {
            return lastActivityTime;
        }

        public void setLastActivityTime(long lastActivityTime) {
            this.lastActivityTime = lastActivityTime;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public String getInputCode() {
            return inputCode;
        }

        public void setInputCode(String inputCode) {
            this.inputCode = inputCode;
        }

        public int getIsCloseWifi() {
            return isCloseWifi;
        }

        public void setIsCloseWifi(int isCloseWifi) {
            this.isCloseWifi = isCloseWifi;
        }

        public String getNetType() {
            return netType;
        }

        public void setNetType(String netType) {
            this.netType = netType;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj != null && obj instanceof Client) {
                if (clientKey != null && clientKey.equals(((Client) obj).getClientKey())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 代理客户端与其后面真实服务器映射关系
     *
     * @author fengfei
     *
     */
    public static class ClientProxyMapping {

        /** 代理服务器端口 */
        private Integer inetPort;

        /** 需要代理的网络信息（代理客户端能够访问），格式 192.168.1.99:80 (必须带端口) */
        private String lan;

        /** 备注名称 */
        private String name;

        public Integer getInetPort() {
            return inetPort;
        }

        public void setInetPort(Integer inetPort) {
            this.inetPort = inetPort;
        }

        public String getLan() {
            return lan;
        }

        public void setLan(String lan) {
            this.lan = lan;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

    public interface OnUserStartListener {
        public boolean onUserStart(int port, String clientSig);

        public boolean onUserStop(int port, String clientSig);
    }

    public interface OnRemoveChannelListener{
        void onRemove(String clientSig);
    }
}
