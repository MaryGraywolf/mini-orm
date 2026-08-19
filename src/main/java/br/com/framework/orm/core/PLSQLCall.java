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

    public static PLSQLCall name(String procedureName) {
        return new PLSQLCall(procedureName);
    }

    public PLSQLCall in(Object value) {
        parameters.add(new Parameter(value));
        return this;
    }

    public PLSQLCall out(String alias, int sqlType) {
        parameters.add(new Parameter(alias, sqlType, null, true, false));
        return this;
    }

    public PLSQLCall inOut(String alias, Object inValue, int sqlType) {
        parameters.add(new Parameter(alias, sqlType, inValue, true, true));
        return this;
    }

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

    public static class Result {
        private final Map<String, Object> outputs;

        public Result(Map<String, Object> outputs) {
            this.outputs = outputs;
        }

        public Object getObject(String alias) {
            return outputs.get(alias);
        }

        public String getString(String alias) {
            Object val = outputs.get(alias);
            return val != null ? val.toString() : null;
        }

        public Long getLong(String alias) {
            Object val = outputs.get(alias);
            if (val == null) return null;
            if (val instanceof BigDecimal) return ((BigDecimal) val).longValue();
            if (val instanceof Number) return ((Number) val).longValue();
            return Long.parseLong(val.toString());
        }

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

        Parameter(Object value) {
            this.isIn = true;
            this.isOut = false;
            this.value = value;
        }

        Parameter(String alias, int sqlType, Object value, boolean isOut, boolean isIn) {
            this.alias = alias;
            this.sqlType = sqlType;
            this.value = value;
            this.isOut = isOut;
            this.isIn = isIn;
        }
    }

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
