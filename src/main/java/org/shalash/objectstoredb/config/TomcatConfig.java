package org.shalash.objectstoredb.config;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers((Connector connector) -> {
            connector.setProperty("connectionTimeout", "600000");
            connector.setProperty("keepAliveTimeout", "600000");
            connector.setProperty("connectionUploadTimeout", "600000");
            connector.setProperty("disableUploadTimeout", "false");
            connector.setProperty("maxSwallowSize", "-1");
            connector.setMaxPostSize(2 * 1024 * 1024 * 1024);        // 2GB
        });
    }

    @Bean
    public Filter logFilter() {
        return (req, res, chain) -> {
            System.out.println(" FILTER HIT: " + ((HttpServletRequest)req).getMethod()
                    + " " + ((HttpServletRequest)req).getRequestURI()
                    //print parameters
                    + " " + ((HttpServletRequest)req).getParameterMap()
            );
            chain.doFilter(req, res);
        };
    }
}