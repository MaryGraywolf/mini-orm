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

/**
 * Wrapper do SQL gerado pelo {@link SQLBuilder}.
 * <p>
 * Mantem a instrucao SQL e os parametros na mesma ordem dos placeholders
 * {@code ?}, centralizando a aplicacao dos valores no {@link PreparedStatement}
 * e o mapeamento basico do {@link ResultSet} para DTOs anotados com
 * {@link ColumnDB}.
 * </p>
 * <p>
 * Tambem oferece metodos de execucao direta para reduzir boilerplate nos DAOs:
 * {@link #executeUpdate(Connection)} para comandos de escrita,
 * {@link #executeAndReturnId(Connection, Object)} para {@code INSERT} que precisa
 * do ID gerado pelo banco e 
 * {@link #executeQuery(Connection, Class)} para
 * consultas.
 * </p>
 */
@Getter
public class SQLResult {

    private final String sql;
    private final List<Object> params;

    /**
	 * Cria o resultado de uma montagem dinamica de SQL.
	 * <p>
	 * Antes de armazenar a SQL, valida se a quantidade de placeholders {@code ?}
	 * corresponde a quantidade de parametros recebidos.
	 * </p>
     *
     * <pre>{@code
     * SQLResult sql = new SQLResult(
     *         "SELECT NOME FROM CLIENTE WHERE ID = ?",
     *         Arrays.asList(idCliente));
     *
     * String nome = sql.executeScalar(conn, String.class);
     * }</pre>
	 *
	 * @param sql    instrucao SQL pronta para uso em {@link PreparedStatement}
	 * @param params valores que preenchem os placeholders {@code ?}, na ordem exata
	 *               em que aparecem na SQL
	 * @throws IllegalArgumentException se a quantidade de placeholders nao bater
	 *                                  com a quantidade de parametros
	 */
    public SQLResult(String sql, List<Object> params) {
        validarParametros(sql, params);
        this.sql = sql;
        this.params = params;
    }

    /**
	 * Executa a SQL como comando de escrita.
	 * <p>
	 * Deve ser usado para instrucoes como {@code INSERT}, {@code UPDATE} e
	 * {@code DELETE}. O metodo cria o {@link PreparedStatement}, aplica os
	 * parametros e retorna a quantidade de linhas afetadas.
	 * </p>
     *
     * <pre>{@code
     * SQLResult sql = SQLBuilder.buildUpdate(cliente, "ID = ?", id);
     * int linhasAfetadas = sql.executeUpdate(conn);
     * }</pre>
	 *
	 * @param conn conexao JDBC aberta pelo chamador
	 * @return quantidade de linhas afetadas pelo comando
	 * @throws Exception se houver falha ao preparar, parametrizar ou executar a SQL
	 */
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

