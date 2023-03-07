package org.fengfei.lanproxy.server.config.web.routes;

public class Session {
    public Session(String token) {
        this.token = token;
        lastActivityTime = System.currentTimeMillis();
    }
    private String token;

    private long lastActivityTime;

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public String getToken() {
        return token;
    }

    public void setLastActivityTime(long lastActivityTime) {
        this.lastActivityTime = lastActivityTime;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null &&
                obj instanceof Session &&
                ((Session) obj).token != null &&
                ((Session) obj).token.equals(token);
    }
}

