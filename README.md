# Health Now Integrator

Aplicação Java responsável pela integração de serviços do Health Now.

## Requisitos

- Java 8
- Maven 3.6 ou superior
- PostgreSQL (quando a integração com banco de dados for configurada)

## Compilar

```bash
mvn clean compile
```

## Executar

```bash
mvn clean package
java -jar target/integrator-1.0.0-jar-with-dependencies.jar
```

## Dependências

O projeto utiliza o driver JDBC do PostgreSQL (`org.postgresql:postgresql`).

## Extração local

Após informar a conexão e clicar em Próximo, o integrador consulta o banco em modo
somente leitura e gera arquivos JSONL, manifesto, relatório de reconciliação e um ZIP
em `exports/<run_id>/`. O andamento aparece na tela. Não há envio ao S3 nesta etapa.

Configuração, consultas, relacionamentos e testes: [Database extraction](docs/DATABASE_EXTRACTION.md).
