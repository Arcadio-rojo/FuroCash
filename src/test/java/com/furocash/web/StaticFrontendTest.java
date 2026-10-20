package com.furocash.web;

import com.furocash.Main;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

class StaticFrontendTest {
    static Javalin app;
    @BeforeAll static void start() {
        Assumptions.assumeTrue(Files.exists(Path.of("frontend/dist/index.html")),"Build the frontend first to test static hosting");
        app=Main.createApp((mobile,message)->{}).start(0);
    }
    @AfterAll static void stop(){if(app!=null)app.stop();}
    @Test void builtFrontendAndItsAssetsAreServed() throws Exception {
        var client=HttpClient.newHttpClient();
        var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+app.port()+"/")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());assertTrue(response.body().contains("FuroCash"));
        var matcher=java.util.regex.Pattern.compile("(?:src|href)=\"(/assets/[^\"]+)\"").matcher(response.body());
        int count=0;
        while(matcher.find()){
            var asset=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+app.port()+matcher.group(1))).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,asset.statusCode());assertTrue(asset.body().length>0);count++;
        }
        assertTrue(count>=2);
    }
    @Test void rootEnvironmentFileIsNotPublic() throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+app.port()+"/.env")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(404,response.statusCode());
    }
}
