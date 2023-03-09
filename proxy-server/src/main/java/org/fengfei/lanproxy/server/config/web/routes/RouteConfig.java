package org.fengfei.lanproxy.server.config.web.routes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.fengfei.lanproxy.common.JsonUtil;
import org.fengfei.lanproxy.server.ProxyChannelManager;
import org.fengfei.lanproxy.server.config.ProxyConfig;
import org.fengfei.lanproxy.server.config.ProxyConfig.Client;
import org.fengfei.lanproxy.server.config.web.*;
import org.fengfei.lanproxy.server.config.web.exception.ContextException;
import org.fengfei.lanproxy.server.metrics.MetricsCollector;
import org.fengfei.lanproxy.server.save.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * 接口实现
 *
 * @author fengfei
 *
 */
public class RouteConfig {

    protected static final String   AUTH_COOKIE_KEY = "token";

    private static Logger logger = LoggerFactory.getLogger(RouteConfig.class);

    /** 管理员不能同时在多个地方登录 */
    //private static List<String> tokens = new ArrayList<>();
    private static ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public static Session getSession(String token) {
        return sessions.get(token);
    }

    private static boolean isSessionTimeOut(Session session) {
        return System.currentTimeMillis() - session.getLastActivityTime() > 24 * 60 * 60 * 1000L;
    }

    private static void clearOutTimeSession() {
        try {
            for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                if (entry.getValue() == null || isSessionTimeOut(entry.getValue())) {
                    sessions.remove(entry.getKey());
                    return;
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    public static void init() {

        ApiRoute.addMiddleware(new RequestMiddleware() {

            @Override
            public void preRequest(FullHttpRequest request, Map<String, String> params) {
                clearOutTimeSession();

                String cookieHeader = request.headers().get(HttpHeaders.Names.COOKIE);
                boolean authenticated = false;
                if (cookieHeader != null) {
                    String[] cookies = cookieHeader.split(";");
                    for (String cookie : cookies) {
                        String[] cookieArr = cookie.split("=");
                        if (AUTH_COOKIE_KEY.equals(cookieArr[0].trim())) {
                            if (cookieArr.length == 2 && cookieArr[1] != null && cookieArr[1].length() > 0) {
                                Session session = sessions.get(cookieArr[1]);
                                if (session != null && !isSessionTimeOut(session)) {
                                    session.setLastActivityTime(System.currentTimeMillis());
                                    params.put(AUTH_COOKIE_KEY, cookieArr[1]);
                                    authenticated = true;
                                }
                            }
                        }
                    }
                }

//                String auth = request.headers().get(HttpHeaders.Names.AUTHORIZATION);
//                if (!authenticated && auth != null) {
//                    String[] authArr = auth.split(" ");
//                    if (authArr.length == 2 && authArr[0].equals(ProxyConfig.getInstance().getConfigAdminUsername()) && authArr[1].equals(ProxyConfig.getInstance().getConfigAdminPassword())) {
//                        authenticated = true;
//                    }
//                }

                if (!request.getUri().equals("/login") &&
                        !request.getUri().equals("/api/create/peer") &&
                        !request.getUri().equals("/api/list/server") &&
                        !authenticated) {
                    throw new ContextException(ResponseInfo.CODE_UNAUTHORIZED);
                }

                logger.info("handle request for api {}", request.getUri());
            }
        });

        // 获取配置详细信息
        ApiRoute.addRoute("/config/detail/on", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
                List<Client> clients = ProxyConfig.getInstance().getClients();
                for (Client client : clients) {
                    Channel channel = ProxyChannelManager.getCmdChannel(client.getClientKey());
                    if (channel != null) {
                        client.setStatus(1);// online
                    } else {
                        client.setStatus(0);// offline
                    }
                }
                ResponseInfo info = ResponseInfo.build(ProxyConfig.getInstance().getClients());
                return info;
            }
        });

        // 获取配置详细信息
        ApiRoute.addRoute("/search/on", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String inputParams = new String(buf);
                Map<String, String> paramsMaps = JsonUtil.json2object(inputParams, new TypeToken<Map<String, String>>() {
                });

                String key = paramsMaps.get("key");
                List<Client> list = new ArrayList<>();

                List<Client> clients = ProxyConfig.getInstance().getClients();
                for (Client client : clients) {
                    if(client == null) {
                        continue;
                    }
                    Channel channel = ProxyChannelManager.getCmdChannel(client.getClientKey());
                    if (channel != null) {
                        client.setStatus(1);// online
                    } else {
                        client.setStatus(0);// offline
                    }
                    if (client.getClientIp() == null) {
                        client.setClientIp("");
                    }
                    if (client.getInputCode() == null) {
                        client.setInputCode("");
                    }
                    if (client.getLastClientIp() == null) {
                        client.setLastClientIp("");
                    }
                    if (client.getName().contains(key) ||
                            client.getClientIp().contains(key) ||
                            client.getInputCode().contains(key) ||
                            client.getLastClientIp().contains(key)) {
                        list.add(client);
                    }
                }
                ResponseInfo info = ResponseInfo.build(list);
                return info;
            }
        });


