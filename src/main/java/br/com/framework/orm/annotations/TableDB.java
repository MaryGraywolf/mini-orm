package br.com.framework.orm.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mapeia uma classe DTO para uma tabela fisica do banco de dados.
 *
 * <pre>{@code
 * @TableDB(name = "TGFPAR")
 * public class ParceiroDTO {
 *     @ColumnDB(localName = "CODPARC")
 *     private Long codigoParceiro;
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TableDB {
    /**
     * Nome fisico da tabela usada na montagem das instrucoes SQL.
     *
     * @return nome da tabela no banco de dados
     */
    String name();
}
