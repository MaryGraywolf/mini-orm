package br.com.framework.orm.core;

import br.com.framework.orm.annotations.ColumnDB;
import br.com.framework.orm.config.OrmConfig;
import lombok.Getter;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SQLResult {

    private final String sql;
    private final List<Object> params;

    public SQLResult(String sql, List<Object> params) {
        validarParametros(sql, params);
        this.sql = sql;
        this.params = params;
    }

    public int executeUpdate(Connection conn) throws Exception {
        long tempoInicio = System.currentTimeMillis();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, this.params);
            int linhas = ps.executeUpdate();
            auditarPerformance(tempoInicio, null);
            return linhas;
        } catch (Exception e) {
            auditarPerformance(tempoInicio, e);
            throw e;
        }
    }

    public <T> List<T> executeQuery(Connection conn, Class<T> classeDTO) throws Exception {
        long tempoInicio = System.currentTimeMillis();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, this.params);
            try (ResultSet rs = ps.executeQuery()) {
                auditarPerformance(tempoInicio, null);
                return mapearResultSet(rs, classeDTO);
            }
        } catch (Exception e) {
            auditarPerformance(tempoInicio, e);
            throw e;
        }
    }

    public <T> T executeQuerySingle(Connection conn, Class<T> classeDTO) throws Exception {
        List<T> lista = this.executeQuery(conn, classeDTO);
        return lista.isEmpty() ? null : lista.get(0);
    }

    public List<Map<String, Object>> executeQueryAsMap(Connection conn) throws Exception {
        long tempoInicio = System.currentTimeMillis();
        List<Map<String, Object>> lista = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(this.sql)) {
            setParameters(ps, this.params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        map.put(meta.getColumnName(i).toUpperCase(), rs.getObject(i));
                    }
                    lista.add(map);
                }
            }
            auditarPerformance(tempoInicio, null);
            return lista;
        } catch (Exception e) {
            auditarPerformance(tempoInicio, e);
            throw e;
        }
    }

    public Map<String, Object> executeQueryAsSingleMap(Connection conn) throws Exception {
        List<Map<String, Object>> lista = this.executeQueryAsMap(conn);
        return lista.isEmpty() ? null : lista.get(0);
    }

    @SuppressWarnings("unchecked")
    public <T> T executeScalar(Connection conn, Class<T> tipoEsperado) throws Exception {
        long tempoInicio = System.currentTimeMillis();

        try (PreparedStatement ps = conn.prepareStatement(this.sql)) {
            setParameters(ps, this.params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object valorBanco = rs.getObject(1);
                    if (valorBanco == null)
                        return null;

                    Object valorConvertido = converterParaTipoCorreto(valorBanco, tipoEsperado);
                    auditarPerformance(tempoInicio, null);
                    return (T) valorConvertido;
                }
            }
            auditarPerformance(tempoInicio, null);
            return null;
        } catch (Exception e) {
            auditarPerformance(tempoInicio, e);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> executeAsDictionary(Connection conn) throws Exception {
        long tempoInicio = System.currentTimeMillis();
        Map<K, V> dicionario = new HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(this.sql)) {
            setParameters(ps, this.params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    K chave = (K) rs.getObject(1);
                    V valor = (V) rs.getObject(2);
                    if (chave != null) {
                        dicionario.put(chave, valor);
                    }
                }
            }
            auditarPerformance(tempoInicio, null);
            return dicionario;
        } catch (Exception e) {
            auditarPerformance(tempoInicio, e);
            throw e;
        }
    }

    private void validarParametros(String sql, List<Object> params) {
        long expected = sql.chars().filter(ch -> ch == '?').count();
        int actual = params == null ? 0 : params.size();

        if (expected != actual) {
            throw new IllegalArgumentException(
                    String.format("Inconsistência SQL: O comando espera %d parâmetros (?), mas foram fornecidos %d.",
                            expected, actual));
        }
    }

    private void setParameters(PreparedStatement ps, List<Object> parametros) throws SQLException {
        if (parametros != null) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
        }
    }

    public <T> List<T> mapearResultSet(ResultSet rs, Class<T> classeDTO) throws Exception {
        List<T> resultado = new ArrayList<>();
        Field[] fields = classeDTO.getDeclaredFields();

        while (rs.next()) {
            T dto = classeDTO.getDeclaredConstructor().newInstance();

            for (Field field : fields) {
                if (field.isAnnotationPresent(ColumnDB.class)) {
                    ColumnDB anotacao = field.getAnnotation(ColumnDB.class);
                    String nomeColuna = anotacao.localName();

                    try {
                        Object valorBanco = rs.getObject(nomeColuna);

                        if (valorBanco != null) {
                            field.setAccessible(true);
                            Class<?> tipoCampo = field.getType();

                            if (tipoCampo.isEnum()) {
                                try {
                                    @SuppressWarnings({"unchecked", "rawtypes"})
                                    Object enumInstancia = Enum.valueOf((Class<Enum>) tipoCampo, valorBanco.toString());
                                    field.set(dto, enumInstancia);
                                } catch (IllegalArgumentException e) {
                                    field.set(dto, null);
                                }
                            } else if (tipoCampo == Long.class && valorBanco instanceof Number) {
                                field.set(dto, ((Number) valorBanco).longValue());

                            } else if (tipoCampo == Integer.class && valorBanco instanceof Number) {
                                field.set(dto, ((Number) valorBanco).intValue());

                            } else if (tipoCampo == Double.class && valorBanco instanceof Number) {
                                field.set(dto, ((Number) valorBanco).doubleValue());

                            } else if (tipoCampo == Float.class && valorBanco instanceof Number) {
                                field.set(dto, ((Number) valorBanco).floatValue());

                            } else if (valorBanco instanceof java.sql.Clob) {
                                java.sql.Clob clob = (java.sql.Clob) valorBanco;
                                long lerDoInicio = 1;
                                int tamanhoTotal = (int) clob.length();
                                field.set(dto, clob.getSubString(lerDoInicio, tamanhoTotal));

                            } else if (valorBanco instanceof java.sql.Timestamp) {
                                // Tratamento exclusivo para Timestamp (Data e Hora)
                                java.sql.Timestamp ts = (java.sql.Timestamp) valorBanco;

                                if (tipoCampo == String.class) {
                                    java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                                    field.set(dto, formatter.format(ts));
                                } else if (tipoCampo.isAssignableFrom(valorBanco.getClass())) {
                                    field.set(dto, valorBanco);
                                } else if (tipoCampo == java.util.Date.class) {
                                    field.set(dto, new java.util.Date(ts.getTime()));
                                }

                            } else if (valorBanco instanceof java.util.Date) {
                                java.util.Date dataBanco = (java.util.Date) valorBanco;

                                if (tipoCampo == String.class) {
                                    java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("dd/MM/yyyy");
                                    field.set(dto, formatter.format(dataBanco));
                                } else if (tipoCampo.isAssignableFrom(valorBanco.getClass())) {
                                    field.set(dto, valorBanco);
                                } else if (tipoCampo == java.util.Date.class) {
                                    field.set(dto, new java.util.Date(dataBanco.getTime()));
                                }

                            } else {
                                field.set(dto, valorBanco);
                            }
                        }

                    } catch (SQLException e) {
                        throw new RuntimeException("Erro ao mapear os setters dos campos", e);
                    }
                }
            }
            resultado.add(dto);
        }
        return resultado;
    }

    private Object converterParaTipoCorreto(Object valorBanco, Class<?> tipoEsperado) {
        if (valorBanco == null)
            return null;

        if (valorBanco instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) valorBanco;
            if (tipoEsperado == Long.class || tipoEsperado == long.class)
                return bd.longValue();
            if (tipoEsperado == Integer.class || tipoEsperado == int.class)
                return bd.intValue();
            if (tipoEsperado == Double.class || tipoEsperado == double.class)
                return bd.doubleValue();
            if (tipoEsperado == String.class)
                return bd.toPlainString();
        }
        return valorBanco;
    }

    @Getter
    public static class Batch {

        private final String sql;
        private final List<List<Object>> batchParams;

        public Batch(String sql, List<List<Object>> batchParams) {
            this.sql = sql;
            this.batchParams = batchParams;
        }

        public int[] executeBatch(Connection conn) throws Exception {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (List<Object> paramsDaLinha : batchParams) {
                    for (int i = 0; i < paramsDaLinha.size(); i++) {
                        ps.setObject(i + 1, paramsDaLinha.get(i));
                    }
                    ps.addBatch();
                }
                return ps.executeBatch();
            }
        }
    }

    private String obterOrigemDaChamada() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (!className.equals(Thread.class.getName()) && !className.equals(SQLResult.class.getName())
                    && !className.equals("br.com.tbm.framework.core.SQLBuilder") && !className.startsWith("java.")) {

                return className.substring(className.lastIndexOf('.') + 1) + "." + element.getMethodName();
            }
        }
        return "Origem Desconhecida";
    }

    private void auditarPerformance(long tempoInicio, Exception excecao) {
        long tempoExecucaoMs = System.currentTimeMillis() - tempoInicio;
        String origem = obterOrigemDaChamada();

        if (excecao != null) {
            // Repare na alteração: Usando o Logger Injetado do OrmConfig
            String msg = String.format("[Mini-ORM ERRO] Falha ao executar SQL. Origem: [%s] | Tempo: %d ms | SQL: %s", origem, tempoExecucaoMs, this.sql);
            OrmConfig.getLogger().error(SQLResult.class.getSimpleName(), "SQL-ERR-001", msg, excecao);
            return;
        }

        if (tempoExecucaoMs > 10000) {
            String msg = String.format("[Mini-ORM LENTIDAO] Slow Query detectada! Origem: [%s] | Tempo: %d ms | SQL: %s", origem, tempoExecucaoMs, this.sql);
            OrmConfig.getLogger().warn(SQLResult.class.getSimpleName(), "SQL-WARN-001", msg);
        }
    }
}
