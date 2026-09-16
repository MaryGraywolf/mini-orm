AGENTS.md
==========

Propósito
---------

Arquivo de instruções curto e prático para agentes AI que trabalham neste repositório.

Comandos rápidos
---------------

- Build (Windows): `mvnw.cmd package`
- Build (Unix): `./mvnw package`
- Rodar testes: `mvnw.cmd test` ou `./mvnw test`
- Compilar sem testes: `mvnw.cmd -DskipTests package`

Notas importantes
-----------------

- Projeto Java com Maven (há `pom.xml` na raiz). Use o *Maven Wrapper* (`mvnw`/`mvnw.cmd`).
- O diretório fonte é `src/main/java` e o pacote principal é `br.com.framework.orm`.
- Processamento por anotações gera arquivos em `target/generated-sources/annotations` — não editar arquivos gerados.
- Existem artefatos de compilação em `target/` e metadados em `target/maven-status/`.

Onde olhar
----------

- Documentação do projeto: [README.md](README.md)
- Código principal: `src/main/java/br/com/framework/orm` (subpackages: `annotations`, `config`, `core`, `spi`).

Diretrizes para agentes
-----------------------

- Prefira alterações pequenas e focadas; preserve a estrutura de pacotes e convenções existentes.
- Use ferramentas de navegação Java (Language Server / `java-lsp-tools`) para localizar símbolos com precisão.
- Não altere arquivos em `target/` ou em `target/generated-sources/` — são gerados pela build.
- Ao propor mudanças de API pública, mencione impacto no `pom.xml` e no artefato publicado.

Próximos passos sugeridos
------------------------

- Se desejar, posso criar um arquivo `.github/copilot-instructions.md` com regras específicas de revisão e estilo.
- Posso também gerar skills/agents para tarefas recorrentes (build, testes, geração de Javadoc, revisão de PRs).

Checklist de revisão de segurança
--------------------------------

- **Logger de produção:** garantir que `OrmConfig.setLogger(...)` seja chamado na inicialização da aplicação com um logger corporativo (SLF4J/Logback). O logger padrão não deve ser usado em produção.
- **Não logar SQL com valores sensíveis:** logs só devem conter placeholders (`?`) ou metadados (contagem de parâmetros), nunca concatenar valores sensíveis em strings de consulta.
- **Evitar printStackTrace em produção:** remover `e.printStackTrace()` e imprimir apenas mensagens de erro sanitizadas ou enviar o `Exception` para o logger que controla formatação/retenção.
- **PreparedStatements obrigatórios:** prefira sempre `PreparedStatement`/`CallableStatement` e evite construir `WHERE` por concatenação de valores.
- **Varredura de dependências:** executar OWASP Dependency-Check regularmente em CI para detectar bibliotecas vulneráveis.
- **Análise estática:** incluir SpotBugs/PMD no pipeline para encontrar problemas comuns de segurança e código.
- **Secrets e configuração:** garantir que segredos não estejam no repositório e usar cofre (Vault, Azure Key Vault, AWS Secrets Manager) ou variáveis de ambiente seguras.
- **Documentar práticas seguras:** adicionar este checklist ao `AGENTS.md` e ao README do projeto para novos contribuidores.

