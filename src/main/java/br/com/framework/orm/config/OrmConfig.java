package br.com.framework.orm.config;

import br.com.framework.orm.spi.OrmLogger;
import lombok.Getter;

public class OrmConfig {

    @Getter
    private static OrmLogger logger = new DefaultConsoleLogger();

    public static void setLogger(OrmLogger customLogger) {
        if (customLogger != null) {
            logger = customLogger;
        }
    }

    // Logger padrão de sobrevivência
    private static class DefaultConsoleLogger implements OrmLogger {
        @Override
        public void info(String origin, String code, String msg, Object... params) {
            System.out.println("[INFO] " + origin + " [" + code + "] - " + msg);
        }

        @Override
        public void warn(String origin, String code, String msg, Object... params) {
            System.out.println("[WARN] " + origin + " [" + code + "] - " + msg);
        }

        @Override
        public void error(String origin, String code, String msg, Exception e, Object... params) {
            System.err.println("[ERROR] " + origin + " [" + code + "] - " + msg);
            if (e != null) e.printStackTrace();
        }
    }
}