        // 获取配置详细信息
        ApiRoute.addRoute("/config/detail/off", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
                List<Client> clients = ProxyConfig.getInstance().getClients();
                List<Client> allClients = RedisUtils.getAllClient();
                for (Client client : clients) {
                    if (allClients.contains(client)) {
                        allClients.remove(client);
                    }
                }
                for (Client client : allClients) {
                    client.setStatus(0);// offline
                }
                ResponseInfo info = ResponseInfo.build(allClients);
                return info;
            }
        });

        // 获取配置详细信息
        ApiRoute.addRoute("/search/off", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {

                List<Client> list = new ArrayList<>();

                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String inputParams = new String(buf);
                Map<String, String> paramsMaps = JsonUtil.json2object(inputParams, new TypeToken<Map<String, String>>() {
                });

                String key = paramsMaps.get("key");

                List<Client> clients = ProxyConfig.getInstance().getClients();
                List<Client> allClients = RedisUtils.getAllClient();
                for (Client client : clients) {
                    if (allClients.contains(client)) {
                        allClients.remove(client);
                    }
                }
                for (Client client : allClients) {
                    client.setStatus(0);// offline
                    if (client.getClientIp() == null) {
                        client.setClientIp("");
                    }
                    if (client.getInputCode() == null) {
                        client.setInputCode("");
                    }
                    if (client.getName().contains(key) ||
                            client.getClientIp().contains(key) ||
                            client.getInputCode().contains(key)) {
                        list.add(client);
                    }
                }
                ResponseInfo info = ResponseInfo.build(list);
                return info;
            }
        });


        // 获取配置详细信息
        ApiRoute.addRoute("/config/detail/remark", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {

                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String inputParams = new String(buf);
                Map<String, String> paramsMaps = JsonUtil.json2object(inputParams, new TypeToken<Map<String, String>>() {
                });
                String sig = paramsMaps.get("sig");
                String remark = paramsMaps.get("remark");
                if (remark == null || remark.trim().length() == 0) {
                    try {
                        int closeWifi = Integer.parseInt(paramsMaps.get("closeWifi"));
                        ProxyConfig.getInstance().updateWifState(sig, closeWifi);
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                } else {
                    ProxyConfig.getInstance().updateRemark(sig, remark);
                }

                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        });

        // 更新配置
        ApiRoute.addRoute("/config/update", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
//                byte[] buf = new byte[request.content().readableBytes()];
//                request.content().readBytes(buf);
//                String config = new String(buf, Charset.forName("UTF-8"));
//                List<Client> clients = JsonUtil.json2object(config, new TypeToken<List<Client>>() {
//                });
//                if (clients == null) {
//                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error json config");
//                }
//
//                try {
//                    ProxyConfig.getInstance().update(config);
//                } catch (Exception ex) {
//                    logger.error("config update error", ex);
//                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, ex.getMessage());
//                }

                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        });

        ApiRoute.addRoute("/login", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf);
                Map<String, String> loginParams = JsonUtil.json2object(config, new TypeToken<Map<String, String>>() {
                });
                if (loginParams == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error login info");
                }

                String username = loginParams.get("username");
                String password = loginParams.get("password");
                if (username == null || password == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error username or password");
                }

                if (username.equals(ProxyConfig.getInstance().getConfigAdminUsername()) && password.equals(ProxyConfig.getInstance().getConfigAdminPassword())) {
                    String token = UUID.randomUUID().toString().replace("-", "");
                    sessions.put(token, new Session(token));
                    return ResponseInfo.build(token);
                }

                return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error username or password");
            }
        });

        ApiRoute.addRoute("/logout", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
                sessions.remove(params.get(AUTH_COOKIE_KEY));
                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        });

        ApiRoute.addRoute("/metrics/get", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
                return ResponseInfo.build(MetricsCollector.getAllMetrics());
            }
        });

        ApiRoute.addRoute("/metrics/getandreset", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
                return ResponseInfo.build(MetricsCollector.getAndResetAllMetrics());
            }
        });

        // 获取配置详细信息
        ApiRoute.addRoute("/api/create/peer", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf);
                Map<String, String> info = JsonUtil.json2object(config, new TypeToken<Map<String, String>>() {
                });
                Client client = ProxyConfig.getInstance().add(
                        info.get("sig"),
                        info.get("client_name"),
                        info.get("lan"),
                        info.get("port_name"),
                        Integer.parseInt(info.get("port")));
                if (client != null) {
                    client.setServerIp(params.get("server_ip"));
                    client.setClientIp(params.get("client_ip"));
                    client.setLastActivityTime(System.currentTimeMillis());
                    if (info.get("input_code")!= null && info.get("input_code").trim().length() > 0) {
                        client.setInputCode(info.get("input_code"));
                    }
                    if (info.get("net_type")!= null && info.get("net_type").trim().length() > 0) {
                        client.setNetType(info.get("net_type"));
                    }
                    Client saveClient = RedisUtils.getClient(client.getClientKey());
                    if (saveClient != null) {
                        if (saveClient.getInputCode() != null && saveClient.getInputCode().trim().length() > 0) {
                            client.setInputCode(saveClient.getInputCode());
                        }
                        //&& !saveClient.getClientIp().equals(client.getClientIp())
                        if (saveClient.getClientIp() != null && saveClient.getClientIp().length() != 0 && !saveClient.getClientIp().equals(client.getClientIp())) {
                            client.setLastClientIp(saveClient.getClientIp());
                        }

                        client.setRemark(saveClient.getRemark());
                    }

                    RedisUtils.putClient(client.getClientKey(), client);
                    return ResponseInfo.build(client);
                } else {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "error");
                }

            }
        });

        // 获取配置详细信息
        ApiRoute.addRoute("/api/list/server", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
                return ResponseInfo.build(ProxyConfig.getInstance().getServerList());
            }
        });

    }

}
