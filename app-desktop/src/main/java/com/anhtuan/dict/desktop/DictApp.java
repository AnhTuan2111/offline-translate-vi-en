package com.anhtuan.dict.desktop;

/**
 * TODO(M5) - Diem vao cua app desktop.
 *
 * Bootstrap Spring Boot NON-WEB roi mo cua so JavaFX (PLAN.md AD-8):
 * <pre>
 * public class DictApp extends Application {
 *     private ConfigurableApplicationContext ctx;
 *
 *     public void init() {
 *         ctx = new SpringApplicationBuilder(DesktopConfig.class)
 *                 .web(WebApplicationType.NONE)      // khong Tomcat, khong servlet
 *                 .run();
 *     }
 *     public void start(Stage stage) { ctx.getBean(MainView.class).show(stage); }
 *     public void stop() { ctx.close(); Platform.exit(); }
 * }
 * </pre>
 *
 * CANH BAO (PLAN.md AD-7): render ket qua bang TextFlow, TUYET DOI khong dung WebView.
 * Kiem tra: mvn -pl app-desktop dependency:tree | grep javafx-web  -&gt; phai rong.
 */
public final class DictApp {

    public static void main(String[] args) {
        throw new UnsupportedOperationException("TODO(M5): xem PLAN.md muc 10, milestone M5");
    }

    private DictApp() {}
}
