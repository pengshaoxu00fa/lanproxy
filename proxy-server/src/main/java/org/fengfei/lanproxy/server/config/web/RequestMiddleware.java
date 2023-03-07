package org.fengfei.lanproxy.server.config.web;

import io.netty.handler.codec.http.FullHttpRequest;

import java.util.Map;

/**
 * 请求拦截器
 *
 * @author fengfei
 *
 */
public interface RequestMiddleware {

    /**
     * 请求预处理
     *
     * @param request
     */
    void preRequest(FullHttpRequest request, Map<String, String> params);
}
