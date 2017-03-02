import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@EnableAutoConfiguration
public class Server {

    private static final Map<String, Boolean> currentRequests = new ConcurrentHashMap<>();

    @RequestMapping("/")
    @ResponseBody
    String home() {
        return "Hello World!";
    }

    @Bean
    Filter slow() {
        return new Filter() {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {

            }

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                String uuid = ((HttpServletRequest) request).getHeader("uuid");
                System.out.println(uuid);
                // with only one client thread doing synchronous requests, we should only ever have one entry in this map
                Boolean put = currentRequests.put(uuid, true);
                if (put != null) {
                    System.out.print("duplicated request !");
                }
                try {
                    chain.doFilter(request, response);
                } finally {
                    currentRequests.remove(uuid);
                }
            }

            @Override
            public void destroy() {

            }
        };
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Server.class, args);
    }
}
