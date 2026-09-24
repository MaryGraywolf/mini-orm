# 🚀 TBM Mini-ORM Framework

Um micro-framework ORM (Object-Relational Mapping) leve, rápido e 100% focado em produtividade. Desenvolvido para eliminar o boilerplate de código JDBC manual, mapear ResultSets automaticamente para DTOs e padronizar chamadas a banco de dados e Procedures (PL/SQL).

## 📦 Instalação

Adicione a dependência no seu `pom.xml`:

```xml
<dependency>
    <groupId>br.com.orm.framework</groupId>
    <artifactId>mini-orm</artifactId>
    <version>1.0.0</version>
</dependency>
```

## ⚙️ Inicialização (Configurando o Log)

A biblioteca é livre de dependências externas. Para que ela registre erros ou lentidão no banco de dados, você deve injetar o seu Logger principal (ex: AppLog ou SLF4J) na inicialização da sua aplicação:

```java
import br.com.tbm.framework.config.OrmConfig;
import br.com.tbm.framework.spi.OrmLogger;

public class AppInitializer {
    public static void init() {
        OrmConfig.setLogger(new OrmLogger() {
            @Override
            public void error(String origin, String code, String msg, Exception e, Object... params) {
                br.com.util.AppLog.error(origin, code, msg, e);
            }
            // ... implementar info e warn
        });
    }
}
```

## 🛠️ Guia Rápido de Uso

### 1. Mapeando suas Entidades (DTOs)

Utilize as anotações `@TableDB` e `@ColumnDB` para mapear os campos da sua classe com as colunas físicas do banco de dados e sequences do Oracle.

```java
import br.com.framework.orm.annotations.TableDB;
import br.com.framework.orm.annotations.ColumnDB;
import lombok.Data;

@Data
@TableDB(name = "CLIENTES")
public class Cliente {

    @ColumnDB(localName = "ID_CLIENTE", sequence = "SEQ_CLIENTE.NEXTVAL", isPrimaryKey = true)
    private Long idCliente;

    @ColumnDB(localName = "NOME")
    private String nome;

    @ColumnDB(localName = "STATUS")
    private StatusCliente enumStatus; // Suporte nativo a Enums!
}
```

### 2. DQL: Consultas (SELECT)

Você pode usar a Fluent API para criar Selects de forma programática ou escrever seu SQL na mão e deixar o framework apenas mapear o resultado.

#### Modo Fluente:

```java
import br.com.framework.orm.core.SQLBuilder;
import br.com.framework.orm.core.SQLResult;

SQLResult result = SQLBuilder.select(Cliente.class)
        .campos("ID_CLIENTE", "NOME") // Opcional: Se omitido, traz todas as colunas
        .where("STATUS = ?", "ATIVO")
        .build();

List<Cliente> clientes = result.executeQuery(conn, Cliente.class);
```

#### Modo SQL Nativo (Trabalhando com dicionários ou tipos primitivos):

```java
// Retorna a contagem (Scalar)
Integer total = new SQLResult("SELECT COUNT(1) FROM CLIENTES WHERE STATUS = ?", Arrays.asList("ATIVO"))
                .executeScalar(conn, Integer.class);

// Retorna as duas primeiras colunas como Chave-Valor (Dictionary)
Map<Long, String> mapaClientes = new SQLResult("SELECT ID_CLIENTE, NOME FROM CLIENTES", null)
        .executeAsDictionary(conn);
```

### 3. DML: Inserções e Atualizações (INSERT / UPDATE)

O framework lê a sua entidade populada e monta o SQL correspondente.

```java
// INSERT
Cliente novo = new Cliente();
novo.setNome("Empresa XYZ");
SQLBuilder.buildInsert(novo).executeUpdate(conn);

// INSERT retornando o ID gerado pela sequence
// Exige @ColumnDB(isPrimaryKey = true) no campo da chave primária.
// Além de retornar o ID, o método preenche o próprio DTO.
Cliente comId = new Cliente();
comId.setNome("Empresa XYZ");
Long id = SQLBuilder.buildInsert(comId).executeAndReturnId(conn, comId);

// UPDATE (Cria o SET apenas com os campos não-nulos populados no DTO)
Cliente update = new Cliente();
update.setEnumStatus(StatusCliente.INATIVO);
SQLBuilder.buildUpdate(update, "ID_CLIENTE = ?", 12345L).executeUpdate(conn);
```

### 4. DML em Lote (Execute Batch)

Perfeito para inserir milhares de registros de uma única vez com altíssima performance.

```java
List<Cliente> lote = new ArrayList<>();
// ... popule milhares de itens na lista ...

SQLBuilder.buildInsertBatch(lote).executeBatch(conn);
```

### 5. Stored Procedures e Functions (PL/SQL)

Execute chamadas complexas no Oracle com facilidade, lidando nativamente com parâmetros de entrada (IN) e saída (OUT).

```java
import br.com.framework.orm.core.PLSQLCall;

PLSQLCall.Result retorno = PLSQLCall.name("STP_PROCESSAR_FECHAMENTO")
        .in(12345L) // Parâmetro 1 (IN)
        .in("FATURAR") // Parâmetro 2 (IN)
        .out("PO_MENSAGEM", java.sql.Types.VARCHAR) // Parâmetro 3 (OUT)
        .out("PO_QTD_PROCESSADA", java.sql.Types.NUMERIC) // Parâmetro 4 (OUT)
        .execute(conn);

String erro = retorno.getString("PO_MENSAGEM");
Long qtd = retorno.getLong("PO_QTD_PROCESSADA");
```

## 🔒 Auditoria e Segurança (Padrão LGPD)

O framework possui rastreabilidade interna de execução através de inspeção de pilha (Stack Trace Injection). Se uma query for muito lenta ou falhar, o Logger registrará a origem exata da classe Java que chamou o comando (ex: FinanceiroDAO.buscarAtrasados), sem gravar os valores reais dos parâmetros (blindagem via Prepared Statements), garantindo que dados sensíveis nunca vazem nos logs do servidor.