    /**
	 * Executa um {@code INSERT} e devolve o ID gerado pelo banco.
	 * <p>
	 * A coluna da chave primaria e descoberta pelo atributo anotado com
	 * {@code @ColumnDB(isPrimaryKey = true)} e informada ao driver por
	 * {@code prepareStatement(sql, new String[] { "ID_CLIENTE" })}. Essa forma e
	 * obrigatoria no Oracle: com {@code Statement.RETURN_GENERATED_KEYS} o driver
	 * devolve o {@code ROWID} da linha inserida, e nao o valor da sequence. Como o
	 * Oracle compara o nome informado com o dicionario de dados,
	 * {@link ColumnDB#localName()} deve estar em letras maiusculas.
	 * </p>
	 * <p>
	 * Alem de retornar o ID, o metodo preenche o atributo de chave primaria do
	 * proprio {@code dto} recebido, convertido para o tipo declarado do atributo.
	 * </p>
     *
     * <pre>{@code
     * Cliente novo = new Cliente();
     * novo.setNome("Empresa XYZ");
     *
     * SQLResult sql = SQLBuilder.buildInsert(novo);
     * Long idGerado = sql.executeAndReturnId(conn, novo);
     *
     * // o DTO tambem fica preenchido
     * Long mesmoId = novo.getIdCliente();
     * }</pre>
	 *
	 * @param conn conexao JDBC aberta pelo chamador
	 * @param dto  mesmo DTO usado em {@link SQLBuilder#buildInsert(Object)}; recebe
	 *             o ID gerado por reflexao
	 * @return ID gerado pelo banco, convertido para {@link Long}
	 * @throws IllegalArgumentException se {@code dto} for nulo
	 * @throws IllegalStateException    se o DTO nao possuir atributo anotado com
	 *                                  {@code @ColumnDB(isPrimaryKey = true)}
	 * @throws SQLException             se o banco nao devolver o ID gerado
	 * @throws Exception                se houver falha ao preparar, parametrizar ou
	 *                                  executar a SQL
	 */
    public Long executeAndReturnId(Connection conn, Object dto) throws Exception {
        long tempoInicio = System.currentTimeMillis();

        try {
            if (dto == null) {
                throw new IllegalArgumentException("O DTO de destino do ID gerado nao pode ser nulo.");
            }

            Field campoId = SQLBuilder.descobrirCampoChavePrimaria(dto.getClass());
            String colunaId = campoId.getAnnotation(ColumnDB.class).localName();

            try (PreparedStatement ps = conn.prepareStatement(this.sql, new String[] { colunaId })) {
                setParameters(ps, this.params);
                ps.executeUpdate();

                try (ResultSet chaves = ps.getGeneratedKeys()) {
                    if (!chaves.next()) {
                        throw new SQLException(String.format(
                                "Falha na criacao do registro: o banco nao retornou a coluna %s.", colunaId));
                    }

                    Object idGerado = chaves.getObject(1);
                    if (idGerado == null) {
                        throw new SQLException(String.format(
                                "Falha na criacao do registro: a coluna %s retornou nula.", colunaId));
                    }

                    campoId.set(dto, converterParaTipoCorreto(idGerado, campoId.getType()));

                    auditarPerformance(tempoInicio, null);
                    return (Long) converterParaTipoCorreto(idGerado, Long.class);
                }
            }
        } catch (Exception e) {
            auditarPerformance(tempoInicio, e);
            throw e;
        }
    }

    /**
	 * Executa a SQL como consulta e mapeia o resultado para DTOs.
	 * <p>
	 * O metodo cria o {@link PreparedStatement}, aplica os parametros, executa a
	 * consulta e delega o preenchimento dos objetos para
	 * {@link #mapearResultSet(ResultSet, Class)}.
	 * </p>
     *
     * <pre>{@code
     * SQLResult sql = SQLBuilder.buildSelect(ClienteDTO.class, "ATIVO = ?", "S");
     * List<ClienteDTO> clientes = sql.executeQuery(conn, ClienteDTO.class);
     * }</pre>
	 *
	 * @param <T>       tipo do DTO retornado
	 * @param conn      conexao JDBC aberta pelo chamador
	 * @param classeDTO classe concreta do DTO anotado com {@link ColumnDB}
	 * @return lista de DTOs mapeados a partir do {@link ResultSet}
	 * @throws Exception se houver falha ao consultar, instanciar ou mapear os DTOs
	 */
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

    /**
     * Executa a consulta e retorna somente o primeiro DTO encontrado.
     * <p>
     * Use quando a consulta representa uma busca unica por chave, codigo ou outro
     * filtro que deveria retornar no maximo um registro relevante.
     * </p>
     *
     * <pre>{@code
     * SQLResult sql = SQLBuilder.buildSelect(ClienteDTO.class, "ID = ?", id);
     * ClienteDTO cliente = sql.executeQuerySingle(conn, ClienteDTO.class);
     * }</pre>
     *
     * @param <T>       tipo do DTO retornado
     * @param conn      conexao JDBC aberta pelo chamador
     * @param classeDTO classe concreta do DTO anotado com {@link ColumnDB}
     * @return primeiro DTO da consulta, ou {@code null} quando nao houver linhas
     * @throws Exception se houver falha ao consultar ou mapear os DTOs
     */
    public <T> T executeQuerySingle(Connection conn, Class<T> classeDTO) throws Exception {
        List<T> lista = this.executeQuery(conn, classeDTO);
        return lista.isEmpty() ? null : lista.get(0);
    }

