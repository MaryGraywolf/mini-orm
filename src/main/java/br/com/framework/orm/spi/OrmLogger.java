package br.com.framework.orm.spi;

/**
 * Contrato de logging usado pelo mini-ORM para registrar informacoes,
 * advertencias e erros de execucao.
 */
public interface OrmLogger {

    /**
     * Registra uma mensagem informativa.
     *
     * @param origin origem logica do evento
     * @param code   codigo identificador do evento
     * @param msg    mensagem principal
     * @param params parametros adicionais opcionais
     */
    void info(String origin, String code, String msg, Object... params);

    /**
     * Registra uma advertencia, como consultas lentas.
     *
     * @param origin origem logica do evento
     * @param code   codigo identificador do evento
     * @param msg    mensagem principal
     * @param params parametros adicionais opcionais
     */
    void warn(String origin, String code, String msg, Object... params);

    /**
     * Registra uma falha ocorrida durante a execucao do mini-ORM.
     *
     * @param origin origem logica do evento
     * @param code   codigo identificador do evento
     * @param msg    mensagem principal
     * @param e      excecao associada ao erro
     * @param params parametros adicionais opcionais
     */
    void error(String origin, String code, String msg, Exception e, Object... params);

}
