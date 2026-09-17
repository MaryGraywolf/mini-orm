package br.com.framework.orm.core;

import br.com.framework.orm.annotations.TableDB;
import br.com.framework.orm.annotations.ColumnDB;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gera instrucoes SQL dinamicas para DTOs anotados com {@link TableDB} e
 * {@link ColumnDB}.
 * <p>
 * Esta classe implementa um mini-ORM hibrido: automatiza a montagem mecanica de
 * {@code INSERT}, {@code UPDATE} e {@code SELECT} por reflection leve, mas
 * mantem a clausula {@code WHERE} sob controle explicito do desenvolvedor.
 * </p>
 * <p>
 * A estrategia Anti-Null ignora atributos nulos em escritas parciais. No
 * {@code INSERT}, atributos nulos com {@link ColumnDB#sequence()} preenchida
 * entram como expressao SQL direta, por exemplo {@code SEQ_TABELA.NEXTVAL}.
 * </p>
 *
 * @author Maria Lima
 * @since 2026
 */
public class SQLBuilder {

    /**
	 * Resolve o nome fisico da tabela configurado no DTO.
	 *
	 * @param classe classe que deve possuir {@link TableDB}
	 * @return nome da tabela definido em {@link TableDB#name()}
	 * @throws IllegalArgumentException se a classe nao possuir {@link TableDB}
	 */
	private static String descobrirNomeTabela(Class<?> classe) {
		if (classe.isAnnotationPresent(TableDB.class)) {
			return classe.getAnnotation(TableDB.class).name();
		}
		throw new IllegalArgumentException(
				"A classe " + classe.getSimpleName() + " nao possui a anotacao @TableDB no nivel de classe.");
	}

    /**
     * Inicia a montagem fluente de um {@code SELECT} para o DTO informado.
     * <p>
     * Use esta forma quando quiser escolher somente alguns atributos do DTO ou
     * encadear a clausula {@code WHERE} antes de chamar {@link SelectBuilder#build()}.
     * </p>
     *
     * <pre>{@code
     * SQLResult sql = SQLBuilder
     *         .select(ClienteDTO.class)
     *         .campos("id", "nome")
     *         .where("ATIVO = ?", "S")
     *         .build();
     *
     * List<ClienteDTO> clientes = sql.executeQuery(conn, ClienteDTO.class);
     * }</pre>
     *
     * @param classeDTO classe anotada com {@link TableDB} e campos {@link ColumnDB}
     * @return builder de SELECT para escolher campos, filtro e montar o
     *         {@link SQLResult}
     */
	public static SelectBuilder select(Class<?> classeDTO) {
		return new SelectBuilder(classeDTO);
	}

    /**
     * Builder fluente para montar consultas {@code SELECT} com projecao opcional
     * de campos.
     */
    public static class SelectBuilder {
        private final Class<?> classeDTO;
        private final List<String> camposFiltro = new ArrayList<>();
        private String clausulaWhere = "";
        private final List<Object> parametros = new ArrayList<>();

        private SelectBuilder(Class<?> classeDTO) {
            this.classeDTO = classeDTO;
        }

        /**
         * Restringe o {@code SELECT} aos atributos Java informados.
         * <p>
         * Os nomes recebidos devem ser os nomes dos campos do DTO. O builder
         * converte cada campo para a coluna fisica configurada em
         * {@link ColumnDB#localName()}.
         * </p>
         *
         * <pre>{@code
         * SQLResult sql = SQLBuilder
         *         .select(ClienteDTO.class)
         *         .campos("id", "nome", "email")
         *         .build();
         * }</pre>
         *
         * @param campos nomes dos atributos do DTO que devem entrar no SELECT
         * @return o proprio builder para encadeamento fluente
         */
        public SelectBuilder campos(String... campos) {
            this.camposFiltro.addAll(Arrays.asList(campos));
            return this;
        }

        /**
         * Define a clausula {@code WHERE} da consulta e seus parametros.
         * <p>
         * Informe apenas o filtro, sem a palavra {@code WHERE}. Os valores em
         * {@code params} sao aplicados aos placeholders {@code ?} na mesma ordem.
         * </p>
         *
         * <pre>{@code
         * SQLResult sql = SQLBuilder
         *         .select(PedidoDTO.class)
         *         .where("NUNOTA = ? AND STATUS = ?", nuNota, "PENDENTE")
         *         .build();
         * }</pre>
         *
         * @param where  trecho SQL do filtro, sem a palavra {@code WHERE}
         * @param params valores que preencherao os placeholders {@code ?} do filtro
         * @return o proprio builder para encadeamento fluente
         */
        public SelectBuilder where(String where, Object... params) {
            this.clausulaWhere = where;
            this.parametros.addAll(Arrays.asList(params));
            return this;
        }

        /**
         * Monta o {@link SQLResult} final a partir da tabela, colunas e filtros
         * configurados.
         * <p>
         * Depois de montado, o {@link SQLResult} pode ser executado como lista de
         * DTOs, mapa, escalar ou dicionario.
         * </p>
         *
         * <pre>{@code
         * SQLResult sql = SQLBuilder
         *         .select(ProdutoDTO.class)
         *         .campos("codigo", "descricao")
         *         .where("CODGRUPOPROD = ?", codigoGrupo)
         *         .build();
         * }</pre>
         *
         * @return SQL e parametros prontos para execucao
         * @throws IllegalArgumentException se o DTO nao possuir {@link TableDB}
         * @throws IllegalStateException    se nenhum campo anotado puder ser
         *                                  projetado
         */
        public SQLResult build() {
            String nomeTabela = descobrirNomeTabela(classeDTO);
            List<String> colunasParaSelect = new ArrayList<>();

            for (Field field : classeDTO.getDeclaredFields()) {
                if (field.isAnnotationPresent(ColumnDB.class)) {
                    if (!camposFiltro.isEmpty() && !camposFiltro.contains(field.getName())) {
                        continue;
                    }
                    colunasParaSelect.add(field.getAnnotation(ColumnDB.class).localName());
                }
            }

            if (colunasParaSelect.isEmpty()) {
                throw new IllegalStateException("Nenhuma coluna mapeada para o SELECT.");
            }

            String colunasSql = String.join(", ", colunasParaSelect);
            StringBuilder sql = new StringBuilder("SELECT ").append(colunasSql).append(" FROM ").append(nomeTabela);

            if (!clausulaWhere.isEmpty()) {
                sql.append(" WHERE ").append(clausulaWhere);
            }

            return new SQLResult(sql.toString(), parametros);
        }
    }

    /**
	 * Monta um {@code INSERT INTO} a partir dos campos anotados do DTO.
	 * <p>
	 * Campos com valor nao nulo sao adicionados como placeholders {@code ?} e seus
	 * valores entram na lista de parametros do {@link SQLResult}. Campos nulos sao
	 * ignorados, exceto quando a anotacao {@link ColumnDB} define uma sequence;
	 * nesse caso, a sequence e inserida diretamente no trecho {@code VALUES}.
	 * </p>
     *
     * <pre>{@code
     * ClienteDTO novoCliente = new ClienteDTO();
     * novoCliente.setNome("Maria");
     * novoCliente.setEmail("maria@email.com");
     *
     * SQLResult sql = SQLBuilder.buildInsert(novoCliente);
     * int linhasInseridas = sql.executeUpdate(conn);
     * }</pre>
	 *
	 * @param dto instancia anotada com {@link TableDB} e atributos persistiveis
	 *            anotados com {@link ColumnDB}
	 * @return SQL e parametros prontos para execucao via
	 *         {@link java.sql.PreparedStatement}
	 * @throws IllegalArgumentException se a classe do DTO nao possuir
	 *                                  {@link TableDB}
	 * @throws IllegalStateException    se nenhum campo puder ser usado no INSERT
	 * @throws RuntimeException         se ocorrer falha ao acessar atributos por
	 *                                  reflection
	 */
    public static SQLResult buildInsert(Object dto) {

        Class<?> classe = dto.getClass();
        String nomeTabela = descobrirNomeTabela(classe);
        StringBuilder sql = new StringBuilder("INSERT INTO " + nomeTabela + " (");
        List<String> colunas = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        Field[] fields = classe.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(ColumnDB.class)) {
                try {
                    field.setAccessible(true);
                    Object valor = field.get(dto);
                    ColumnDB anotacao = field.getAnnotation(ColumnDB.class);

                    if (valor != null) {
                        // TRATATIVA DE ENUM
                        if (field.getType().isEnum()) {
                            valor = ((Enum<?>) valor).name();
                        }
                        colunas.add(anotacao.localName());
                        placeholders.add("?");
                        params.add(valor);
                    } else if (!anotacao.sequence().isEmpty()) {
                        colunas.add(anotacao.localName());
                        placeholders.add(anotacao.sequence());
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Erro ao gerar INSERT", e);
                }
            }
        }

        if (colunas.isEmpty()) {
            throw new IllegalStateException("Nenhum campo populado encontrado para realizar o INSERT.");
        }

        sql.append(String.join(", ", colunas)).append(") VALUES (").append(String.join(", ", placeholders)).append(")");

        return new SQLResult(sql.toString(), params);
    }

    /**
	 * Monta um {@code UPDATE} parcial a partir dos campos nao nulos do DTO.
	 * <p>
	 * O trecho {@code SET} e gerado automaticamente com base nos atributos anotados
	 * e preenchidos. A clausula {@code WHERE} permanece explicita para permitir
	 * filtros complexos do dominio Sankhya/Oracle, mas seus valores devem ser
	 * enviados por {@code whereParams}.
	 * </p>
	 *
	 * <pre>{@code
	 * SolicitacaoRevAdmDTO patch = SolicitacaoRevAdmDTO.builder()
	 * 		.dhResolucao(new Timestamp(System.currentTimeMillis())).build();
	 *
	 * SQLResult result = SQLBuilder.buildUpdate(patch, "ID = ?", id);
	 * }</pre>
     *
     * <p>
     * Para executar a atualizacao:
     * </p>
     *
     * <pre>{@code
     * int linhasAtualizadas = result.executeUpdate(conn);
     * }</pre>
	 *
	 * @param dto           DTO parcial contendo somente os campos que devem ser
	 *                      alterados
	 * @param whereClausula clausula SQL do filtro, preferencialmente constante e
	 *                      com placeholders {@code ?}
	 * @param whereParams   valores que preencherao os placeholders do
	 *                      {@code WHERE}, na ordem exata
	 * @return SQL e parametros prontos para execucao via
	 *         {@link java.sql.PreparedStatement}
	 * @throws IllegalArgumentException se a classe do DTO nao possuir
	 *                                  {@link TableDB}
	 * @throws IllegalStateException    se nenhum campo nao nulo existir para o
	 *                                  {@code SET}
	 * @throws RuntimeException         se ocorrer falha ao acessar atributos por
	 *                                  reflection
	 */
    public static SQLResult buildUpdate(Object dto, String whereClausula, Object... whereParams) {

        String nomeTabela = descobrirNomeTabela(dto.getClass());
        StringBuilder sql = new StringBuilder("UPDATE " + nomeTabela + " SET ");
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        Field[] fields = dto.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(ColumnDB.class)) {
                try {
                    field.setAccessible(true);
                    Object valor = field.get(dto);

                    if (valor != null) {
                        if (field.getType().isEnum()) {
                            valor = ((Enum<?>) valor).name();
                        }
                        ColumnDB anotacao = field.getAnnotation(ColumnDB.class);
                        setClauses.add(anotacao.localName() + " = ?");
                        params.add(valor);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Erro ao gerar UPDATE", e);
                }
            }
        }

        if (setClauses.isEmpty()) {
            throw new IllegalStateException("Nenhum campo populado encontrado para realizar o UPDATE.");
        }

        sql.append(String.join(", ", setClauses));

        if (whereClausula != null && !whereClausula.trim().isEmpty()) {
            sql.append(" WHERE ").append(whereClausula);
            if (whereParams != null) {
                for (Object param : whereParams) {
                    params.add(param);
                }
            }
        } else {
            throw new IllegalArgumentException("Camada de WHERE é obrigatória para essa operação de UPDATE.");
        }

        return new SQLResult(sql.toString(), params);
    }

    /**
	 * Monta um {@code SELECT} projetando somente as colunas anotadas no DTO.
	 * <p>
	 * A tabela e obtida de {@link TableDB#name()} e a lista de colunas nasce dos
	 * atributos anotados com {@link ColumnDB}. Isso evita {@code SELECT *} e mantem
	 * a consulta alinhada ao contrato do DTO.
	 * </p>
	 *
	 * <pre>{@code
	 * SQLResult result = SQLBuilder.buildSelect(SolicitacaoRevAdmDTO.class, "NUNOTA = ? ORDER BY ID DESC", nuNota);
	 * }</pre>
     *
     * <p>
     * Exemplo executando a consulta:
     * </p>
     *
     * <pre>{@code
     * SQLResult sql = SQLBuilder.buildSelect(ClienteDTO.class, "ATIVO = ?", "S");
     * List<ClienteDTO> clientesAtivos = sql.executeQuery(conn, ClienteDTO.class);
     * }</pre>
	 *
	 * @param classeDTO     classe do DTO anotada com {@link TableDB}
	 * @param whereClausula filtro opcional; quando {@code null} ou vazio, o SELECT
	 *                      e gerado sem {@code WHERE}
	 * @param whereParams   valores que preencherao os placeholders do filtro
	 * @return SQL e parametros prontos para execucao via
	 *         {@link java.sql.PreparedStatement}
	 * @throws IllegalArgumentException se a classe do DTO nao possuir
	 *                                  {@link TableDB}
	 * @throws IllegalStateException    se a classe nao possuir campos anotados com
	 *                                  {@link ColumnDB}
	 */
    public static SQLResult buildSelect(Class<?> classeDTO, String whereClausula, Object... whereParams) {
        List<String> colunas = new ArrayList<>();
        String nomeTabela = descobrirNomeTabela(classeDTO);

        Field[] fields = classeDTO.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(ColumnDB.class)) {
                ColumnDB anotacao = field.getAnnotation(ColumnDB.class);
                colunas.add(anotacao.localName());
            }
        }

        if (colunas.isEmpty()) {
            throw new IllegalStateException(
                    "A classe " + classeDTO.getSimpleName() + " não possui nenhuma coluna mapeada com @ColumnDB.");
        }

        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", colunas)).append(" FROM ")
                .append(nomeTabela);

        List<Object> params = new ArrayList<>();
        if (whereClausula != null && !whereClausula.trim().isEmpty()) {
            sql.append(" WHERE ").append(whereClausula);
            if (whereParams != null) {
                for (Object param : whereParams) {
                    params.add(param);
                }
            }
        }

        return new SQLResult(sql.toString(), params);
    }

    /**
	 * Monta um {@code INSERT} em lote para uma lista de DTOs do mesmo tipo.
	 * <p>
	 * A SQL e definida uma unica vez a partir da classe do primeiro item da lista.
	 * Para campos anotados com {@link ColumnDB#sequence()}, o metodo usa a sequence
	 * diretamente quando o valor do primeiro DTO estiver nulo. Os demais campos sao
	 * mapeados como placeholders {@code ?} e geram uma lista de parametros por
	 * linha.
	 * </p>
	 * <p>
	 * O retorno e um {@link SQLResult.Batch}, que executa internamente
	 * {@link java.sql.PreparedStatement#addBatch()} e
	 * {@link java.sql.PreparedStatement#executeBatch()}.
	 * </p>
     *
     * <pre>{@code
     * List<ClienteDTO> clientes = Arrays.asList(clienteUm, clienteDois);
     *
     * SQLResult.Batch batch = SQLBuilder.buildInsertBatch(clientes);
     * int[] resultados = batch.executeBatch(conn);
     * }</pre>
	 *
	 * @param dtos lista nao vazia de DTOs anotados com {@link TableDB} e
	 *             {@link ColumnDB}
	 * @return comando batch pronto para execucao em uma conexao JDBC
	 * @throws IllegalArgumentException se a lista for nula, vazia ou se a classe do
	 *                                  primeiro DTO nao possuir {@link TableDB}
	 * @throws RuntimeException         se ocorrer falha ao acessar atributos por
	 *                                  reflection
	 */
    public static SQLResult.Batch buildInsertBatch(List<?> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new IllegalArgumentException(
                    "A lista de DTOs fornecida para a operação em lote não pode estar vazia.");
        }

        Class<?> classeDTO = dtos.get(0).getClass();
        String nomeTabela = descobrirNomeTabela(classeDTO);

        StringBuilder sql = new StringBuilder("INSERT INTO " + nomeTabela + " (");
        List<String> colunas = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Field> camposMapeadosParaBind = new ArrayList<>();

        Object primeiroDto = dtos.get(0);

        for (Field field : classeDTO.getDeclaredFields()) {
            if (field.isAnnotationPresent(ColumnDB.class)) {
                ColumnDB anotacao = field.getAnnotation(ColumnDB.class);
                colunas.add(anotacao.localName());

                try {
                    field.setAccessible(true);
                    Object valorNoPrimeiroItem = field.get(primeiroDto);

                    if (valorNoPrimeiroItem == null && !anotacao.sequence().isEmpty()) {
                        placeholders.add(anotacao.sequence());
                    } else {
                        placeholders.add("?");
                        camposMapeadosParaBind.add(field);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Erro ao inspecionar o primeiro item do Batch", e);
                }
            }
        }

        sql.append(String.join(", ", colunas)).append(") VALUES (").append(String.join(", ", placeholders)).append(")");

        List<List<Object>> batchParams = new ArrayList<>();
        for (Object dto : dtos) {
            List<Object> paramsDestaLinha = new ArrayList<>();
            for (Field field : camposMapeadosParaBind) {
                try {
                    field.setAccessible(true);
                    Object valorLote = field.get(dto);

                    if (valorLote != null && field.getType().isEnum()) {
                        valorLote = ((Enum<?>) valorLote).name();
                    }

                    paramsDestaLinha.add(valorLote);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Erro ao mapear parâmetros de linha para o lote", e);
                }
            }
            batchParams.add(paramsDestaLinha);
        }

        return new SQLResult.Batch(sql.toString(), batchParams);
    }
}
