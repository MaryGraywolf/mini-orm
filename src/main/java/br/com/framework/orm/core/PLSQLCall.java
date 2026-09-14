package br.com.framework.orm.core;

import br.com.framework.orm.config.OrmConfig;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PLSQLCall {

    private final String procedureName;
    private final List<Parameter> parameters = new ArrayList<>();

    private PLSQLCall(String procedureName) {
        this.procedureName = procedureName;
    }

    /**
     * Inicia a montagem de uma chamada PL/SQL para a procedure informada.
     * <p>
     * O nome pode ser apenas a procedure ou o caminho completo com package,
     * conforme esperado pelo banco.
     * </p>
     *
     * <pre>{@code
     * PLSQLCall.Result result = PLSQLCall
     *         .name("PKG_CLIENTE.CRIAR_CLIENTE")
     *         .in("Maria")
     *         .out("ID_CLIENTE", Types.NUMERIC)
     *         .execute(conn);
     *
     * Long idCliente = result.getLong("ID_CLIENTE");
     * }</pre>
     *
     * @param procedureName nome da procedure ou package.procedure que sera chamada
     * @return builder configurado para receber parametros de entrada e saida
     */
    public static PLSQLCall name(String procedureName) {
        return new PLSQLCall(procedureName);
    }

    /**
     * Adiciona um parametro de entrada na proxima posicao da chamada.
     *
     * <pre>{@code
     * PLSQLCall call = PLSQLCall
     *         .name("PKG_PEDIDO.REPROCESSAR")
     *         .in(nuNota);
     * }</pre>
     *
     * @param value valor enviado para a procedure
     * @return a propria chamada para encadeamento fluente
     */
    public PLSQLCall in(Object value) {
        parameters.add(new Parameter(value));
        return this;
    }

    /**
     * Adiciona um parametro de saida na proxima posicao da chamada.
     *
     * <pre>{@code
     * PLSQLCall.Result result = PLSQLCall
     *         .name("PKG_PEDIDO.GERAR_NUMERO")
     *         .out("NUNOTA", Types.NUMERIC)
     *         .execute(conn);
     *
     * Long nuNota = result.getLong("NUNOTA");
     * }</pre>
     *
     * @param alias   nome usado para recuperar o valor no {@link Result}
     * @param sqlType tipo JDBC registrado em {@link CallableStatement#registerOutParameter(int, int)}
     * @return a propria chamada para encadeamento fluente
     */
    public PLSQLCall out(String alias, int sqlType) {
        parameters.add(new Parameter(alias, sqlType, null, true, false));
        return this;
    }

    /**
     * Adiciona um parametro que possui valor de entrada e tambem retorna valor de
     * saida.
     *
     * <pre>{@code
     * PLSQLCall.Result result = PLSQLCall
     *         .name("PKG_ESTOQUE.RESERVAR")
     *         .inOut("QTD_RESERVADA", quantidadeSolicitada, Types.NUMERIC)
     *         .execute(conn);
     *
     * BigDecimal quantidadeReservada = result.getBigDecimal("QTD_RESERVADA");
     * }</pre>
     *
     * @param alias   nome usado para recuperar o valor no {@link Result}
     * @param inValue valor inicial enviado para a procedure
     * @param sqlType tipo JDBC registrado para o retorno do parametro
     * @return a propria chamada para encadeamento fluente
     */
    public PLSQLCall inOut(String alias, Object inValue, int sqlType) {
        parameters.add(new Parameter(alias, sqlType, inValue, true, true));
        return this;
    }

    /**
     * Monta a sintaxe JDBC de chamada de procedure com um placeholder por
     * parametro registrado.
     *
     * @return instrucao no formato {@code {call PROCEDURE(?, ?)}}
     */
    private String buildSql() {
        StringBuilder sb = new StringBuilder("{call ");
        sb.append(procedureName).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            sb.append("?");
            if (i < parameters.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")}");
        return sb.toString();
    }

    /**
     * Executa a procedure configurada na conexao informada.
     * <p>
     * Parametros de saida sao registrados antes da execucao e retornados em
     * {@link Result} pelo alias configurado em {@link #out(String, int)} ou
     * {@link #inOut(String, Object, int)}.
     * </p>
     *
     * <pre>{@code
     * PLSQLCall.Result result = PLSQLCall
     *         .name("PKG_FINANCEIRO.BAIXAR_TITULO")
     *         .in(nuFin)
     *         .out("MENSAGEM", Types.VARCHAR)
     *         .execute(conn);
     *
     * String mensagem = result.getString("MENSAGEM");
     * }</pre>
     *
     * @param conn conexao JDBC aberta pelo chamador
     * @return valores de saida retornados pela procedure
     * @throws Exception se houver falha ao preparar, parametrizar ou executar a
     *                   chamada
     */
    public Result execute(Connection conn) throws Exception {
        long tempoInicio = System.currentTimeMillis();
        String sql = buildSql();

        try (CallableStatement cs = conn.prepareCall(sql)) {

            for (int i = 0; i < parameters.size(); i++) {
                Parameter p = parameters.get(i);
                int jdbcIndex = i + 1;

                if (p.isOut) {
                    cs.registerOutParameter(jdbcIndex, p.sqlType);
                }
                if (p.isIn) {
                    cs.setObject(jdbcIndex, p.value);
                }
            }

            cs.execute();

            Map<String, Object> outValues = new HashMap<>();
            for (int i = 0; i < parameters.size(); i++) {
                Parameter p = parameters.get(i);
                if (p.isOut) {
                    outValues.put(p.alias, cs.getObject(i + 1));
                }
            }

            auditarPerformance(tempoInicio, null, sql);
            return new Result(outValues);

        } catch (Exception e) {
            auditarPerformance(tempoInicio, e, sql);
            throw e;
        }
    }

    /**
     * Resultado de uma chamada PL/SQL contendo os parametros de saida por alias.
     */
    public static class Result {
        private final Map<String, Object> outputs;

        /**
         * Cria um resultado com os valores de saida capturados.
         *
         * @param outputs mapa de alias para valor retornado pelo JDBC
         */
        public Result(Map<String, Object> outputs) {
            this.outputs = outputs;
        }

        /**
         * Retorna o valor bruto associado ao alias.
         *
         * <pre>{@code
         * Object valor = result.getObject("RETORNO");
         * }</pre>
         *
         * @param alias nome do parametro de saida
         * @return valor retornado pelo JDBC, ou {@code null} quando ausente
         */
        public Object getObject(String alias) {
            return outputs.get(alias);
        }

        /**
         * Retorna o valor associado ao alias convertido para {@link String}.
         *
         * <pre>{@code
         * String mensagem = result.getString("MENSAGEM");
         * }</pre>
         *
         * @param alias nome do parametro de saida
         * @return texto do valor retornado, ou {@code null} quando ausente
         */
        public String getString(String alias) {
            Object val = outputs.get(alias);
            return val != null ? val.toString() : null;
        }

        /**
         * Retorna o valor associado ao alias convertido para {@link Long}.
         *
         * <pre>{@code
         * Long idGerado = result.getLong("ID_GERADO");
         * }</pre>
         *
         * @param alias nome do parametro de saida
         * @return numero convertido, ou {@code null} quando ausente
         */
        public Long getLong(String alias) {
            Object val = outputs.get(alias);
            if (val == null) return null;
            if (val instanceof BigDecimal) return ((BigDecimal) val).longValue();
            if (val instanceof Number) return ((Number) val).longValue();
            return Long.parseLong(val.toString());
        }

        /**
         * Retorna o valor associado ao alias convertido para {@link BigDecimal}.
         *
         * <pre>{@code
         * BigDecimal saldo = result.getBigDecimal("SALDO");
         * }</pre>
         *
         * @param alias nome do parametro de saida
         * @return numero decimal convertido, ou {@code null} quando ausente
         */
        public BigDecimal getBigDecimal(String alias) {
            Object val = outputs.get(alias);
            if (val == null) return null;
            if (val instanceof BigDecimal) return (BigDecimal) val;
            return new BigDecimal(val.toString());
        }
    }

    private static class Parameter {
        boolean isIn;
        boolean isOut;
        String alias;
        int sqlType;
        Object value;

        /**
         * Cria um parametro somente de entrada.
         *
         * @param value valor enviado para a procedure
         */
        Parameter(Object value) {
            this.isIn = true;
            this.isOut = false;
            this.value = value;
        }

        /**
         * Cria um parametro de saida ou entrada/saida.
         *
         * @param alias   nome usado para recuperar o valor retornado
         * @param sqlType tipo JDBC do parametro de saida
         * @param value   valor de entrada, quando aplicavel
         * @param isOut   indica se o parametro deve ser registrado como saida
         * @param isIn    indica se o parametro deve receber valor de entrada
         */
        Parameter(String alias, int sqlType, Object value, boolean isOut, boolean isIn) {
            this.alias = alias;
            this.sqlType = sqlType;
            this.value = value;
            this.isOut = isOut;
            this.isIn = isIn;
        }
    }

    /**
     * Identifica o primeiro metodo externo ao helper na pilha de chamada para uso
     * em mensagens de auditoria.
     *
     * @return origem resumida no formato {@code Classe.metodo}
     */
    private String obterOrigemDaChamada() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (!className.equals(Thread.class.getName())
                    && !className.equals(PLSQLCall.class.getName())
                    && !className.startsWith("java.")) {
                return className.substring(className.lastIndexOf('.') + 1) + "." + element.getMethodName();
            }
        }
        return "Origem Desconhecida";
    }

    /**
     * Registra falhas e chamadas lentas de procedure usando o logger configurado.
     *
     * @param tempoInicio instante em milissegundos capturado antes da execucao
     * @param excecao     excecao capturada, ou {@code null} em caso de sucesso
     * @param sql         chamada SQL executada
     */
    private void auditarPerformance(long tempoInicio, Exception excecao, String sql) {
        long tempoExecucaoMs = System.currentTimeMillis() - tempoInicio;
        String origem = obterOrigemDaChamada();

        if (excecao != null) {
            String msg = String.format("[Mini-ORM PL/SQL ERRO] Falha ao executar Procedure. Origem: [%s] | Tempo: %d ms | SQL: %s",
                    origem, tempoExecucaoMs, sql);
            OrmConfig.getLogger().error(PLSQLCall.class.getSimpleName(), "PLSQL-ERR-001", msg, excecao);
            return;
        }

        if (tempoExecucaoMs > 3000) {
            String msg = String.format("[Mini-ORM LENTIDAO] Procedure Lenta! Origem: [%s] | Tempo: %d ms | SQL: %s",
                    origem, tempoExecucaoMs, sql);
            OrmConfig.getLogger().warn(PLSQLCall.class.getSimpleName(), "PLSQL-WARN-001", msg);
        }
    }
}
