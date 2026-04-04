package Projects.Network.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.url:http://localhost:8080}")
    private String serverUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API OCR Documents Camerounais")
                        .version("2.0")
                        .description("API d'analyse OCR pour pièces d'identité camerounaises")
                        .contact(new Contact()
                                .name("Thomas Djotio Ndié")
                                .email("tdjotio@gmail.com")
                                .url("https://github.com/"))
    )
                .servers(List.of(
                        new Server().url(serverUrl).description("Serveur principal"),
                        new Server().url("http://localhost:8080").description("Développement local")
                ));
    }
}