package br.com.framework.orm.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mapeia um atributo Java para uma coluna fisica do banco de dados.
 * <p>
 * A anotacao e lida em tempo de execucao pelo mini-ORM para montar SQLs e mapear
 * resultados via reflection.
 * </p>
 *
 * <pre>{@code
 * @ColumnDB(localName = "CODPARC", sequence = "SEQ_PARCEIRO.NEXTVAL", isPrimaryKey = true)
 * private Long codigoParceiro;
 *
 * @ColumnDB(localName = "NOMEPARC")
 * private String nomeParceiro;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ColumnDB {
    /**
     * Nome fisico da coluna no banco de dados.
     *
     * @return nome da coluna usado nos comandos SQL
     */
    String localName();

    /**
     * Expressao de sequence usada em inserts quando o valor do atributo estiver
     * nulo.
     *
     * @return expressao SQL da sequence, ou texto vazio quando nao houver
     */
    String sequence() default "";

    /**
     * Marca o atributo como chave primaria da tabela.
     * <p>
     * A marcacao e usada para recuperar o ID gerado pelo banco apos um
     * {@code INSERT}, em
     * {@link br.com.framework.orm.core.SQLResult#executeAndReturnId(java.sql.Connection, Object)}:
     * o {@link #localName()} deste atributo e informado ao driver JDBC como a
     * coluna que deve ser devolvida.
     * </p>
     *
     * @return {@code true} quando o atributo representa a chave primaria
     */
    boolean isPrimaryKey() default false;
}
