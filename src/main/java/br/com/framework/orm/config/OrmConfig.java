package br.com.framework.orm.config;

import br.com.framework.orm.spi.OrmLogger;
import lombok.Getter;

/**
 * Configuracoes globais do mini-ORM.
 * <p>
 * Atualmente centraliza o logger usado pelas rotinas de auditoria de SQL e
 * PL/SQL.
 * </p>
 */
public class OrmConfig {

    @Getter
    private static OrmLogger logger = new DefaultConsoleLogger();

    /**
     * Substitui o logger padrao por uma implementacao fornecida pela aplicacao.
     * <p>
     * Use este metodo na inicializacao da aplicacao para integrar os logs do
     * mini-ORM ao mecanismo de observabilidade ja usado pelo projeto.
     * </p>
     *
     * <pre>{@code
     * OrmConfig.setLogger(new OrmLogger() {
     *     @Override
     *     public void info(String origin, String code, String msg, Object... params) {
     *         logger.info("{} [{}] {}", origin, code, msg);
     *     }
     *
     *     @Override
     *     public void warn(String origin, String code, String msg, Object... params) {
     *         logger.warn("{} [{}] {}", origin, code, msg);
     *     }
     *
     *     @Override
     *     public void error(String origin, String code, String msg, Exception e, Object... params) {
     *         logger.error("{} [{}] {}", origin, code, msg, e);
     *     }
     * });
     * }</pre>
     *
     * @param customLogger implementacao customizada; valores {@code null} sao
     *                     ignorados
     */
    public static void setLogger(OrmLogger customLogger) {
        if (customLogger != null) {
            logger = customLogger;
        }
    }

    // Logger padrão de sobrevivência
    private static class DefaultConsoleLogger implements OrmLogger {
        /**
         * {@inheritDoc}
         */
        @Override
        public void info(String origin, String code, String msg, Object... params) {
            System.out.println("[INFO] " + origin + " [" + code + "] - " + msg);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void warn(String origin, String code, String msg, Object... params) {
            System.out.println("[WARN] " + origin + " [" + code + "] - " + msg);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void error(String origin, String code, String msg, Exception e, Object... params) {
            System.err.println("[ERROR] " + origin + " [" + code + "] - " + msg);
            if (e != null) e.printStackTrace();
        }
    }
}
