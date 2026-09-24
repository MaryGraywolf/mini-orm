package br.com.framework.orm.core;

import br.com.framework.orm.config.OrmConfig;
import br.com.framework.orm.spi.OrmLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PLSQLCallTest {

    @BeforeAll
    static void setUpLogger() {
        OrmConfig.setLogger(mock(OrmLogger.class));
    }

    @Test
    @DisplayName("Deve preparar e executar a chamada configurando os tipos adequadamente")
    void deveExecutarPLSQLComSucesso() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);

        when(conn.prepareCall("{call PKG_TESTE.MEU_METODO(?, ?, ?)}")).thenReturn(cs);

        // Simulando os retornos do CallableStatement
        when(cs.getObject(2)).thenReturn("SUCESSO"); // parametro out
        when(cs.getObject(3)).thenReturn(new BigDecimal("150.5")); // parametro inOut

        PLSQLCall.Result result = PLSQLCall
                .name("PKG_TESTE.MEU_METODO")
                .in("Entrada1")
                .out("MSG_SAIDA", Types.VARCHAR)
                .inOut("VALOR_SAIDA", 100, Types.NUMERIC)
                .execute(conn);

        // Verificando as chamadas
        verify(conn).prepareCall("{call PKG_TESTE.MEU_METODO(?, ?, ?)}");
        
        // P1: .in("Entrada1")
        verify(cs).setObject(1, "Entrada1");
        
        // P2: .out("MSG_SAIDA")
        verify(cs).registerOutParameter(2, Types.VARCHAR);
        
        // P3: .inOut("VALOR_SAIDA", 100)
        verify(cs).registerOutParameter(3, Types.NUMERIC);
        verify(cs).setObject(3, 100);
        
        verify(cs).execute();

        // Validando o Result map
        assertNotNull(result);
        assertEquals("SUCESSO", result.getString("MSG_SAIDA"));
        
        // Testa a conversão para BigDecimal
        assertEquals(new BigDecimal("150.5"), result.getBigDecimal("VALOR_SAIDA"));
        
        // Testa a conversão para Long
        assertEquals(150L, result.getLong("VALOR_SAIDA"));
    }

    @Test
    @DisplayName("Deve tratar conversões de tipos no Result do PLSQL")
    void deveTratarConversoesNoResult() {
        PLSQLCall.Result result = new PLSQLCall.Result(java.util.Map.of(
                "TEXTO", "Valor",
                "NUMERO_INT", 10,
                "NUMERO_STR", "55",
                "NUMERO_BD", new BigDecimal("100.5")
        ));

        assertEquals("Valor", result.getString("TEXTO"));
        assertEquals("10", result.getString("NUMERO_INT"));
        
        assertEquals(10L, result.getLong("NUMERO_INT"));
        assertEquals(55L, result.getLong("NUMERO_STR"));
        assertEquals(100L, result.getLong("NUMERO_BD"));
        
        assertEquals(new BigDecimal("10"), result.getBigDecimal("NUMERO_INT"));
        assertEquals(new BigDecimal("55"), result.getBigDecimal("NUMERO_STR"));
        assertEquals(new BigDecimal("100.5"), result.getBigDecimal("NUMERO_BD"));
        
        // Testando valores nulos
        assertNull(result.getString("NAO_EXISTE"));
        assertNull(result.getLong("NAO_EXISTE"));
        assertNull(result.getBigDecimal("NAO_EXISTE"));
    }

    @Test
    @DisplayName("Deve propagar Exceção em caso de erro no prepareCall ou execute")
    void devePropagarExcecao() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareCall(anyString())).thenThrow(new java.sql.SQLException("Erro de banco"));

        Exception ex = assertThrows(Exception.class, () -> {
            PLSQLCall.name("PROC_FALHA").in("A").execute(conn);
        });

        assertEquals("Erro de banco", ex.getMessage());
    }
}
