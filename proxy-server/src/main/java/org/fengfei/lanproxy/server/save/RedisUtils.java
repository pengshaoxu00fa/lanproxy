package org.fengfei.lanproxy.server.save;

import com.google.gson.reflect.TypeToken;
import org.fengfei.lanproxy.common.JsonUtil;
import org.fengfei.lanproxy.server.config.ProxyConfig;
import redis.clients.jedis.Jedis;

import java.util.*;

public class RedisUtils {
//    private static String HOST = "154.39.248.96";
    private static String HOST = "127.0.0.1";//访问本机redis必须要用127.0.0.1
    public static String prefix_sig = "lan_sig_";

    public static Jedis jedis;

    private static TypeToken<ProxyConfig.Client> typeToken = new TypeToken<ProxyConfig.Client>() {};

    static {
        init();
    }

    private static void init() {
        jedis = new Jedis(HOST, 6379);
        jedis.auth("IP68ELh:xI_2");
    }

    public static void putClient(String sig, ProxyConfig.Client client) {
        if (!jedis.isConnected()) {
            init();
        }
        if (sig == null || sig.trim().length() == 0) {
            return;
        }
        int index = 0;
        while (index < 20) {
            try {
                jedis.set(prefix_sig + sig, JsonUtil.object2json(client));
                break;
            } catch (Exception e){
                e.printStackTrace();
                init();
                index++;
            }
        }

    }

    public static ProxyConfig.Client getClient(String sig) {
        if (!jedis.isConnected()) {
            init();
        }
        if (sig == null || sig.trim().length() == 0) {
            return null;
        }
        int index = 0;
        while (index < 20) {
            try {
                return JsonUtil.json2object(jedis.get(prefix_sig + sig), typeToken);
            } catch (Exception e){
                e.printStackTrace();
                init();
                index ++;
            }
        }
        return null;
    }

    public static List<ProxyConfig.Client> getAllClient() {
        if (!jedis.isConnected()) {
            init();
        }
        List<ProxyConfig.Client> result = new ArrayList<>();
        Set<String> keys = null;
        int index = 0;
        while (index <20) {
            try {
                keys = jedis.keys("lan_sig_*");
                break;
            } catch (Exception e){
                e.printStackTrace();
                index++;
                init();
            }
        }
        if (keys == null) {
            keys = new HashSet<>();
        }

        Iterator<String> it=keys.iterator() ;
        while(it.hasNext()){
            String key = it.next();
            String json = jedis.get(key);
            ProxyConfig.Client client = JsonUtil.json2object(json, typeToken);
            if (client != null) {
                result.add(client);
            }
        }
        return result;
    }
}
