package org.glassfish.jersey.client;

public class ClientRequestAccessor {

    public static ClientRequest request(JerseyInvocation invocation) {
        return invocation.request();
    }
}
