package br.com.framework.orm.core;

import br.com.framework.orm.annotations.ColumnDB;
import br.com.framework.orm.annotations.TableDB;
import br.com.framework.orm.config.OrmConfig;
import br.com.framework.orm.spi.OrmLogger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SQLResultTest {

    @BeforeAll
    static void setUpLogger() {
        // Mock simples para não poluir os logs
        OrmConfig.setLogger(mock(OrmLogger.class));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @TableDB(name = "TB_TESTE")
    static class DtoTeste {
        @ColumnDB(localName = "ID")
        private Long id;
        @ColumnDB(localName = "NOME")
        private String nome;
    }

    @Test
    @DisplayName("Deve validar a correspondência entre os parâmetros informados e os placeholders ?")
    void deveValidarQuantidadeDeParametros() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            new SQLResult("SELECT * FROM TABELA WHERE ID = ? AND NOME = ?", Arrays.asList(1L)); // Faltou 1 parametro
        });
        assertTrue(ex.getMessage().contains("O comando espera 2 parâmetros (?), mas foram fornecidos 1"));
    }

    @Test
    @DisplayName("Deve executar o executeUpdate e retornar a quantidade de linhas afetadas")
    void deveExecutarUpdate() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(conn.prepareStatement("UPDATE TABELA SET NOME = ?")).thenReturn(ps);
        when(ps.executeUpdate()).thenReturn(3);

        SQLResult sqlResult = new SQLResult("UPDATE TABELA SET NOME = ?", Arrays.asList("Joao"));
        int linhas = sqlResult.executeUpdate(conn);

        assertEquals(3, linhas);
        verify(ps).setObject(1, "Joao");
        verify(ps).executeUpdate();
    }

    @Test
    @DisplayName("Deve executar executeQuery e retornar uma lista de DTOs")
    void deveExecutarQueryRetornandoDto() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getObject("ID")).thenReturn(10L);
        when(rs.getObject("NOME")).thenReturn("Maria");

        SQLResult sqlResult = new SQLResult("SELECT ID, NOME FROM TB_TESTE WHERE ID = ?", Arrays.asList(10L));
        List<DtoTeste> lista = sqlResult.executeQuery(conn, DtoTeste.class);

        assertEquals(1, lista.size());
        assertEquals(10L, lista.get(0).getId());
        assertEquals("Maria", lista.get(0).getNome());
        verify(ps).setObject(1, 10L);
    }

    @Test
    @DisplayName("Deve executar executeQuerySingle e retornar apenas 1 objeto ou null")
    void deveExecutarQuerySingle() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getObject("ID")).thenReturn(20L);

        SQLResult sqlResult = new SQLResult("SELECT ID FROM TB_TESTE WHERE ID = ?", Arrays.asList(20L));
        DtoTeste dto = sqlResult.executeQuerySingle(conn, DtoTeste.class);

        assertNotNull(dto);
        assertEquals(20L, dto.getId());

        when(rs.next()).thenReturn(false);
        DtoTeste dtoNulo = sqlResult.executeQuerySingle(conn, DtoTeste.class);
        assertNull(dtoNulo);
    }

    @Test
    @DisplayName("Deve executar executeQueryAsMap e converter nomes de coluna para maiúsculo")
    void deveExecutarQueryAsMap() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnName(1)).thenReturn("id_cliente");
        when(metaData.getColumnName(2)).thenReturn("NOME_CLIENTE");
        
        when(rs.next()).thenReturn(true, false);
        when(rs.getObject(1)).thenReturn(55L);
        when(rs.getObject(2)).thenReturn("Pedro");

        SQLResult sqlResult = new SQLResult("SELECT id_cliente, NOME_CLIENTE FROM X", Arrays.asList());
        List<Map<String, Object>> mapList = sqlResult.executeQueryAsMap(conn);

        assertEquals(1, mapList.size());
        Map<String, Object> map = mapList.get(0);
        
        assertTrue(map.containsKey("ID_CLIENTE"));
        assertTrue(map.containsKey("NOME_CLIENTE"));
        assertEquals(55L, map.get("ID_CLIENTE"));
        assertEquals("Pedro", map.get("NOME_CLIENTE"));
    }

    @Test
    @DisplayName("Deve executar executeScalar e converter numero corretamente")
    void deveExecutarScalar() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        
        when(rs.getObject(1)).thenReturn(new BigDecimal("99"));

        SQLResult sqlResult = new SQLResult("SELECT COUNT(1) FROM X", Arrays.asList());
        
        Long valorConvertido = sqlResult.executeScalar(conn, Long.class);
        assertEquals(99L, valorConvertido);

        Integer valorConvertidoInt = sqlResult.executeScalar(conn, Integer.class);
        assertEquals(99, valorConvertidoInt);
    }

    @Test
    @DisplayName("Deve executar executeAsDictionary e mapear 2 colunas")
    void deveExecutarAsDictionary() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        
        when(rs.getObject(1)).thenReturn(1L, 2L);
        when(rs.getObject(2)).thenReturn("Ativo", "Inativo");

        SQLResult sqlResult = new SQLResult("SELECT ID, STATUS FROM X", Arrays.asList());
        Map<Long, String> dict = sqlResult.executeAsDictionary(conn);

        assertEquals(2, dict.size());
        assertEquals("Ativo", dict.get(1L));
        assertEquals("Inativo", dict.get(2L));
    }

    @Test
    @DisplayName("Deve executar um Batch Insert na API")
    void deveExecutarBatchInsert() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeBatch()).thenReturn(new int[]{1, 1});

        List<List<Object>> params = Arrays.asList(
            Arrays.asList(1L, "Joao"),
            Arrays.asList(2L, "Maria")
        );

        SQLResult.Batch batch = new SQLResult.Batch("INSERT INTO X (ID, NOME) VALUES (?, ?)", params);
        int[] resultados = batch.executeBatch(conn);

        assertEquals(2, resultados.length);
        verify(ps).setObject(1, 1L);
        verify(ps).setObject(2, "Joao");
        verify(ps).setObject(1, 2L);
        verify(ps).setObject(2, "Maria");
        verify(ps, times(2)).addBatch();
        verify(ps).executeBatch();
    }

    @Test
    @DisplayName("Deve executar executeQueryAsSingleMap e retornar apenas 1 mapa")
    void deveExecutarQueryAsSingleMap() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnName(1)).thenReturn("NOME");
        
        when(rs.next()).thenReturn(true, false);
        when(rs.getObject(1)).thenReturn("Joao");

        SQLResult sqlResult = new SQLResult("SELECT NOME FROM X", Arrays.asList());
        Map<String, Object> map = sqlResult.executeQueryAsSingleMap(conn);

        assertNotNull(map);
        assertEquals("Joao", map.get("NOME"));
        
        when(rs.next()).thenReturn(false);
        Map<String, Object> mapNulo = sqlResult.executeQueryAsSingleMap(conn);
        assertNull(mapNulo);
    }

    @Test
    @DisplayName("Deve propagar exceções e auditar erro no executeUpdate")
    void devePropagarExcecaoExecuteUpdate() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenThrow(new java.sql.SQLException("DB Off"));

        SQLResult sqlResult = new SQLResult("UPDATE X SET Y = ?", Arrays.asList("A"));
        Exception ex = assertThrows(Exception.class, () -> sqlResult.executeUpdate(conn));
        assertEquals("DB Off", ex.getMessage());
    }

    @Test
    @DisplayName("Deve aceitar null na lista de params no construtor")
    void deveAceitarParamsNull() {
        SQLResult result = new SQLResult("SELECT * FROM X", null);
        assertNull(result.getParams());
    }

    enum StatusEnum { ATIVO, INATIVO }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @TableDB(name = "TB_TIPOS")
    static class DtoTipos {
        @ColumnDB(localName = "CAMPO_ENUM")
        private StatusEnum status;
        @ColumnDB(localName = "CAMPO_DOUBLE")
        private Double campoDouble;
        @ColumnDB(localName = "CAMPO_FLOAT")
        private Float campoFloat;
        @ColumnDB(localName = "CAMPO_CLOB")
        private String campoClob;
        @ColumnDB(localName = "CAMPO_TS_TO_STRING")
        private String campoTsToString;
        @ColumnDB(localName = "CAMPO_DATE")
        private java.util.Date campoDate;
        @ColumnDB(localName = "CAMPO_DATE_SQL")
        private java.util.Date campoDateSql;
    }

    @Test
    @DisplayName("Deve converter tipos avançados (Enum, Double, Float, Clob, Timestamp, Date) no mapearResultSet")
    void deveConverterTiposAvancadosNoResultSet() throws Exception {
        ResultSet rsMock = mock(ResultSet.class);
        when(rsMock.next()).thenReturn(true, false);
        
        // Mockando os valores vindos do banco
        when(rsMock.getObject("CAMPO_ENUM")).thenReturn("ATIVO");
        when(rsMock.getObject("CAMPO_DOUBLE")).thenReturn(new BigDecimal("99.99"));
        when(rsMock.getObject("CAMPO_FLOAT")).thenReturn(new BigDecimal("10.5"));
        
        java.sql.Clob clobMock = mock(java.sql.Clob.class);
        when(clobMock.length()).thenReturn(5L);
        when(clobMock.getSubString(1L, 5)).thenReturn("TEXTO");
        when(rsMock.getObject("CAMPO_CLOB")).thenReturn(clobMock);

        java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2026-09-24 10:00:00");
        when(rsMock.getObject("CAMPO_TS_TO_STRING")).thenReturn(ts);
        
        java.util.Date data = new java.util.Date();
        when(rsMock.getObject("CAMPO_DATE")).thenReturn(data);
        
        java.sql.Date dataSql = new java.sql.Date(data.getTime());
        when(rsMock.getObject("CAMPO_DATE_SQL")).thenReturn(dataSql);

        SQLResult sqlResult = new SQLResult("SELECT *", Arrays.asList());
        List<DtoTipos> lista = sqlResult.mapearResultSet(rsMock, DtoTipos.class);
        
        assertEquals(1, lista.size());
        DtoTipos dto = lista.get(0);
        
        assertEquals(StatusEnum.ATIVO, dto.getStatus());
        assertEquals(99.99, dto.getCampoDouble());
        assertEquals(10.5f, dto.getCampoFloat());
        assertEquals("TEXTO", dto.getCampoClob());
        assertTrue(dto.getCampoTsToString().contains("24/09/2026"));
        assertNotNull(dto.getCampoDate());
        assertNotNull(dto.getCampoDateSql());
    }

    @Test
    @DisplayName("Deve capturar SQLException no mapearResultSet e converter para RuntimeException")
    void deveLancarRuntimeExceptionNoMapear() throws Exception {
        ResultSet rsMock = mock(ResultSet.class);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getObject(anyString())).thenThrow(new java.sql.SQLException("Coluna não existe"));

        SQLResult sqlResult = new SQLResult("SELECT *", Arrays.asList());
        
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            sqlResult.mapearResultSet(rsMock, DtoTipos.class);
        });
        
        assertTrue(ex.getMessage().contains("Erro ao mapear os setters"));
    }
}