    /**
	 * Executa a instrução SQL de leitura (SELECT) e mapeia o resultado
	 * dinamicamente para uma lista de mapas, eliminando a necessidade de uma classe
	 * DTO específica. *
	 * <p>
	 * Esta abordagem é ideal para consultas genéricas, relatórios dinâmicos ou
	 * extrações de colunas avulsas onde a criação de um DTO seria excessiva.
	 * </p>
	 * *
	 * <p>
	 * <b>Atenção:</b> As chaves do mapa (nomes das colunas) são sempre convertidas
	 * para letras maiúsculas para manter a compatibilidade e previsibilidade com o
	 * Oracle.
	 * </p>
	 *
     *
     * <pre>{@code
     * SQLResult sql = new SQLResult(
     *         "SELECT CODPROD, DESCRPROD FROM TGFPRO WHERE ATIVO = ?",
     *         Arrays.asList("S"));
     *
     * List<Map<String, Object>> linhas = sql.executeQueryAsMap(conn);
     * Object descricao = linhas.get(0).get("DESCRPROD");
     * }</pre>
	 *
	 * @param conn A conexão ativa com o banco de dados (fornecida pelo DAO).
	 * @return Uma lista onde cada {@code Map<String, Object>} representa uma linha
	 *         retornada pelo banco. A chave do mapa é o nome da coluna (em
	 *         UPPERCASE) e o valor é o dado bruto retornado pelo JDBC. Retorna uma
	 *         lista vazia se a consulta não encontrar nenhum registro.
	 * @throws Exception Caso ocorra algum erro na preparação do statement, injeção
	 *                   de parâmetros ou execução da query.
	 */
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

    /**
     * Executa a consulta como mapa e retorna somente a primeira linha encontrada.
     *
     * <pre>{@code
     * SQLResult sql = new SQLResult(
     *         "SELECT CODPROD, DESCRPROD FROM TGFPRO WHERE CODPROD = ?",
     *         Arrays.asList(codigoProduto));
     *
     * Map<String, Object> produto = sql.executeQueryAsSingleMap(conn);
     * }</pre>
     *
     * @param conn conexao JDBC aberta pelo chamador
     * @return mapa da primeira linha retornada, ou {@code null} quando nao houver
     *         linhas
     * @throws Exception se houver falha ao preparar, parametrizar ou executar a
     *                   consulta
     */
    public Map<String, Object> executeQueryAsSingleMap(Connection conn) throws Exception {
        List<Map<String, Object>> lista = this.executeQueryAsMap(conn);
        return lista.isEmpty() ? null : lista.get(0);
    }

    /**
     * Executa a consulta e retorna o valor da primeira coluna da primeira linha.
     * <p>
     * Quando o valor retornado pelo banco for numerico, aplica conversoes basicas
     * para o tipo esperado usando {@link #converterParaTipoCorreto(Object, Class)}.
     * </p>
     *
     * <pre>{@code
     * SQLResult sql = new SQLResult(
     *         "SELECT COUNT(1) FROM TGFCAB WHERE CODPARC = ?",
     *         Arrays.asList(codigoParceiro));
     *
     * Long totalPedidos = sql.executeScalar(conn, Long.class);
     * }</pre>
     *
     * @param <T>          tipo esperado para o valor escalar
     * @param conn         conexao JDBC aberta pelo chamador
     * @param tipoEsperado classe do tipo esperado pelo chamador
     * @return valor convertido, ou {@code null} quando a consulta nao retornar
     *         linhas ou a coluna estiver nula
     * @throws Exception se houver falha ao preparar, parametrizar ou executar a
     *                   consulta
     */
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

    /**
     * Executa a consulta e transforma as duas primeiras colunas em um dicionario.
     * <p>
     * A primeira coluna e usada como chave e a segunda como valor. Linhas com chave
     * nula sao ignoradas.
     * </p>
     *
     * <pre>{@code
     * SQLResult sql = new SQLResult(
     *         "SELECT CODTIPOPER, DESCROPER FROM TGFTOP WHERE ATIVO = ?",
     *         Arrays.asList("S"));
     *
     * Map<Long, String> tiposOperacao = sql.executeAsDictionary(conn);
     * }</pre>
     *
     * @param <K>  tipo esperado para as chaves
     * @param <V>  tipo esperado para os valores
     * @param conn conexao JDBC aberta pelo chamador
     * @return mapa preenchido com os pares chave/valor retornados pela consulta
     * @throws Exception se houver falha ao preparar, parametrizar ou executar a
     *                   consulta
     */
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

