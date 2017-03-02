import jersey.repackaged.com.google.common.base.Predicates;
import jersey.repackaged.com.google.common.collect.Maps;
import org.glassfish.jersey.client.*;
import org.glassfish.jersey.internal.util.collection.UnsafeValue;
import org.glassfish.jersey.internal.util.collection.Values;
import org.glassfish.jersey.message.internal.OutboundMessageContext;
import org.glassfish.jersey.message.internal.Statuses;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Client {

    static volatile Object blackhole;

    // run with low memory params to force frequent GC
    public static void main(String[] args) throws IOException, URISyntaxException {
//        pureJersey();
        emulatedJersey();

        // alloc some garbage to have more frequent GC and finalization
        Thread thread = new Thread(() -> {
            while (true) {
                blackhole = new byte[1024 * 1024];
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void pureJersey() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.register(((ClientRequestFilter) requestContext -> requestContext.getHeaders().putSingle("uuid", UUID.randomUUID().toString())));
        // no timeout
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, Integer.MAX_VALUE);
        clientConfig.property(ClientProperties.READ_TIMEOUT, Integer.MAX_VALUE);

        JerseyClient client = (JerseyClient) ClientBuilder.newBuilder()
                .withConfig(clientConfig)
                .build();

        while (true) {
            Response response = client.target("http://localhost:8080").request().buildGet().invoke();
            System.out.println(response.getStatus());
        }
    }

    private static void emulatedJersey() throws IOException {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.register(((ClientRequestFilter) requestContext -> requestContext.getHeaders().putSingle("uuid", UUID.randomUUID().toString())));
        // no timeout
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, Integer.MAX_VALUE);
        clientConfig.property(ClientProperties.READ_TIMEOUT, Integer.MAX_VALUE);

        JerseyClient client = (JerseyClient) ClientBuilder.newBuilder()
                .withConfig(clientConfig)
                .build();

        // emulate what jersey is doing
        while (true) {
            JerseyInvocation invocation = client.target("http://localhost:8080").request().buildGet();
            ClientRequest request = ClientRequestAccessor.request(invocation);
            request.getHeaders().add("uuid", UUID.randomUUID().toString());
            _apply(request);
        }
    }

    // copied from jersey's HttpUrlConnector and removed some unnecessary stuff
    private static ClientResponse _apply(final ClientRequest request) throws IOException {
        final HttpURLConnection uc;

        uc = (HttpURLConnection) request.getUri().toURL().openConnection();
        uc.setDoInput(true);

        uc.setRequestMethod(request.getMethod());

        uc.setInstanceFollowRedirects(request.resolveProperty(ClientProperties.FOLLOW_REDIRECTS, true));

        uc.setConnectTimeout(request.resolveProperty(ClientProperties.CONNECT_TIMEOUT, uc.getConnectTimeout()));

        uc.setReadTimeout(request.resolveProperty(ClientProperties.READ_TIMEOUT, uc.getReadTimeout()));

//        if (uc instanceof HttpsURLConnection) {
//            HttpsURLConnection suc = (HttpsURLConnection) uc;
//
//            final JerseyClient client = request.getClient();
//            final HostnameVerifier verifier = client.getHostnameVerifier();
//            if (verifier != null) {
//                suc.setHostnameVerifier(verifier);
//            }
//            suc.setSSLSocketFactory(client.getSslContext().getSocketFactory());
//        }
//
//        final Object entity = request.getEntity();
//        if (entity != null) {
//            RequestEntityProcessing entityProcessing = request.resolveProperty(
//                    ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.class);
//
//
//            if (entityProcessing == null || entityProcessing != RequestEntityProcessing.BUFFERED) {
//                final int length = request.getLength();
//                if (fixLengthStreaming && length > 0) {
//                    uc.setFixedLengthStreamingMode(length);
//                } else if (entityProcessing == RequestEntityProcessing.CHUNKED) {
//                    uc.setChunkedStreamingMode(chunkSize);
//                }
//            }
//            uc.setDoOutput(true);
//
////            if ("GET".equalsIgnoreCase(request.getMethod())) {
////                final Logger logger = Logger.getLogger(HttpUrlConnector.class.getName());
////                if (logger.isLoggable(Level.INFO)) {
////                    logger.log(Level.INFO, LocalizationMessages.HTTPURLCONNECTION_REPLACES_GET_WITH_ENTITY());
////                }
////            }
//
//            request.setStreamProvider(new OutboundMessageContext.StreamProvider() {
//
//                @Override
//                public OutputStream getOutputStream(int contentLength) throws IOException {
//                    setOutboundHeaders(request.getStringHeaders(), uc);
//                    return uc.getOutputStream();
//                }
//            });
//            request.writeEntity();
//
//        } else {
            setOutboundHeaders(request.getStringHeaders(), uc);
//        }

        final int code = uc.getResponseCode();
        final String reasonPhrase = uc.getResponseMessage();
        final Response.StatusType status =
                reasonPhrase == null ? Statuses.from(code) : Statuses.from(code, reasonPhrase);
        final URI resolvedRequestUri;
        try {
            resolvedRequestUri = uc.getURL().toURI();
        } catch (URISyntaxException e) {
            throw new ProcessingException(e);
        }

        ClientResponse responseContext = new ClientResponse(status, request, resolvedRequestUri);
        responseContext.headers(Maps.filterKeys(uc.getHeaderFields(), Predicates.notNull()));
        responseContext.setEntityStream(getInputStream(uc));

        return responseContext;
    }

    private static void setOutboundHeaders(MultivaluedMap<String, String> headers, HttpURLConnection uc) {
        // removed restricted header sent stuff
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            String headerName = header.getKey();
            String headerValue;

            List<String> headerValues = header.getValue();
            if (headerValues.size() == 1) {
                headerValue = headerValues.get(0);
                uc.setRequestProperty(headerName, headerValue);
            } else {
                StringBuilder b = new StringBuilder();
                boolean add = false;
                for (Object value : headerValues) {
                    if (add) {
                        b.append(',');
                    }
                    add = true;
                    b.append(value);
                }
                headerValue = b.toString();
                uc.setRequestProperty(headerName, headerValue);
            }
        }
    }

    private static InputStream getInputStream(final HttpURLConnection uc) throws IOException {
        return new InputStream() {
            private final UnsafeValue<InputStream, IOException> in = Values.lazy(new UnsafeValue<InputStream, IOException>() {
                @Override
                public InputStream get() throws IOException {
                    if (uc.getResponseCode() < Response.Status.BAD_REQUEST.getStatusCode()) {
                        return uc.getInputStream();
                    } else {
                        InputStream ein = uc.getErrorStream();
                        return (ein != null) ? ein : new ByteArrayInputStream(new byte[0]);
                    }
                }
            });

            @Override
            public int read() throws IOException {
                return in.get().read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                return in.get().read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return in.get().read(b, off, len);
            }

            @Override
            public long skip(long n) throws IOException {
                return in.get().skip(n);
            }

            @Override
            public int available() throws IOException {
                return in.get().available();
            }

            @Override
            public void close() throws IOException {
                in.get().close();
            }

            @Override
            public void mark(int readLimit) {
                try {
                    in.get().mark(readLimit);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to retrieve the underlying input stream.", e);
                }
            }

            @Override
            public void reset() throws IOException {
                in.get().reset();
            }

            @Override
            public boolean markSupported() {
                try {
                    return in.get().markSupported();
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to retrieve the underlying input stream.", e);
                }
            }
        };
    }
}
