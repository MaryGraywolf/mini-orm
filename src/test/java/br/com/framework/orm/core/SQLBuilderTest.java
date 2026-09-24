package br.com.framework.orm.core;

import br.com.framework.orm.annotations.ColumnDB;
import br.com.framework.orm.annotations.TableDB;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SQLBuilderTest {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @TableDB(name = "TB_USER")
    static class UserTestDTO {

        @ColumnDB(localName = "USER_ID", sequence = "SEQ_USER_ID.NEXTVAL")
        private Long id;

        @ColumnDB(localName = "USER_NAME")
        private String userName;

        @ColumnDB(localName = "USER_AGE")
        private Integer userAge;
    }

    static class ClasseSemAnotacao {
        private String texto;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @TableDB(name = "TB_USER")
    static class ClasseSemCampo {
        private Long id;
        private String userName;
        private Integer userAge;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @TableDB(name = "TB_USER")
    static class UserSemSequence {

        @ColumnDB(localName = "USER_ID")
        private Long id;

        @ColumnDB(localName = "USER_NAME")
        private String userName;

        @ColumnDB(localName = "USER_AGE")
        private Integer userAge;
    }

    @Test
    @DisplayName("Deve Gerar o SQL Insert ignorando nulos e aplicando a sequence")
    void deveGerarInsertComSucesso() {

        UserTestDTO dto = UserTestDTO.builder()
                .userName("João Silva")
                .userAge(30)
                .build();

        SQLResult sql = SQLBuilder.buildInsert(dto);
        assertNotNull(sql);

        String sqlEsperado = "INSERT INTO TB_USER (USER_ID, USER_NAME, USER_AGE) VALUES (SEQ_USER_ID.NEXTVAL, ?, ?)";
        assertEquals(sqlEsperado, sql.getSql());

        List<Object> params = sql.getParams();
        assertEquals(2, params.size());
        assertEquals("João Silva", params.get(0));
        assertEquals(30, params.get(1));
    }

    @Test
    @DisplayName("Deve Gerar Excecao ao INSERT se não tiver dados populados")
    void deveGerarExcecaoAoInserirSeNaoTiverDados() {
        UserSemSequence dto = UserSemSequence.builder().build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            SQLBuilder.buildInsert(dto);
        });

        assertEquals("Nenhum campo populado encontrado para realizar o INSERT.", exception.getMessage());
    }

    @Test
    @DisplayName("Deve Gerar Excecao ao UPDATE se não tiver dados populados")
    void deveGerarExcecaoAoAtualizarSeNaoTiverDados() {
        UserTestDTO dto = UserTestDTO.builder().build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            SQLBuilder.buildUpdate(dto, "USER_AGE = ?", 30);
        });

        assertEquals("Nenhum campo populado encontrado para realizar o UPDATE.", exception.getMessage());
    }

    @Test
    @DisplayName("Deve gerar SQL SELECT dinâmico aplicando WHERE")
    void deveGerarSelectComSucesso() {
        SQLResult sql = SQLBuilder.buildSelect(UserTestDTO.class, "USER_AGE = ?", 30);
        assertNotNull(sql);

        String sqlEsperado = "SELECT USER_ID, USER_NAME, USER_AGE FROM TB_USER WHERE USER_AGE = ?";
        assertEquals(sqlEsperado, sql.getSql());

        assertEquals(1, sql.getParams().size());
        assertEquals(30, sql.getParams().get(0));
    }

    @Test
    @DisplayName("Deve mapear os dados do ResultSet (Banco de Dados) para o DTO corretamente")
    void deveMapearResultSetParaDto() throws Exception {
        ResultSet rsMock = mock(ResultSet.class);

        when(rsMock.next()).thenReturn(true, false);
        when(rsMock.getObject("USER_ID")).thenReturn(99L);
        when(rsMock.getObject("USER_NAME")).thenReturn("Maria da Silva");
        when(rsMock.getObject("USER_AGE")).thenReturn(25);

        SQLResult sql = SQLBuilder.buildSelect(UserTestDTO.class, null);

        List<UserTestDTO> result = sql.mapearResultSet(rsMock, UserTestDTO.class);
        assertNotNull(result);

        assertEquals(1, result.size(), "Deveria ter mapeado 1 registro");

        UserTestDTO dtoMapeado = result.get(0);

        assertEquals(99L, dtoMapeado.getId());
        assertEquals("Maria da Silva", dtoMapeado.getUserName());
        assertEquals(25, dtoMapeado.getUserAge());
    }

    @Test
    @DisplayName("Deve gerar SQL UPDATE desconsiderando nulos e aplicando WHERE")
    void deveGerarUpdateComSucesso() {

        UserTestDTO dto = UserTestDTO.builder()
                .userName("João Silva Junior")
                .build();

        SQLResult sql = SQLBuilder.buildUpdate(dto, "USER_ID = ?", 1);
        assertNotNull(sql);

        String sqlEsperado = "UPDATE TB_USER SET USER_NAME = ? WHERE USER_ID = ?";
        assertEquals(sqlEsperado, sql.getSql());

        assertEquals(2, sql.getParams().size());
        assertEquals("João Silva Junior", sql.getParams().get(0));
        assertEquals(1, sql.getParams().get(1));
    }

    @Test
    @DisplayName("Deve lançar exceção se a classe não possuir a anotação @TableDB")
    void deveLancarExcecaoSeNaoTiverTableDB() {
        ClasseSemAnotacao obj = new ClasseSemAnotacao();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            SQLBuilder.buildInsert(obj);
        });

        assertEquals("A classe ClasseSemAnotacao nao possui a anotacao @TableDB no nivel de classe.", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção se a classe não possuir a anotação @ColumnDB")
    void deveLancarExcecaoSeNaoExisteColumnDB() {

        IllegalStateException exceptionSelect = assertThrows(IllegalStateException.class, () -> {
            SQLBuilder.select(ClasseSemCampo.class).campos("USER_NAME").where("USER_AGE = ?", 30).build();
        });

        assertEquals("Nenhuma coluna mapeada para o SELECT.", exceptionSelect.getMessage());

        IllegalStateException exceptionBuildSelect = assertThrows(IllegalStateException.class, () -> {
            SQLBuilder.buildSelect(ClasseSemCampo.class, null);
        });

        assertEquals("A classe ClasseSemCampo não possui nenhuma coluna mapeada com @ColumnDB.", exceptionBuildSelect.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar realizar UPDATE sem a cláusula WHERE")
    void deveLancarExcecaoSeUpdateNaoTiverWhere() {
        UserTestDTO dto = UserTestDTO.builder().userName("João").build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            SQLBuilder.buildUpdate(dto, null);
        });

        assertEquals("Camada de WHERE é obrigatória para essa operação de UPDATE.", exception.getMessage());
    }

    @Test
    @DisplayName("Deve gerar SQL de Insert em Lote (Batch) corretamente")
    void deveGerarInsertBatchComSucesso() {
        List<UserTestDTO> lista = new ArrayList<>();
        lista.add(UserTestDTO.builder().userName("João").userAge(20).build());
        lista.add(UserTestDTO.builder().userName("Maria").userAge(25).build());

        SQLResult.Batch batch = SQLBuilder.buildInsertBatch(lista);

        assertNotNull(batch);
        assertEquals("INSERT INTO TB_USER (USER_ID, USER_NAME, USER_AGE) VALUES (SEQ_USER_ID.NEXTVAL, ?, ?)", batch.getSql());

        List<List<Object>> params = batch.getBatchParams();
        assertEquals(2, params.size());

        // Parâmetros do João
        assertEquals(2, params.get(0).size());
        assertEquals("João", params.get(0).get(0));
        assertEquals(20, params.get(0).get(1));

        // Parâmetros da Maria
        assertEquals(2, params.get(1).size());
        assertEquals("Maria", params.get(1).get(0));
        assertEquals(25, params.get(1).get(1));
    }

    @Test
    @DisplayName("Deve gerar SQL de Select usando a API Fluente (SelectBuilder) especificando campos")
    void deveGerarFluentSelectComCamposEspecificos() {
        SQLResult sql = SQLBuilder
                .select(UserTestDTO.class)
                .campos("userName") // nome do atributo na classe
                .where("USER_AGE > ?", 18)
                .build();

        assertNotNull(sql);
        assertEquals("SELECT USER_NAME FROM TB_USER WHERE USER_AGE > ?", sql.getSql());
        assertEquals(1, sql.getParams().size());
        assertEquals(18, sql.getParams().get(0));
    }

    @Test
    @DisplayName("Deve gerar SQL Select completo usando a API Fluente se não especificar campos")
    void deveGerarFluentSelectCompleto() {
        SQLResult sql = SQLBuilder
                .select(UserTestDTO.class)
                .where("USER_NAME = ?", "João")
                .build();

        assertNotNull(sql);
        assertEquals("SELECT USER_ID, USER_NAME, USER_AGE FROM TB_USER WHERE USER_NAME = ?", sql.getSql());
        assertEquals(1, sql.getParams().size());
        assertEquals("João", sql.getParams().get(0));
    }
}
