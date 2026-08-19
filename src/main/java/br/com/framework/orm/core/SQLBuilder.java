package br.com.framework.orm.core;

import br.com.framework.orm.annotations.TableDB;
import br.com.framework.orm.annotations.ColumnDB;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SQLBuilder {

    private static String descobrirNomeTabela(Class<?> classe) {
        if (classe.isAnnotationPresent(TableDB.class)) {
            return classe.getAnnotation(TableDB.class).name();
        }
        throw new IllegalArgumentException(
                "A classe " + classe.getSimpleName() + " nao possui a anotacao @TableDB no nivel de classe.");
    }

    public static SelectBuilder select(Class<?> classeDTO) {
        return new SelectBuilder(classeDTO);
    }

    public static class SelectBuilder {
        private final Class<?> classeDTO;
        private final List<String> camposFiltro = new ArrayList<>();
        private String clausulaWhere = "";
        private final List<Object> parametros = new ArrayList<>();

        private SelectBuilder(Class<?> classeDTO) {
            this.classeDTO = classeDTO;
        }

        public SelectBuilder campos(String... campos) {
            this.camposFiltro.addAll(Arrays.asList(campos));
            return this;
        }

        public SelectBuilder where(String where, Object... params) {
            this.clausulaWhere = where;
            this.parametros.addAll(Arrays.asList(params));
            return this;
        }

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