    /**
	 * Valida a correspondencia entre placeholders e parametros.
	 * <p>
	 * A validacao protege contra erro comum de JDBC manual: SQL com quantidade de
	 * {@code ?} diferente da lista de parametros gerada pelo builder.
	 * </p>
	 *
	 * @param sql    instrucao SQL gerada
	 * @param params parametros associados aos placeholders
	 * @throws IllegalArgumentException se a contagem de {@code ?} for diferente da
	 *                                  quantidade de parametros
	 */
    private void validarParametros(String sql, List<Object> params) {
        long expected = sql.chars().filter(ch -> ch == '?').count();
        int actual = params == null ? 0 : params.size();

        if (expected != actual) {
            throw new IllegalArgumentException(
                    String.format("Inconsistência SQL: O comando espera %d parâmetros (?), mas foram fornecidos %d.",
                            expected, actual));
        }
    }

    /**
	 * Preenche automaticamente os parametros {@code ?} no
	 * {@link PreparedStatement}.
	 * <p>
	 * O indice JDBC comeca em 1. Por isso, cada item de {@link #params} e aplicado
	 * em {@code i + 1}, evitando inversao manual de indices em INSERT, UPDATE e
	 * SELECT.
	 * </p>
	 *
	 * @param ps         statement preparado que recebera os parametros
	 * @param parametros valores que serao aplicados ao statement
	 * @throws SQLException se o driver JDBC falhar ao aplicar algum parametro
	 */
    private void setParameters(PreparedStatement ps, List<Object> parametros) throws SQLException {
        if (parametros != null) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
        }
    }

    /**
	 * Le o {@link ResultSet} e transforma cada linha em uma instancia do DTO
	 * informado.
	 * <p>
	 * Sao considerados apenas atributos anotados com {@link ColumnDB}. O metodo
	 * tambem normaliza tipos comuns do Oracle/JDBC: valores numericos para
	 * {@link Long}, {@link Integer}, {@link Double} e {@link Float}; CLOB para
	 * {@link String}; e datas/timestamps para {@link String},
	 * {@link java.util.Date} ou tipos JDBC compativeis.
	 * </p>
     *
     * <pre>{@code
     * SQLResult sqlResult = SQLBuilder.buildSelect(ClienteDTO.class, null);
     *
     * try (PreparedStatement ps = conn.prepareStatement(sqlResult.getSql())) {
     *     try (ResultSet rs = ps.executeQuery()) {
     *         List<ClienteDTO> clientes = sqlResult.mapearResultSet(rs, ClienteDTO.class);
     *     }
     * }
     * }</pre>
	 *
	 * @param <T>       tipo do DTO de destino
	 * @param rs        resultado vindo de {@code executeQuery()}
	 * @param classeDTO classe concreta do DTO, com construtor sem argumentos
	 * @return lista de DTOs preenchidos com os dados retornados
	 * @throws Exception se houver falha de instanciacao, reflexao ou leitura de
	 *                   tipos nao tratados
	 */
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

    /**
     * Converte valores comuns retornados pelo JDBC para o tipo esperado.
     * <p>
     * Trata as tres origens que os drivers costumam devolver: {@link BigDecimal}
     * (padrao do Oracle), qualquer outro {@link Number} e {@link String}. Quando o
     * valor ja e uma instancia do tipo esperado, ele e devolvido sem conversao.
     * </p>
     * <p>
     * Tipos primitivos sao aceitos em {@code tipoEsperado} e resolvidos para o
     * wrapper correspondente, o que permite usar o metodo tanto para valores
     * escalares quanto para preencher atributos de DTO por reflection.
     * </p>
     *
     * @param valorBanco   valor bruto retornado pelo banco
     * @param tipoEsperado tipo solicitado pelo chamador
     * @return valor convertido quando houver conversao conhecida, ou o valor
     *         original caso contrario
     * @throws NumberFormatException se o valor for um texto que nao representa um
     *                               numero valido para o tipo esperado
     */
    private Object converterParaTipoCorreto(Object valorBanco, Class<?> tipoEsperado) {
        if (valorBanco == null || tipoEsperado == null || tipoEsperado == Object.class)
            return valorBanco;

        if (tipoEsperado.isInstance(valorBanco))
            return valorBanco;

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

        if (valorBanco instanceof Number) {
            Number numero = (Number) valorBanco;
            if (tipoEsperado == Long.class || tipoEsperado == long.class)
                return numero.longValue();
            if (tipoEsperado == Integer.class || tipoEsperado == int.class)
                return numero.intValue();
            if (tipoEsperado == Short.class || tipoEsperado == short.class)
                return numero.shortValue();
            if (tipoEsperado == Double.class || tipoEsperado == double.class)
                return numero.doubleValue();
            if (tipoEsperado == Float.class || tipoEsperado == float.class)
                return numero.floatValue();
            if (tipoEsperado == BigDecimal.class)
                return new BigDecimal(numero.toString());
            if (tipoEsperado == String.class)
                return numero.toString();
        }

        if (valorBanco instanceof String) {
            String texto = ((String) valorBanco).trim();
            if (tipoEsperado == Long.class || tipoEsperado == long.class)
                return Long.parseLong(texto);
            if (tipoEsperado == Integer.class || tipoEsperado == int.class)
                return Integer.parseInt(texto);
            if (tipoEsperado == BigDecimal.class)
                return new BigDecimal(texto);
        }

        return valorBanco;
    }

    /**
	 * Wrapper para execucao de comandos SQL em lote.
	 * <p>
	 * Armazena uma unica instrucao SQL e uma lista de parametros por linha. Cada
	 * linha e aplicada ao {@link PreparedStatement}, adicionada ao lote com
	 * {@link PreparedStatement#addBatch()} e executada em conjunto por
	 * {@link PreparedStatement#executeBatch()}.
	 * </p>
	 */
    @Getter
    public static class Batch {

        private final String sql;
        private final List<List<Object>> batchParams;

        /**
		 * Cria um comando batch.
         *
         * <pre>{@code
         * SQLResult.Batch batch = new SQLResult.Batch(
         *         "INSERT INTO CLIENTE (ID, NOME) VALUES (?, ?)",
         *         Arrays.asList(
         *                 Arrays.asList(1L, "Maria"),
         *                 Arrays.asList(2L, "Joao")));
         * }</pre>
		 *
		 * @param sql         instrucao SQL unica usada por todas as linhas
		 * @param batchParams lista de parametros por linha, na ordem dos placeholders
		 */
        public Batch(String sql, List<List<Object>> batchParams) {
            this.sql = sql;
            this.batchParams = batchParams;
        }

        /**
		 * Executa o lote na conexao informada.
         *
         * <pre>{@code
         * SQLResult.Batch batch = SQLBuilder.buildInsertBatch(clientes);
         * int[] resultados = batch.executeBatch(conn);
         * }</pre>
		 *
		 * @param conn conexao JDBC aberta pelo chamador
		 * @return vetor com o resultado de execucao de cada linha do batch
		 * @throws Exception se houver falha ao preparar, parametrizar ou executar o
		 *                   lote
		 */
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

    /**
     * Registra falhas e consultas lentas usando o logger configurado.
     *
     * @param tempoInicio instante em milissegundos capturado antes da execucao
     * @param excecao     excecao capturada, ou {@code null} em caso de sucesso
     */
    private void auditarPerformance(long tempoInicio, Exception excecao) {
        long tempoExecucaoMs = System.currentTimeMillis() - tempoInicio;
        String origem = obterOrigemDaChamada();
        int paramCount = this.params == null ? 0 : this.params.size();

        if (excecao != null) {
            String msg = String.format("[Mini-ORM ERRO] Falha ao executar SQL. Origem: [%s] | Tempo: %d ms | Params: %d", origem, tempoExecucaoMs, paramCount);
            OrmConfig.getLogger().error(SQLResult.class.getSimpleName(), "SQL-ERR-001", msg, excecao);
            return;
        }

        if (tempoExecucaoMs > 10000) {
            String msg = String.format("[Mini-ORM LENTIDAO] Slow Query detectada! Origem: [%s] | Tempo: %d ms | Params: %d", origem, tempoExecucaoMs, paramCount);
            OrmConfig.getLogger().warn(SQLResult.class.getSimpleName(), "SQL-WARN-001", msg);
        }
    }
}
