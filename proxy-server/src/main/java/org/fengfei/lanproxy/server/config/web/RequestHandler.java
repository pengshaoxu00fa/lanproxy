package org.fengfei.lanproxy.server.config.web;

import io.netty.handler.codec.http.FullHttpRequest;

import java.util.Map;

/**
 * 接口请求处理
 *
 * @author fengfei
 *
 */
public class RequestHandler {

    /**
     * 请求处理
     *
     * @param request
     * @return
     */
    public ResponseInfo request(FullHttpRequest request, Map<String, String> params) {
        return null;
    }
}